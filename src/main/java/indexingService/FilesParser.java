package indexingService;

import Configuration.Config;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FilesParser {

    public static TreeMap<String, HashMap<String, HashMap<String, Integer>>> tokenInfo;

    public FilesParser(){
        tokenInfo = new TreeMap<>();
    }

    private List<String> parseStopwords(String path) throws IOException { // TODO make path manager
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    private String doLexicalAnalysis(String str){
        String result = str.replaceAll("['´῾᾽\"]","");
        return result.replaceAll("[\\p{Punct}]", " ");
    }

    public void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", doLexicalAnalysis(xmlFile.getTitle()));
        tagPairs.put("pmcid", doLexicalAnalysis(xmlFile.getPMCID()));
        tagPairs.put("abstract", doLexicalAnalysis(xmlFile.getAbstr()));
        tagPairs.put("body", doLexicalAnalysis(xmlFile.getBody()));
        tagPairs.put("journal", doLexicalAnalysis(xmlFile.getJournal()));
        tagPairs.put("publisher", doLexicalAnalysis(xmlFile.getPublisher()));
        int counter = 0;
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors_" + counter++, doLexicalAnalysis(entry));
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories_" + counter++, doLexicalAnalysis(entry));
        }
        populateTokenInfo(tagPairs, path);
    }

    private String doStemming(String str) {
        Stemmer.Initialize();
        return Stemmer.Stem(str);
    }

    private void populateTokenInfo(HashMap<String, String> tagPairs, String path) throws IOException {

        String delimiter = "\t\n\r\f "; // TODO find shmeia stikshs
        String fileName = path.substring(path.lastIndexOf("/"));
        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = doStemming(tokenizer.nextToken());
                if (tokenInfo.containsKey(currentToken)) {
                    int newValue;
                    if(tokenInfo.get(currentToken).containsKey(fileName)){
                        if(tokenInfo.get(currentToken).get(fileName).containsKey(tagName)){
                            newValue = tokenInfo.get(currentToken).get(fileName).get(tagName) + 1;
                            tokenInfo.get(currentToken).get(fileName).put(tagName, newValue);
                        }
                        else if(!tokenInfo.get(currentToken).get(fileName).containsKey(tagName)){
                            newValue = 1;
                            tokenInfo.get(currentToken).get(fileName).put(tagName, newValue);
                        }
                    }
                    else if(!tokenInfo.get(currentToken).containsKey(fileName)){
                        HashMap<String, Integer> m = new HashMap<>();
                        m.put(tagName, 1);
                        tokenInfo.get(currentToken).put(fileName, m);
                    }
                } else {
                    HashMap<String, Integer> hm = new HashMap<>();
                    HashMap<String, HashMap<String, Integer>> h = new HashMap<>();
                    hm.put(tagName, 1);
                    h.put(fileName, hm);
                    tokenInfo.put(currentToken, h);
                }
            }
        }

        // TODO make function optional
        tokenInfo.keySet().removeAll(parseStopwords("Stopwords/stopwordsEn.txt"));
        tokenInfo.keySet().removeAll(parseStopwords("Stopwords/stopwordsGr.txt"));

    }

    public static void listFilesForFolder(File folder, FilesParser fp) throws IOException {

        if(folder.listFiles() == null){
            Config.files.add(folder.getName());
            fp.parseTags(folder.getAbsolutePath());
            return;
        }

        int fileCounter = 0;

        for (File fileEntry : folder.listFiles()) {

            if (Config.getNumOfFiles() != -1){
                if (fileCounter > Config.getNumOfFiles() - 1)
                    return;
            }

            if (fileEntry.isDirectory()) {
                Config.files.add(fileEntry.getName());
                listFilesForFolder(fileEntry, fp);
            } else {
                fp.parseTags(fileEntry.getAbsolutePath());
                Config.files.add(fileEntry.getName());
            }
            fileCounter++;
        }

    }

    public static void createVocabularyFile(String term, Integer df) throws IOException {
        BufferedWriter writer = new BufferedWriter(
                new FileWriter(System.getProperty("user.dir") + "/CollectionIndex/VocabularyFile.txt", true)  //Set true for append mode
        );

        writer.write(term + " " + df);
        writer.newLine();
        writer.close();
    }

    // for testing
    public static void exportToFile(String str) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new FileWriter(Config.getCollectionPath() + "/output.txt", true)  //Set true for append mode
        );

        writer.write(str);
        writer.newLine();
        writer.close();
    }

}
