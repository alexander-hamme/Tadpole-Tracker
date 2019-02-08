package sproj.analysis;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import sproj.util.DetectionsParser;
import sproj.util.IOUtils;
import sproj.yolo.YOLOModelContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Abstract class extended by TrackerAccuracyEvaluator and ModelAccuracyEvaluator classes
 */
public abstract class ModelEvaluator {

    protected final boolean CHECK_TO_RESUME_PROGRESS = true;      // for resuming evaluation instead of restarting from beginning
    protected final boolean COUNT_EXTRA_DETECTIONS_NEGATIVELY = true;

    protected boolean WRITE_TO_FILE;
    protected boolean SHOW_LIVE_EVAL_DISPLAY;

    protected FFmpegFrameGrabber grabber;
    protected YOLOModelContainer yoloModelContainer;
    protected DetectionsParser detectionsParser;
    protected OpenCVFrameConverter frameConverter;


    protected void initializeGrabber(File videoFile) throws FrameGrabber.Exception {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET);           // Suppress verbose FFMPEG metadata output to console
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();        // open video file
    }

    protected void closeGrabber() {
        try {
            grabber.close();
        } catch (FrameGrabber.Exception ignored) {
        }
    }

    protected void initalizeModelContainer(File modelPath) throws IOException{
        yoloModelContainer = new YOLOModelContainer(modelPath);
    }


    protected List<Double[]> scalePoints(List<Double[]> points, int origWidth, int origHeight) {

        double widthMultiplier = (YOLOModelContainer.IMG_WIDTH / (double) origWidth);
        double heightMultiplier = (YOLOModelContainer.IMG_HEIGHT / (double) origHeight);

        List<Double[]> scaled = new ArrayList<>(points.size());

        // Double arrays are in the form of:
        for (int i=0; i<points.size(); i++) {
            Double[] pt = points.get(i);
            Double[] scaledPt = new Double[]{
                    pt[0] * widthMultiplier,
                    pt[1] * heightMultiplier,
                    pt[2], pt[3]
            };

            scaled.add(scaledPt);
        }
        return scaled;
    }


    protected List<Double> evaluateModelOnVideo(File videoFile, int numbAnimals, opencv_core.Rect cropRectangle) throws IOException {
        return null;
    }

    protected List<Double> evaluateModelOnVideoWithTruth(File videoFile, int numbAnimals,
                                                         opencv_core.Rect cropRectangle, File truthFile) throws IOException{
        return null;
    }

    protected List<Double> evaluateOnVideo(int numbAnimals, opencv_core.Rect cropRect) throws IOException {
        return null;
    }

    protected List<Double> evaluateOnVideo(int numbAnimals, opencv_core.Rect cropRect,
                                           List<List<Double[]>> truthPoints) throws IOException {
        return null;
    }


    /**
     * metaVideoList is a meta list of lists of videos. Each string is a path to a text file,
     * which contains line separated video descriptions, which are
     * comma separated as follows:  full/video/path,number of animals in video, crop dimensions,path/to/labeled/truth/file
     * eg:  /home/Videos/video1.mp4,5,230 10 720 720,/home/data/video1_pts.dat
     *
     * In addition, files should be organized such that each path in metaVideoList represents the complete set
     * of testing videos for a number group of animals.
     *
     * @param modelPath
     * @param metaVideoList
     * @param dataSaveName
     */
    protected void evaluateModel(File modelPath, HashMap<Integer, String> metaVideoList,
                               String dataSaveName) throws IOException {

        initalizeModelContainer(modelPath);

        List<List<Double>> anmlGroupAccuracies = new ArrayList<>(metaVideoList.size()); // each inner list contains points for all videos for animal number

        Set<Integer> anmlGroupNumbs = metaVideoList.keySet();

        for (Integer anmlNumb : anmlGroupNumbs) {

            if (CHECK_TO_RESUME_PROGRESS) {
                if (new File(dataSaveName.split("\\.")[0] + anmlNumb + ".eval").exists()) {
                    //System.out.println("\nModel already evaluated for current group");
                    continue;
                }
            }

            List<String> textLines = IOUtils.readInLargeFile(new File(metaVideoList.get(anmlNumb)));
            List<Double> videoEvals = new ArrayList<>();       // each individual point represents accuracy over an entire video

            System.out.println("\nGroup " + anmlNumb + " videos");

            for (String individualLine : textLines) {     // individual video file to evaluate on

                String[] split = individualLine.split(",");

                if (split.length < 2) {
                    System.out.println(String.format("Skipping incorrectly formatted line: '%s'", individualLine));
                    continue;
                }

                int numbAnimals;
                File videoFile, truthLabelsFile;
                opencv_core.Rect cropRect;
                List<Integer> cropDims = new ArrayList<>();

                // TODO: 11/20/18 add labeled truth files to meta video list file


                try {

                    videoFile = new File(split[0]);
                    assert videoFile.exists() && !videoFile.isDirectory();

                    numbAnimals = Integer.parseInt(split[1]);

                    // convert third string argument to 4 integers; the crop dimensions
                    Arrays.asList(split[2].split(" ")).forEach(s ->
                            cropDims.add(Integer.valueOf(s)));

                    assert cropDims.size() == 4;

                    cropRect = new opencv_core.Rect(
                            cropDims.get(0), cropDims.get(1), cropDims.get(2), cropDims.get(3)
                    );

                    truthLabelsFile = new File(split[3]);
                    assert truthLabelsFile.exists() && !truthLabelsFile.isDirectory();

                } catch (AssertionError | NumberFormatException ignored) {

                    System.out.println(String.format("Skipping invalid video path or incorrectly formatted line: '%s'", individualLine));
                    continue;
                }

                System.out.println(String.format("\nVideo %d of %d: %s",
                        textLines.indexOf(individualLine) + 1, textLines.size(), videoFile.toString()));

                List<Double> dataPoints = evaluateModelOnVideoWithTruth(videoFile, numbAnimals, cropRect, truthLabelsFile);

                // one point for each video
                videoEvals.add(
                        dataPoints.stream().reduce(0.0, Double::sum) / dataPoints.size() // average
                );


                System.out.println(String.format("\nDetection accuracy: %.4f", dataPoints.stream().reduce(0.0, Double::sum) / dataPoints.size()));

                /* todo : if save data for all individual videos
                saveName = String.format("%s_%d.dat", savePrefix, textLines.indexOf(individualLine) + 1);

                if (saveName == null) {
                    saveName = videoFile.toPath().getParent() + "/" + videoFile.getName().substring(0, videoFile.getName().length() - 4) + ".dat";
                }*/
            }
            anmlGroupAccuracies.add(videoEvals);

            if (WRITE_TO_FILE) {
//                for (List<Double> points : anmlGroupAccuracies) {
                IOUtils.writeDataToFile(videoEvals,
                        dataSaveName + "_" + anmlNumb.toString() + ".eval", "\n", true);
//                }
            }
        }

        /*if (WRITE_TO_FILE) {
            for (List<Double> points : anmlGroupAccuracies) {
                IOUtils.writeDataToFile(points, dataSaveName, "\n", true);
            }
        }*/

        System.out.println();
        anmlGroupAccuracies.forEach(lst ->
                System.out.println(String.format("Average accuracy on groups of %d: %.4f",
                        (Integer) anmlGroupNumbs.toArray()[anmlGroupAccuracies.indexOf(lst)],
                        lst.stream().reduce(0.0, Double::sum) / lst.size())
                )
        );
    }




    /**
     * All File objects in modelPaths array should be valid paths, as they are generated by
     * the java.io listFiles() function
     *
     * @param modelPaths
     * @param metaVideoList
     * @param evalsSaveDir
     * @throws IOException
     */
    public void evaluateMultipleModels(File[] modelPaths, HashMap<Integer,String> metaVideoList,
                                       String evalsSaveDir) throws IOException {

//        ModelAccuracyEvaluator evaluator = new ModelAccuracyEvaluator();

        int numbVideosPerGroup = 20;
        int videoLength = 30; // each video is 30 seconds long
        int fps = 30;
        double avgTimePerFrame; // in seconds

        if (SHOW_LIVE_EVAL_DISPLAY) {
            avgTimePerFrame = 0.0312;
        } else {
            avgTimePerFrame = 0.0257;
        }
        double estTime = modelPaths.length * metaVideoList.size()
                * numbVideosPerGroup * videoLength * fps * avgTimePerFrame;

        System.out.println(String.format(
                "Estimated time of evaluation: %dh %dm %ds",
                (int) Math.floor(estTime / 60 / 60), (int) Math.floor(estTime / 60 % 60),
                (int) Math.round(estTime % 60.0)
                )
        );

        for (File modelPath : modelPaths) {

            if (!(modelPath.exists() && modelPath.isFile())) {
                System.out.println("Invalid model path: " + modelPath.toString());
                continue;
            }
            String baseName = modelPath.getName().split("\\.")[0];

            long startTime = System.currentTimeMillis();

            System.out.println("Evaluating model: " + modelPath.toString());

            String baseSaveName = evalsSaveDir + baseName;

            if (CHECK_TO_RESUME_PROGRESS) {

                Integer[] keySet = new Integer[metaVideoList.keySet().size()];
                keySet = metaVideoList.keySet().toArray(keySet);

                boolean allExist = false;

                // Strangely, assert (new File(path).exists) does not raise an AssertionError for invalid paths
                try {
                    for (Integer i : keySet) {
                        if (! Files.exists(Paths.get(baseSaveName + "_" + i + ".eval"))) {
                            throw new AssertionError();
                        }
                    }
                    allExist = true;

                } catch (AssertionError ignored) {
                    allExist = false;
                }

                if (allExist) {
                    System.out.println("Model already evaluated");
                    continue;
                }
            }

            evaluateModel(modelPath, metaVideoList, baseSaveName);

            long endTime = System.currentTimeMillis() - startTime;
            System.out.println(String.format("Total evaluation time of model: %d (%dm %.3fs)",
                    endTime,
                    (int) Math.floor((int) (endTime / 1000) / 60d), endTime / 1000.0 % 60));
        }
    }
}
