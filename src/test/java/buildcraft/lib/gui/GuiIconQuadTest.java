package buildcraft.lib.gui;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

/** drawCustomQuad's projective map: the corners land on the quad's corners, and the square's centre on the crossing of
 * the quad's diagonals (what the old projective texture coordinates produced; an affine map would not) */
public class GuiIconQuadTest {

    private static double[] map(double[] h, double a, double b) {
        double w = h[6] * a + h[7] * b + 1;
        return new double[] { (h[0] * a + h[1] * b + h[2]) / w, (h[3] * a + h[4] * b + h[5]) / w };
    }

    @Test
    public void mapsCornersAndCentre() {
        Random random = new Random(347);
        int tested = 0;
        while (tested < 500) {
            double[] q = new double[8];
            for (int i = 0; i < 8; i++) q[i] = random.nextDouble() * 400 - 200;
            if (!GuiIcon.isConvex(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7])) continue;
            double[] h = GuiIcon.squareToQuad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7]);
            Assert.assertNotNull(h);
            double[][] unit = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
            for (int c = 0; c < 4; c++) {
                double[] p = map(h, unit[c][0], unit[c][1]);
                Assert.assertEquals(q[c * 2], p[0], 1e-6);
                Assert.assertEquals(q[c * 2 + 1], p[1], 1e-6);
            }
            // Diagonals corner1-corner3 and corner2-corner4
            double ax = q[4] - q[0], ay = q[5] - q[1], bx = q[6] - q[2], by = q[7] - q[3];
            double s = ((q[2] - q[0]) * by - (q[3] - q[1]) * bx) / (ax * by - ay * bx);
            double[] centre = map(h, 0.5, 0.5);
            Assert.assertEquals(q[0] + s * ax, centre[0], 1e-6);
            Assert.assertEquals(q[1] + s * ay, centre[1], 1e-6);
            tested++;
        }
    }

    @Test
    public void parallelogramIsAffine() {
        double[] h = GuiIcon.squareToQuad(0, 0, 10, 2, 13, 9, 3, 7);
        Assert.assertEquals(0, h[6], 0);
        Assert.assertEquals(0, h[7], 0);
    }
}
