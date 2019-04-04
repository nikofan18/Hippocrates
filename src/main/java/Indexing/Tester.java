package Indexing;

import java.io.IOException;

public class Tester {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();
        i.index("/test"); // The argument is the extraPath (to index whole collection, use "")
    }
}