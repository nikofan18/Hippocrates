package Evaluation;

import Searching.Searcher;
import Utilities.PathManager;
import Utilities.SharedUtilities;
import gr.uoc.csd.hy463.Topic;
import gr.uoc.csd.hy463.TopicsReader;
import org.apache.commons.lang3.tuple.MutablePair;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

public class IRQueryEvaluator {

    // Fields

    /*
     * Holds info (from qrels.txt) like this:
     * topicNum1 -> [{docId1, relevance1},
     *              {docId2, relevance2}, ...]
     * topicNum2 -> ...
     */
    private HashMap<Integer, HashMap<String, Integer>> qrelsHm;

    /*
     * Holds info (from results.txt) like this:
     * topicNum1 -> [{rank1, docId1, score1},
     *               {rank2, docId2, score2}, ...]
     * topicNum2 -> ...
     */
    private HashMap<Integer, TreeMap<Integer, MutablePair<String, Double>>> resultsHm;

    /*
     * To store evaluation measurements
     */
    private HashMap<Integer, Double> bprefHm, avepHm, ndcgHm;

    /*
     * Maximum number of docs to take from an answer
     */
    private Integer maxRank;

    // Constructor

    /*
     * Initialize things ..
     */
    public IRQueryEvaluator() {
        bprefHm = new HashMap<>();
        avepHm = new HashMap<>();
        ndcgHm = new HashMap<>();
        resultsHm = new HashMap<>();
        qrelsHm = new HashMap<>();
        maxRank = 1000;
    }

    // Methods

    /*
     * Produce results to topic queries, compare them with judged results,
     * compute the three measures and produce statistics
     */
    public void evaluate(boolean isResultsFileCreated) throws Exception {
        if (isResultsFileCreated) {
            System.out.println("Loading results.txt");
            loadResults();
        } else {
            System.out.println("Creating results.txt");
            produceResults();
        }

        System.out.println("Loading qrels.txt");
        loadQrels();

        System.out.println("Computing measures");
        computeBpref();
        computeAvep();
        computeNdcg();

        System.out.println("Writing eval_results.txt");
        produceEvalResults();

        System.out.println("Producing statistics");
        produceStatistics();

        System.out.println("Finished.");
    }

    /*
     * Makes a -processed like in B9, phase A- query
     * and produces results.txt file with top 1000 results for every topic in topics.xml
     */
    private void produceResults() throws Exception {

        BufferedWriter res = new BufferedWriter(
                new FileWriter(
                        PathManager.getInstance().getEvalFilesPath() + "/results.txt"
                )
        );
        Searcher s = new Searcher();
        ArrayList<Topic> topics = TopicsReader.readTopics(
                PathManager.getInstance().getEvalFilesPath() + "/topics.xml"
        );

        String runName = "R0";
        for (Topic topic : topics) {
            JSONObject answer = s.search(
                    topic.getDescription(), topic.getType().toString()
            );
            TreeMap<Integer, MutablePair<String, Double>> tm = new TreeMap<>();
            Integer topicNo = topic.getNumber();

            Integer rank = 0;
            int results = Integer.parseInt(answer.get("results").toString());
            int lastDoc = Math.max(results - maxRank, 0);
            for (int j = results - 1; j >= lastDoc; j--) {

                /* The result values */
                JSONObject obj = (JSONObject) answer.get("doc" + j);
                String pmcid =
                        obj.get("name").toString().substring(0, obj.get("name").toString().lastIndexOf("."));
                rank++;
                Double score = Double.valueOf(obj.get("score").toString());

                /* Write them to disk */
                res.write(topicNo.toString());
                res.write('\t');
                res.write("0"); // unused constant
                res.write('\t');
                res.write(pmcid);
                res.write('\t');
                res.write(rank.toString());
                res.write('\t');
                res.write(score.toString());
                res.write('\t');
                res.write(runName);
                res.write("\n");

                /* Save result values to memory too for instant use */
                MutablePair<String, Double> mp = new MutablePair<>(pmcid, score);
                tm.put(rank, mp);

            }
            resultsHm.put(topicNo, tm);
        }
        res.close();
    }

