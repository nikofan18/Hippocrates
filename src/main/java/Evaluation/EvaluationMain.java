package Evaluation;

public class EvaluationMain {

    public static void main(String[] args) throws Exception {
        IRQueryEvaluator qe = new IRQueryEvaluator();
        qe.evaluate(false); // set this to 'true' to use existing results.txt file
    }

}
