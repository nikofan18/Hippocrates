package Indexing;

import Utilities.PathManager;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.lang3.tuple.MutablePair;

public class Indexer {

    // Fields

    /* The tokenInfo TreeMap holds information like this:
     * token1 -> {doc1, tf} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> {doc2, tf} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> ...
     * token2 ...
     */
    private TreeMap<String, HashMap<String, MutablePair<Integer, HashMap<String, Integer>>>> tokenInfo;

    /*
     * The docInfo TreeMap holds information like this:
     * <docId1, docFullPath1, docVecLen1>
     * <docId2, docFullPath2, docVecLen2>
     * ...
     */
    private TreeMap<String, MutablePair<String, Double>> docInfo;

    /*
     * Sets with english and greek stopwords
     */
    private HashSet<String> enSwSet, grSwSet;

    /*
     * Max terms of a partial index
     */
    private Integer piThreshold;

    /*
     * Current partial index number
     */
    private Integer piCurrentNum;

    /*
     * Use as a queue to store partial index file suffix names
     * (e.g. PartialVocabularyFile4.txt -> "4")
     */
    private LinkedList<String> piFileSuffixes;

    // Constructor

    /*
     * Initialize things (stemmer, stopword lists etc.)
     */
    public Indexer() throws IOException {
        tokenInfo = new TreeMap<>();
        docInfo = new TreeMap<>();
        piFileSuffixes = new LinkedList<>();
        Stemmer.Initialize();
        enSwSet = new HashSet<>(parseStopwords(PathManager.getStopwordsPath() + "/stopwordsEn.txt"));
        grSwSet = new HashSet<>(parseStopwords(PathManager.getStopwordsPath() + "/stopwordsGr.txt"));
        piThreshold = 60000; /* TODO: Determine threshold statistically */
        piCurrentNum = -1;
    }

    // Methods

    /*
     * Perform all the necessary actions to produce the index
     * from the collection given by path (it may be a single file or a directory)
     */
    public void index(String path) throws IOException {
        System.out.println("Indexing " + path + " ...");
        new File(System.getProperty("user.dir") + "/CollectionIndex").mkdir();
        PathManager.setExtraPath(path);
        File f = new File(PathManager.getCollectionPath() + PathManager.getExtraPath());
        parseRecursively(f);
        if(tokenInfo.size() > 0)
            createPartialIndex(); // Create the last partial index
        createFinalIndex(); // Uncomment this when implementation is ready
        System.out.println("Files Indexed: " + PathManager.fileNames);
    }

    /*
     * Return a list with all stopwords read from file with path = path
     */
    private List<String> parseStopwords(String path) throws IOException {
        Path swFile = Paths.get(path);
        return Files.readAllLines(swFile, Charset.forName("UTF-8"));
    }

    /*
     * Perform the appropriate lexical analysis actions on a string
     */
    private String doLexicalAnalysis(String str){
        String result;

        result = str.replaceAll("['´῾᾽\"]",""); // Ignore apostrophes
        result = result.replaceAll("[\\p{Punct}]", " "); // Replace punctuations with space character

        return result;
    }

