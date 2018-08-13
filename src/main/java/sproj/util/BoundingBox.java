package sproj.util;

public class BoundingBox {
    // todo  decide which to use:  floats?  ints?  doubles?
    // todo OR don't use a separate class if it takes too much memory / time?
    public int topleftX;
    public int topleftY;
    public int botRightX;
    public int botRightY;

    public int centerX, centerY;

    public BoundingBox(int x1, int y1, int x2, int y2) {
        this.topleftX = x1;
        this.topleftY = y1;
        this.botRightX = x2;
        this.botRightY = y2;

        this.centerX = x1 + (int) ((x2 - x1) / 2.0);
        this.centerY = y1 + (int) ((y2 - y1) / 2.0);
    }



    public String toString() {
        return String.format(
                "Top Left: (%d, %d), Bottom Right:(%d, %d)",
                this.topleftX, this.topleftY, this.botRightX, this.botRightY);
    }
}
