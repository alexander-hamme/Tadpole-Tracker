package sproj.util;


/**
 * Temporary class for logging until formal Log4J logging framework is incorporated
 */
public class Logger {

    public Logger() {

    }

    public void warn(String msg) {
        System.err.println(msg);
    }

    public void info(String msg) {
        System.out.println(msg);
    }


    public void error(String msg, Exception e) {
        info(msg);
        System.err.println(e.getMessage());
        e.printStackTrace();
    }

    public void error(Exception e) {
        System.err.println(e.getMessage());
        e.printStackTrace();
    }
}
