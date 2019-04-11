package Searching;

import Utilities.PathManager;
import Utilities.SharedUtilities;
import mitos.stemmer.Stemmer;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.json.JSONObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.StringTokenizer;

public class Searcher {

    private HashMap<String, MutableTriple<Long, Long, Integer>> vocMap;

    private RandomAccessFile doc, voc, post;

    public Searcher() throws IOException {

        /* Open index files */
        voc = new RandomAccessFile(PathManager.getIndexDirPath() + "/VocabularyFile.txt", "rw");
        post = new RandomAccessFile(PathManager.getIndexDirPath() + "/PostingFile.txt", "rw");
        doc = new RandomAccessFile(PathManager.getIndexDirPath() + "/DocumentsFile.txt", "rw");

        /* Load vocabulary in memory */
        vocMap = new HashMap<>();
        while(!isEOFReached(voc)) {
            String term = voc.readUTF();
            MutableTriple<Long, Long, Integer> triple = new MutableTriple<>(
                    voc.readLong(), voc.readLong(), voc.readInt()
            );
            vocMap.put(term, triple);
        }

        Stemmer.Initialize();

        SharedUtilities.getInstance().docsNum = doc.readLong(); // docs num

    }

    /*
     * Do searching for a query using vector space model and
     * return a JSON object with the results
     */
    public JSONObject search(String query) throws IOException {
        JSONObject obj = null;
        double qLen;
        double docLen;
        ArrayList<String> tokens = getLexicalAnalysedTokens(query);

        if(!tokens.isEmpty()) {

//            for(String token : tokens) {
//
//            }
//
//            post.seek(vocMap());
//            qLen = computeQueryVectorLength(tokens);
//
//            docLen = 1;


        } else {
            // handle empty query
        }

        return obj;
    }

    private double computeQueryVectorLength(ArrayList<String> tokens) throws IOException {

        long df;
        double idf, nonNormTF, maxTF = 0.0, result = 0.0;
        HashMap<String, Double> weightMap = new HashMap<>();

        /* Compute non-normalized tf for every token */
        for(String token : tokens) {
            nonNormTF = Collections.frequency(tokens, token);
            if (nonNormTF > maxTF)
                maxTF = nonNormTF;
            weightMap.put(token, nonNormTF);
        }

        /* Compute tf * idf weight for each token */
        for(String token : tokens) {
            df = vocMap.get(token).getLeft();
            if(df != 0) {
                idf = Math.log(SharedUtilities.getInstance().docsNum / (double)df) / Math.log(2.0);
            } else {
                idf = 0;
            }
            double normTF = weightMap.get(token) / maxTF;
            weightMap.put(token, normTF * idf);
        }

        /* Produce result */
        for(String token : weightMap.keySet()) {
            result += weightMap.get(token) * weightMap.get(token);
        }

        return Math.sqrt(result);
    }

    /*
     * Performs lexical analysis on query and returns its tokens
     */
    private ArrayList<String> getLexicalAnalysedTokens(String query) throws IOException {

        String delimiter = "\t\n\r\f ";
        ArrayList<String> ret = new ArrayList<>();
        query = SharedUtilities.getInstance().doLexicalAnalysis(query); // do lexical analysis
        StringTokenizer tokenizer = new StringTokenizer(query, delimiter);
        while(tokenizer.hasMoreTokens()) {
            String currentToken = tokenizer.nextToken();
            if(!SharedUtilities.getInstance().enSwSet.contains(currentToken)
                    && !SharedUtilities.getInstance().grSwSet.contains(currentToken)) { // accept only non-stopwords
                currentToken = Stemmer.Stem(currentToken); // do stemming
                ret.add(currentToken);
            }
        }

        return ret;
    }

    /*
     * Helper function to check if a random access file has reached the EOF
     */
    private boolean isEOFReached(RandomAccessFile f) throws IOException {
        boolean ret = false;
        long prevPtr = f.getFilePointer();
        if(f.read() == -1) {
            ret = true;
        }
        f.seek(prevPtr);
        return ret;
    }

}
