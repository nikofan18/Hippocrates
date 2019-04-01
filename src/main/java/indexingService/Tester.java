package indexingService;

import Configuration.Config;
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
        FilesParser fp = new FilesParser();

        Config.setExtraPath("/0"); // delete line for the whole collection
        File folder = new File(Config.getCollectionPath() + Config.getExtraPath());

        System.out.println("waiting...");
        FilesParser.listFilesForFolder(folder, fp);

        for (String e:FilesParser.tokenInfo.keySet()) {
            FilesParser.exportToFile(e+" "+FilesParser.tokenInfo.get(e));
        }

        System.out.println(Config.files);
    }
}