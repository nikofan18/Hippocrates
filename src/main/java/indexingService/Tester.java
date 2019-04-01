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
        FilesParser fp = new FilesParser();
        for (int i=0; i<=0; i++){
            File folder = new File("../../MedicalCollection/"+i);
            FilesParser.listFilesForFolder(folder, fp);
        }

        for (String e:FilesParser.tokenInfo.keySet()) {
            System.out.println(e+" "+FilesParser.tokenInfo.get(e));
        }
    }
}
