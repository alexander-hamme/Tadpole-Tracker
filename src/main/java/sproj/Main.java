package sproj;

import sproj.util.Logger;

/* Launches the TrackerApp */
public class Main {

    static final Logger logger = new Logger();   // LogManager.getLogger("Main");

    private Main(){/* not to be instantiated*/}

    public static void main(String[] args) throws Exception {

        try {
            TrackerApp.launch();
        } catch (Exception e) {
            // todo    specific error handling, etc
//            e.printStackTrace();
            logger.fatal(e);
            throw e;
        } finally {
            // todo
            logger.info("Exiting SinglePlateTracker App");
        }
        System.exit(0);
    }
}