    /*
     * Returns a hashset with all document ids from DocumentsFile.txt
     */
    private HashSet<String> findAllDocIds() throws IOException {

        HashSet<String> ret = new HashSet<>();

        RandomAccessFile doc = new RandomAccessFile(
                PathManager.getInstance().getIndexDirPath() + "/DocumentsFile.txt", "rw"
        );
        doc.readLong(); // skip docs number

        while (!SharedUtilities.getInstance().isEOFReached(doc)) {
            ret.add(doc.readUTF()); // doc id
            doc.readUTF(); // path
            doc.readDouble(); // vec length
        }

        doc.close();

        return ret;
    }

    /*
     * Load relevant docs to topics as decided by experts (qrels.txt) into memory
     * Note that judged documents that do not exist in corpus will be ignored
     */
    private void loadQrels() throws Exception {

        HashSet<String> allDocIds = findAllDocIds(); // ids of all docs in corpus

        BufferedReader br = new BufferedReader(
                new FileReader(PathManager.getInstance().getEvalFilesPath() + "/qrels.txt")
        );
        String line;
        HashMap<String, Integer> hm = new HashMap<>();
        Integer topicNo = 1, prevTopicNo = topicNo;
        while ((line = br.readLine()) != null) {
            String[] lineWords = line.split("\t");
            String docId = lineWords[2];
            if (!allDocIds.contains(docId)) // ignore docs non-existed in corpus
                continue;
            Integer relevance = Integer.valueOf(lineWords[3]);
            topicNo = Integer.valueOf(lineWords[0]);
            if (!topicNo.equals(prevTopicNo)) {
                qrelsHm.put(prevTopicNo, hm);
                prevTopicNo = topicNo;
                hm = new HashMap<>();
            }
            hm.put(docId, relevance);
        }
        qrelsHm.put(topicNo, hm);
        br.close();

    }

    /*
     * Loads information from results.txt into memory
     */
    private void loadResults() throws Exception {
        BufferedReader br = new BufferedReader(
                new FileReader(
                        PathManager.getInstance().getEvalFilesPath() + "/results.txt"
                )
        );
        String line;
        TreeMap<Integer, MutablePair<String, Double>> tm = new TreeMap<>();
        Integer topicNo = 1, prevTopicNo = topicNo;
        while ((line = br.readLine()) != null) {
            String[] lineWords = line.split("\t");
            String docId = lineWords[2];
            Double score = Double.valueOf(lineWords[4]);
            Integer rank = Integer.valueOf(lineWords[3]);
            topicNo = Integer.valueOf(lineWords[0]);
            if (!topicNo.equals(prevTopicNo)) {
                resultsHm.put(prevTopicNo, tm);
                prevTopicNo = topicNo;
                tm = new TreeMap<>();
            }
            MutablePair<String, Double> mp = new MutablePair<>(docId, score);
            tm.put(rank, mp);
        }
        resultsHm.put(topicNo, tm);
        br.close();
    }

    /*
     * Compute bpref measure for each topic and store measurements into bprefHm
     */
    private void computeBpref() {

        for (Integer topicNo : qrelsHm.keySet()) {
            HashMap<String, Integer> judged = qrelsHm.get(topicNo);
            TreeMap<Integer, MutablePair<String, Double>> retrieved = resultsHm.get(topicNo);

            /* Compute R (# relevant judged), N (# non-relevant judged) */
            int R = 0;
            for (String docId : judged.keySet()) {
                int relevance = judged.get(docId);
                if (relevance != 0) // convert graded relevance to binary: 0 = {0}, 1 = {1, 2}
                    R++;
            }

            /* Compute sum */
            double sum = 0.0;
            int irrelevantNo = 0;
            for (Integer rank : retrieved.keySet()) { // for every retrieved doc
                String docId = retrieved.get(rank).getLeft();

                if (judged.containsKey(docId)) { // if it's judged
                    if (judged.get(docId) == 0) // if it's irrelevant, count it to use this when a relevant is found
                        irrelevantNo++;
                    else // if it's relevant, update sum using the number of irrelevant docs until this time
                        sum += (1 - (irrelevantNo / (double) R));
                }
            }

            /* Compute bpref */
            Double bpref = (1 / (double) R) * sum;
            bprefHm.put(topicNo, bpref);

        }

    }

