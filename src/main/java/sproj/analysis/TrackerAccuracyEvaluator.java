package sproj.analysis;

import org.apache.commons.collections4.map.HashedMap;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import sproj.tracking.Animal;
import sproj.tracking.SinglePlateTracker;
import sproj.util.DetectionsParser;
import sproj.util.MissingDataHandeler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.bytedeco.javacpp.opencv_imgproc.circle;


/**
 * Class for evaluating SinglePlateTracker tracking accuracy,
 * using ground-truth coordinate-labeled videos
 */
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


    /**
     * todo add explanation
     * @param tracker
     * @param cropRect
     * @param truthPoints
     * @return
     * @throws IOException
     */
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
                break;
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

        return accuracyPoints;
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