    /*
     * For a given file with path = path, parse its tag contents
     */
    private void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", doLexicalAnalysis(xmlFile.getTitle()));
        tagPairs.put("pmcid", xmlFile.getPMCID());
        tagPairs.put("abstract", doLexicalAnalysis(xmlFile.getAbstr()));
        tagPairs.put("body", doLexicalAnalysis(xmlFile.getBody()));
        tagPairs.put("journal", doLexicalAnalysis(xmlFile.getJournal()));
        tagPairs.put("publisher", doLexicalAnalysis(xmlFile.getPublisher()));
        int counter = 0; // Used to ensure that every author key in this doc is different
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors_" + counter++, doLexicalAnalysis(entry));
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories_" + counter++, doLexicalAnalysis(entry));
        }
        populateTokenInfo(tagPairs, xmlFile.getPMCID());
        populateDocInfo(xmlFile.getPMCID(), path);
    }

    /*
     * Put a new triple <docId, docFullPath, docVecLen (currently = 0)> inside docInfo TreeMap
     */
    private void populateDocInfo(String docId, String fullPath) {
        MutablePair<String, Double> p = new MutablePair<>(fullPath, 0.0);
        docInfo.put(docId, p);
    }

    /*
     * Read a HashMap of pairs of type <tagName, tagContent> coming from a file in path = path,
     * do tokenization, stopword removal, stemming and populate tokenInfo TreeMap with new tokens
     */
    private void populateTokenInfo(HashMap<String, String> tagPairs, String docId) throws IOException {

        String delimiter = "\t\n\r\f ";
        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken().toLowerCase(); // Convert to lower case
                if(!enSwSet.contains(currentToken) && !grSwSet.contains(currentToken)) { // Accept only non-stopwords
                    currentToken = Stemmer.Stem(currentToken); // Do stemming
                    if (tokenInfo.containsKey(currentToken)) {
                        if (tokenInfo.get(currentToken).containsKey(docId)) {
                            int newTF = tokenInfo.get(currentToken).get(docId).getLeft() + 1;
                            tokenInfo.get(currentToken).get(docId).setLeft(newTF); // Increase tf
                            if (tokenInfo.get(currentToken).get(docId).getRight().containsKey(tagName)) {
                                int newValue = tokenInfo.get(currentToken).get(docId).getRight().get(tagName) + 1;
                                tokenInfo.get(currentToken).get(docId).getRight().put(tagName, newValue);
                            } else {
                                tokenInfo.get(currentToken).get(docId).getRight().put(tagName, 1);
                            }
                        } else {
                            HashMap<String, Integer> tagHm = new HashMap<>();
                            tagHm.put(tagName, 1);
                            MutablePair<Integer, HashMap<String, Integer>> p = new MutablePair<>(1, tagHm);
                            tokenInfo.get(currentToken).put(docId, p);
                        }
                    } else {
                        HashMap<String, Integer> tagHm = new HashMap<>();
                        tagHm.put(tagName, 1);
                        MutablePair<Integer, HashMap<String, Integer>> p = new MutablePair<>(1, tagHm);
                        HashMap<String, MutablePair<Integer, HashMap<String, Integer>>> docHm = new HashMap<>();
                        docHm.put(docId, p);
                        tokenInfo.put(currentToken, docHm);
                    }

                    /* Is it time to write a partial index to disk? */
                    if(tokenInfo.size() == piThreshold) {
                        createPartialIndex();
                        tokenInfo = new TreeMap<>(); // Prepare (clear) tokenInfo for the new partial index
                    }
                }
            }
        }
    }

    /*
     * Recursively parse all documents inside dir.
     * If dir is a file, just parse it
     */
    private void parseRecursively(File dir) throws IOException {

        if(dir.listFiles() == null) {
            PathManager.fileNames.add(dir.getName());
            this.parseTags(dir.getAbsolutePath());
            return;
        }

        int fileCounter = 0;
        for (File fileEntry : dir.listFiles()) {

            if (PathManager.getNumOfFiles() != -1){
                if (fileCounter > PathManager.getNumOfFiles() - 1)
                    return;
            }

            if (fileEntry.isDirectory()) {
                PathManager.fileNames.add(fileEntry.getName());
                parseRecursively(fileEntry);
            } else {
                this.parseTags(fileEntry.getAbsolutePath());
                PathManager.fileNames.add(fileEntry.getName());
            }

            fileCounter++;
        }

    }

    /*
     * Produce partial index files: VocabularyFile<Num>.txt, PostingFile<Num>.txt
     */
    private void createPartialIndex() throws IOException {

        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";
        long postFp;

        piCurrentNum++;
        piFileSuffixes.add(piCurrentNum.toString());

        RandomAccessFile voc =
                new RandomAccessFile(indexDir + "/VocabularyFile" + piCurrentNum + ".txt", "rw");
        voc.setLength(0);

        RandomAccessFile post =
                new RandomAccessFile(indexDir + "/PostingFile" + piCurrentNum + ".txt", "rw");
        post.setLength(0);

        /* Create a partial vocabulary and a partial posting file using current tokenInfo's state */
        for(String term : tokenInfo.keySet()) {
            voc.writeUTF(term);
            voc.writeLong(tokenInfo.get(term).size());
            postFp = post.getFilePointer();
            voc.writeLong(postFp);
            for(String docId : tokenInfo.get(term).keySet()) {
                post.writeUTF(docId);
                post.writeInt(tokenInfo.get(term).get(docId).getLeft());
                post.writeLong(0); // pointer to DocumentsFile.txt record is currently unknown
            }
            voc.writeInt((int)(post.getFilePointer() - postFp)); // Byte length of term's posting data
        }

        /* Close files */
        voc.close();
        post.close();
    }

    /*
     * Produce the DocumentsFile.txt using the docInfo data structure and
     * return a hashmap with the file pointer values for each record
     */
    private HashMap<String, Long> createDocumentsFile() throws IOException {

        HashMap<String, Long> docBytes = new HashMap<>();
        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";

        RandomAccessFile doc = new RandomAccessFile(indexDir + "/DocumentsFile.txt", "rw");
        doc.setLength(0);

        for(String docId : docInfo.keySet()) {
            docBytes.put(docId, doc.getFilePointer());
            doc.writeUTF(docId);
            doc.writeUTF(docInfo.get(docId).getLeft());
            doc.writeDouble(docInfo.get(docId).getRight()); // At this moment, this must be equal to 0.0
        }

        doc.close();

        return docBytes;
    }

    /*
     * Merge partial index files and create DocumentsFile.txt
     */
    private void createFinalIndex() throws IOException {

        RandomAccessFile voc1, voc2, post1, post2, vocMerged = null, postMerged = null, doc;
        String indexDir = System.getProperty("user.dir") + "/CollectionIndex";
        String suffix1, suffix2, mergedSuffix = "", w1, w2, docId;
        long voc1fp, voc2fp, ptr, df;
        double idf;
        int pdSz, tf, docsNum, currentTF, newTF, mergedFilesCounter = 0, wordComparison;

        TreeMap<String, MutablePair<Integer, Long>> postData = new TreeMap<>();

        HashMap<String, Long> docBytes = createDocumentsFile(); // use this in merging

        /*
         * In case there's only one partial index, create an empty - dummy partial index
         * for the algorithm to be generic
         */
        if(piFileSuffixes.size() == 1) {
            piFileSuffixes.add("1");
        }

        /* Merge partial indices */
        while(piFileSuffixes.size() >= 2) {

            /* Open random access files */
            suffix1 = piFileSuffixes.remove();
            voc1 = new RandomAccessFile(indexDir + "/VocabularyFile" + suffix1 + ".txt", "rw");
            post1 = new RandomAccessFile(indexDir + "/PostingFile" + suffix1 + ".txt", "rw");
            suffix2 = piFileSuffixes.remove();
            voc2 = new RandomAccessFile(indexDir + "/VocabularyFile" + suffix2 + ".txt", "rw");
            post2 = new RandomAccessFile(indexDir + "/PostingFile" + suffix2 + ".txt", "rw");
            mergedSuffix = "_m" + mergedFilesCounter++;
            vocMerged = new RandomAccessFile(
                    indexDir + "/VocabularyFile" + mergedSuffix + ".txt", "rw"
            );
            postMerged = new RandomAccessFile(
                    indexDir + "/PostingFile" + mergedSuffix + ".txt", "rw"
            );

            while(!isEOFReached(voc1) && !isEOFReached(voc2)) {

                /* Save previous file pointer values in case there's no need to move the file pointers */
                voc1fp = voc1.getFilePointer();
                voc2fp = voc2.getFilePointer();

                w1 = voc1.readUTF();
                w2 = voc2.readUTF();

                wordComparison = w1.compareTo(w2);

                if (wordComparison < 0) { // w1 < w2

                    vocMerged.writeUTF(w1); // copy term
                    vocMerged.writeLong(voc1.readLong()); // copy df
                    vocMerged.writeLong(postMerged.getFilePointer()); // new fp
                    post1.seek(ptr = voc1.readLong());
                    vocMerged.writeInt(pdSz = voc1.readInt()); // copy posting data size
                    while(post1.getFilePointer() != pdSz + ptr) {
                        postMerged.writeUTF(docId = post1.readUTF());
                        postMerged.writeInt(post1.readInt());
                        postMerged.writeLong(docBytes.get(docId));
                        post1.readLong(); // skip ptr
                    }

                    /* Don't move voc2 file pointer */
                    voc2.seek(voc2fp);

                } else if (wordComparison > 0) { // w1 > w2

                    vocMerged.writeUTF(w2); // copy term
                    vocMerged.writeLong(voc2.readLong()); // copy df
                    vocMerged.writeLong(postMerged.getFilePointer()); // new fp
                    post2.seek(ptr = voc2.readLong());
                    vocMerged.writeInt(pdSz = voc2.readInt()); // copy posting data size
                    while(post2.getFilePointer() != pdSz + ptr) {
                        postMerged.writeUTF(docId = post2.readUTF());
                        postMerged.writeInt(post2.readInt());
                        postMerged.writeLong(docBytes.get(docId));
                        post2.readLong(); // skip ptr
                    }

                    /* Don't move voc1 file pointer */
                    voc1.seek(voc1fp);

                } else {

                    /* Save post1's data for w1 to a structure */
                    voc1.readLong(); // skip df
                    post1.seek(ptr = voc1.readLong());
                    pdSz = voc1.readInt();
                    while(post1.getFilePointer() != ptr + pdSz) {
                        postData.put(docId = post1.readUTF(), new MutablePair<>(post1.readInt(), docBytes.get(docId)));
                        post1.readLong(); // skip old ptr
                    }

                    /* Save post2's data for w2 to a structure */
                    voc2.readLong(); // skip df
                    post2.seek(ptr = voc2.readLong());
                    pdSz = voc2.readInt();
                    while(post2.getFilePointer() != ptr + pdSz) {
                        currentTF = 0;
                        docId = post2.readUTF();
                        newTF = post2.readInt();
                        if(postData.containsKey(docId)) {
                            currentTF = postData.get(docId).getLeft();
                        }
                        newTF += currentTF;
                        postData.put(docId, new MutablePair<>(newTF, docBytes.get(docId)));
                        post2.readLong(); // skip old ptr
                    }

                    /* Write merged files */
                    vocMerged.writeUTF(w1);
                    vocMerged.writeLong(postData.size());
                    vocMerged.writeLong(ptr = postMerged.getFilePointer());
                    for(String id : postData.keySet()) {
                        postMerged.writeUTF(id);
                        postMerged.writeInt(postData.get(id).getLeft());
                        postMerged.writeLong(postData.get(id).getRight());
                    }
                    vocMerged.writeInt((int)(postMerged.getFilePointer() - ptr));

                    postData = new TreeMap<>(); // clear posting data structure
                }
            }

            /* In case voc2 has finished, but not voc1 */
            while(!isEOFReached(voc1)) {
                vocMerged.writeUTF(voc1.readUTF()); // copy term
                vocMerged.writeLong(voc1.readLong()); // copy df
                vocMerged.writeLong(postMerged.getFilePointer()); // new fp
                post1.seek(ptr = voc1.readLong());
                vocMerged.writeInt(pdSz = voc1.readInt()); // copy posting data size
                while(post1.getFilePointer() != pdSz + ptr) {
                    postMerged.writeUTF(docId = post1.readUTF()); // write doc id
                    postMerged.writeInt(post1.readInt()); // write tf
                    postMerged.writeLong(docBytes.get(docId)); // write
                    post1.readLong(); // skip ptr
                }
            }

            /* In case voc1 has finished, but not voc2 */
            while(!isEOFReached(voc2)) {
                vocMerged.writeUTF(voc2.readUTF()); // copy term
                vocMerged.writeLong(voc2.readLong()); // copy df
                vocMerged.writeLong(postMerged.getFilePointer()); // new fp
                post2.seek(ptr = voc2.readLong());
                vocMerged.writeInt(pdSz = voc2.readInt()); // copy posting data size
                while(post2.getFilePointer() != pdSz + ptr) {
                    postMerged.writeUTF(docId = post2.readUTF()); // write doc id
                    postMerged.writeInt(post2.readInt()); // write tf
                    postMerged.writeLong(docBytes.get(docId)); // write
                    post2.readLong(); // skip ptr
                }
            }

            /* Close and delete merged files */
            voc1.close(); voc2.close(); post1.close(); post2.close();
            new File(indexDir + "/VocabularyFile" + suffix1 + ".txt").delete();
            new File(indexDir + "/PostingFile" + suffix1 + ".txt").delete();
            new File(indexDir + "/VocabularyFile" + suffix2 + ".txt").delete();
            new File(indexDir + "/PostingFile" + suffix2 + ".txt").delete();

            /* Add merged file suffix to queue */
            piFileSuffixes.add(mergedSuffix);
        }

        /* Fill missing vector lengths in docInfo */
        vocMerged.seek(0);
        postMerged.seek(0);
        docsNum = docInfo.size();
        while(!isEOFReached(vocMerged)) {
            vocMerged.readUTF(); // term
            df = vocMerged.readLong(); // df
            ptr = vocMerged.readLong(); // ptr
            pdSz = vocMerged.readInt(); // record's posting data size
            postMerged.seek(ptr);
            while(postMerged.getFilePointer() != ptr + pdSz) {
                docId = postMerged.readUTF();
                tf = postMerged.readInt();
                postMerged.readLong();
                idf = Math.log((float)docsNum / df) / Math.log(2.0);
                docInfo.get(docId).setRight(docInfo.get(docId).getRight() + Math.sqrt(tf * idf));
            }
        }

        /* Square the results when sum computation is finished and write them to DocumentsFile.txt */
        doc = new RandomAccessFile(indexDir + "/DocumentsFile.txt", "rw");
        for(String id : docInfo.keySet()) {
            docInfo.get(id).setRight(Math.sqrt(docInfo.get(id).getRight()));
            doc.readUTF();
            doc.readUTF();
            doc.writeDouble(docInfo.get(id).getRight());
        }

        /* Give index files a generic name */
        new File(indexDir + "/VocabularyFile" + mergedSuffix + ".txt").renameTo(
                new File(indexDir + "/VocabularyFile.txt")
        );
        new File(indexDir + "/PostingFile" + mergedSuffix + ".txt").renameTo(
                new File(indexDir + "/PostingFile.txt")
        );

        doc.close();
        postMerged.close();
        vocMerged.close();
    }

    /*
     * Helper function to check if a random access file has reached the EOF
     */
    private boolean isEOFReached(RandomAccessFile f) throws IOException {
        boolean ret = false;
        long prevPtr = f.getFilePointer();
        if(f.read() == -1) {
            ret = true;
        }
        f.seek(prevPtr);
        return ret;
    }

}
