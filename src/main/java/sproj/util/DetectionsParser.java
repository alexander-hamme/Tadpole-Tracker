package sproj.util;

import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.YoloUtils;
import sproj.yolo.YOLOModelContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * This class converts DetectedObject instances returned by
 * the YoloModelContainer.runInference(), which use grid cell units for location,
 * to BoundingBox instances, which use image coordinates
 */
public class DetectionsParser {

    private final double iouThreshold = 0.5;  // if iou (intersection over union) between two boxes > threshold, they are merged
    private final int IMG_WIDTH = YOLOModelContainer.IMG_WIDTH;
    private final int IMG_HEIGHT = YOLOModelContainer.IMG_HEIGHT;
    private final double numberOfGridCells = 13.0;
    private final double pixelsPerCell = (double) IMG_WIDTH / numberOfGridCells;    // NOTE: assumes a 1:1 image aspect ratio

    /**
     * This functions converts the detected objects positions from grid cell units to positions on the original input image.
     *
     * Note that the dimensions (for center X/Y, width/height) depend on the specific implementation.
     * For example, in the Yolo2OutputLayer, the dimensions are grid cell units-
     * with 416x416 input, 32x downsampling, we have 13x13 grid cells (each corresponding to 32 pixels in the input image).
     *
     * Thus, a centerX of 5.5 would be xPixels=5.5x32 = 176 pixels from left. Widths and heights are similar:
     * in this example, a with of 13 would be the entire image (416 pixels), and a height of 6.5 would be 6.5/13 = 0.5 of the image (208 pixels).
     *
     * @param detections List of DetectedObject instances created by YoloModelContainer.runInference()
     * @return List of BoundingBox instances
     */
    public List<BoundingBox> parseDetections(List<DetectedObject> detections) {

        double centerX, centerY;
        double width, height;
        int topLeftX, topLeftY, botRightX, botRightY;
        List<BoundingBox> boundingBoxes = new ArrayList<>(detections.size());

        YoloUtils.nms(detections, iouThreshold); // apply non maxima suppression (NMS)

        for (DetectedObject object : detections) {

            // convert from grid cell units to pixels
            centerX = object.getCenterX() * pixelsPerCell;
            centerY = object.getCenterY() * pixelsPerCell;
            width = object.getWidth() / numberOfGridCells * IMG_WIDTH;
            height = object.getHeight() / numberOfGridCells * IMG_HEIGHT;

            // calculate corner points of box
            topLeftX = (int) Math.round(centerX - (width / 2));
            topLeftY = (int) Math.round(centerY - (height / 2));
            botRightX = (int) Math.round(centerX + (width / 2));
            botRightY = (int) Math.round(centerY + (height / 2));

            boundingBoxes.add(
                    new BoundingBox(topLeftX, topLeftY, botRightX, botRightY)
            );
        }
        return boundingBoxes;
    }

}
