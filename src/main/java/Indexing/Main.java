package Indexing;

import Utilities.PathManager;

import java.io.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();

        String path = PathManager.getCollectionPath(); // default path
        if(args.length > 0)
            path = args[0];

        i.index(path);
    }

}