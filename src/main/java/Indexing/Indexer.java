package Indexing;

import Utilities.PathManager;
import Utilities.SharedUtilities;
import gr.uoc.csd.hy463.NXMLFileReader;
import mitos.stemmer.Stemmer;

import java.io.*;
import java.util.*;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;


public class Indexer {

    // Fields

    /* The tokenInfo TreeMap holds information like this:
     * token1 -> {doc1, nonNormalizedTF1} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> {doc2, nonNormalizedTF2} -> { [tagName1, occurencesInTag1], [tagName2, occurencesInTag2], ... }
     *        -> ...
     * token2 ...
     */
    private TreeMap<String, HashMap<String, MutablePair<Integer, HashMap<String, Integer>>>> tokenInfo;

    /*
     * The docInfo TreeMap holds information like this:
     * <docId1, docFullPath1, docMaxTF1, docVecLen1>
     * <docId2, docFullPath2, docMaxTF2, docVecLen2>
     * ...
     */
    private TreeMap<String, MutableTriple<String, Integer, Double>> docInfo;

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
    public Indexer() {
        tokenInfo = new TreeMap<>();
        docInfo = new TreeMap<>();
        piFileSuffixes = new LinkedList<>();
        Stemmer.Initialize();
        piThreshold = 1000000;
        piCurrentNum = -1;
    }

    // Methods

    /*
     * Perform all the necessary actions to produce the index
     * from the collection given by path (it may be a single file or a directory)
     */
    public void index(String path) throws IOException {
        new File(System.getProperty("user.dir") + "/CollectionIndex").mkdir();
        new File(PathManager.getIndexDirPath() + "/DocumentsFile.txt").delete();
        new File(PathManager.getIndexDirPath() + "/VocabularyFile.txt").delete();
        new File(PathManager.getIndexDirPath() + "/PostingFile.txt").delete();
        PathManager.setExtraPath(path);
        String finalPath = PathManager.getCollectionPath() + PathManager.getExtraPath();
        File f = new File(finalPath);
        System.out.println("Indexing " + finalPath + " ...");
        parseRecursively(f);
        if(tokenInfo.size() > 0)
            createPartialIndex(); // Create the last partial index
        createFinalIndex(); // Finalize index (do merging etc.)
        System.out.println("Files Indexed: " + PathManager.fileNames);
    }

    /*
     * For a given file with path = path, parse its tag contents
     */
    private void parseTags(String path) throws IOException {
        HashMap<String, String> tagPairs =  new HashMap<>();
        File f = new File(path);
        NXMLFileReader xmlFile =  new NXMLFileReader(f);
        tagPairs.put("title", SharedUtilities.getInstance().doLexicalAnalysis(xmlFile.getTitle()));
        tagPairs.put("pmcid", xmlFile.getPMCID());
        tagPairs.put("abstract", SharedUtilities.getInstance().doLexicalAnalysis(xmlFile.getAbstr()));
        tagPairs.put("body", SharedUtilities.getInstance().doLexicalAnalysis(xmlFile.getBody()));
        tagPairs.put("journal", SharedUtilities.getInstance().doLexicalAnalysis(xmlFile.getJournal()));
        tagPairs.put("publisher", SharedUtilities.getInstance().doLexicalAnalysis(xmlFile.getPublisher()));
        int counter = 0; // Used to ensure that every author key in this doc is different
        for(String entry : xmlFile.getAuthors()) {
            tagPairs.put("authors_" + counter++, SharedUtilities.getInstance().doLexicalAnalysis(entry));
        }
        counter = 0;
        for(String entry : xmlFile.getCategories()) {
            tagPairs.put("categories_" + counter++, SharedUtilities.getInstance().doLexicalAnalysis(entry));
        }
        int maxTF = populateTokenInfo(tagPairs, xmlFile.getPMCID());
        populateDocInfo(xmlFile.getPMCID(), path, maxTF);

        /* Is it time to write a partial index to disk? */
        if(tokenInfo.size() >= piThreshold) {
            createPartialIndex();
            tokenInfo = new TreeMap<>(); // Prepare (clear) tokenInfo for the new partial index
        }

    }

