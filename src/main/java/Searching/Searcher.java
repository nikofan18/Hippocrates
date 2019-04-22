package Searching;

import Utilities.PathManager;
import Utilities.SharedUtilities;
import mitos.stemmer.Stemmer;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.lucene.wordnet.SynonymMap;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/*
 * A class that provides the appropriate fields and methods to find relevant
 * documents to a query, using an inverted index and vector space model
 */
public class Searcher {

    // Fields

    /*
     * Will store the whole vocabulary. Holds information like this:
     * term1 -> [df1, ptrToPost1, postDataSize1]
     * term2 -> [df2, ptrToPost2, postDataSize2]
     * ...
     */
    private HashMap<String, MutableTriple<Long, Long, Integer>> vocMap;

    /*
     * The documents and posting random access files
     */
    private RandomAccessFile doc, post;

    /*
     * Important words in topics.xml file
     */
    private HashSet<String> topicImp;

    /*
     * To use synonyms in query expansion, using a WordNet dictionary
     */
    private SynonymMap synMap;

    // Constructor

    /*
     * Initialize things, load vocabulary etc.
     */
    public Searcher() throws IOException {

        /* Open index files */
        RandomAccessFile voc = new RandomAccessFile(
                PathManager.getIndexDirPath() + "/VocabularyFile.txt", "rw"
        );
        post = new RandomAccessFile(PathManager.getIndexDirPath() + "/PostingFile.txt", "rw");
        doc = new RandomAccessFile(PathManager.getIndexDirPath() + "/DocumentsFile.txt", "rw");

        /* Load vocabulary in memory */
        vocMap = new HashMap<>();
        while(!SharedUtilities.getInstance().isEOFReached(voc)) {
            String term = voc.readUTF();
            MutableTriple<Long, Long, Integer> triple = new MutableTriple<>(
                    voc.readLong(), voc.readLong(), voc.readInt()
            );
            vocMap.put(term, triple);
        }
        voc.close();

        /* Load important words of topics.xml file */
        topicImp = new HashSet<>(SharedUtilities.getInstance().parseWords(
                PathManager.getWordsPath() + "/importantInTopics.txt")
        );

        /* Initialize synonym map using the appropriate WordNet prolog file */
        synMap = new SynonymMap(new FileInputStream("WordNet/wn_s.pl"));

        Stemmer.Initialize();

        SharedUtilities.getInstance().docsNum = doc.readLong(); // total documents number

    }

    // Methods

