package sproj.analysis;

import java.awt.event.KeyEvent;
import java.util.*;
import java.io.File;
import java.io.IOException;

import org.apache.commons.collections4.map.HashedMap;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.*;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import sproj.tracking.Animal;
import sproj.tracking.SinglePlateTracker;
import sproj.util.BoundingBox;
import sproj.util.DetectionsParser;
import sproj.util.IOUtils;
import sproj.util.MissingDataHandeler;
import sproj.yolo.YOLOModelContainer;

import static org.bytedeco.javacpp.opencv_imgproc.*;

public class TrackerAccuracyEvaluator extends ModelEvaluator {

    private final boolean DEBUG = true;

    public TrackerAccuracyEvaluator() {
        this(true, true);
    }

    public TrackerAccuracyEvaluator(boolean writeResultsToFile, boolean showVisualStream) {
        this.WRITE_TO_FILE = writeResultsToFile;
        this.SHOW_LIVE_EVAL_DISPLAY = showVisualStream;
        this.detectionsParser = new DetectionsParser();
        this.frameConverter = new OpenCVFrameConverter.ToMat();
    }

    @Override
    protected List<Double> evaluateModelOnVideoWithTruth(File videoFile, int numbAnimals, opencv_core.Rect cropRectangle,
                                                         File truthFile) throws IOException {
        //initializeGrabber(videoFile);
        int[] crop = new int[] {cropRectangle.x(), cropRectangle.y(), cropRectangle.width(), cropRectangle.height()};

        SinglePlateTracker tracker = new SinglePlateTracker(numbAnimals, true, crop,
                videoFile.toString(), null);

        MissingDataHandeler handeler = new MissingDataHandeler();

        List<List<Double[]>> fixed = handeler.fillInMissingData(truthFile, numbAnimals);
        List<List<Double[]>> rearranged = handeler.rearrangeData(fixed, numbAnimals);

        return evaluateOnVideo(tracker, cropRectangle, rearranged);
    }



