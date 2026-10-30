package de.bauloo.styles;

/** The tilted menu is not in public builds yet (bauloo's PC only). */
public final class MenuTilt {

    public static volatile boolean layoutOn;
    public static volatile float hingeX;

    private MenuTilt() {
    }

    public static boolean active() {
        return false;
    }

    public static void layout(float hinge, float width, float height) {
    }

    public static double[] toPanel(double x, double y) {
        return new double[]{x, y};
    }
}