    /*
     * Do searching for a query using vector space model and
     * return a JSON object with the results
     */
    public JSONObject search(String query, String type) throws IOException {
        JSONObject answer = new JSONObject();
        double maxTF = 0.0;
        HashMap<String, MutableTriple<String, HashMap<String, Double>, Double>> docHm = new HashMap<>();
        HashMap<String, Double> queryHm = new HashMap<>();

        ArrayList<String> queryTokens = makeQueryTokens(query, type);

        long startTime = System.nanoTime();

        if(!queryTokens.isEmpty()) {

            for (String token : queryTokens) {

                if (vocMap.containsKey(token)) {

                    /* For document vector */
                    long df = vocMap.get(token).getLeft(); // term's df
                    double idf = Math.log(SharedUtilities.getInstance().docsNum / (double) df) / Math.log(2.0);
                    long ptrToPost = vocMap.get(token).getMiddle(); // ptr to posting data
                    int pdSz = vocMap.get(token).getRight(); // posting data size
                    post.seek(ptrToPost);
                    do {
                        String docId = post.readUTF();
                        double tf = post.readDouble();
                        long ptrToDoc = post.readLong();
                        doc.seek(ptrToDoc);

                        doc.readUTF(); // skip docId, we already have it
                        String fullPath = doc.readUTF();
                        double docVecLen = doc.readDouble();

                        double weight = tf * idf;

                        if (!docHm.containsKey(docId)) {
                            HashMap<String, Double> hm = new HashMap<>();
                            hm.put(token, weight);
                            MutableTriple<String, HashMap<String, Double>, Double> p =
                                    new MutableTriple<>(fullPath, hm, docVecLen);
                            docHm.put(docId, p);
                        } else {
                            docHm.get(docId).getMiddle().put(token, weight);
                        }
                    } while (post.getFilePointer() != pdSz + ptrToPost);
                }

                /* For query vector */
                double nonNormTF = (double) Collections.frequency(queryTokens, token);
                if (nonNormTF > maxTF)
                    maxTF = nonNormTF;
                queryHm.put(token, nonNormTF); // for now, just store the non-normalized tf values
            }

            /* Put final weights in query vector and compute its length */
            double queryVecLen = 0.0;
            for (String token : queryTokens) {
                double weight = 0.0;
                if (vocMap.containsKey(token)) {
                    double normTF = queryHm.get(token) / maxTF; // normalize tf
                    long df = vocMap.get(token).getLeft();
                    double idf = Math.log(SharedUtilities.getInstance().docsNum / (double) df) / Math.log(2.0);
                    weight = normTF * idf;
                    queryVecLen += weight * weight;
                }
                queryHm.put(token, weight);
            }
            queryVecLen = Math.sqrt(queryVecLen);

            /* Compute the score (cosine similarity) for each document */
            List<MutablePair<String, Double>> docList = new ArrayList<>();
            for (String docId : docHm.keySet()) {
                Double docVecLen = docHm.get(docId).getRight();
                double cross = 0.0;
                for (String term : queryHm.keySet()) {
                    if (docHm.get(docId).getMiddle().containsKey(term))
                        cross += queryHm.get(term) * docHm.get(docId).getMiddle().get(term);
                }

                double score = 0.0;
                if (queryVecLen != 0) {
                    score = cross / (docVecLen * queryVecLen);
                }

                docList.add(new MutablePair<>(docHm.get(docId).getLeft(), score));

            }

            /* Sort documents by score */
            docList.sort(
                    (MutablePair<String, Double> p1, MutablePair<String, Double> p2) ->
                    {
                        if (p1.getRight() > p2.getRight())
                            return 1;
                        else if (p1.getRight() < p2.getRight())
                            return -1;
                        else
                            return 0;
                    });

            /* Put the answer in the JSON object */
            Iterator<MutablePair<String, Double>> docListIterator = docList.iterator();
            MutablePair<String, Double> p;
            int counter = 0;
            while (docListIterator.hasNext()) {
                JSONObject docObj = new JSONObject();
                p = docListIterator.next();
                docObj.put("name", p.left.substring(p.left.lastIndexOf("/") + 1));
                docObj.put("path", p.left.substring(p.left.lastIndexOf("/MedicalCollection")));
                docObj.put("score", p.right);
                answer.put("doc" + counter++, docObj);
            }

        }

        long endTime = System.nanoTime();

        double searchTime = (endTime - startTime) / 1000000.0;
        searchTime = BigDecimal.valueOf(searchTime).setScale(3, RoundingMode.HALF_UP).doubleValue();

        int resultsNum = answer.length();
        answer.put("time", searchTime);
        answer.put("results", resultsNum);
        return answer;
    }


    /*
     * Takes a query and the type of the searching, makes the appropriate
     * processing and returns a collection with the query's tokens
     */
    private ArrayList<String> makeQueryTokens(String query, String type) throws IOException {

        String delimiter = "\t\n\r\f ";
        ArrayList<String> ret = new ArrayList<>();
        boolean isTypeGiven = false;
        type = type.toLowerCase();
        if(type.equals("diagnosis") || type.equals("test") || type.equals("treatment"))
            isTypeGiven = true;

        query = SharedUtilities.getInstance().doLexicalAnalysis(query); // do lexical analysis

        StringTokenizer tokenizer = new StringTokenizer(query, delimiter);
        while (tokenizer.hasMoreTokens()) {
            String currentToken = tokenizer.nextToken();
            if (!SharedUtilities.getInstance().enSwSet.contains(currentToken)
                    && !SharedUtilities.getInstance().grSwSet.contains(currentToken)) { // accept only non-stopwords

                if(isTypeGiven) {
                    if(!topicImp.contains(currentToken))
                        continue; // keep only topic important words (medical terms, diseases etc.)
                    String[] synonyms = synMap.getSynonyms(currentToken);
                    if(synonyms.length != 0)
                        ret.add(Stemmer.Stem(synonyms[0])); // add one synonym after doing stemming on it
                }

                currentToken = Stemmer.Stem(currentToken); // do stemming
                ret.add(currentToken);
            }
        }

        if(isTypeGiven)
            ret.add(Stemmer.Stem(type)); // add the type as a word

        return ret;
    }

}
