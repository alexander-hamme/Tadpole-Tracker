package sproj.util;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgproc.COLOR_GRAY2RGB;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;

public class FrameLabeler {

    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();

    private FFmpegFrameGrabber grabber;


    private class LabeledPointsHolder {

        List<List<Point>> labeledPoints;
        private int currLstIndx;

        private LabeledPointsHolder(int estLength) {
            labeledPoints = new ArrayList<>(estLength);
            currLstIndx = 0;

        }
    }


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
        while(grabber.grab() != null) {
            if (N-- <= 0) break;
        }
    }

    private void addMouseListener(CanvasFrame frame) {
        frame.getCanvas().addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.println("Clicked: " + e.getPoint().toString());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("Pressed");
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                System.out.println("Released");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                System.out.println("Entered");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                System.out.println("Exited");
            }
        });
    }

    private void run(File videoFile, List<Integer> cropDims, String saveName) throws IOException, InterruptedException {

        initializeFGrabber(videoFile);

        CanvasFrame canvasFrame = new CanvasFrame("'Shift+S' to save progress, 'Shift+Z' to undo, 'Esc' to quit");
        KeyEvent keyEvent;
        Frame frame, newFrame;

        int[] imageDimensions = new int[]{grabber.getImageWidth(), grabber.getImageHeight()};

        opencv_core.Rect cropRect = new opencv_core.Rect(
                cropDims.get(0), cropDims.get(1), cropDims.get(2), cropDims.get(3)
        );
        //new Rect(cropDims[0], cropDims[1], cropDims[2], cropDims[3]);  // use Range instead of Rect?

        int skipFrames = 60;
        int frameNo;

        int numbFiltersToUse = 3;
        int filterMethod = numbFiltersToUse-1;      // this makes it start at 0, filter 1
        int filterSwitchFrequency = 3;

//        canvasFrame
//        addMouseListener(canvasFrame);

        final Stack<Integer[]> currPointsToDraw = new Stack<>();

        canvasFrame.getCanvas().addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.println("Clicked: " + e.getPoint().toString());
                currPointsToDraw.push(new Integer[]{e.getX(), e.getY()});
            }

            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("Pressed");
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                System.out.println("Released");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                System.out.println("Entered");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                System.out.println("Exited");
            }
        });

        List<Integer[][]> labeledPoints = new ArrayList<>(grabber.getLengthInVideoFrames());
        opencv_core.Mat frameImg;


        boolean savePoints;

        while ((frame = grabber.grabImage()) != null) {

            savePoints = false;

            frameNo = grabber.getFrameNumber();
            frameImg = new opencv_core.Mat(frameConverter.convertToMat(frame), cropRect);

            // cvtColor(frameImg, frameImg, COLOR_RGB2GRAY);
            //cvtColor(frameImg, frameImg, COLOR_GRAY2RGB);

            // paint canvas
            newFrame = frameConverter.convert(frameImg);
            canvasFrame.showImage(newFrame);

            boolean exitLoop = false;

            Integer[][] points = null;

            while (! exitLoop) {

                if (! currPointsToDraw.isEmpty()) {

                    opencv_core.Mat matCopy = frameImg.clone();
                    points = new Integer[currPointsToDraw.size()][];
                    currPointsToDraw.toArray(points);
                    for (Integer[] pt : points) {
                        circle(matCopy, new opencv_core.Point(pt[0], pt[1]),
                                3, opencv_core.Scalar.RED, CV_FILLED, 8, 0);
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
                            Thread.sleep(250); // to prevent latency in releasing the key from saving hundreds of images
                            exitLoop = true;
                            break;
                        }
                        case KeyEvent.VK_ESCAPE: {
                            Thread.sleep(500);
                            exitLoop = true;
                            break;
                        }
                        case KeyEvent.VK_S: {
                            Thread.sleep(150);
                            savePoints = true;
                            break;
                        }
                        case KeyEvent.VK_Q: {
                            Thread.sleep(250);

                            System.out.println("Labeled Points:\n");
                            for (Integer[][] pts : labeledPoints) {
                                for (Integer[] pt : pts) {
                                    System.out.println(Arrays.toString(pt));
                                }
                            }

                            canvasFrame.dispose();
                            System.exit(0);
                        }
                        case KeyEvent.VK_Z: { // undo most recent point
                            Thread.sleep(250);
                            if (!currPointsToDraw.isEmpty()) {
                                currPointsToDraw.pop();
                            }
                        }
                    }
                }
            }
            if (points != null) {//currPointsToDraw.isEmpty()) {
                /*points = new Point[currPointsToDraw.size()];
                for (int i=0; i<points.length; i++) {
                    points[i] = currPointsToDraw.pop();
                }*/
                labeledPoints.add(points);
                while(! currPointsToDraw.empty()) {currPointsToDraw.pop();}
            }


            if (! labeledPoints.isEmpty() && (savePoints || labeledPoints.size() > 15)) {
                IOUtils.writeNestedObjArraysToFile(labeledPoints, saveName, ",", true);
                System.out.println(String.format("Saved %d points to file", labeledPoints.size()));
                labeledPoints.clear();
            }

            System.out.println("Labeled Points size: " + labeledPoints.size());
        }

        System.out.println("Labeled Points:\n");
        for (Integer[][] pts : labeledPoints) {
            System.out.println(Arrays.toString(pts));
        }
        canvasFrame.dispose();
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        ///*
        final String saveDir = "/home/ah2166/Documents/sproj/java/Tadpole-Tracker/data/labeledVideoPoints/";
        int[] numbersOfTadpoles = {1, 2, 4, 6};
        int imageNumber = 764;

//        if (args.length < 1) {
        /*String[] fileDescriptors = new String[numbersOfTadpoles.length];

        for (int i=0;i < numbersOfTadpoles.length; i++) {

            fileDescriptors[i] = String.format(
                    "/home/ah2166/Videos/tad_test_vids/trialVids/%d_tadpoles/eval_list_%dt.txt",
                    numbersOfTadpoles[i], numbersOfTadpoles[i]);
        }
        */
        String[] fileDescriptors = new String[]{
                String.format(
                        "/home/ah2166/Videos/tad_test_vids/trialVids/%dtads/eval_list_%dt.txt",
                        numbersOfTadpoles[2], numbersOfTadpoles[2]),
        };
        /**/
        //generateImages(saveDir, fileDescriptors, imageNumber);

        imageNumber = 734;

        runLabeler(saveDir, fileDescriptors, imageNumber);
    }

    public static void runLabeler(String saveDir, String[] fileDescriptors, int imageNo)
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

                try {

                    videoFile = new File(split[0]);
                    assert videoFile.exists() && !videoFile.isDirectory();

                    // numbAnimals = Integer.parseInt(split[1]);

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
                System.out.println("Running " + videoFile.toString());

                labeler.run(videoFile, cropDims, saveDir + videoFile.getName().split("\\.")[0] + "_pts.dat");

            }
        }
    }
}