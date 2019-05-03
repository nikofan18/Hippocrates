package Evaluation;

import Searching.Searcher;
import Utilities.PathManager;
import gr.uoc.csd.hy463.Topic;
import gr.uoc.csd.hy463.TopicsReader;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;

public class IRQueryEvaluator {

    /*
     * Makes a -processed like in B9, phase A- query
     * and produces results.txt file with top 1000 results for every topic in topics.xml
     */
    public void produceResults() throws Exception {

        BufferedWriter res = new BufferedWriter(
                new FileWriter(
                        PathManager.getInstance().getEvalFilesPath() + "/results.txt"
                )
        );
        Searcher s = new Searcher();
        ArrayList<Topic> topics = TopicsReader.readTopics(PathManager.getInstance().getTopicsPath());

        for(Topic topic : topics) {
            JSONObject answer = s.search(topic.getDescription(), topic.getType().toString());
            int start = Math.min(Integer.parseInt(answer.get("results").toString()), 1000);
            for(int j = start - 1; j >= 0; j--) {
                res.write(String.valueOf(topic.getNumber())); // TOPIC_NO
                res.write('\t');
                res.write("0"); // UNUSED CONSTANT
                res.write('\t');
                JSONObject obj = (JSONObject) answer.get("doc" + j);
                res.write(obj.get("name").toString().substring(0, obj.get("name").toString().lastIndexOf("."))); // PMCID
                res.write('\t');
                int rank = start - j;
                res.write(String.valueOf(rank)); // RANK
                res.write('\t');
                res.write("RUN_0"); // RUN_NAME
                res.write("\n");
            }
            res.write("TOPIC_SEPARATOR\n");
        }
        res.close();
    }

}
