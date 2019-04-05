package Indexing;

import Utilities.PathManager;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.lang3.tuple.MutablePair;

public class Indexer {

    // Fields

    /* The tokenInfo TreeMap holds information like this:
     * token1 -> {doc1, tf} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> {doc2, tf} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> ...
     * token2 ...
     */
    private TreeMap<String, HashMap<String, MutablePair<Integer, HashMap<String, Integer>>>> tokenInfo;

    /*
     * The docInfo TreeMap holds information like this:
     * <docId1, docFullPath1, docVecLen1>
     * <docId2, docFullPath2, docVecLen2>
     * ...
     */
    private TreeMap<String, MutablePair<String, Double>> docInfo;

    /*
     * Sets with english and greek stopwords
     */
    private HashSet<String> enSwSet, grSwSet;

    /*
     * Max terms of a partial index
     */
    private Integer piThreshold;

    /*
     * Current partial index number
     */
    private Integer piCurrentNum;

    /*
     * Use as a queue to store partial index file suffix names
     * (e.g. PartialVocabularyFile4.txt -> "4")
     */
    private LinkedList<String> piFileSuffixes;

    // Constructor

    /*
     * Initialize things (stemmer, stopword lists etc.)
     */
    public Indexer() throws IOException {
        tokenInfo = new TreeMap<>();
        docInfo = new TreeMap<>();
        piFileSuffixes = new LinkedList<>();
        Stemmer.Initialize();
        enSwSet = new HashSet<>(parseStopwords(PathManager.getStopwordsPath() + "/stopwordsEn.txt"));
        grSwSet = new HashSet<>(parseStopwords(PathManager.getStopwordsPath() + "/stopwordsGr.txt"));
        piThreshold = 60000; /* TODO: find a statistical way to determine threshold */
        piCurrentNum = -1;
    }

    // Methods

    /*
     * Perform all the necessary actions to produce the index
     * from the collection given by path (it may be a single file or a directory)
     */
    public void index(String path) throws IOException {
        System.out.println("Indexing " + path + " ...");
        new File(System.getProperty("user.dir") + "/CollectionIndex").mkdir();
        PathManager.setExtraPath(path);
        File f = new File(PathManager.getCollectionPath() + PathManager.getExtraPath());
        parseRecursively(f);
        if(tokenInfo.size() > 0)
            createPartialIndex(); // Create the last partial index
        System.out.println("Files Indexed: " + PathManager.fileNames);
    }

