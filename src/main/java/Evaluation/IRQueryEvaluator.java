package Evaluation;

import Searching.Searcher;
import Utilities.PathManager;
import gr.uoc.csd.hy463.Topic;
import gr.uoc.csd.hy463.TopicsReader;
import org.apache.commons.lang3.tuple.MutablePair;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

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
    public IRQueryEvaluator() throws Exception {
        bprefHm = new HashMap<>();
        avepHm = new HashMap<>();
        ndcgHm = new HashMap<>();
        resultsHm = new HashMap<>();
        qrelsHm = new HashMap<>();
        maxRank = 1000;

        /* Load relevant docs to topics as decided by experts */
        System.out.println("Loading qrels.txt");
        BufferedReader br = new BufferedReader(
                new FileReader(PathManager.getInstance().getEvalFilesPath() + "/qrels.txt")
        );
        String line;
        HashMap<String, Integer> hm = new HashMap<>();
        Integer topicNo = 1, prevTopicNo = topicNo;
        while ((line = br.readLine()) != null) {
            String[] lineWords = line.split("\t");
            String docId = lineWords[2];
            Integer relevance = Integer.valueOf(lineWords[3]);
            topicNo = Integer.valueOf(lineWords[0]);
            if(!topicNo.equals(prevTopicNo)) {
                qrelsHm.put(prevTopicNo, hm);
                prevTopicNo = topicNo;
                hm = new HashMap<>();
            }
            hm.put(docId, relevance);
        }
        qrelsHm.put(topicNo, hm);
        br.close();
    }

    // Methods

    /* TODO: explain what this does when its implementation finishes */
    public void evaluate(boolean isResultsFileCreated) throws Exception {
        if (isResultsFileCreated) {
            System.out.println("Loading results.txt");
            loadResults();
        } else {
            System.out.println("Creating results.txt");
            produceResults();
        }
        System.out.println("Computing measures");
        computeBpref();
        computeAvep();
//        computeNdcg();
        System.out.println("Writing eval_results.txt");
        produceEvalResults();
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

        String runName = "FIRST_RUN";
        for (Topic topic : topics) {
            JSONObject answer = s.search(topic.getDescription(), topic.getType().toString());
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
            if(!topicNo.equals(prevTopicNo)) {
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

        for(Integer topicNo : qrelsHm.keySet()) {
            HashMap<String, Integer> judged = qrelsHm.get(topicNo);
            TreeMap<Integer, MutablePair<String, Double>> retrieved = resultsHm.get(topicNo);

            /* Compute R (# relevant judged), N (# non-relevant judged) */
            int R = 0, N;
            for(String docId : judged.keySet()) {
                int relevance = judged.get(docId);
                if(relevance != 0) // convert graded relevance to binary: 0 = {0}, 1 = {1, 2}
                    R++;
            }
            N = judged.size() - R;

            /* Compute sum */
            double sum = 0.0;
            int irrelevantNo = 0;
            for(Integer rank : retrieved.keySet()) { // for every retrieved doc
                String docId = retrieved.get(rank).getLeft();

                if(judged.containsKey(docId)) { // if it's judged
                    if (judged.get(docId) == 0) // if it's irrelevant, count it to use this when a relevant is found
                        irrelevantNo++;
                    else // if it's relevant, update sum using the number of irrelevant docs until this time
                        sum += (1 - (irrelevantNo / (double)Math.min(R, N)));
                }
            }

            /* Compute bpref */
            Double bpref = (1 / (double)R) * sum;
            bprefHm.put(topicNo, bpref);

        }

    }

    /*
     * Compute AveP' measure for each topic and store measurements into avepHm
     */
    private void computeAvep() {

        for(Integer topicNo : qrelsHm.keySet()) {
            HashMap<String, Integer> judged = qrelsHm.get(topicNo);
            TreeMap<Integer, MutablePair<String, Double>> retrieved = resultsHm.get(topicNo);

            /* Compute R (# relevant judged) */
            int R = 0;
            for(String docId : judged.keySet()) {
                int relevance = judged.get(docId);
                if(relevance != 0) // convert graded relevance to binary: 0 = {0}, 1 = {1, 2}
                    R++;
            }

            /* Create condensed list (exclude unjudged docs) */
            int condensedRank = 1;
            TreeMap<Integer, String> condensed = new TreeMap<>();
            for(Integer rank : retrieved.keySet()) { // for every retrieved doc
                String docId = retrieved.get(rank).getLeft();
                if(judged.containsKey(docId)) {
                    condensed.put(condensedRank, docId);
                    condensedRank++;
                }
            }

            /* Compute sum */
            double sum = 0.0;
            int relativeNo = 0;
            for(Integer cRank : condensed.keySet()) {
                String docId = condensed.get(cRank);

                if(judged.get(docId) != 0) { // relevant
                    relativeNo++;
                    sum += relativeNo / cRank;
                }
            }

            /* Compute AveP' */
            Double avep = (1 / (double)R) * sum;
            avepHm.put(topicNo, avep);

        }

    }

    /*
     * Compute nDCG' measure for each topic and store measurements into ndcgHm
     */
    private void computeNdcg() {
        /* TODO */
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
            //er.write("\t");
            //er.write(ndcgHm.get(topicNum).toString());
            er.write("\n");
        }
        er.close();
    }

}
