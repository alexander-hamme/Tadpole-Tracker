package sproj.analysis;


import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.util.MissingDataHandeler;
import sproj.yolo.YOLOModelContainer;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.bytedeco.javacpp.opencv_imgproc.*;

/**
 * Class to test the accuracy of the underlying Yolo model
 *
 * Calculate metrics of how frequently  the model correctly
 * find all subjects of interest in each frame of a video feed
 */
public class ModelAccuracyEvaluator extends ModelEvaluator {

    public ModelAccuracyEvaluator() {
        this(true, true);
    }

    public ModelAccuracyEvaluator(boolean writeResultsToFile, boolean showVisualStream) {
        this.WRITE_TO_FILE = writeResultsToFile;
        this.SHOW_LIVE_EVAL_DISPLAY = showVisualStream;
        this.detectionsParser = new DetectionsParser();
        this.frameConverter = new OpenCVFrameConverter.ToMat();
    }

    @Override
    protected List<Double> evaluateModelOnVideoWithTruth(File videoFile, int numbAnimals, opencv_core.Rect cropRectangle,
                                                         File truthFile) throws IOException {
        initializeGrabber(videoFile);
        MissingDataHandeler handeler = new MissingDataHandeler();
        List<List<Double[]>> fixed = handeler.fillInMissingData(truthFile, numbAnimals);
        List<List<Double[]>> rearranged = handeler.rearrangeData(fixed, numbAnimals);

        return evaluateOnVideo(numbAnimals, cropRectangle, rearranged);
    }

    @Override
    protected List<Double> evaluateModelOnVideo(File videoFile, int numbAnimals,
                                                Rect cropRectangle) throws IOException {
        initializeGrabber(videoFile);
        return evaluateOnVideo(numbAnimals, cropRectangle);
    }


    private double calculateAccuracy(List<DetectedObject> detections, int numbAnimals) {
        double accuracy = detections.size() / (double) numbAnimals;

        accuracy = Math.min(1.0, accuracy);

        if (COUNT_EXTRA_DETECTIONS_NEGATIVELY) {
            accuracy = accuracy > 1.0 ? 1 - Math.abs(1 - accuracy) : accuracy;     // count each extra detection as one negative detection from the score
        }
        return accuracy;
    }

    private double calculateAccuracy(List<BoundingBox> boundingBoxes, int numbAnimals,
                                     List<Double[]> truthPoints) {

        double accuracy = 0.0;

        for (BoundingBox box : boundingBoxes) {

            for (Double[] pt : truthPoints) {

                if (box.contains(pt)) {
                    accuracy += 1.0 / numbAnimals;   // e.g. if there are 4 animals, each correct one adds 0.25
                    break;  // count only one point per box
                }
            }
        }

        return Math.min(1.0, accuracy);
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
    @Override
    protected List<Double> evaluateOnVideo(int numbAnimals, Rect cropRect,
                                           List<List<Double[]>> truthPoints) throws IOException {


        boolean HAVE_GROUND_TRUTH = (truthPoints != null);

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
        Mat frameImg;
        KeyEvent keyEvent;

        int truthIdx = 0;   // current index in truth points list

        long startTime = System.currentTimeMillis();

        while ((frame = grabber.grabImage()) != null && !exitLoop) {

            frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);
            frameNo = grabber.getFrameNumber();

            //todo test effects on accuracy of different frame filter algorithms

            detectedObjects = yoloModelContainer.runInference(frameImg);
            List<BoundingBox> boundingBoxes = detectionsParser.parseDetections(detectedObjects);

            double accuracy;

            if (! HAVE_GROUND_TRUTH) {

                accuracy = calculateAccuracy(detectedObjects, numbAnimals);

            } else {

                if (truthIdx >= truthPoints.size()) {
                    System.out.println("Reached end of truth data");
                    break;      // todo does this break out of while loop
                }

                List<Double[]> truthCoordinates = truthPoints.get(truthIdx);

                if (SHOW_LIVE_EVAL_DISPLAY) {
                    for (Double[] pt : truthCoordinates) {

                        if (pt == null) {
                            continue;
                        }
                        circle(frameImg, new opencv_core.Point(
                                        (int) Math.round(pt[0]), (int) Math.round(pt[1])
                                ),
                                3, opencv_core.Scalar.RED, -1, 8, 0);       // -1 is CV_FILLED, to fill the circle
                    }
                }

                truthCoordinates = scalePoints(truthCoordinates, cropRect.width(), cropRect.height());

                int frameNumbStamp = (int) Math.round(truthCoordinates.get(0)[3]);

                // todo don't skip all unlabeled frames here
                if (frameNo != frameNumbStamp) {
                    continue;
                }

                accuracy = calculateAccuracy(boundingBoxes, numbAnimals, truthCoordinates);

                truthIdx++;
            }

            detectionAccuracies.add(accuracy);

            System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");

            if (SHOW_LIVE_EVAL_DISPLAY && canvasFrame != null) {

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


    /**
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException, NumberFormatException {

        // args: modelsDir, int[] anmlGroupNumbers, and/or testVideosDir
        /*if (args.length < 1) {
            System.out.println(
                    "Required: text file with list of video files to evaluate on.\n" +
                            "Syntax for this file is comma separated values, in this format:\n" +
                            "<full/path/to/video>,<number of animals in video>,<crop dimensions\n" +
                            "crop dimensions should be space separated integers, of the form: center_x center_y width height"
            );
            return;
        }*/

        ModelAccuracyEvaluator evaluator = new ModelAccuracyEvaluator(
                true, false
        );

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
        String evalsSaveDir = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker" +
                "/data/modelEvals/againstGroundTruth/";


        for (int groupN : numberOfTadpoles) {
            anmlGroupsMetaList.putIfAbsent(groupN, String.format(
                    "/home/ah2166/Videos/tad_test_vids/trialVids/%dtads/eval_list_%dt.txt",
                    groupN, groupN));
        }


        evaluator.evaluateMultipleModels(modelPaths, anmlGroupsMetaList, evalsSaveDir);
    }
}