    /*
     * Put a new record <docId, docFullPath, docMaxTF, docVecLen (currently = 0)> inside docInfo TreeMap
     */
    private void populateDocInfo(String docId, String fullPath, Integer maxTF) {
        MutableTriple<String, Integer, Double> p = new MutableTriple<>(fullPath, maxTF, 0.0);
        docInfo.put(docId, p);
    }

    /*
     * Read a HashMap of pairs of type <tagName, tagContent> coming from a file in path = path,
     * do tokenization, stopword removal, stemming and populate tokenInfo TreeMap with new tokens.
     * Return the max tf of the document.
     */
    private int populateTokenInfo(HashMap<String, String> tagPairs, String docId) throws IOException {

        int maxTF = 1;
        String delimiter = "\t\n\r\f ";
        for(String tagName : tagPairs.keySet()) {
            StringTokenizer tokenizer = new StringTokenizer(tagPairs.get(tagName), delimiter);
            while (tokenizer.hasMoreTokens()) {
                String currentToken = tokenizer.nextToken();
                if(!SharedUtilities.getInstance().enSwSet.contains(currentToken)
                        && !SharedUtilities.getInstance().grSwSet.contains(currentToken)) { // Accept only non-stopwords
                    currentToken = Stemmer.Stem(currentToken); // Do stemming
                    if (tokenInfo.containsKey(currentToken)) {
                        if (tokenInfo.get(currentToken).containsKey(docId)) {
                            int nonNormTF = tokenInfo.get(currentToken).get(docId).getLeft() + 1;
                            tokenInfo.get(currentToken).get(docId).setLeft(nonNormTF); // Increase non-normalized tf
                            if(nonNormTF > maxTF)
                                maxTF = nonNormTF;
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
                }
            }
        }
        return maxTF;
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

        int sizeBefore;

        piCurrentNum++;
        piFileSuffixes.add(piCurrentNum.toString());

        DataOutputStream voc = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(
                                PathManager.getIndexDirPath() + "/VocabularyFile" + piCurrentNum + ".txt"
                        )
                )
        );

        DataOutputStream post = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(
                                PathManager.getIndexDirPath() + "/PostingFile" + piCurrentNum + ".txt"
                        )
                )
        );

        /* Create a partial vocabulary and a partial posting file using current tokenInfo's state */
        for(String term : tokenInfo.keySet()) {
            voc.writeUTF(term);
            voc.writeLong(tokenInfo.get(term).size());
            sizeBefore = post.size();
            for(String docId : tokenInfo.get(term).keySet()) {
                post.writeUTF(docId);
                post.writeDouble(
                        tokenInfo.get(term).get(docId).getLeft() / (double)docInfo.get(docId).getMiddle()
                ); // normalized tf
            }
            voc.writeInt(computeInterval(post.size(), sizeBefore)); // Byte length of term's posting data
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

        RandomAccessFile docRAF = new RandomAccessFile(
                PathManager.getIndexDirPath() + "/DocumentsFile.txt", "rw"
        );
        DataOutputStream doc = new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(
                                docRAF.getFD()
                        )
                )
        );

        doc.writeLong(docInfo.size()); // write doc num at the start of the file
        doc.flush();
        for(String docId : docInfo.keySet()) {
            docBytes.put(docId, docRAF.getFilePointer());
            doc.writeUTF(docId);
            doc.writeUTF(docInfo.get(docId).getLeft());
            doc.writeDouble(docInfo.get(docId).getRight()); // At this moment, this must be equal to 0.0
            doc.flush();
        }

        doc.close();

        return docBytes;
    }

    /*
     * Merge partial index files and create DocumentsFile.txt
     */
    private void createFinalIndex() throws IOException {

        RandomAccessFile voc1, voc2, post1, post2;
        DataOutputStream vocMerged = null, postMerged = null;
//        RandomAccessFile voc1RAF, voc2RAF, post1RAF, post2RAF, doc;
        RandomAccessFile vocMergedRAF = null, postMergedRAF = null;
        String suffix1, suffix2, mergedSuffix = "", w1, w2, docId;
        long voc1fp, voc2fp, ptr;
        int pdSz, mergedFilesCounter = 0, wordComparison, sizeBefore, longSize = 8, newPtrsSum = 0;
        int interval;
        boolean isLastMerging = false;

        TreeMap<String, MutablePair<Double, Long>> postData = new TreeMap<>();

        HashMap<String, Long> docBytes = createDocumentsFile(); // use this in merging

        /*
         * In case there's only one partial index, create an empty - dummy partial index
         * for the algorithm to be generic.
         */
        if(piFileSuffixes.size() == 1) {
            piFileSuffixes.add("1");
        }

        /* Merge partial indices */
        while(piFileSuffixes.size() >= 2) {

            suffix1 = piFileSuffixes.remove();
            voc1 = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/VocabularyFile" + suffix1 + ".txt", "rw"
            );
//            voc1 = new DataInputStream(
//                    new BufferedInputStream(
//                            new FileInputStream(voc1RAF.getFD())
//                    )
//            );
            post1 = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/PostingFile" + suffix1 + ".txt", "rw"
            );
