package sproj.analysis;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import sproj.tracking.Tracker;
import sproj.util.DetectionsParser;
import sproj.yolo.YOLOModelContainer;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TrackerEvaluator extends Tracker {

    @Override
    public Frame timeStep() throws IOException {
        return null;
    }

    @Override
    protected void createAnimalObjects() {

    }

    @Override
    protected void createAnimalFiles(String baseFilePrefix) throws IOException {

    }
}
