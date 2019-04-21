package Utilities;

import java.util.ArrayList;

public class PathManager {

    // Fields

    private static String wordsPath = "WordLists";

    private static String collectionPath = "MedicalCollection";

    private static String indexDirPath = "CollectionIndex";

    private static int numOfFiles = -1;

    public static ArrayList<String> fileNames = new ArrayList<>();


    // Methods

    public static String getIndexDirPath() { return indexDirPath; }

    public static void setIndexDirPath(String indexDirPath) { PathManager.indexDirPath = indexDirPath; }

    public static String getWordsPath() { return wordsPath; }

    public static void setWordsPath(String wordsPath) { PathManager.wordsPath = wordsPath; }

    public static String getCollectionPath(){
       return collectionPath;
    }

    public static void setCollectionPath(String collectionPath) {
        PathManager.collectionPath = collectionPath;
    }

    public static void setNumOfFiles(int numOfFiles) {
        PathManager.numOfFiles = numOfFiles;
    }

    public static int getNumOfFiles() {
        return numOfFiles;
    }

}
