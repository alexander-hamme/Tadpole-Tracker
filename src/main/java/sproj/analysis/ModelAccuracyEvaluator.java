package sproj.analysis;


import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.util.IOUtils;
import sproj.yolo_porting_attempts.YOLOModelContainer;

import org.bytedeco.javacpp.avutil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class to test the accuracy of the underlying Yolo model
 *
 * Calculate metrics of how frequently  the model correctly
 * find all subjects of interest in each frame of a video feed
 */
public class ModelAccuracyEvaluator {

    private FFmpegFrameGrabber grabber;
    private YOLOModelContainer yoloModelContainer;
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();


    private void initializeGrabber(File videoFile) throws FrameGrabber.Exception {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET);           // Suppress verbose FFMPEG metadata output to console
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();        // open video file
    }

    public ModelAccuracyEvaluator() throws IOException {
        yoloModelContainer = new YOLOModelContainer();
    }

    public List<Double> evaluateModelOnVideo(File videoFile, int numbAnimals, Rect cropRectangle) throws IOException {

        initializeGrabber(videoFile);
        return evaluateOnVideo(numbAnimals, cropRectangle);

    }


    /**
     * Returns a list of detection accuracy evaluations, one for each frame of the video.
     *
     * For any given frame, if the model finds all animals correctly, the evaluation is 1.0, or 100%
     *
     * If the number of predictions
     * @param numbAnimals
     * @param cropRect
     * @return
     * @throws IOException
     */
    private List<Double> evaluateOnVideo(double numbAnimals, Rect cropRect) throws IOException {

        int frameNo;
        int totalFrames = grabber.getLengthInVideoFrames();

        List<DetectedObject> detectedObjects;
        List<Double> detectionAccuracies = new ArrayList<>(totalFrames);   // store one detection accuracy record per frame


        Frame frame;
        while ((frame = grabber.grabImage()) != null) {

            detectedObjects = yoloModelContainer.runInference(new Mat(frameConverter.convertToMat(frame), cropRect));
            double accuracy = detectedObjects.size() / numbAnimals;
            detectionAccuracies.add(
                    accuracy <= 1.0 ? accuracy : 1 - Math.abs(1 - accuracy)     // count each extra detection as one negative detection from the score
            );

            frameNo = grabber.getFrameNumber();
            System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");
        }
        return detectionAccuracies;
    }

    private void tearDown() {
        try {
            grabber.close();
        } catch (FrameGrabber.Exception ignored) {
        }
    }

    private List<String> readTextFiles(String textFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(textFile))) {
            stream.forEach(lines::add);
        }
        return lines;
    }


    /**
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, NumberFormatException {


        if (args.length < 1) {
            System.out.println(
                "Required: text file with list of video files to evaluate on.\n" +
                "Syntax for this file is comma separated values, in this format:\n" +
                "<full/path/to/video>,<number of animals in video>,<crop dimensions\n" +
                "crop dimensions should be space separated integers, of the form: center_x center_y width height"
            );
            return;
        }


        ModelAccuracyEvaluator evaluator = new ModelAccuracyEvaluator();

        List<String> textLines = evaluator.readTextFiles(args[0]);        // video files to evaluate on

        List<List<Double>> detectionAccuracies = new ArrayList<>(textLines.size());


        for (String line : textLines) {

            String[] split = line.split(",");

            if (split.length < 2) {continue;}

            File videoFile;

            int numbAnimals;
            Rect cropRect;
            List<Integer> cropDims = new ArrayList<>();

            try {

                videoFile = new File(split[0]);
                assert videoFile.exists() && !videoFile.isDirectory();

                numbAnimals = Integer.parseInt(split[1]);

                // convert third string argument to 4 integers; the crop dimensions
                Arrays.asList(split[2].split(" ")).forEach(s ->
                        cropDims.add(Integer.valueOf(s)));

                cropRect = new Rect(
                        cropDims.get(0), cropDims.get(1), cropDims.get(2), cropDims.get(3)
                );

            } catch (AssertionError ignored) {
                System.out.println(String.format(
                        "Invalid video file path in line: '%s'", line
                ));
                continue;

            } catch (NumberFormatException ignored) {
                System.out.println(String.format(
                        "Skipping incorrectly formatted line: '%s'", line
                ));
                continue;
            }

            System.out.println(String.format("\nRunning evaluator on video %d of %d", textLines.indexOf(line)+1, textLines.size()));

            List<Double> dataPoints = evaluator.evaluateModelOnVideo(videoFile, numbAnimals, cropRect);

            IOUtils.writeDataToFile(
                    dataPoints, videoFile.toPath().getParent() + "/" + videoFile.getName().substring(0, videoFile.getName().length()-4) + ".dat"
            );

//            detectionAccuracies.add(dataPoints);
        }

        detectionAccuracies.forEach(lst ->

                System.out.println("Average accuracy: " + lst.stream().reduce(0.0, Double::sum) / lst.size())
        );
    }
}