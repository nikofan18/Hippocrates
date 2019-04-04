package Indexing;

import Utilities.PathManager;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
     * Lists with english and greek stopwords
     */
    private List<String> enSwList, grSwList;

    // Constructor

    /*
     * Initialize things (stemmer, stopword lists etc.)
     */
    public Indexer() throws IOException {
        tokenInfo = new TreeMap<>();
        docInfo = new TreeMap<>();
        Stemmer.Initialize();
        enSwList = parseStopwords(PathManager.getStopwordsPath() + "/stopwordsEn.txt");
        grSwList = parseStopwords(PathManager.getStopwordsPath() + "/stopwordsGr.txt");
    }

    // Methods

    /*
     * Perform all the necessary actions to produce the index
     * from the collection given by path (it may be a single file or a directory)
     */
    public void index(String path) throws IOException {
        System.out.println("Indexing " + path + " ...");
        PathManager.setExtraPath(path);
        File f = new File(PathManager.getCollectionPath() + PathManager.getExtraPath());
        parseRecursively(f);
        computeDocumentVectorLengths();
        new File(System.getProperty("user.dir") + "/CollectionIndex").mkdir();
        createIndexFiles();
//        createVocabularyFile();
//        createDocumentsFile();
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
                if(!enSwList.contains(currentToken) && !grSwList.contains(currentToken)) { // Accept only non-stopwords
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

    private void createIndexFiles() throws IOException {

        StringBuilder sb = new StringBuilder(); // To save time and space

        HashMap<String, Integer> docBytes = new HashMap<>();
        Integer byteSum = 0;

        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";

        RandomAccessFile voc = null;
        RandomAccessFile post = null;
        RandomAccessFile doc = null;

//        voc = new RandomAccessFile(indexDir + "/VocabularyFile.txt", "rw");
//        post = new RandomAccessFile(indexDir + "/PostingFile.txt", "rw");
        doc = new RandomAccessFile(indexDir + "/DocumentsFile.txt", "rw");

        /* Firstly, create the DocumentsFile.txt */
        for(String docId : docInfo.keySet()) {
            docBytes.put(docId, byteSum);

            sb.append(docId);
            sb.append(" ");
            sb.append(docInfo.get(docId).getLeft());
            sb.append(" ");
            sb.append(docInfo.get(docId).getRight());
            sb.append(" ");
            sb.append("\n");
            doc.writeUTF("akakak" + "akdawd" + "dvvvjvnvnvnvnv\n");

            byteSum += sb.toString().getBytes(StandardCharsets.UTF_8).length;

            sb.setLength(0); // clear string buffer
        }

        doc.close();

//
//        for(String term : tokenInfo.keySet()) {
//
//
//
////            String vocRecord = term + " " + tokenInfo.get(term).size();
////            voc.writeChars(term); voc.
//        }

    }

    /*
     * Create VocabularyFile.txt with each line as a <term, df> pair
     */
    private void createVocabularyFile() throws IOException {
        String fileName = System.getProperty("user.dir") + "/CollectionIndex/VocabularyFile.txt";
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(fileName, false)
        );

        for (String term : tokenInfo.keySet()) {
            writer.write(term + " " + tokenInfo.get(term).size());
            writer.newLine();
        }

        writer.close();
    }

    /*
     * Create DocumentsFile.txt with each line as a <docId, docFullPath, docVecLen> triplet
     */
    private void createDocumentsFile() throws IOException {
        String fileName = System.getProperty("user.dir") + "/CollectionIndex/DocumentsFile.txt";
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(fileName, false)
        );

        for(String docId : docInfo.keySet()) {
            writer.write(docId + " " + docInfo.get(docId).getLeft() + " " + docInfo.get(docId).getRight());
            writer.newLine();
        }

        writer.close();
    }

    // FOR TESTING, TO BE REMOVED
    public static void exportToFile(String str) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new FileWriter(PathManager.getCollectionPath() + "/output.txt", true)  //Set true for append mode
        );

        writer.write(str);
        writer.newLine();
        writer.close();
    }

}
