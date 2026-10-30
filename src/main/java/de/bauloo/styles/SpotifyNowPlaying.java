package de.bauloo.styles;

/** The clean download has no Spotify card. */
public final class SpotifyNowPlaying {

    public static final boolean AVAILABLE = false;

    public static volatile String artist;
    public static volatile String title;
    public static volatile long changedAt;
    public static volatile boolean appOpen;
    public static volatile boolean knownStart;
    public static volatile boolean timeline;
    public static volatile long timelineLengthMs;

    private SpotifyNowPlaying() {
    }

    public static void start() {
    }

    public static long timelineMs() {
        return 0;
    }

    public static long elapsedMs() {
        return 0;
    }
}
