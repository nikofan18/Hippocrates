package Utilities;

import java.util.ArrayList;

public class PathManager {

    // Fields

    private static String collectionPath = "MedicalCollection"; // Add your own path here

    private static String extraPath = "";

    private static int numOfFiles = -1;

    public static ArrayList<String> fileNames = new ArrayList<>();


    // Methods

    public static String getCollectionPath(){
       return collectionPath;
    }

    public static void setCollectionPath(String collectionPath) {
        PathManager.collectionPath = collectionPath;
    }

    public static String getExtraPath() {
        return extraPath;
    }

    public static void setExtraPath(String extraPath) {
        PathManager.extraPath = extraPath;
    }

    public static void setNumOfFiles(int numOfFiles) {
        PathManager.numOfFiles = numOfFiles;
    }

    public static int getNumOfFiles() {
        return numOfFiles;
    }

}
