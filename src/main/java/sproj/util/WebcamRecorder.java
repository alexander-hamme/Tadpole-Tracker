package sproj.util;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.*;

import java.awt.event.KeyEvent;

public class WebcamRecorder {

    private static final int CAMERA_INDEX = 1;

    private static final int CAPTURE_WIDTH = 1280;
    private static final int CAPTURE_HEIGHT = 720;


    public WebcamRecorder(String cameraPath) {

    }

    public static void main(String[] args) throws Exception {

        int currentNumberGroup = 1;     // group of 1 tadpole
        int numberOfTrialsToRecord = 20;
        long lengthOfEachVideo = 30 * 1000;   // 30 seconds, while loop will stop before 31
        long timeBetweenVideos = 30 * 1000;

        String savePrefix = String.format("/home/alex/Videos/tadpole_test_videos/videoTrials/1tadpoles/tadpoles%d", currentNumberGroup);

        String videoPath = String.format("/dev/video%d", CAMERA_INDEX);

        System.out.println(String.format("Starting recordings. \n%d trials to record, with video lengths of %ds and pause intervals of %ds between videos",
                numberOfTrialsToRecord, lengthOfEachVideo/1000, timeBetweenVideos/1000));

        for (int i = 0; i < numberOfTrialsToRecord; i++) {

            String savePath = String.format("%s_%d", savePrefix, i + 1);

            recordVideo(videoPath, savePath, lengthOfEachVideo * 1000);


            long remainingTime = (numberOfTrialsToRecord - (i + 1)) * lengthOfEachVideo + (numberOfTrialsToRecord - (i + 1) - 1) * timeBetweenVideos;

            if (i + 1 == numberOfTrialsToRecord) {
                remainingTime = 0L;
            }

            System.out.print("\r" + (i + 1) + " of " + numberOfTrialsToRecord + " videos recorded. ETA until completion: " +
                    (int) ((remainingTime / 1000.0) / 60) + "m " + (remainingTime / 1000.0) % 60.0 + "s");

            Thread.sleep(timeBetweenVideos);
        }
    }


    public static void recordVideo(String videoPath, String savePath, long lengthOfEachVideo) throws FrameGrabber.Exception, FrameRecorder.Exception, InterruptedException {

        avutil.av_log_set_level(avutil.AV_LOG_QUIET);       // suppress verbose qqqqqqqffmpeg console output

//            OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(2);
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath);
        grabber.start();

        Frame frame = grabber.grab();
        CanvasFrame canvasFrame = new CanvasFrame("Video Preview");
        canvasFrame.setCanvasSize(frame.imageWidth, frame.imageHeight);
        grabber.setFrameRate(grabber.getFrameRate());

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(savePath, CAPTURE_WIDTH, CAPTURE_HEIGHT);
//                    grabber.getImageWidth(), grabber.getImageHeight());  // specify your path


        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoBitrate(2000000);


            /*
            recorder.setVideoOption("tune", "zerolatency");


            // Constant Rate Factor (see: https://trac.ffmpeg.org/wiki/Encode/H.264)
            recorder.setVideoOption("crf", "28");

            // 2000 kb/s, reasonable "sane" area for 720
            recorder.setVideoBitrate(2000000);
//            recorder.setVideoBitrate(10 * 1024 * 1024);

            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);


            recorder.setFormat("flv");
            recorder.setFrameRate(30);
//            recorder.setVideoBitrate(10 * 1024 * 1024);

            */

        recorder.setFormat("mp4");
        recorder.setFrameRate(30);
        recorder.setVideoBitrate(10 * 1024 * 1024);


        recorder.start();
        while (canvasFrame.isVisible() && (frame = grabber.grab()) != null) {

            if (recorder.getTimestamp() >= (lengthOfEachVideo + 1)) {       // stops 1 second early otherwise
//                System.out.println("Video ending: " + recorder.getTimestamp() + " >= " + lengthOfEachVideo);
                break;
            }

            canvasFrame.showImage(frame);
            recorder.record(frame);


            KeyEvent keyPressed = canvasFrame.waitKey(10);
            if (keyPressed != null) {

                int keyChar = keyPressed.getKeyCode();

                if (keyChar == KeyEvent.VK_ESCAPE) {
                    break;
                }

                switch (keyChar) {

//                    case KeyEvent.VK_ESCAPE: break;      // hold escape key or 'q' to quit
                    case KeyEvent.VK_Q:
                        recorder.stop();
                        grabber.stop();
                        canvasFrame.dispose();
                        System.exit(0);

                    case KeyEvent.VK_P:
                        ;// pause? ;
                }

            }
        }


        recorder.stop();
        grabber.stop();
        canvasFrame.dispose();
    }
}
