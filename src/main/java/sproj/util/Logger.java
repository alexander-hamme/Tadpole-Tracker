package sproj.util;


/**
 * Temporary class until you get org.slf4j.impl.StaticLoggerBinder to work
 */
public class Logger {

    public Logger() {

    }

    public void warn(String msg) {
        logSimpleMessage(msg);
    }

    public void info(String msg) {
        logSimpleMessage(msg);
    }


    public void error(String msg, Exception e) throws Exception {
        logSimpleMessage(msg);
        throw e;
    }

    public void error(Exception e) throws Exception {
        logSimpleMessage(e.getMessage());
        throw e;
    }

    private void logSimpleMessage(Object message) {
        // todo don't do this in the end?
        // logger.info(...)
        System.out.println(message);
    }
}
