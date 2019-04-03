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

public class Indexer {

    // Fields

    /* The tokenInfo TreeMap holds information like this:
     * token1 -> {doc1} -> { [tagName1, timesInTag1], [tagName2, timesInTag2], ... }
     *        -> {doc2} -> { [tagName1, timesInTag1], [tagName2, timesInTag2], ... }
     *        -> ...
     * token2 ...
     */
    private TreeMap<String, HashMap<String, HashMap<String, Integer>>> tokenInfo;

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
        Stemmer.Initialize();
        enSwList = parseStopwords("Stopwords/stopwordsEn.txt");
        grSwList = parseStopwords("Stopwords/stopwordsGr.txt");
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
        indexRecursively(f);
        new File(System.getProperty("user.dir")+"/CollectionIndex").mkdir();
        for (String term : tokenInfo.keySet()) {
            populateVocabularyFile(term, tokenInfo.get(term).size());
        }
        System.out.println(PathManager.fileNames);
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
        tagPairs.put("pmcid", doLexicalAnalysis(xmlFile.getPMCID()));
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
        populateTokenInfo(tagPairs, path);
    }

    /*
     * Read a HashMap of pairs of type <tagName, tagContent> coming from a file in path = path,
     * do tokenization, stopword removal, stemming and populate tokenInfo TreeMap with new tokens
     */
    private void populateTokenInfo(HashMap<String, String> tagPairs, String path) throws IOException {

        String delimiter = "\t\n\r\f ";
        String fileName = path.substring(path.lastIndexOf("/"));
        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken().toLowerCase(); // Convert to lower case
                if(!enSwList.contains(currentToken) && !grSwList.contains(currentToken)) { // Accept only non-stopwords
                    currentToken = Stemmer.Stem(currentToken); // Do stemming
                    if (tokenInfo.containsKey(currentToken)) {
                        int newValue;
                        if (tokenInfo.get(currentToken).containsKey(fileName)) {
                            if (tokenInfo.get(currentToken).get(fileName).containsKey(tagName)) {
                                newValue = tokenInfo.get(currentToken).get(fileName).get(tagName) + 1;
                                tokenInfo.get(currentToken).get(fileName).put(tagName, newValue);
                            } else {
                                newValue = 1;
                                tokenInfo.get(currentToken).get(fileName).put(tagName, newValue);
                            }
                        } else {
                            HashMap<String, Integer> docHm = new HashMap<>();
                            docHm.put(tagName, 1);
                            tokenInfo.get(currentToken).put(fileName, docHm);
                        }
                    } else {
                        HashMap<String, HashMap<String, Integer>> docHm = new HashMap<>();
                        HashMap<String, Integer> tagHm = new HashMap<>();
                        tagHm.put(tagName, 1);
                        docHm.put(fileName, tagHm);
                        tokenInfo.put(currentToken, docHm);
                    }
                }
            }
        }
    }

    /*
     * Recursively index all documents inside dir.
     * If dir is a file, just index it
     */
    private void indexRecursively(File dir) throws IOException {

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
                indexRecursively(fileEntry);
            } else {
                this.parseTags(fileEntry.getAbsolutePath());
                PathManager.fileNames.add(fileEntry.getName());
            }

            fileCounter++;
        }

    }

    /*
     * Writes a term followed by its df value inside VocabularyFile.txt
     */
    private void populateVocabularyFile(String term, Integer df) throws IOException {
        String fileName = System.getProperty("user.dir") + "/CollectionIndex/VocabularyFile.txt";
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(fileName, true)
        );

        writer.write(term + " " + df);
        writer.newLine();
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
