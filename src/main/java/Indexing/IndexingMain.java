package Indexing;

import Utilities.PathManager;

import java.io.*;

public class IndexingMain {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();
        String path = PathManager.getInstance().getCollectionPath();
        i.index(path);
    }

}