package Indexing;

import mitos.stemmer.Stemmer;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeMap;

public class Main {

    public static void main(String[] args) throws IOException {
        Indexer i = new Indexer();
        i.index("/00"); // The argument is the extraPath (to index whole collection, use "")

//        /* TO TEST IF INDEX IS SUCCESSFULLY CREATED */
//
//        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";
//        RandomAccessFile vocMerged = new RandomAccessFile(indexDir + "/VocabularyFile.txt", "rw");
//        RandomAccessFile postMerged = new RandomAccessFile(indexDir + "/PostingFile.txt", "rw");
//        RandomAccessFile doc = new RandomAccessFile(indexDir + "/DocumentsFile.txt", "rw");
//
//        /* Load vocabulary in memory */
//        TreeMap<String, ArrayList<Long>> vocMap = new TreeMap<>();
//        vocMerged.seek(0);
//        postMerged.seek(0);
//        while(!isEOFReached(vocMerged)) {
//            String term = vocMerged.readUTF();
//            ArrayList<Long> al = new ArrayList<>();
//            al.add(vocMerged.readLong());
//            al.add(vocMerged.readLong());
//            al.add((long)vocMerged.readInt());
//            vocMap.put(term, al);
//        }
//
//        /* Do the searching */
//        Scanner s = new Scanner(System.in);
//        String term;
//        System.out.println("Search Term:");
//        while(!(term = s.next()).equals("exit")) {
//
//            term = Stemmer.Stem(term);
//            System.out.println("Term: " + term);
//            System.out.println("df: " + vocMap.get(term).get(0));
//            postMerged.seek(vocMap.get(term).get(1));
//
//            while (postMerged.getFilePointer() != vocMap.get(term).get(1) + vocMap.get(term).get(2)) {
//                System.out.println("~~~~~~~");
//                postMerged.readUTF();
//                System.out.println("tf: " + postMerged.readDouble());
//                doc.seek(postMerged.readLong());
//                System.out.println("docId: " + doc.readUTF());
//                System.out.println("fullpath: " + doc.readUTF());
//                System.out.println("|d|: " + doc.readDouble());
//            }
//            System.out.println("~~~~~~~\nSearch term:");
//        }
    }

    private static boolean isEOFReached(RandomAccessFile f) throws IOException {
        boolean ret = false;
        long prevPtr = f.getFilePointer();
        if(f.read() == -1) {
            ret = true;
        }
        f.seek(prevPtr);
        return ret;
    }
}