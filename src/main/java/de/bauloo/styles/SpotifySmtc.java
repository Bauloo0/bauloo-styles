package de.bauloo.styles;

/** The clean download has no Spotify card. */
final class SpotifySmtc {

    record Info(String title, String artist, boolean playing, long positionMs, long lengthMs) {
    }

    private SpotifySmtc() {
    }

    static Info read() {
        return null;
    }

    static Object[] cover() {
        return null;
    }
}
