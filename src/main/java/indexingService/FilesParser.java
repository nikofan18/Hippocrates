package indexingService;

import gr.uoc.csd.hy463.NXMLFileReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FilesParser {

    public static HashMap<String, HashMap<String, Integer>> tokenInfo;

    public FilesParser(){
        tokenInfo = new HashMap<>();
    }

    private List<String> parseStopwords(String path) throws IOException { // TODO make path manager
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    public void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", xmlFile.getTitle());
        tagPairs.put("pmcid", xmlFile.getPMCID());
        tagPairs.put("abstract", xmlFile.getAbstr());
        tagPairs.put("body", xmlFile.getBody());
        tagPairs.put("journal", xmlFile.getJournal());
        tagPairs.put("publisher", xmlFile.getPublisher());
        int counter = 0;
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors" + counter++, entry);
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories" + counter++, entry);
        }
        populateTokenInfo(tagPairs);
    }

    private void populateTokenInfo(HashMap<String, String> tagPairs) throws IOException {

        String delimiter = "\t\n\r\f :"; // TODO find shmeia stikshs

        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken();
                if (tokenInfo.containsKey(currentToken)) {
                    int newValue;
                    if(tokenInfo.get(currentToken).containsKey(tagName))
                        newValue = tokenInfo.get(currentToken).get(tagName) + 1;
                    else
                        newValue = 1;
                    tokenInfo.get(currentToken).put(tagName, newValue);
                } else {
                    HashMap<String, Integer> hm = new HashMap<>();
                    hm.put(tagName, 1);
                    tokenInfo.put(currentToken, hm);
                }
            }
        }

        // TODO make function optional
        tokenInfo.keySet().removeAll(parseStopwords("Stopwords/stopwordsEn.txt"));
        tokenInfo.keySet().removeAll(parseStopwords("Stopwords/stopwordsGr.txt"));
    }
}
