package sproj.util;

import sproj.yolo_porting_attempts.YOLOModelContainer;

public class DetectionsParser {



    private double iouThreshold = 0.4;
    double numberOfGridCells = 13.0;
    double pixelsPerCell = (double) YOLOModelContainer.IMG_WIDTH / numberOfGridCells;
}
