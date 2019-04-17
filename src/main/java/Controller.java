import Searching.Searcher;
import org.json.JSONObject;

import java.io.IOException;

import static spark.Spark.*;

public class Controller {

    public static Searcher s;

    static {
        try {
            s = new Searcher();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        /*
         * Put files in 'src/main/resources/public'
         */
        staticFiles.location("/public");

        /*
         * Root REST path
         */
        path("/api", () -> {

            /*
             * Query search get request
             */
            get("/queries/:query", (req, res) -> {
                JSONObject answer = s.search(req.params(":query"));
                System.out.println(answer);
                return answer;
            });

        });

    }
}