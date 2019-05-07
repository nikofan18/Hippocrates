package Utilities;

import java.util.ArrayList;

public class PathManager {

    // Fields

    public ArrayList<String> fileNames;
    private final String wordsPath;
    private final String collectionPath;
    private final String indexDirPath;
    private final String evalFilesPath;
    private final int numOfFiles;

    /*
     * To create a singleton
     */
    private static PathManager instance = null;
    public static PathManager getInstance() {
        if(instance == null)
            instance = new PathManager();
        return instance;
    }

    /*
     * Private constructor used in a singleton class
     */
    private PathManager() {
        fileNames = new ArrayList<>();
        wordsPath = "WordLists";
        collectionPath = "src/main/resources/public/MedicalCollection";
        indexDirPath = "CollectionIndex";
        evalFilesPath = "EvalFiles";

        numOfFiles = -1;
    }

    // Methods

    public String getIndexDirPath() { return indexDirPath; }

    public String getWordsPath() { return wordsPath; }

    public String getCollectionPath(){
       return collectionPath;
    }

    public int getNumOfFiles() {
        return numOfFiles;
    }

    public String getEvalFilesPath() { return evalFilesPath; }


}
