package de.bauloo.styles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Starts StylesUpdateHelper in its own windowless Java (javaw, so no console window shows).
 * It runs from a copy outside the mods folder, waits for this game to close and then moves the
 * already checksum-checked pending jar over the old one.
 *
 * Only the "with auto updater" download contains this class. The download without the updater
 * has a stub in its place (AVAILABLE = false) and no helper at all.
 */
public final class StylesUpdateLauncher {

    /** false in the download without the auto updater */
    public static final boolean AVAILABLE = true;

    private StylesUpdateLauncher() {
    }

    /** When the game closes: move the update in, or - if Windows still holds the file - start the helper. */
    static void installOnExit(Path dir, Path pending, Path pendingInfo, Path target) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(pendingInfo);
                StylesUpdater.LOGGER.info("[Updater] installed the update on exit");
            } catch (java.io.IOException e) {
                start(dir, pending, target);
            }
        }, "Bauloo Updater install"));
    }

    static void start(Path dir, Path pending, Path target) {
        try {
            Path helperDir = dir.resolve("helper");
            Path cls = helperDir.resolve("de/bauloo/styles/StylesUpdateHelper.class");
            Files.createDirectories(cls.getParent());
            try (java.io.InputStream in = StylesUpdateLauncher.class.getResourceAsStream("/de/bauloo/styles/StylesUpdateHelper.class")) {
                if (in == null) {
                    StylesUpdater.LOGGER.info("[Updater] helper missing, next start will try again");
                    return;
                }
                Files.copy(in, cls, StandardCopyOption.REPLACE_EXISTING);
            }
            new ProcessBuilder(javaw(), "-Xmx32m", "-cp", helperDir.toString(), "de.bauloo.styles.StylesUpdateHelper",
                    String.valueOf(ProcessHandle.current().pid()), pending.toString(), target.toString(),
                    dir.resolve("helper.log").toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            StylesUpdater.LOGGER.info("[Updater] the update goes in right after the game has closed");
        } catch (Exception e) {
            StylesUpdater.LOGGER.info("[Updater] could not start the helper ({}), next start will try again", e.toString());
        }
    }

    /** The Java this game runs on, as javaw on Windows so no console window opens. */
    private static String javaw() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path exe = bin.resolve(windows ? "javaw.exe" : "java");
        if (Files.exists(exe)) {
            return exe.toString();
        }
        return ProcessHandle.current().info().command().orElse(windows ? "javaw" : "java");
    }
}
