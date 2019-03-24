package indexingService;

import gr.uoc.csd.hy463.NXMLFileReader;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Tester {

    public static void main(String[] args) throws UnsupportedEncodingException, IOException {

        // Parse stopwords
        Path swEnFile = Paths.get("Stopwords/stopwordsEn.txt");
        List<String> swEnList = Files.readAllLines(swEnFile, Charset.forName("UTF-8"));
        Path swGrFile = Paths.get("Stopwords/stopwordsGr.txt");
        List<String> swGrList = Files.readAllLines(swGrFile, Charset.forName("UTF-8"));

        // Parse tags as strings
        File example = new File("MedicalCollection/00/29048.nxml");
        NXMLFileReader xmlFile = new NXMLFileReader(example);
        String pmcid = xmlFile.getPMCID();
        String title = xmlFile.getTitle();
        String abstr = xmlFile.getAbstr();
        String body = xmlFile.getBody();
        String journal = xmlFile.getJournal();
        String publisher = xmlFile.getPublisher();
        ArrayList<String> authors = xmlFile.getAuthors();
        HashSet<String> categories = xmlFile.getCategories();

        // Tokenize tags
        String delimiter = "\t\n\r\f :"; // TODO find shmeia stikshs
        HashMap<String, HashMap<String, Integer>> tokenInfo = new HashMap<>();

        // title
        StringTokenizer tokenizer = new StringTokenizer(title, delimiter);
        while(tokenizer.hasMoreTokens()) {
            String currentToken = tokenizer.nextToken();
            if(tokenInfo.containsKey(currentToken)) {
                tokenInfo.get(currentToken).put("title", tokenInfo.get(currentToken).get("title") + 1);
            } else {
                HashMap<String, Integer> hm = new HashMap<>();
                hm.put("title", 1);
                tokenInfo.put(currentToken, hm);
            }
        }

        // TODO for other tags
        //..
        //..

        // remove stopwords
        tokenInfo.keySet().removeAll(swEnList);
        tokenInfo.keySet().removeAll(swGrList);


        System.out.println(title);
        System.out.println(tokenInfo);

    }
}
