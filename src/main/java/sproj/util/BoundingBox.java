package sproj.util;

public class BoundingBox {
    // todo  decide which to use:  floats?  ints?  doubles?
    // todo OR don't use a separate class if it takes too much memory / time?
    public int topleftX;
    public int topleftY;
    public int botRightX;
    public int botRightY;

    public BoundingBox(int x1, int y1, int x2, int y2) {
        this.topleftX = x1;
        this.topleftY = y1;
        this.botRightX = x2;
        this.botRightY = y2;
    }

    public String toString() {
        return String.format(
                "Detection at (%d, %d)topleft, (%d, %d)bottomright",
                this.topleftX, this.topleftY, this.botRightX, this.botRightY);
    }
}
