package sproj.analysis;

import org.bytedeco.javacv.Frame;
import sproj.tracking.Tracker;
import java.io.IOException;

public class EvaluationTracker extends Tracker {

    public EvaluationTracker() {

    }

    @Override
    public Frame timeStep() throws IOException {
        return null;
    }

    @Override
    protected void createAnimalObjects() {

    }
}