    protected List<Double> evaluateOnVideo(SinglePlateTracker tracker, opencv_core.Rect cropRect,
                                            List<List<Double[]>> truthPoints) throws IOException {

        List<Double> accuracyPoints = new ArrayList<>();

        CanvasFrame canvas = new CanvasFrame("Tracker Evaluator");
        Frame frame;

        int truthIdx = 0;  // current index in truth points list
        int frameNo;
        int totalFrames = tracker.getTotalFrames();

        final List<Animal> animals = tracker.getAnimals();  // not to be modified in this function
        int numbAnimals = animals.size();

        // record of frames that each animal was accurately tracking
        HashedMap<Animal, List<Double>> accuracyRecords = new HashedMap<>();

        for (Animal a : animals) {
            accuracyRecords.put(a, new ArrayList<>());
        }

        while (true) {

            frame = tracker.timeStep();
            frameNo = tracker.getFrameNumb();

            if (frame == null) {    // end of video reached
                canvas.dispose();
                tracker.tearDown();
                break;
            }

            opencv_core.Mat mat = frameConverter.convertToMat(frame);

            if (truthIdx >= truthPoints.size()) {
                System.out.println("Reached end of truth data");
                break;      // todo does this break out of while loop
            }

            List<Double[]> groundTruth = truthPoints.get(truthIdx);

            int frameNumbStamp = (int) Math.round(groundTruth.get(0)[3]);

            // dont check against ground truth, but tracker has still updated with detections on current frame
            if (frameNo == frameNumbStamp) {

                List<Double[]> scaled = scalePoints(groundTruth, cropRect.width(), cropRect.height());

                if (SHOW_LIVE_EVAL_DISPLAY) {   // draw first before scaling to Yolo dimensions

                    for (Double[] pt : scaled) {
                        circle(mat, new opencv_core.Point(
                                        (int) Math.round(pt[0]), (int) Math.round(pt[1])
                                ),
                                3, opencv_core.Scalar.RED, -1, 8, 0);       // -1 is CV_FILLED, to fill the circle
                    }
                }

                double accuracy = 0.0;

                for (Animal anml : animals) {

                    boolean assigned = false;

                    for (Double[] pt : scaled) {    // x, y, isPredicted, timeStamp

                        if (pt == null) {
                            continue;
                        }

                        if (Math.pow(Math.pow(anml.x - pt[0], 2) +
                                Math.pow(anml.y - pt[1], 2), 0.5) <= 7) {

                            //System.out.println("Animal corresponds to point : " + Arrays.toString(pt));
                            assigned = true;
                            accuracy += 1.0 / numbAnimals;
                            break;
                        }
                    }

                    if (assigned) {
                        accuracyRecords.get(anml).add(1.0);
                    } else {
                        accuracyRecords.get(anml).add(0.0);
                    }
                }
                accuracyPoints.add(accuracy);
                truthIdx++;
            }

            if (SHOW_LIVE_EVAL_DISPLAY) {
                canvas.showImage(frameConverter.convert(mat));

            }

            System.out.print("\r" + (frameNo + 1) + " of " + totalFrames + " frames processed");

        }

        if (DEBUG) {
            System.out.println(String.format("\nTracking Accuracy: %.4f",
                    accuracyPoints.stream().reduce(0.0, Double::sum) / accuracyPoints.size())
            );
            for (Animal a : animals) {
                System.out.println(Arrays.toString(accuracyRecords.get(a).toArray()));
            }
        }

        /** TODO REMOVE *
        System.out.println("Total identity swaps: " + tracker.identitySwitches);
        * TODO REMOVE **/


        return accuracyPoints;
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
    //@Override
    protected List<Double> evaluateOnVideoOLD(int numbAnimals, opencv_core.Rect cropRect,
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

        int trthIndx = 0;   // current index in truth points list

        while ((frame = grabber.grabImage()) != null && !exitLoop) {

            frameNo = grabber.getFrameNumber();
            frameImg = new opencv_core.Mat(frameConverter.convertToMat(frame), cropRect);

            // frameImg is now of dimensions cropRect.width() x cropRect.height()

            detectedObjects = yoloModelContainer.runInference(frameImg);

            // yolo automatically resizes image, bounding boxes are relative to 416x416 dimensions

            List<BoundingBox> boundingBoxes = detectionsParser.parseDetections(detectedObjects);




            // TODO:   use SinglePlateTracker.timeStep()  here  and test animal locations




            double accuracy = 0.0;

            // TODO:  extrapolate data to cover all frames?
            if (trthIndx >= truthPoints.size()) {
                System.out.println("Reached end of truth data");
                break;
            }

            List<Double[]> truthCoordinates = truthPoints.get(trthIndx);

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

//                if (frameNo == frameNumbStamp) {
            if (frameNo != frameNumbStamp) {
                continue;
            }

            accuracy = 0.0;

            for (Double[] pt : truthCoordinates) {

                /*if (pt == null) {
                    continue;
                }
                circle(frameImg, new opencv_core.Point(
                                (int) Math.round(pt[0]), (int) Math.round(pt[1])
                        ),
                        3, opencv_core.Scalar.RED, -1, 8, 0);       // -1 is CV_FILLED, to fill the circle
                */


                // todo exclude already "assigned" boxes?
                for (BoundingBox box : boundingBoxes) {
                    if (box.contains(pt)) {
                        /*System.out.println(String.format(
                                "True: %s is within %s",
                                Arrays.toString(pt), box.toString())
                        );*/
                        accuracy += 1.0 / numbAnimals;   // e.g. if there are 4 animals, each correct one adds 0.25
                    }
                }
                accuracy = Math.min(1.0, accuracy);
            }

            System.out.println("Accuracy: " + accuracy);
            trthIndx++;


            /*double accuracy = detectedObjects.size() / (double) numbAnimals;

            accuracy = Math.min(1.0, accuracy);*/

            /*
            if (COUNT_EXTRA_DETECTIONS_NEGATIVELY) {
                accuracy = accuracy > 1.0 ? 1 - Math.abs(1 - accuracy) : accuracy;     // count each extra detection as one negative detection from the score
            }*/

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

                    switch (keyChar) {

                        case KeyEvent.VK_ESCAPE:
                            exitLoop = true;
                            break;      // hold escape key or 'q' to quit

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
