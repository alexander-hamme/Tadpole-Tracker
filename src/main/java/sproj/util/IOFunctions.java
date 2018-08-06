package sproj.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sproj.ObjectDetector;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

public final class IOFunctions {
    private final static Logger LOGGER = LoggerFactory.getLogger(IOFunctions.class);
    private IOFunctions() {}

    public static byte[] readAllBytesOrExit(final String fileName) throws IOException {
        try {
            return IOUtils.toByteArray(ObjectDetector.class.getResourceAsStream(fileName));
        } catch (IOException | NullPointerException ex) {
            LOGGER.error("Failed to read [{}]!", fileName);
            throw new IOException("Failed to read [" + fileName + "]!", ex);
        }
    }

    public static List<String> readAllLinesOrExit(final String filename) throws IOException {
        try {
            File file = new File(ObjectDetector.class.getResource(filename).toURI());
            return Files.readAllLines(file.toPath(), Charset.forName("UTF-8"));
        } catch (IOException | URISyntaxException ex) {
            LOGGER.error("Failed to read [{}]!", filename, ex.getMessage());
            throw new IOException("Failed to read [" + filename + "]!", ex);
        }
    }

    public static void createDirIfNotExists(final File directory) {
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    public static String getFileName(final String path) {
        return path.substring(path.lastIndexOf("/") + 1, path.length());
    }
}