    /*
     * Compute AveP' measure for each topic and store measurements into avepHm
     */
    private void computeAvep() {

        for (Integer topicNo : qrelsHm.keySet()) {
            HashMap<String, Integer> judged = qrelsHm.get(topicNo);
            TreeMap<Integer, MutablePair<String, Double>> retrieved = resultsHm.get(topicNo);

            /* Compute R (# relevant judged) */
            int R = 0;
            for (String docId : judged.keySet()) {
                int relevance = judged.get(docId);
                if (relevance != 0) // convert graded relevance to binary: 0 = {0}, 1 = {1, 2}
                    R++;
            }

            /* Create condensed list (exclude unjudged docs) */
            int condensedRank = 1;
            TreeMap<Integer, String> condensed = new TreeMap<>();
            for (Integer rank : retrieved.keySet()) { // for every retrieved doc
                String docId = retrieved.get(rank).getLeft();
                if (judged.containsKey(docId)) {
                    condensed.put(condensedRank, docId);
                    condensedRank++;
                }
            }

            /* Compute sum */
            double sum = 0.0;
            int relevant = 0;
            for (Integer cRank : condensed.keySet()) {
                String docId = condensed.get(cRank);

                if (judged.get(docId) != 0) { // relevant
                    relevant++;
                    sum += relevant / (double)cRank;
                }
            }

            /* Compute AveP' */
            Double avep = (1 / (double) R) * sum;
            avepHm.put(topicNo, avep);

        }

    }

    /*
     * Compute nDCG' measure for each topic and store measurements into ndcgHm
     */
    private void computeNdcg() {
        for (Integer topicNo : qrelsHm.keySet()) {
            HashMap<String, Integer> judged = qrelsHm.get(topicNo);
            TreeMap<Integer, MutablePair<String, Double>> retrieved = resultsHm.get(topicNo);

            /* Create condensed list (exclude unjudged docs) */
            int condensedRank = 1;
            TreeMap<Integer, String> condensed = new TreeMap<>();
            for (Integer rank : retrieved.keySet()) { // for every retrieved doc
                String docId = retrieved.get(rank).getLeft();
                if (judged.containsKey(docId)) {
                    condensed.put(condensedRank, docId);
                    condensedRank++;
                }
            }

            /* Find DCG' from condensed list */
            List<Integer> sortedCondensed = new ArrayList<>();
            double dcg = 0.0;
            for (Integer cRank : condensed.keySet()) {
                String docId = condensed.get(cRank);
                Integer relevance = judged.get(docId);
                if (cRank == 1)
                    dcg = relevance; // rel_1
                else
                    dcg += relevance / (Math.log(cRank + 1) / Math.log(2.0));
                sortedCondensed.add(relevance);
            }

            /* Sort condensed list to compute IDCG' */
            sortedCondensed.sort(
                    (Integer i1, Integer i2) ->
                    {
                        if (i1 < i2)
                            return 1;
                        else if (i1 > i2)
                            return -1;
                        else
                            return 0;
                    });

            /* Fing IDCG' from sortedCondensed list */
            double idcg = 0.0;
            int rank = 1;
            Iterator<Integer> it = sortedCondensed.iterator();
            while (it.hasNext()) {
                Integer relevance = it.next();
                if (rank == 1)
                    idcg = relevance;
                else
                    idcg += relevance / (Math.log(rank + 1) / Math.log(2.0));
                rank++;
            }

            /* Finally, compute nDCG' */
            double ndcg = 0.0;
            if (dcg != 0.0) // means that idcg != 0.0 too
                ndcg = dcg / idcg;
            ndcgHm.put(topicNo, ndcg);

        }

    }

    /*
     * Writes evaluation measurements to eval_results.txt file
     */
    private void produceEvalResults() throws IOException {
        BufferedWriter er = new BufferedWriter(
                new FileWriter(
                        PathManager.getInstance().getEvalFilesPath() + "/eval_results.txt"
                )
        );
        for (Integer topicNum : bprefHm.keySet()) {
            er.write(topicNum.toString());
            er.write("\t");
            er.write(bprefHm.get(topicNum).toString());
            er.write("\t");
            er.write(avepHm.get(topicNum).toString());
            er.write("\t");
            er.write(ndcgHm.get(topicNum).toString());
            er.write("\n");
        }
        er.close();
    }