    /*
     * Return a list with all stopwords read from file with path = path
     */
    private List<String> parseStopwords(String path) throws IOException {
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    /*
     * Perform the appropriate lexical analysis actions on a string
     */
    private String doLexicalAnalysis(String str){
        String result;

        result = str.replaceAll("['´῾᾽\"]",""); // Ignore apostrophes
        result = result.replaceAll("[\\p{Punct}]", " "); // Replace punctuations with space character

        return result;
    }

    /*
     * For a given file with path = path, parse its tag contents
     */
    private void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", doLexicalAnalysis(xmlFile.getTitle()));
        tagPairs.put("pmcid", xmlFile.getPMCID());
        tagPairs.put("abstract", doLexicalAnalysis(xmlFile.getAbstr()));
        tagPairs.put("body", doLexicalAnalysis(xmlFile.getBody()));
        tagPairs.put("journal", doLexicalAnalysis(xmlFile.getJournal()));
        tagPairs.put("publisher", doLexicalAnalysis(xmlFile.getPublisher()));
        int counter = 0; // Used to ensure that every author key in this doc is different
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors_" + counter++, doLexicalAnalysis(entry));
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories_" + counter++, doLexicalAnalysis(entry));
        }
        populateTokenInfo(tagPairs, xmlFile.getPMCID());
        populateDocInfo(xmlFile.getPMCID(), path);
    }

    /*
     * Put a new triple <docId, docFullPath, docVecLen (currently = 0)> inside docInfo TreeMap
     */
    private void populateDocInfo(String docId, String fullPath) {
        MutablePair<String, Double> p = new MutablePair<>(fullPath, 0.0);
        docInfo.put(docId, p);
    }

    /*
     * Fill document vector length values in docInfo using
     * tokenInfo and tf * idf weights
     */
    private void computeDocumentVectorLengths() {

        // Compute sqrt(tf_1 * idf_1) + sqrt(tf_2 * idf_2) + ... + sqrt(tf_k * idf_k) for every document (with k terms)
        for(String token : tokenInfo.keySet()) {
            for(String docId : tokenInfo.get(token).keySet()) {
                int tf = tokenInfo.get(token).get(docId).getLeft();
                int df = tokenInfo.get(token).size();
                int docsNum = docInfo.size();
                double idf = Math.log((float)docsNum / df) / Math.log(2.0);
                docInfo.get(docId).setRight(docInfo.get(docId).getRight() + Math.sqrt(tf * idf));
            }
        }

        // Square the results when sum computation is finished
        for(String docId : docInfo.keySet()) {
            docInfo.get(docId).setRight(Math.sqrt(docInfo.get(docId).getRight()));
        }
    }

    /*
     * Read a HashMap of pairs of type <tagName, tagContent> coming from a file in path = path,
     * do tokenization, stopword removal, stemming and populate tokenInfo TreeMap with new tokens
     */
    private void populateTokenInfo(HashMap<String, String> tagPairs, String docId) throws IOException {

        String delimiter = "\t\n\r\f ";
        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken().toLowerCase(); // Convert to lower case
                if(!enSwSet.contains(currentToken) && !grSwSet.contains(currentToken)) { // Accept only non-stopwords
                    currentToken = Stemmer.Stem(currentToken); // Do stemming
                    if (tokenInfo.containsKey(currentToken)) {
                        if (tokenInfo.get(currentToken).containsKey(docId)) {
                            int newTF = tokenInfo.get(currentToken).get(docId).getLeft() + 1;
                            tokenInfo.get(currentToken).get(docId).setLeft(newTF); // Increase tf
                            if (tokenInfo.get(currentToken).get(docId).getRight().containsKey(tagName)) {
                                int newValue = tokenInfo.get(currentToken).get(docId).getRight().get(tagName) + 1;
                                tokenInfo.get(currentToken).get(docId).getRight().put(tagName, newValue);
                            } else {
                                tokenInfo.get(currentToken).get(docId).getRight().put(tagName, 1);
                            }
                        } else {
                            HashMap<String, Integer> tagHm = new HashMap<>();
                            tagHm.put(tagName, 1);
                            MutablePair<Integer, HashMap<String, Integer>> p = new MutablePair<>(1, tagHm);
                            tokenInfo.get(currentToken).put(docId, p);
                        }
                    } else {
                        HashMap<String, Integer> tagHm = new HashMap<>();
                        tagHm.put(tagName, 1);
                        MutablePair<Integer, HashMap<String, Integer>> p = new MutablePair<>(1, tagHm);
                        HashMap<String, MutablePair<Integer, HashMap<String, Integer>>> docHm = new HashMap<>();
                        docHm.put(docId, p);
                        tokenInfo.put(currentToken, docHm);
                    }

                    // Is it time to write a partial index to disk?
                    if(tokenInfo.size() == piThreshold) {
                        createPartialIndex();
                        tokenInfo = new TreeMap<>(); // Clear tokenInfo for the new partial index
                    }
                }
            }
        }
    }

    /*
     * Recursively parse all documents inside dir.
     * If dir is a file, just parse it
     */
    private void parseRecursively(File dir) throws IOException {

        if(dir.listFiles() == null) {
            PathManager.fileNames.add(dir.getName());
            this.parseTags(dir.getAbsolutePath());
            return;
        }

        int fileCounter = 0;
        for (File fileEntry : dir.listFiles()) {

            if (PathManager.getNumOfFiles() != -1){
                if (fileCounter > PathManager.getNumOfFiles() - 1)
                    return;
            }

            if (fileEntry.isDirectory()) {
                PathManager.fileNames.add(fileEntry.getName());
                parseRecursively(fileEntry);
            } else {
                this.parseTags(fileEntry.getAbsolutePath());
                PathManager.fileNames.add(fileEntry.getName());
            }

            fileCounter++;
        }

    }

    /*
     * Produce partial index files: PartialVocabularyFile<Num>.txt, PartialPostingFile<Num>.txt
     */
    private void createPartialIndex() throws IOException {

        StringBuilder sb = new StringBuilder(); // To save time and space

        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";

        piCurrentNum++;
        piFileSuffixes.add(piCurrentNum.toString());

        RandomAccessFile voc = null;
        voc = new RandomAccessFile(indexDir + "/PartialVocabularyFile" + piCurrentNum + ".txt", "rw");
        voc.setLength(0);

        RandomAccessFile post = null;
        post = new RandomAccessFile(indexDir + "/PartialPostingFile" + piCurrentNum + ".txt", "rw");
        post.setLength(0);

        /* Create a partial vocabulary and a partial posting file using current tokenInfo's state */
        for(String term : tokenInfo.keySet()) {
            sb.append(term);
            sb.append(" ");
            sb.append(tokenInfo.get(term).size());
            sb.append(" ");
            sb.append(post.getFilePointer());
            sb.append("\n");
            voc.writeUTF(sb.toString());
            sb.setLength(0);
            for(String docId : tokenInfo.get(term).keySet()) {
                sb.append(docId);
                sb.append(" ");
                sb.append(tokenInfo.get(term).get(docId).getLeft());
                sb.append(" ");
                sb.append("P"); // pointer to DocumentsFile.txt record is currently unknown
                sb.append("\n");
                post.writeUTF(sb.toString());
                sb.setLength(0);
            }
        }

        /* Close files */
        voc.close();
        post.close();
    }

    /*
     * Produce the DocumentsFile.txt using the docInfo data structure and
     * return a hashmap with the file pointer values for each record
     */
    HashMap<String, Long> createDocumentsFile() throws IOException {

        StringBuilder sb = new StringBuilder();
        HashMap<String, Long> docBytes = new HashMap<>();
        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";

        RandomAccessFile doc = null;
        doc = new RandomAccessFile(indexDir + "/DocumentsFile.txt", "rw");
        doc.setLength(0);

        for(String docId : docInfo.keySet()) {
            docBytes.put(docId, doc.getFilePointer());
            sb.append(docId);
            sb.append(" ");
            sb.append(docInfo.get(docId).getLeft());
            sb.append(" ");
            sb.append(docInfo.get(docId).getRight());
            sb.append("\n");
            doc.writeUTF(sb.toString());
            sb.setLength(0);
        }

        doc.close();

        return docBytes;
    }

}
