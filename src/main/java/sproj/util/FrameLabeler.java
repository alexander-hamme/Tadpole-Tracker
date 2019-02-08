package sproj.util;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.Point;
import org.bytedeco.javacpp.opencv_core.Scalar;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import javax.swing.WindowConstants;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.stream.Stream;

import static org.bytedeco.javacpp.opencv_imgproc.circle;

/**
 * This class is designed for manually labeling objects in the frames of a video.
 *
 * Basic keyboard input is allowed as follows:
 * 'Enter' go to next frame, 'Shift+Z' undo labeling, 'Shift+K' skip current video, 'Esc' quit
 *
 * Currently, every 10th frame is displayed for labeling to speed up the process
 * (at 30 fps, and for tracking tadpoles specifically, nothing significantly changes in 0.3 seconds)
 *
 * This program also allows loading many videos at once, to be displayed back to back.
 *
 * The labeled coordinates are intermittently saved to file, and if the FrameLabeler program
 * is exited between videos, progress can be resumed at the next run. However, progress currently
 * cannot be resumed mid-video.
 *
 */
public class FrameLabeler {

    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    private FFmpegFrameGrabber grabber;

    private void initializeFGrabber(File videoFile) throws IOException {
        avutil.av_log_set_level(avutil.AV_LOG_QUIET);           // Suppress verbose FFMPEG metadata output to console
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();        // open video file
    }