    /*
     * Computes and prints some statistics from the evaluation results
     */
    private void produceStatistics() {

        double maxBpref = bprefHm.get(1), minBpref = bprefHm.get(1), medianBpref, avgBpref = 0.0;
        double maxAvep = avepHm.get(1), minAvep = avepHm.get(1), medianAvep, avgAvep = 0.0;
        double maxNdcg = ndcgHm.get(1), minNdcg = ndcgHm.get(1), medianNdcg, avgNdcg = 0.0;
        int maxBprefTopic = 1, minBprefTopic = 1;
        int maxAvepTopic = 1, minAvepTopic = 1;
        int maxNdcgTopic = 1, minNdcgTopic = 1;
        int topicsNumber = 0;
        TreeMap<Integer, Double> sumsHm = new TreeMap<>(); // the sum of the three measures for every topic
        ArrayList<Double> sortedBprefList = new ArrayList<>();
        ArrayList<Double> sortedAvepList = new ArrayList<>();
        ArrayList<Double> sortedNdcgList = new ArrayList<>();
        for(Integer topicNo : bprefHm.keySet()) {

            topicsNumber++;

            double bpref = bprefHm.get(topicNo);
            double avep = avepHm.get(topicNo);
            double ndcg = ndcgHm.get(topicNo);

            /* Compute max values */
            if(bpref > maxBpref) {
                maxBpref = bpref;
                maxBprefTopic = topicNo;
            }
            if(avep > maxAvep) {
                maxAvep = avep;
                maxAvepTopic = topicNo;
            }
            if(ndcg > maxNdcg) {
                maxNdcg = ndcg;
                maxNdcgTopic = topicNo;
            }

            /* Compute min values */
            if(bpref < minBpref) {
                minBpref = bpref;
                minBprefTopic = topicNo;
            }
            if(avep < minAvep) {
                minAvep = avep;
                minAvepTopic = topicNo;
            }
            if(ndcg < minNdcg) {
                minNdcg = ndcg;
                minNdcgTopic = topicNo;
            }

            /* For average computation */
            avgBpref += bpref;
            avgAvep += avep;
            avgNdcg += ndcg;

            /* For median computation */
            sortedBprefList.add(bpref);
            sortedAvepList.add(avep);
            sortedNdcgList.add(ndcg);

            /* Sum of measures */
            sumsHm.put(topicNo, bpref + avep + ndcg);

        }

        /* Averages */
        avgBpref /= topicsNumber;
        avgAvep /= topicsNumber;
        avgNdcg /= topicsNumber;

        /* Medians */
        Collections.sort(sortedBprefList);
        Collections.sort(sortedAvepList);
        Collections.sort(sortedNdcgList);
        if(topicsNumber % 2 == 0) {
            medianBpref = sortedBprefList.get((topicsNumber / 2) - 1) + sortedBprefList.get(topicsNumber / 2);
            medianAvep = sortedAvepList.get((topicsNumber / 2) - 1) + sortedAvepList.get(topicsNumber / 2);
            medianNdcg = sortedNdcgList.get((topicsNumber / 2) - 1) + sortedNdcgList.get(topicsNumber / 2);
        } else {
            medianBpref = sortedBprefList.get(topicsNumber / 2); // floor value
            medianAvep = sortedAvepList.get(topicsNumber / 2); // floor value
            medianNdcg = sortedNdcgList.get(topicsNumber / 2); // floor value
        }

        /* Print statistics */
        System.out.println("=== Evaluation Statistics ===");

        System.out.println("Max bpref topic: " + maxBprefTopic + " (" + maxBpref + ")");
        System.out.println("Max AveP' topic: " + maxAvepTopic + " (" + maxAvep + ")");
        System.out.println("Max nDCG' topic: " + maxNdcgTopic + " (" + maxNdcg + ")");

        System.out.println("Min bpref topic: " + minBprefTopic + " (" + minBpref + ")");
        System.out.println("Min AveP' topic: " + minAvepTopic + " (" + minAvep + ")");
        System.out.println("Min nDCG' topic: " + minNdcgTopic + " (" + minNdcg + ")");

        System.out.println("bpref average: " + avgBpref);
        System.out.println("AveP' average: " + avgAvep);
        System.out.println("nDCG' average: " + avgNdcg);

        System.out.println("bpref median: " + medianBpref);
        System.out.println("AveP' median: " + medianAvep);
        System.out.println("nDCG' median: " + medianNdcg);

        System.out.println("Sum of measures for each topic:");
        for(Integer topicNo : sumsHm.keySet())
            System.out.println(topicNo + ": " + sumsHm.get(topicNo));

        System.out.println("=============================");

    }

}
