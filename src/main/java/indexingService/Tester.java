package indexingService;

import Configuration.Config;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

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
        FilesParser fp = new FilesParser();

        Config.setExtraPath("/0/29048.nxml"); // delete line for the whole collection
        File folder = new File(Config.getCollectionPath() + Config.getExtraPath());

        System.out.println("waiting...");
        FilesParser.listFilesForFolder(folder, fp);
        new File(System.getProperty("user.dir")+"/CollectionIndex").mkdirs();
        for (String e : FilesParser.tokenInfo.keySet()) {
            FilesParser.createVocabularyFile(e,FilesParser.tokenInfo.get(e).size());
        }

        System.out.println(Config.files);

//        Stemmer.Initialize();
//        System.out.println(Stemmer.Stem("ψωλοκοπάνας"));
    }
}