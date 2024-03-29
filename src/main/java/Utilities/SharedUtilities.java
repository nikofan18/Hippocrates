package Utilities;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;

/*
 * A class that provides fields and methods to Indexer, Searcher and Evaluator
 */
public class SharedUtilities {

    /*
     * To create a singleton
     */
    private static SharedUtilities instance = null;
    public static SharedUtilities getInstance() throws IOException {
        if(instance == null)
            instance = new SharedUtilities();
        return instance;
    }

    /*
     * Private constructor used in a singleton class
     */
    private SharedUtilities() throws IOException {
        enSwSet = new HashSet<>(parseWords(PathManager.getInstance().getWordsPath() + "/stopwordsEn.txt"));
        grSwSet = new HashSet<>(parseWords(PathManager.getInstance().getWordsPath() + "/stopwordsGr.txt"));
    }

    /*
     * Sets with english and greek stopwords
     */
    public HashSet<String> enSwSet, grSwSet;

    /*
     * The total number of documents in collection
     */
    public Long docsNum;

    /*
     * Return a list with all words read from file with path = path
     */
    public List<String> parseWords(String path) throws IOException {
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    /*
     * Helper function to check if a random access file has reached the EOF
     */
    public boolean isEOFReached(RandomAccessFile f) throws IOException {
        boolean ret = false;
        long prevPtr = f.getFilePointer();
        if(f.read() == -1) {
            ret = true;
        }
        f.seek(prevPtr);
        return ret;
    }

    /*
     * Perform the appropriate lexical analysis actions on a string
     */
    public String doLexicalAnalysis(String str){
        String result;

        result = str.replaceAll("['´῾᾽\"]",""); // Ignore apostrophes
        result = result.replaceAll("[\\p{Punct}]", " "); // Replace punctuations with space character
        result = result.toLowerCase();

        return result;
    }
}
