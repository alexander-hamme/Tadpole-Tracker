package sproj.util;

/**
 * Simple container class to hold bounding box information
 */
public class BoundingBox {

    public int topleftX, topleftY;
    public int botRightX, botRightY;
    public int centerX, centerY;
    public int width, height;

    public BoundingBox(int x1, int y1, int x2, int y2) {
        this.topleftX = x1;
        this.topleftY = y1;
        this.botRightX = x2;
        this.botRightY = y2;
        this.width = botRightX - topleftX;
        this.height = botRightY - topleftY;
        this.centerX = x1 + (int) ((x2 - x1) / 2.0);
        this.centerY = y1 + (int) ((y2 - y1) / 2.0);
    }

    public BoundingBox(int[] centerPos, int width, int height) {
        this.centerX = centerPos[0];
        this.centerY = centerPos[1];
        this.topleftX = this.centerX - (int) Math.round(width / 2.0);
        this.topleftY = this.centerY - (int) Math.round(height / 2.0);
        this.botRightX = this.centerX + (int) Math.round(width / 2.0);
        this.botRightY = this.centerY + (int) Math.round(height / 2.0);
        this.width = width;
        this.height = height;
    }

    public boolean contains(int[] point) {
        return point[0] >= this.topleftX && point[0] <= this.botRightX
                && point[1] >= this.topleftY && point[1] <= this.botRightY;
    }

    /* Stronger than contains(), this function checks if the given point
     * lies within the middle 2/3rds of the current box's area.
     * Currently used to check if a new BoundingBox assignment for an Animal
     * overlaps with another Animal instance's location in the previous frame.
     */
    public boolean overlaps(int[] point) {
        return Math.abs(point[0] - this.centerX) <= this.width / 3.0
            && Math.abs(point[1] - this.centerY) <= this.height / 3.0;
    }

    public boolean contains(Double[] point) {
        return point[0] >= this.topleftX && point[0] <= this.botRightX
                && point[1] >= this.topleftY && point[1] <= this.botRightY;
    }

    public String toString() {
        return String.format(
                "Top Left: (%d, %d), Bottom Right:(%d, %d)",
                this.topleftX, this.topleftY, this.botRightX, this.botRightY);
    }
}
