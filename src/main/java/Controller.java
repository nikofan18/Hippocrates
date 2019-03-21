import givenSoftware.*;
import org.json.JSONObject;
import static spark.Spark.*;

public class Controller {
    public static void main(String[] args) {

        // put files in 'src/main/resources/public'
        staticFiles.location("/public");

        // this is the root REST path
        path("/api", () -> {

            // test get
            get("/test", (req, res) -> "Success !!!");

            // the query from the search engine
            get("/query/:query", (req, res) -> {
                JSONObject obj = new JSONObject();
                return obj.put("query", req.params(":query"));
            });



        });

    }
}