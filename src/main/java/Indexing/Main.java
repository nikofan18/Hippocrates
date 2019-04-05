package Indexing;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();
        i.index("/00"); // The argument is the extraPath (to index whole collection, use "")
    }
}