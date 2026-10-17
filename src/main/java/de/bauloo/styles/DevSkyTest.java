package de.bauloo.styles;

import de.bauloo.styles.mixin.ButtonPressAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Dev check for the version ports (only with bauloo.audit and a "bauloo-skytest" file in the run folder):
 * makes a new world, looks at the sky and takes a screenshot at four times of day with the time changer.
 * Shows that the pack sky and the time changer both work on that version.
 */
final class DevSkyTest {

    private static final int[] TIMES = {1000, 6000, 12800, 18000};
    private static final int WAIT = 200;
    private static final int STEP = 60;

    private DevSkyTest() {
    }

    /** where the camera looks: the sky by default, the ground when the test file says "ground" */
    private static float pitch = -30.0F;
    /** "crystal": an end crystal right in front of the player, to see which texture it gets */
    private static boolean crystal;

    static void start(java.io.File file) {
        try {
            String mode = java.nio.file.Files.readString(file.toPath());
            if (mode.contains("ground")) {
                pitch = 35.0F;
            }
            crystal = mode.contains("crystal");
            if (crystal) {
                pitch = 10.0F;
            }
        } catch (java.io.IOException ignored) {
        }
        int[] t = {0, -1};
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            t[0]++;
            if (t[0] == 20 && c.getResourcePackManager().getIds().contains("file/skytest")
                    && !c.getResourcePackManager().getEnabledIds().contains("file/skytest")) {
                // the test pack in run/resourcepacks/skytest, on top
                c.getResourcePackManager().enable("file/skytest");
                c.options.refreshResourcePacks(c.getResourcePackManager());
                c.reloadResources();
                BaulooStylesClient.LOGGER.info("[SkyTest] sky pack turned on");
            }
            if (c.world == null || c.player == null) {
                if (t[0] > 4000) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] no world");
                    c.scheduleStop();
                } else if (c.getOverlay() == null && c.currentScreen instanceof TitleScreen && t[0] > 40) {
                    c.setScreen(new SelectWorldScreen(c.currentScreen));
                } else if (c.currentScreen != null && t[0] % 20 == 0) {
                    for (Element e : c.currentScreen.children()) {
                        if (e instanceof ButtonWidget b && b.active && b.getMessage().getString().equals("Create New World")) {
                            BaulooStylesClient.LOGGER.info("[SkyTest] pressing Create New World");
                            ((ButtonPressAccessor) b).baulooStyles$onPress().onPress(b);
                            break;
                        }
                    }
                }
                return;
            }
            if (t[1] < 0) {
                t[1] = t[0];
                c.options.hudHidden = true;
                c.setScreen(null);
                BaulooStylesClient.LOGGER.info("[SkyTest] in world, sky layers: {}", CustomSky.hasSky());
            }
            c.options.hudHidden = true;
            if (c.currentScreen != null) {
                c.setScreen(null);   // the pause menu when the window loses focus
            }
            if (crystal && t[0] - t[1] == 20 && c.getServer() != null) {
                net.minecraft.util.math.Vec3d at = new net.minecraft.util.math.Vec3d(c.player.getX(), c.player.getY(), c.player.getZ()).add(c.player.getRotationVector().multiply(3.5)).add(0, 0.5, 0);
                c.getServer().execute(() -> {
                    var w = c.getServer().getOverworld();
                    w.spawnEntity(new net.minecraft.entity.decoration.EndCrystalEntity(w, at.x, at.y, at.z));
                });
            }
            if (pitch > 30 && t[0] - t[1] == 20 && c.getServer() != null) {
                // ground test: a patch of grass with short grass on it right in front of the player
                net.minecraft.util.math.BlockPos feet = c.player.getBlockPos();
                c.getServer().execute(() -> {
                    var w = c.getServer().getOverworld();
                    for (int dx = -6; dx <= 6; dx++) {
                        for (int dz = -6; dz <= 6; dz++) {
                            net.minecraft.util.math.BlockPos p = feet.add(dx, -1, dz);
                            w.setBlockState(p, net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState());
                            w.setBlockState(p.up(), (dx + dz) % 2 == 0 ? net.minecraft.block.Blocks.SHORT_GRASS.getDefaultState()
                                    : net.minecraft.block.Blocks.AIR.getDefaultState());
                        }
                    }
                });
            }
            c.player.setPitch(pitch);
            c.player.setYaw(-90.0F);
            int s = t[0] - t[1] - WAIT;
            if (s < 0) {
                return;
            }
            int i = s / STEP;
            if (i >= TIMES.length) {
                StylesUiConfig.timeOverride = -1;
                BaulooStylesClient.LOGGER.info("[SkyTest] done");
                c.scheduleStop();
                return;
            }
            if (s % STEP == 0) {
                StylesUiConfig.timeOverride = TIMES[i];
            } else if (s % STEP == 40) {
                BaulooStylesClient.LOGGER.info("[SkyTest] screenshot {} at time {}, world time {}", i, TIMES[i], c.world.getTimeOfDay() % 24000L);
                net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                });
            }
        });
    }
}
