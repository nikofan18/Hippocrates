package Indexing;

import Searching.Searcher;
import Utilities.PathManager;

import java.io.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();
        i.index(PathManager.getCollectionPath() + "/test");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Searcher s = new Searcher();

        String query;
        System.out.println("Type a query: ");
        while(!(query = br.readLine()).equals("exit")) {
            System.out.println(s.search(query, "diagnosis"));
            System.out.println("Type a query: ");
        }

    }

}