//            post1 = new DataInputStream(
//                    new BufferedInputStream(
//                            new FileInputStream(post1RAF.getFD())
//                    )
//            );

            suffix2 = piFileSuffixes.remove();
            voc2 = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/VocabularyFile" + suffix2 + ".txt", "rw"
            );
//            voc2 = new DataInputStream(
//                    new BufferedInputStream(
//                            new FileInputStream(voc2RAF.getFD())
//                    )
//            );
            post2 = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/PostingFile" + suffix2 + ".txt", "rw"
            );
//            post2 = new DataInputStream(
//                    new BufferedInputStream(
//                            new FileInputStream(post2RAF.getFD())
//                    )
//            );

            if(piFileSuffixes.isEmpty()) {
                isLastMerging = true;
                mergedSuffix = "";
            } else {
                mergedSuffix = "_m" + mergedFilesCounter++;
            }

            vocMergedRAF = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/VocabularyFile" + mergedSuffix + ".txt", "rw"
            );
            vocMerged = new DataOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(vocMergedRAF.getFD())
                    )
            );
            postMergedRAF = new RandomAccessFile(
                    PathManager.getIndexDirPath() + "/PostingFile" + mergedSuffix + ".txt", "rw"
            );
            postMerged = new DataOutputStream(
                    new BufferedOutputStream(
                            new FileOutputStream(postMergedRAF.getFD())
                    )
            );

            while(!SharedUtilities.getInstance().isEOFReached(voc1)
                    && !SharedUtilities.getInstance().isEOFReached(voc2)) {

                /* Save previous file pointer values in case there's no need to move the file pointers */
                voc1fp = voc1.getFilePointer();
                voc2fp = voc2.getFilePointer();

                w1 = voc1.readUTF();
                w2 = voc2.readUTF();

                wordComparison = w1.compareTo(w2);

                if (wordComparison < 0) { // w1 < w2

                    vocMerged.writeUTF(w1); // copy term
                    vocMerged.writeLong(voc1.readLong()); // copy df
                    pdSz = voc1.readInt();
                    if(isLastMerging) {
                        newPtrsSum = 0;
                        postMerged.flush();
                        vocMerged.writeLong(postMergedRAF.getFilePointer());
                    }
                    sizeBefore = postMerged.size();
                    do {
                        postMerged.writeUTF(docId = post1.readUTF());
                        postMerged.writeDouble(post1.readDouble());
                        if(isLastMerging) {
                            postMerged.writeLong(docBytes.get(docId));
                            newPtrsSum++; // add an extra pointer
                        }
                        interval = computeInterval(postMerged.size(), sizeBefore);
                    } while(interval != pdSz + (newPtrsSum * longSize));
                    vocMerged.writeInt(interval); // posting data size

                    /* Don't move voc2 file pointer */
                    voc2.seek(voc2fp);

                } else if (wordComparison > 0) { // w1 > w2

                    vocMerged.writeUTF(w2); // copy term
                    vocMerged.writeLong(voc2.readLong()); // copy df
                    pdSz = voc2.readInt();
                    if(isLastMerging) {
                        newPtrsSum = 0;
                        postMerged.flush();
                        vocMerged.writeLong(postMergedRAF.getFilePointer());
                    }
                    sizeBefore = postMerged.size();
                    do {
                        postMerged.writeUTF(docId = post2.readUTF());
                        postMerged.writeDouble(post2.readDouble());
                        if(isLastMerging) {
                            postMerged.writeLong(docBytes.get(docId));
                            newPtrsSum++;
                        }
                        interval = computeInterval(postMerged.size(), sizeBefore);
                    } while(interval != pdSz + (newPtrsSum * longSize));
                    vocMerged.writeInt(interval); // posting data size

                    /* Don't move voc1 file pointer */
                    voc1.seek(voc1fp);

                } else {

                    /* Save post1's data for w1 to a structure */
                    voc1.readLong(); // skip df
                    pdSz = voc1.readInt();
                    ptr = post1.getFilePointer();
                    do {
                        postData.put(docId = post1.readUTF(), new MutablePair<>(post1.readDouble(), docBytes.get(docId)));
                    } while(post1.getFilePointer() != ptr + pdSz);

                    /* Save post2's data for w2 to a structure */
                    voc2.readLong(); // skip df
                    pdSz = voc2.readInt();
                    ptr = post2.getFilePointer();
                    do {
                        postData.put(docId = post2.readUTF(), new MutablePair<>(post2.readDouble(), docBytes.get(docId)));
                    } while(post2.getFilePointer() != ptr + pdSz);

                    /* Write merged files */
                    vocMerged.writeUTF(w1);
                    vocMerged.writeLong(postData.size());
                    if(isLastMerging) {
                        postMerged.flush();
                        vocMerged.writeLong(postMergedRAF.getFilePointer());
                    }
                    sizeBefore = postMerged.size();
                    for(String id : postData.keySet()) {
                        postMerged.writeUTF(id);
                        postMerged.writeDouble(postData.get(id).getLeft());
                        if(isLastMerging)
                            postMerged.writeLong(docBytes.get(id));
                    }
                    vocMerged.writeInt(computeInterval(postMerged.size(), sizeBefore));

                    postData = new TreeMap<>(); // clear posting data structure
                }
            }

            /* In case voc2 has finished, but not voc1 */
            while(!SharedUtilities.getInstance().isEOFReached(voc1)) {
                vocMerged.writeUTF(voc1.readUTF()); // copy term
                vocMerged.writeLong(voc1.readLong()); // copy df
                pdSz = voc1.readInt();
                if(isLastMerging) {
                    newPtrsSum = 0;
                    postMerged.flush();
                    vocMerged.writeLong(postMergedRAF.getFilePointer());
                }
                sizeBefore = postMerged.size();
                do {
                    postMerged.writeUTF(docId = post1.readUTF()); // write doc id
                    postMerged.writeDouble(post1.readDouble()); // write tf
                    if(isLastMerging) {
                        postMerged.writeLong(docBytes.get(docId));
                        newPtrsSum++;
                    }
                    interval = computeInterval(postMerged.size(), sizeBefore);
                } while (interval != pdSz + (newPtrsSum * longSize));
                vocMerged.writeInt(interval); // posting data size
            }

            /* In case voc1 has finished, but not voc2 */
            while(!SharedUtilities.getInstance().isEOFReached(voc2)) {
                vocMerged.writeUTF(voc2.readUTF()); // copy term
                vocMerged.writeLong(voc2.readLong()); // copy df
                pdSz = voc2.readInt();
                if(isLastMerging) {
                    newPtrsSum = 0;
                    postMerged.flush();
                    vocMerged.writeLong(postMergedRAF.getFilePointer());
                }
                sizeBefore = postMerged.size();
                do {
                    postMerged.writeUTF(docId = post2.readUTF()); // write doc id
                    postMerged.writeDouble(post2.readDouble()); // write tf
                    if(isLastMerging) {
                        postMerged.writeLong(docBytes.get(docId));
                        newPtrsSum++;
                    }
                    interval = computeInterval(postMerged.size(), sizeBefore);
                } while (interval != pdSz + (newPtrsSum * longSize));
                vocMerged.writeInt(interval); // posting data size
            }

            voc1.close(); voc2.close();
            post1.close(); post2.close();
            vocMerged.close(); postMerged.close();

            /* Delete merged files */
            new File(PathManager.getIndexDirPath() + "/VocabularyFile" + suffix1 + ".txt").delete();
            new File(PathManager.getIndexDirPath() + "/PostingFile" + suffix1 + ".txt").delete();
            new File(PathManager.getIndexDirPath() + "/VocabularyFile" + suffix2 + ".txt").delete();
            new File(PathManager.getIndexDirPath() + "/PostingFile" + suffix2 + ".txt").delete();

            /* Add merged file suffix to queue */
            piFileSuffixes.add(mergedSuffix);
        }

        fillDocumentVectorLengths();
    }

    /*
     * Compute document vector lengths and fill the empty fields in DocumentsFile.txt
     * TODO: Do a more efficient approach, like Papadakos said in e-mail
     */
    private void fillDocumentVectorLengths() throws IOException {

        int pdSz;
        double idf, tf;
        long df, ptr;
        String docId;

        RandomAccessFile voc = new RandomAccessFile(
                PathManager.getIndexDirPath() + "/VocabularyFile.txt", "rw"
        );

        RandomAccessFile post = new RandomAccessFile(
                PathManager.getIndexDirPath() + "/PostingFile.txt", "rw"
        );

        RandomAccessFile doc = new RandomAccessFile(
                PathManager.getIndexDirPath() + "/DocumentsFile.txt", "rw"
        );

        SharedUtilities.getInstance().docsNum = doc.readLong();

        while(!SharedUtilities.getInstance().isEOFReached(voc)) {
            voc.readUTF(); // term
            df = voc.readLong(); // df
            ptr = voc.readLong(); // ptr
            pdSz = voc.readInt(); // record's posting data size
            do {
                docId = post.readUTF();
                tf = post.readDouble();
                post.readLong();
                idf = Math.log(SharedUtilities.getInstance().docsNum / (double)df) / Math.log(2.0);
                docInfo.get(docId).setRight(docInfo.get(docId).getRight() + Math.sqrt(tf * idf));
            } while(post.getFilePointer() != ptr + pdSz);
        }

        /* Square the results when sum computation is finished and write them to DocumentsFile.txt */
        for(String id : docInfo.keySet()) {
            docInfo.get(id).setRight(Math.sqrt(docInfo.get(id).getRight()));
            doc.readUTF();
            doc.readUTF();
            doc.writeDouble(docInfo.get(id).getRight());
        }
    }

    /*
     * Computes interval between end and start
     * Takes care of overflows
     */
    private int computeInterval(int end, int start) {
        if(end >= start)
            return end - start;
        else { // overflow
            System.out.println("OOOPS!!!");
            return 1; /* TODO: Implement overflow case */
        }
    }

}
