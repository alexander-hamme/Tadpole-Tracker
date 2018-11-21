package sproj.analysis;

import java.awt.event.KeyEvent;
import java.util.*;
import java.io.File;
import java.io.IOException;

import com.google.common.base.CharMatcher;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.util.IOUtils;
import sproj.util.MissingDataHandeler;
import sproj.yolo.YOLOModelContainer;

import static org.bytedeco.javacpp.opencv_imgproc.CV_AA;
import static org.bytedeco.javacpp.opencv_imgproc.rectangle;
import static org.bytedeco.javacpp.opencv_imgproc.resize;

public class TrackerAccuracyEvaluator {

    private final boolean CHECK_TO_RESUME_PROGRESS = true;      // for resuming evaluation instead of restarting from beginning

    private boolean WRITE_TO_FILE;
    private boolean SHOW_LIVE_EVAL_DISPLAY;

    private FFmpegFrameGrabber grabber;
    private YOLOModelContainer yoloModelContainer;
    private DetectionsParser detectionsParser = new DetectionsParser();
    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();


    private void initializeGrabber(File videoFile) throws FrameGrabber.Exception {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET);           // Suppress verbose FFMPEG metadata output to console
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();        // open video file
    }

    private void initalizeModelContainer(File modelPath) throws IOException{
        yoloModelContainer = new YOLOModelContainer(modelPath);
    }

    public TrackerAccuracyEvaluator() {
        this(true, true);
    }

    public TrackerAccuracyEvaluator(boolean writeResultsToFile, boolean showVisualStream) {
        this.WRITE_TO_FILE = writeResultsToFile;
        this.SHOW_LIVE_EVAL_DISPLAY = showVisualStream;
    }

    private List<Double> evaluateModelOnVideo(File videoFile, int numbAnimals, opencv_core.Rect cropRectangle,
                                              File truthFile) throws IOException {
        initializeGrabber(videoFile);
        MissingDataHandeler handeler = new MissingDataHandeler();
        List<List<Double[]>> fixed = handeler.fillInData(truthFile, numbAnimals);
        List<List<Double[]>> rearranged = handeler.rearrangeData(fixed, numbAnimals);


        return evaluateOnVideo(numbAnimals, cropRectangle, fixed);
    }


    /**
     * By the time this function is called, the framegrabber has already been initialized on the
     * new video.
     *
     * Returns a list of detection accuracy evaluations, one for each frame of the video.
     *
     * Current metric for accuracy is just the number of detections / ground truth number of animals
     *
     * @param numbAnimals int
     * @param cropRect
     * @return
     * @throws IOException
     */
    private List<Double> evaluateOnVideo(int numbAnimals, opencv_core.Rect cropRect,
                                         List<List<Double[]>> truthPoints) throws IOException {

        int frameNo = 0;
        int totalFrames = grabber.getLengthInVideoFrames();

        List<DetectedObject> detectedObjects;
        List<Double> detectionAccuracies = new ArrayList<>(totalFrames);   // store one detection accuracy record per frame

        CanvasFrame canvasFrame = null;

        if (SHOW_LIVE_EVAL_DISPLAY) {
            canvasFrame = new CanvasFrame("Evaluation on video");
        }

        boolean exitLoop = false;
        Frame frame;
        opencv_core.Mat frameImg;
        KeyEvent keyEvent;

        long startTime = System.currentTimeMillis();

        while ((frame = grabber.grabImage()) != null && !exitLoop) {

            frameImg = new opencv_core.Mat(frameConverter.convertToMat(frame), cropRect);

            //todo test effects on accuracy of different frame filter algorithms

            detectedObjects = yoloModelContainer.runInference(frameImg);
            double accuracy = detectedObjects.size() / (double) numbAnimals;

            accuracy = Math.min(1.0, accuracy);

            /*
            if (COUNT_EXTRA_DETECTIONS_NEGATIVELY) {
                accuracy = accuracy > 1.0 ? 1 - Math.abs(1 - accuracy) : accuracy;     // count each extra detection as one negative detection from the score
            }*/

            detectionAccuracies.add(accuracy);

            frameNo = grabber.getFrameNumber();
            System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");

            if (SHOW_LIVE_EVAL_DISPLAY && canvasFrame != null) {

                List<BoundingBox> boundingBoxes = detectionsParser.parseDetections(detectedObjects);

                resize(frameImg, frameImg, new opencv_core.Size(
                        YOLOModelContainer.IMG_WIDTH, YOLOModelContainer.IMG_HEIGHT)
                );

                for (BoundingBox box : boundingBoxes) {
                    rectangle(frameImg, new opencv_core.Point(box.topleftX, box.topleftY),
                            new opencv_core.Point(box.botRightX, box.botRightY),
                            opencv_core.Scalar.RED, 1, CV_AA, 0);

                }

                //resize(frameImg, frameImg, new opencv_core.Size(720, 720));

                canvasFrame.showImage(
                        frameConverter.convert(frameImg)
                );

                try {
                    keyEvent = canvasFrame.waitKey(10);
                } catch (InterruptedException ignored) {
                    continue;
                }
                if (keyEvent != null) {

                    char keyChar = keyEvent.getKeyChar();

                    switch(keyChar) {

                        case KeyEvent.VK_ESCAPE: exitLoop = true; break;      // hold escape key or 'q' to quit

                        case KeyEvent.VK_Q: {       // shift q to quit entirely
                            canvasFrame.dispose();
                            grabber.release();
                            System.exit(0);
                        }

                    }

                }
            }
        }


//        System.out.print("\r" + (frameNo + 1) + " of " + totalFrames +
//                " frames processed. Elapsed time: " + (System.currentTimeMillis()-startTime));
        long elapsedTime = System.currentTimeMillis()-startTime;

        System.out.println(String.format("\nElapsed time: %dm %.3fs",
                (int) Math.floor((int) (elapsedTime / 1000) / 60d),
                elapsedTime / 1000.0 % 60)
        );

        if (canvasFrame != null) {canvasFrame.dispose();}

        closeGrabber();

        return detectionAccuracies;
    }

    private void closeGrabber() {
        try {
            grabber.close();
        } catch (FrameGrabber.Exception ignored) {
        }
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
    private void evaluateModel(File modelPath, HashMap<Integer, String> metaVideoList,
                               String dataSaveName) throws IOException {

        initalizeModelContainer(modelPath);

        List<List<Double>> anmlGroupAccuracies = new ArrayList<>(metaVideoList.size()); // each inner list contains points for all videos for animal number

        Set<Integer> anmlGroupNumbs = metaVideoList.keySet();

        if (CHECK_TO_RESUME_PROGRESS) {
            if (new File(dataSaveName).exists() ||
                    new File(dataSaveName.split("\\.")[0] + anmlGroupNumbs.toArray()[0].toString() + ".eval").exists()) {
                System.out.println("Model already evaluated for current group");
                return;
            }
        }
//        for (String videosList : metaVideoList) {
        for (Integer anmlNumb : anmlGroupNumbs) {

            List<String> textLines = IOUtils.readLinesFromFile(new File(metaVideoList.get(anmlNumb)));
            List<Double> videoEvals = new ArrayList<>();       // each individual point represents accuracy over an entire video

            System.out.println("\nGroup " + anmlNumb + " videos");

            for (String individualVideo : textLines) {     // individual video file to evaluate on

                String[] split = individualVideo.split(",");

                if (split.length < 2) {
                    System.out.println(String.format("Skipping incorrectly formatted line: '%s'", individualVideo));
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

                    System.out.println(String.format("Skipping invalid video path or incorrectly formatted line: '%s'", individualVideo));
                    continue;
                }

                System.out.println(String.format("\nVideo %d of %d", textLines.indexOf(individualVideo) + 1, textLines.size()));

                List<Double> dataPoints = evaluateModelOnVideo(videoFile, numbAnimals, cropRect, truthLabelsFile);

                // one point for each video
                videoEvals.add(
                        dataPoints.stream().reduce(0.0, Double::sum) / dataPoints.size() // average
                );


                System.out.println(String.format("\nAverage tracking accuracy: %.5f", dataPoints.stream().reduce(0.0, Double::sum) / dataPoints.size()));

                /* todo : if save data for all individual videos
                saveName = String.format("%s_%d.dat", savePrefix, textLines.indexOf(individualVideo) + 1);

                if (saveName == null) {
                    saveName = videoFile.toPath().getParent() + "/" + videoFile.getName().substring(0, videoFile.getName().length() - 4) + ".dat";
                }*/
            }
            anmlGroupAccuracies.add(videoEvals);

            if (WRITE_TO_FILE) {
//                for (List<Double> points : anmlGroupAccuracies) {
                IOUtils.writeDataToFile(videoEvals,
                        dataSaveName.split("\\.")[0] + anmlNumb.toString() + ".eval", "\n", true);
//                }
            }
        }

        /*if (WRITE_TO_FILE) {
            for (List<Double> points : anmlGroupAccuracies) {
                IOUtils.writeDataToFile(points, dataSaveName, "\n", true);
            }
        }*/

        anmlGroupAccuracies.forEach(lst ->
                System.out.println(String.format("Average tracking accuracy on groups of %d: %.4f",
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
     * @param anmlGroupsMetaList
     * @param evalsSaveDir
     * @throws IOException
     */
    public void evaluateMultipleModels(File[] modelPaths, HashMap<Integer,String> anmlGroupsMetaList,
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
        double estTime = modelPaths.length * anmlGroupsMetaList.size()
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

            String dataSaveName = evalsSaveDir + baseName + ".dat";

            if (CHECK_TO_RESUME_PROGRESS) {
                if (new File(dataSaveName).exists()) {
                    System.out.println("Model already evaluated");
                    continue;
                }
            }

            evaluateModel(modelPath, anmlGroupsMetaList,
                    evalsSaveDir + baseName + ".eval");

            long endTime = System.currentTimeMillis() - startTime;
            System.out.println(String.format("Total evaluation time of model: %d (%dm %.3fs)",
                    endTime,
                    (int) Math.floor((int) (endTime / 1000) / 60d), endTime / 1000.0 % 60));
        }
    }


    public static void main(String[] args) throws IOException {

        TrackerAccuracyEvaluator evaluator = new TrackerAccuracyEvaluator();

//        evaluator.loadLabeledData(new File("/home/ah2166/Documents/sproj/java/" +
//                "Tadpole-Tracker/data/labeledVideoPoints/4tads/IMG_5193_pts.dat"), 4);

        // note that in IMG_5193_pts.dat the data had two fake starts from beginning, after the 40 and the 390 ms marks,
        // which have since been removed

        // todo add labeled file paths to eval_list files, parse them, then run tracker against the points

        int[] numberOfTadpoles = {4}; //{1, 2, 4, 6};

        String modelsDir = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker/src/main/resources/inference";
        File[] modelPaths = new File(modelsDir).listFiles(
                (directory, fileName) -> fileName.endsWith(".zip")
        );

        if (modelPaths == null) {
            System.out.println("No model files found in given directory");
            return;
        }

        Arrays.sort(modelPaths);

        HashMap<Integer, String> anmlGroupsMetaList = new HashMap<>();
        String evalsSaveDir = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker/data/trackerEvals/";


        for (int groupN : numberOfTadpoles) {
            anmlGroupsMetaList.putIfAbsent(groupN, String.format(
                    "/home/ah2166/Videos/tad_test_vids/trialVids/%dtads/eval_list_%dt.txt",
                    groupN, groupN));
        }


        evaluator.evaluateMultipleModels(modelPaths, anmlGroupsMetaList, evalsSaveDir);


    }
}
