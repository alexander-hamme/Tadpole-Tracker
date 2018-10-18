package sproj.util;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.*;

import javax.imageio.ImageIO;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgproc.GaussianBlur;
import static org.bytedeco.javacpp.opencv_imgproc.createCLAHE;

public class TrainingImageGenerator {

    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private Java2DFrameConverter converterToImg = new Java2DFrameConverter();

    FFmpegFrameGrabber grabber;

    private final boolean RANDOM_CROP = false;

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

    private void saveFrame(Frame frame, String fileName) throws IOException {
        ImageIO.write(converterToImg.convert(frame), "jpg", new File(fileName));
        System.out.println(String.format("Saved frame to %s", fileName));
    }

    private void skipAhead(int N) throws FrameGrabber.Exception {
        while(grabber.grab() != null) {
            if (N-- <= 0) break;
        }
    }

    private void enhanceImageMethod1(Mat img) {

        GaussianBlur(img, img, new Size(3,3), 0.0);

        adaptiveThreshold(img, img, 220, ADAPTIVE_THRESH_MEAN_C,//ADAPTIVE_THRESH_MEAN_C,   //ADAPTIVE_THRESH_GAUSSIAN_C,//
                THRESH_BINARY, 5, 7);

//        GaussianBlur(img, img, new Size(3,3), 0.0);

        /*Mat element = getStructuringElement(MORPH_RECT,
                    new Size(2*1 + 1, 2*1+1 ),
                    new Point(1, 1) );
            dilate(toThreshold, toThreshold, element);*/
    }

    private void enhanceImageMethod2(Mat img) {
        equalizeHist(img, img);
    }

    private void enhanceImageMethod3(Mat img) {
        CLAHE clahe = createCLAHE(2.0, new Size(3,3));
        clahe.apply(img, img);
    }

    private void enhanceImageMethod4(Mat img) {
        GaussianBlur(img, img, new Size(3,3), 0.0);
        CLAHE clahe = createCLAHE(2.0, new Size(5,5));
        clahe.apply(img, img);
    }

    private void run(File videoFile, List<Integer> cropDims, String saveDir) throws IOException, InterruptedException {

        initializeFGrabber(videoFile);

        CanvasFrame canvasFrame = new CanvasFrame("'Shift+S' to save frame, 'Shift+K' to skip, 'Esc' to quit");
        KeyEvent keyEvent;
        Frame frame, newFrame;
        boolean exitLoop = false;

        int[] imageDimensions = new int[]{grabber.getImageWidth(), grabber.getImageHeight()};

        Rect cropRect = new Rect(
                cropDims.get(0), cropDims.get(1), cropDims.get(2), cropDims.get(3)
        );
        //new Rect(cropDims[0], cropDims[1], cropDims[2], cropDims[3]);  // use Range instead of Rect?

        int imgNo = 568;
        String saveName;

        int skipFrames = 60;

        int frameNo;

        int filterMethod = 0;
        int filterSwitchFrequency = 300;

        while ((frame = grabber.grabImage()) != null && !exitLoop) {

            frameNo = grabber.getFrameNumber();

            Mat frameImg;

            if (RANDOM_CROP) {
                // todo dont call this every frame or it'll bug out
                /*int x = cropDims[0] + random blah;
                int[] randCrop = new int[]{ cropDims[0] }
                cropRect = new Rect()*/
                frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);
            } else {
                frameImg = new Mat(frameConverter.convertToMat(frame), cropRect);
            }


            cvtColor(frameImg, frameImg, COLOR_RGB2GRAY);

            if (frameNo % filterSwitchFrequency == 0) {
                filterMethod = ((filterMethod + 1) % 5);
            }

            switch (filterMethod) {
                case 0: {
                    enhanceImageMethod1(frameImg);
                    break;
                }
                case 1: {
                    enhanceImageMethod2(frameImg);
                    break;
                }
                case 2: {
                    enhanceImageMethod3(frameImg);
                    break;
                }
                case 3: {
                    enhanceImageMethod4(frameImg);
                    break;
                }
                case 4: {
                    // no filter
                }
            }

            cvtColor(frameImg, frameImg, COLOR_GRAY2RGB);


            newFrame = frameConverter.convert(frameImg);

            boolean saveFrame = false;
            keyEvent = canvasFrame.waitKey(10);
            if (keyEvent != null) {

                char keyChar = keyEvent.getKeyChar();

                switch(keyChar) {
                    case KeyEvent.VK_ESCAPE: {exitLoop = true; break;}      // hold escape key or 'q' to quit
                    case KeyEvent.VK_S: {saveFrame = true; break;}
                    case KeyEvent.VK_K: {skipAhead(skipFrames);}
                    case KeyEvent.VK_Q: {canvasFrame.dispose(); System.exit(0);}
                }
            }
            if (saveFrame) {
                saveName = String.format("%s/tadpole%d.jpg", saveDir, imgNo++);
                saveFrame(newFrame, saveName);
                Thread.sleep(500); // to prevent latency in releasing the key from saving hundreds of images
            }

            canvasFrame.showImage(newFrame);
        }

        canvasFrame.dispose();
    }


    public static void main(String[] args) throws IOException, InterruptedException {

        TrainingImageGenerator imageGenerator = new TrainingImageGenerator();

        final String saveDir = "/home/ah2166/Documents/tadpole_dataset/NO_TAILS/new_images_10-18-18";

        int[] numberOfTadpoles = {1, 2, 4, 8};

//        args = new String[]{"/home/ah2166/Videos/tad_test_vids/trialVids/1_tadpole/filenames.txt"};
//        args = new String[]{"/home/ah2166/Videos/tad_test_vids/trialVids/2_tadpoles/eval_list_2t.txt"};
//        args = new String[]{"/home/ah2166/Videos/tad_test_vids/trialVids/4_tadpoles/eval_list_4t.txt"};

        String[] fileDescriptors = new String[]{

                String.format(
                        "/home/ah2166/Videos/tad_test_vids/trialVids/%d_tadpoles/eval_list_%dt.txt",
                        numberOfTadpoles[0], numberOfTadpoles[0]),
                String.format(
                        "/home/ah2166/Videos/tad_test_vids/trialVids/%d_tadpoles/eval_list_%dt.txt",
                        numberOfTadpoles[1], numberOfTadpoles[1]),
                String.format(
                        "/home/ah2166/Videos/tad_test_vids/trialVids/%d_tadpoles/eval_list_%dt.txt",
                        numberOfTadpoles[1], numberOfTadpoles[2]),
                String.format(
                        "/home/ah2166/Videos/tad_test_vids/trialVids/%d_tadpoles/eval_list_%dt.txt",
                        numberOfTadpoles[1], numberOfTadpoles[3]),
        };

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
                            cropDims.add(Integer.valueOf(s)));

                    assert cropDims.size() == 4;

                } catch (AssertionError | NumberFormatException ignored) {
                    System.out.println(String.format(
                            "Skipping invalid video path or incorrectly formatted line: '%s'", line
                    ));
                    continue;
                }

                imageGenerator.run(videoFile, cropDims, saveDir);
            }
        }
    }
}
