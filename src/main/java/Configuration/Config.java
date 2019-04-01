package Configuration;

import java.util.ArrayList;

public class Config {

    private static String collectionPath = System.getProperty("user.dir").substring(0,
            System.getProperty("user.dir").lastIndexOf('/')) + "/MedicalCollection";

    private static String extraPath;

    private static int numOfFiles = -1;

    public static ArrayList<String> files = new ArrayList<>();


    public static String getCollectionPath(){
       return collectionPath;
    }

    public static void setCollectionPath(String collectionPath) {
        Config.collectionPath = collectionPath;
    }

    public static String getExtraPath() {
        return extraPath;
    }

    public static void setExtraPath(String extraPath) {
        Config.extraPath = extraPath;
    }

    public static void setNumOfFiles(int numOfFiles) {
        Config.numOfFiles = numOfFiles;
    }

    public static int getNumOfFiles() {
        return numOfFiles;
    }

}
