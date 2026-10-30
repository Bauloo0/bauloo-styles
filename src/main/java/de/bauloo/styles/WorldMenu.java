package de.bauloo.styles;

/** The World menu theme is not in public builds yet (bauloo's PC only). */
public final class WorldMenu {

    public static final float CAMERA_BACK = 0F;
    public static volatile float freePart = 0.4F;

    private WorldMenu() {
    }

    public static float cameraTurn() {
        return 0F;
    }

    public static boolean active() {
        return false;
    }

    public static void open() {
    }

    public static void close() {
    }

    public static void register() {
    }
}
