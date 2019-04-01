package indexingService;

import Configuration.Config;
import gr.uoc.csd.hy463.NXMLFileReader;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FilesParser {

    public static HashMap<String, HashMap<String, HashMap<String, Integer>>> tokenInfo;

    public FilesParser(){
        tokenInfo = new HashMap<>();
    }

    private List<String> parseStopwords(String path) throws IOException { // TODO make path manager
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    private String removePunct(String str){
        return str.replaceAll("[\\p{Punct}]", "");
    }

    public void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", removePunct(xmlFile.getTitle()));
        tagPairs.put("pmcid", removePunct(xmlFile.getPMCID()));
        tagPairs.put("abstract", removePunct(xmlFile.getAbstr()));
        tagPairs.put("body", removePunct(xmlFile.getBody()));
        tagPairs.put("journal", removePunct(xmlFile.getJournal()));
        tagPairs.put("publisher", removePunct(xmlFile.getPublisher()));
        int counter = 0;
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors" + counter++, removePunct(entry));
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories" + counter++, removePunct(entry));
        }
        populateTokenInfo(tagPairs, path);
    }

    private void populateTokenInfo(HashMap<String, String> tagPairs, String path) throws IOException {

        String delimiter = "\t\n\r\f "; // TODO find shmeia stikshs

        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken();
                if (tokenInfo.containsKey(currentToken)) {
                    int newValue;
                    if(tokenInfo.get(currentToken).containsKey(path)){
                        if(tokenInfo.get(currentToken).get(path).containsKey(tagName)){
                            newValue = tokenInfo.get(currentToken).get(path).get(tagName) + 1;
                            tokenInfo.get(currentToken).get(path).put(tagName, newValue);
                        }
                        else if(!tokenInfo.get(currentToken).get(path).containsKey(tagName)){
                            newValue = 1;
                            tokenInfo.get(currentToken).get(path).put(tagName, newValue);
                        }
                    }
                    else if(!tokenInfo.get(currentToken).containsKey(path)){
                        HashMap<String, Integer> m = new HashMap<>();
                        m.put(tagName, 1);
                        tokenInfo.get(currentToken).put(path, m);
                    }
                } else {
                    HashMap<String, Integer> hm = new HashMap<>();
                    HashMap<String, HashMap<String, Integer>> h = new HashMap<>();
                    hm.put(tagName, 1);
                    h.put(path, hm);
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

    public static void exportToFile(String str) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new FileWriter(Config.getCollectionPath() + "/output.txt", true)  //Set true for append mode
        );

        writer.newLine();
        writer.write(str);
        writer.close();
    }

}