    private static List<String> readTextFiles(String textFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(textFile))) {
            stream.forEach(lines::add);
        }
        return lines;
    }

    private void skipAhead(int N) throws FrameGrabber.Exception {

        int length = grabber.getLengthInVideoFrames();

        while (--N > 0) {

            if (grabber.getFrameNumber() == length - 2) {   // dont skip the last frame
                break;
            }

            if (grabber.grabImage() == null) {
                break;
            }
        }
    }

    private void run(File videoFile, List<Integer> cropDims, String saveName) throws IOException, InterruptedException {

        if (new File(saveName).exists()) {
            System.out.println("Video already has labeled points file: " + saveName);
            return;
        }

        initializeFGrabber(videoFile);

        CanvasFrame canvasFrame = new CanvasFrame("'Enter' continue, 'Shift+Z' undo, 'Shift+K' skip, 'Esc' quit");
        canvasFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        KeyEvent keyEvent;
        Frame frame, newFrame;

        int[] imageDimensions = new int[]{grabber.getImageWidth(), grabber.getImageHeight()};

        Rect cropRect = new Rect(
                cropDims.get(0), cropDims.get(1), cropDims.get(2), cropDims.get(3)
        );

        int framesToSkip = 10;
        int frameNo;

        final Stack<Integer[]> currPointsToDraw = new Stack<>();

        canvasFrame.getCanvas().addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent e) {
                currPointsToDraw.push(new Integer[]{e.getX(), e.getY()});
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });

        List<Integer[][]> labeledPoints = new ArrayList<>(grabber.getLengthInVideoFrames());
        Mat frameImg;

        int length = grabber.getLengthInVideoFrames();

        while ((frame = grabber.grabImage()) != null) {

            frameNo = grabber.getFrameNumber();
            frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);

            System.out.print("\rFrame " + frameNo + " of " + length +
                            ", " + (((length - frameNo) / framesToSkip) + 1) + " frames left to label");
            // cvtColor(frameImg, frameImg, COLOR_RGB2GRAY);
            //cvtColor(frameImg, frameImg, COLOR_GRAY2RGB);

            // paint canvas
            newFrame = frameConverter.convert(frameImg);
            canvasFrame.showImage(newFrame);

            boolean exitLoop = false;

            Integer[][] points = null;

            while (! exitLoop) {

                if (! currPointsToDraw.isEmpty()) {

                    Mat matCopy = frameImg.clone();
                    points = new Integer[currPointsToDraw.size()][];
                    currPointsToDraw.toArray(points);

                    for (Integer[] pt : points) {
                        if (pt==null) {continue;}
                        circle(matCopy, new Point(pt[0], pt[1]),
                                3, Scalar.RED, -1, 8, 0);       // -1 is CV_FILLED, to fill the circle
                    }
                    newFrame = frameConverter.convert(matCopy);
                } else {
                    // repaint
                    newFrame = frameConverter.convert(frameImg);
                }

                canvasFrame.showImage(newFrame);

                keyEvent = canvasFrame.waitKey(5);

                if (keyEvent != null) {

                    char keyChar = keyEvent.getKeyChar();

                    switch (keyChar) {

                        case KeyEvent.VK_ENTER: {

                            if (currPointsToDraw.empty()) {break;}   // don't continue if no labels on current frame

                            exitLoop = true;
                            break;
                        }
                        case KeyEvent.VK_ESCAPE: {
                            canvasFrame.dispose();
                            Thread.sleep(500);
                            System.exit(0);
                            break;
                        }
                        case KeyEvent.VK_K: {                           // skip current video
                            Thread.sleep(150);
                            canvasFrame.dispose();
                            return;
                        }
                        /*case KeyEvent.VK_Q: {
                            canvasFrame.dispose();
                            Thread.sleep(500);
                            System.exit(0);
                            break;
                        }*/
                        case KeyEvent.VK_Z: { // undo most recent point
                            Thread.sleep(300);  // create small amount of latency to allow releasing the key
                            if (!currPointsToDraw.isEmpty()) {
                                currPointsToDraw.pop();
                            }
                            break;
                        }
                    }
                }
            }

            skipAhead(framesToSkip);

            if (points != null) {//currPointsToDraw.isEmpty()) {
                points = new Integer[currPointsToDraw.size()+1][];
                for (int i=0; i<points.length-1; i++) {
                    points[i] = currPointsToDraw.pop();
                }
                points[points.length-1] = new Integer[]{frameNo};
                labeledPoints.add(points);
                //while(! currPointsToDraw.empty()) {currPointsToDraw.pop();}
            }


            if (! labeledPoints.isEmpty() && labeledPoints.size() >= 5) {
                IOUtils.writeNestedObjArraysToFile(labeledPoints, saveName, ",", true);
                // System.out.println(String.format("Saved %d points to file", labeledPoints.size()));
                labeledPoints.clear();
            }
        }

        /*System.out.println("Labeled Points:\n");
        for (Integer[][] pts : labeledPoints) {
            for (Integer[] pt : pts) {
                System.out.println(Arrays.toString(pt));
            }
        }*/

        // TODO fix this so it doesn't put commas between the x & y coordinates of each point ->
        // todo     makes it hard to read and parse data from file automatically
        if (! labeledPoints.isEmpty()) {
            IOUtils.writeNestedObjArraysToFile(labeledPoints, saveName, ",", true);
        }
        canvasFrame.dispose();
    }


    public static void main(String[] args) throws IOException, InterruptedException {

        final String saveDir = "output/";
        int[] numbersOfTadpoles = {1, 2, 4, 6};

        String [] fileDescriptors = new String[numbersOfTadpoles.length];

        for (int i=0;i < numbersOfTadpoles.length; i++) {
            fileDescriptors[i] = String.format("videos/eval_list_%dt.txt", numbersOfTadpoles[i]);
        }


        /*
        String[] fileDescriptors = new String[]{
                String.format(
                        "videos/%dtads/eval_list_%dt.txt",
                        numbersOfTadpoles[2], numbersOfTadpoles[2]),
        };
        /**/
        //generateImages(saveDir, fileDescriptors, imageNumber);

        runLabeler(saveDir, fileDescriptors);
    }

    public static void runLabeler(String saveDir, String[] fileDescriptors)
            throws IOException, InterruptedException {

        FrameLabeler labeler = new FrameLabeler();

        for (String fileDesc : fileDescriptors) {

            List<String> textLines = readTextFiles(fileDesc);        // video files to evaluate on

            for (String line : textLines) {

                String[] split = line.split(",");

                if (split.length < 2) {
                    continue;
                }

                File videoFile;
                List<Integer> cropDims = new ArrayList<>();

                int numbAnimals;

                try {

                    videoFile = new File(split[0]);
                    assert videoFile.exists() && !videoFile.isDirectory();

                    numbAnimals = Integer.parseInt(split[1]);

                    // convert third string argument to 4 integers; the crop dimensions
                    Arrays.asList(split[2].split(" ")).forEach(s ->
                            cropDims.add(Integer.valueOf(s))
                    );

                    assert cropDims.size() == 4;

                } catch (AssertionError | NumberFormatException ignored) {
                    System.out.println(String.format(
                            "Skipping invalid video path or incorrectly formatted line: '%s'", line
                    ));
                    continue;
                }
                System.out.println("\nRunning " + videoFile.toString());

                labeler.run(videoFile, cropDims,
                        saveDir + numbAnimals + "tads/" +
                                videoFile.getName().split("\\.")[0] + "_pts.dat");

            }
        }
    }
}
