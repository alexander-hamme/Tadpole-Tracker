package sproj.analysis;

import org.bytedeco.javacv.Frame;
import sproj.tracking.Tracker;
import java.io.IOException;

public class TrackerEvaluator extends Tracker {

    public TrackerEvaluator() {

    }

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
