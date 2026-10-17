package de.bauloo.styles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Finishes an update after the game has closed.
 *
 * Windows keeps a mod jar locked for as long as Minecraft runs, so the mod cannot replace its
 * own file. When the game closes with an update waiting, StylesUpdater starts this class in a
 * separate, windowless Java (javaw) from a copy outside the mods folder. It waits until the game
 * process is gone, then moves the already checksum-checked pending jar over the old one.
 * It touches nothing else and runs nothing else.
 *
 * Arguments: game-pid pending-jar target-jar log-file
 */
public final class StylesUpdateHelper {

    private StylesUpdateHelper() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            return;
        }
        long pid = Long.parseLong(args[0]);
        Path pending = Path.of(args[1]);
        Path target = Path.of(args[2]);
        Path log = Path.of(args[3]);

        // wait for the game to be gone (at most two minutes)
        Optional<ProcessHandle> game = ProcessHandle.of(pid);
        if (game.isPresent()) {
            try {
                game.get().onExit().get(2, TimeUnit.MINUTES);
            } catch (Exception e) {
                log(log, "game did not close within two minutes, giving up (next start tries again)");
                return;
            }
        }
        // Windows may need a moment to let go of the file after the process ended
        for (int attempt = 1; attempt <= 40; attempt++) {
            if (!Files.exists(pending)) {
                log(log, "nothing to install any more");
                return;
            }
            try {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(pending.resolveSibling("pending.txt"));
                log(log, "installed the update into " + target.getFileName() + " after " + attempt + " attempt(s)");
                return;
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }
        log(log, "the mod file stayed locked, next start tries again");
    }

    private static void log(Path log, String line) {
        try {
            Files.writeString(log, LocalDateTime.now() + " " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // nowhere to report to
        }
    }
}
