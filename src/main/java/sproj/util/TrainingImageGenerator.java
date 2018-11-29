package sproj.util;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;

import javax.imageio.ImageIO;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgproc.GaussianBlur;
import static org.bytedeco.javacpp.opencv_imgproc.createCLAHE;

public class TrainingImageGenerator {

    private OpenCVFrameConverter frameConverter = new OpenCVFrameConverter.ToMat();
    private Java2DFrameConverter converterToImg = new Java2DFrameConverter();

    private FFmpegFrameGrabber grabber;
    private Random randGen = new Random();

    private final boolean RANDOM_CROP = true;
    private final boolean RANDOM_ROTATE = true;
    private final boolean SWAP_FILTERS = false;

    private int imageNumber;

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
        BufferedImage img = converterToImg.convert(frame);
        if (RANDOM_CROP) {
            img = randomCrop(img);
        }
        ImageIO.write(img, "jpg", new File(fileName));
        System.out.println(String.format("Saved frame to %s", fileName));
    }

    private void skipAhead(int N) throws FrameGrabber.Exception {
        while(grabber.grab() != null) {
            if (N-- <= 0) break;
        }
    }

    private void saveImage(BufferedImage img, String fileName) throws IOException {
        ImageIO.write(img, "jpg", new File(fileName));
        System.out.println(String.format("Saved image to %s", fileName));
    }


    public static void main2(String[] args) throws IOException{
        TrainingImageGenerator generator = new TrainingImageGenerator();

        BufferedImage img = ImageIO.read(new File("/home/ah2166/Pictures/exampleTrainingImage1.png"));

        Mat blurred = generator.frameConverter.convertToMat(generator.converterToImg.convert(deepCopy(img)));
        generator.gaussianBlur(blurred);
        generator.saveImage(generator.converterToImg.convert(generator.frameConverter.convert(blurred)), "/home/ah2166/Pictures/image2.jpg");

        opencv_core.Mat eh = generator.frameConverter.convertToMat(generator.converterToImg.convert(deepCopy(img)));
        cvtColor(eh, eh, COLOR_RGB2GRAY);
        generator.eqHist(eh);
        generator.saveImage(generator.converterToImg.convert(generator.frameConverter.convert(eh)), "/home/ah2166/Pictures/image3.jpg");

        Mat cl = generator.frameConverter.convertToMat(generator.converterToImg.convert(deepCopy(img)));
        cvtColor(cl, cl, COLOR_RGB2GRAY);
        generator.clahe(cl);
        generator.saveImage(generator.converterToImg.convert(generator.frameConverter.convert(cl)), "/home/ah2166/Pictures/image4.jpg");

        Mat adaptThresh = generator.frameConverter.convertToMat(generator.converterToImg.convert(deepCopy(img)));
        cvtColor(adaptThresh, adaptThresh, COLOR_RGB2GRAY);
        generator.adaptiveThresh(adaptThresh);
        generator.saveImage(generator.converterToImg.convert(generator.frameConverter.convert(adaptThresh)), "/home/ah2166/Pictures/image5.jpg");


    }

    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    private void adaptiveThresh(Mat img) {

        GaussianBlur(img, img, new Size(3,3), 0.0);

        adaptiveThreshold(img, img, 220, ADAPTIVE_THRESH_GAUSSIAN_C,//ADAPTIVE_THRESH_MEAN_C,   //ADAPTIVE_THRESH_GAUSSIAN_C,//
                THRESH_BINARY, 9, 7);

//        GaussianBlur(img, img, new Size(3,3), 0.0);

        /*Mat element = getStructuringElement(MORPH_RECT,
                    new Size(2*1 + 1, 2*1+1 ),
                    new Point(1, 1) );
            dilate(toThreshold, toThreshold, element);*/
    }

    private void eqHist(Mat img) {
        equalizeHist(img, img);
    }

    private void clahe(Mat img) {
        CLAHE cl = createCLAHE(2.0, new Size(5,5));
        cl.apply(img, img);
    }

    private void gaussianBlur(Mat img) {
        GaussianBlur(img, img, new Size(3,3), 0.0);
    }

    private BufferedImage randomRotate(BufferedImage img) {

        double angle = randGen.nextInt(4) * 90.0;//randGen.nextInt(271);
        //angle = Math.round(angle / 90) * 90.0;

        AffineTransform affineTransform = new AffineTransform();

        if (angle == 90.0 || angle == 270.0) {
            affineTransform.translate(img.getHeight() / 2.0, img.getWidth() / 2.0);
            affineTransform.rotate(angle * Math.PI / 180.0);
            affineTransform.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);

        } else if (angle == 180) {
            affineTransform.translate(img.getWidth() / 2.0, img.getHeight() / 2.0);
            affineTransform.rotate(angle * Math.PI / 180.0);
            affineTransform.translate(-img.getWidth() / 2.0, -img.getHeight() / 2.0);

        } else {
            affineTransform.rotate(angle * Math.PI / 180.0);
        }

        AffineTransformOp affineTransformOp = new AffineTransformOp(
                affineTransform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

        BufferedImage result;

        if (angle == 90.0 || angle == 270.0) {
            result = new BufferedImage(img.getHeight(), img.getWidth(), img.getType());

        } else {
            result = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        }

        affineTransformOp.filter(img, result);

        return result;
    }

    private BufferedImage randomCrop(BufferedImage img) {

        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();

        int x = randGen.nextInt(2 * imgWidth / 3);
        int y = randGen.nextInt(2 * imgHeight / 3);

        int width = imgWidth - randGen.nextInt(imgWidth / 3);
        int height = imgHeight - randGen.nextInt(imgHeight / 3);

        while (x + width > imgWidth){x--;}
        while (y + height > imgHeight){y--;}

        return img.getSubimage(x, y, width, height);

    }

    private List<BufferedImage> warpImages(File[] imagePaths) {

        List<BufferedImage> randWarped = new ArrayList<>(imagePaths.length);

        for (File imgPath : imagePaths) {

            try {
                BufferedImage img = ImageIO.read(imgPath);

                if (img != null) {

                    if (RANDOM_ROTATE) {
                        img = randomRotate(img);
                    }
                    if (RANDOM_CROP) {
                        img = randomCrop(img);
                    }

                    randWarped.add(img);
                }
            } catch (IOException e) {
                System.err.println("Could not read image: " + imgPath);
            }
        }
        return randWarped;
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

        String saveName;

        int skipFrames = 60;
        int frameNo;

        int numbFiltersToUse = 3;
        int filterMethod = numbFiltersToUse-1;      // this makes it start at 0, filter 1
        int filterSwitchFrequency = 3;

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

            if (SWAP_FILTERS) {
                if (frameNo % filterSwitchFrequency == 0) {
                    filterMethod = ((filterMethod + 1) % numbFiltersToUse);
                }
            }

            switch (filterMethod) {
                case 0: {
                    adaptiveThresh(frameImg);
                    break;
                }
                case 1: {
                    eqHist(frameImg);
                    break;
                }
                case 2: {
                    clahe(frameImg);
                    break;
                }
                case 3: {
                    // no filter
                    break;
                }
                case 4: {
                    break;
                }
            }

            cvtColor(frameImg, frameImg, COLOR_GRAY2RGB);


            newFrame = frameConverter.convert(frameImg);

            boolean saveFrame = false;
            keyEvent = canvasFrame.waitKey(30);
            if (keyEvent != null) {

                char keyChar = keyEvent.getKeyChar();

                switch(keyChar) {

                    case KeyEvent.VK_S: {
                        Thread.sleep(500); // to prevent latency in releasing the key from saving hundreds of images
                        saveFrame = true;
                        break;
                    }
                    case KeyEvent.VK_K: {
                        skipAhead(skipFrames);
                        break;
                    }
                    case KeyEvent.VK_F: {   // switch filter method
                        Thread.sleep(250);
                        filterMethod = ((filterMethod + 1) % numbFiltersToUse);
                        break;
                    }
                    case KeyEvent.VK_ESCAPE: {
                        Thread.sleep(500);
                        exitLoop = true; break;
                    }
                    case KeyEvent.VK_Q: {
                        Thread.sleep(250);
                        canvasFrame.dispose();
                        System.exit(0);
                    }
                }
            }
            if (saveFrame) {
                saveName = String.format("%s/tadpole%d.jpg", saveDir, imageNumber++);
                saveFrame(newFrame, saveName);
            }

            canvasFrame.showImage(newFrame);
        }

        canvasFrame.dispose();
    }


    public static void generateWarpedImages(String imgDir, String saveDir, int imgNo) throws IOException {

        File[] files = new File(imgDir).listFiles();

        String saveName;
        int saveIndx = imgNo;

        if (files != null && files.length > 0) {

            Arrays.sort(files);

            List<BufferedImage> randCropped = new TrainingImageGenerator().warpImages(files);

            for (BufferedImage img : randCropped) {

                System.out.print("\r" + (randCropped.indexOf(img) + 1) + " of " + randCropped.size() + " images warped");

                saveName = String.format("%stadpole%d.jpg", saveDir, saveIndx++);
                ImageIO.write(img, "jpg", new File(saveName));
            }

        }
    }

    private void setImgNo(int n) {
        this.imageNumber = n;
    }

    public static void main1(String[] args) throws IOException, InterruptedException {
        ///*
        final String saveDir = "/home/ah2166/Documents/tadpole_dataset/NO_TAILS/new_images_10-30-18";
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

        String imgDir = "/home/ah2166/Documents/tadpole_dataset/NO_TAILS/new_images_10-30-18/";
        String saveImgDir = "/home/ah2166/Documents/tadpole_dataset/NO_TAILS/new_images_10-30-18/warped/";
        generateWarpedImages(imgDir, saveImgDir, imageNumber);
    }

    public static void generateImages(String saveDir, String[] fileDescriptors, int imageNo)
            throws IOException, InterruptedException {

        TrainingImageGenerator imageGenerator = new TrainingImageGenerator();
        imageGenerator.setImgNo(imageNo);

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
                System.out.println("Running " + videoFile.toString());
                imageGenerator.run(videoFile, cropDims, saveDir);
            }
        }
    }
}