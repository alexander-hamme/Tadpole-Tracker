package sproj;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

import sproj.util.Logger;

public class Main {

    static final Logger logger = new sproj.util.Logger();   // LogManager.getLogger("Main");

    private Main(){/* not to be instantiated*/}

    public static void main(String[] args) {

        TrackerApp app = new TrackerApp();

        try {
            app.run();
        } catch (Exception e) {
            // todo    specific error handling, etc
            e.printStackTrace();
            logger.error(e);
        } finally {
            // todo
            logger.info("Exiting Tracker App");
        }
    }
}
