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
    /** "outline": your own player from behind with Player Outline on, at two thicknesses */
    private static boolean outline;
    /** "totem": a totem pop cloud in front of the camera, Totem Pop plus signs, then vanilla */
    private static boolean totem;
    /** "kill": the special kill effects one after another, a few screenshots of each */
    private static boolean kill;
    /** "pet": every pet one after another, seen from the front */
    private static boolean pet;
    /** "skyfx": all sky effects at night, looking north, east, south, west and up */
    private static boolean skyfx;
    /** "draw": the draw menu with its 3D preview, small and big, for an item, a block and the anchor */
    private static boolean draw;
    /** "menu": the styles menu, a screenshot of its first page (for mockups) */
    private static boolean menu;
    /** "menu plain": the menu without the tour on top */
    private static boolean plain;
    /** "menu plain world": the menu in the World theme on the platform */
    private static boolean worldTheme;
    /** "menu aichat": the AI chat window with an example */
    private static boolean aiTest;
    /** "mace": every mace effect on a flat field, a few screenshots of each */
    private static boolean mace;
    /** "front": you in third person from the front on the platform, one screenshot (for mockups) */
    private static boolean front;
    private static final float[][] LOOKS = {{180, -22}, {-12, -62}, {0, -88}, {90, -28}};
    private static final String[] KILLS = {"ufo", "blackhole", "meteor", "grave", "snap", "rocket", "anvil", "tornado", "freeze"};

    private static void totemCloud(net.minecraft.client.MinecraftClient c) {
        net.minecraft.util.math.Vec3d at = c.player.getEyePos().add(c.player.getRotationVector().multiply(3));
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 60; i++) {
            // the way a real pop goes (through the world, so Particles: Minimal applies)
            c.world.addParticleClient(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                    at.x + r.nextGaussian() * 0.6, at.y + r.nextGaussian() * 0.6, at.z + r.nextGaussian() * 0.6,
                    r.nextGaussian() * 0.05, r.nextGaussian() * 0.05 + 0.05, r.nextGaussian() * 0.05);
        }
    }

    static void start(java.io.File file) {
        try {
            String mode = java.nio.file.Files.readString(file.toPath());
            if (mode.contains("ground")) {
                pitch = 35.0F;
            }
            crystal = mode.contains("crystal");
            outline = mode.contains("outline");
            totem = mode.contains("totem");
            kill = mode.contains("kill");
            pet = mode.contains("pet");
            skyfx = mode.contains("skyfx");
            draw = mode.contains("draw");
            menu = mode.contains("menu");
            plain = mode.contains("plain");
            worldTheme = mode.contains("world");
            if (worldTheme) {
                StylesUiConfig.worldMenu = true;
                pitch = 0.0F;
            }
            aiTest = mode.contains("aichat");
            plain |= aiTest;
            mace = mode.contains("mace");
            front = mode.contains("front");
            if (front) {
                pitch = 5.0F;
            }
            if (mace) {
                pitch = -18.0F;
            }
            if (pet) {
                pitch = 8.0F;
            }
            if (kill) {
                pitch = -22.0F;
            }
            if (totem) {
                pitch = 0.0F;
            }
            if (outline) {
                pitch = 15.0F;
            }
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
            if (menu) {
                int s = t[0] - t[1] - 40;
                if (worldTheme && t[0] - t[1] == 10 && c.getServer() != null) {
                    // a free platform high up, so the blocks beside you stand on something
                    java.util.UUID id = c.player.getUuid();
                    net.minecraft.util.math.BlockPos base = c.player.getBlockPos().up(40);
                    c.getServer().execute(() -> {
                        var w = c.getServer().getOverworld();
                        for (int dx = -6; dx <= 6; dx++) {
                            for (int dz = -6; dz <= 6; dz++) {
                                w.setBlockState(base.add(dx, 0, dz), net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState());
                            }
                        }
                        var p = c.getServer().getPlayerManager().getPlayer(id);
                        if (p != null) {
                            p.requestTeleport(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                        }
                    });
                }
                if (worldTheme) {
                    c.player.setYaw(-90.0F);
                    c.player.setPitch(0.0F);
                    c.player.setBodyYaw(-90.0F);
                    c.player.setHeadYaw(-90.0F);
                }
                if (s == 0) {
                    c.setScreen(new BaulooStylesConfigScreen(null));
                } else if (s == 20 && c.currentScreen instanceof BaulooStylesConfigScreen b) {
                    b.auditShow(0);
                } else if (s == 30 && c.currentScreen instanceof BaulooStylesConfigScreen b && aiTest) {
                    b.testAi();
                } else if (s == 30 && c.currentScreen instanceof BaulooStylesConfigScreen b && !plain) {
                    b.testTour(0);
                } else if (s == 60 && c.currentScreen instanceof BaulooStylesConfigScreen b && !plain) {
                    b.testTour(1);
                } else if (s == 80 && c.currentScreen instanceof BaulooStylesConfigScreen b && !plain) {
                    b.testTour(3);
                } else if (s == 100 && c.currentScreen instanceof BaulooStylesConfigScreen b && !plain) {
                    b.testTour(4);
                } else if (s == 50 || s == 75 || s == 95 || s == 115) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] menu screenshot");
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                } else if (s > 130) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                }
                return;
            }
            if (draw) {
                int s = t[0] - t[1] - 40;
                int[] targets = {2, 0, 3};
                int n = s / 60;
                int k = s % 60;
                if (s < 0) {
                    return;
                }
                if (n >= targets.length * 2) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                } else if (k == 0) {
                    DrawScreen d = c.currentScreen instanceof DrawScreen ds ? ds : new DrawScreen(null);
                    d.testShow(targets[n / 2], n % 2 == 1);
                    c.setScreen(d);
                } else if (k == 40) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] draw screenshot target {} big {}", targets[n / 2], n % 2 == 1);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                }
                return;
            }
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
            if ((kill || mace) && t[0] - t[1] == 20 && c.getServer() != null) {
                // a flat grass field with nothing above it, so every effect is easy to see
                net.minecraft.util.math.BlockPos feet = c.player.getBlockPos();
                c.getServer().execute(() -> {
                    var w = c.getServer().getOverworld();
                    for (int dx = -4; dx <= 16; dx++) {
                        for (int dz = -10; dz <= 10; dz++) {
                            w.setBlockState(feet.add(dx, -1, dz), net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState());
                            for (int dy = 0; dy < 30; dy++) {
                                w.setBlockState(feet.add(dx, dy, dz), net.minecraft.block.Blocks.AIR.getDefaultState());
                            }
                        }
                    }
                });
            }
            if ((outline || pet || skyfx || front || worldTheme) && t[0] - t[1] == 10 && c.getServer() != null) {
                // a free platform high up, so the camera behind the player has room
                java.util.UUID id = c.player.getUuid();
                net.minecraft.util.math.BlockPos base = c.player.getBlockPos().up(40);
                c.getServer().execute(() -> {
                    var w = c.getServer().getOverworld();
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            w.setBlockState(base.add(dx, 0, dz), net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState());
                        }
                    }
                    var p = c.getServer().getPlayerManager().getPlayer(id);
                    if (p != null) {
                        p.requestTeleport(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                    }
                });
            }
            if (outline && t[0] - t[1] == 20) {
                c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                StylesUiConfig.outline = true;
                StylesUiConfig.outlineSelf = true;
                StylesUiConfig.outlineRainbow = false;
                StylesUiConfig.outlineColor = 0xFF5AC8;
                StylesUiConfig.outlineWidth = 2;
            }
            if (skyfx) {
                int s0 = Math.max(0, t[0] - t[1] - WAIT);
                float[] look = LOOKS[Math.min(LOOKS.length - 1, s0 / 60)];
                c.player.setYaw(look[0]);
                c.player.setPitch(look[1]);
            } else {
                c.player.setPitch(pitch);
                c.player.setYaw(-90.0F);
            }
            int s = t[0] - t[1] - WAIT;
            if (s < 0) {
                return;
            }
            if (totem) {
                if (s == 0) {
                    c.options.getParticles().setValue(net.minecraft.particle.ParticlesMode.MINIMAL);
                    StylesUiConfig.totemPartStyle = 1;
                    StylesUiConfig.totemPartSize = 200;
                    StylesUiConfig.totemPartCustom = true;
                    StylesUiConfig.totemPartColor = 0xFF5AC8;
                    totemCloud(c);
                } else if (s == 6 || s == 66) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] totem screenshot, style {}, particles {}",
                            StylesUiConfig.totemPartStyle, c.particleManager.getDebugString());
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                } else if (s == 60) {
                    StylesUiConfig.totemPartStyle = 0;
                    StylesUiConfig.totemPartSize = 100;
                    StylesUiConfig.totemPartCustom = false;
                    totemCloud(c);
                } else if (s > 100) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                }
                return;
            }
            if (skyfx) {
                if (s == 0) {
                    c.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                    StylesUiConfig.skyShooting = StylesUiConfig.skyShower = StylesUiConfig.skyAurora = StylesUiConfig.skyMilky = true;
                    StylesUiConfig.skyTwinkle = StylesUiConfig.skyComet = StylesUiConfig.skyConstellations = true;
                    StylesUiConfig.skyBlood = false;
                    StylesUiConfig.skyAurora = false;
                    SkyEffects.preview();
                }
                if (s % 60 == 0 && s > 0) {
                    SkyEffects.preview();
                }
                if (s / 60 >= LOOKS.length) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                } else if (s % 60 == 45) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] skyfx screenshot {}", s / 60);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                }
                return;
            }
            if (pet) {
                int n = s / 40;
                int k = s % 40;
                if (n >= Pets.LIST.length) {
                    StylesUiConfig.cosPet = "none";
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                } else if (k == 0) {
                    c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                    StylesUiConfig.cosPet = Pets.LIST[n][0];
                } else if (k == 30) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] pet screenshot {}", Pets.LIST[n][0]);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                }
                return;
            }
            if (front) {
                if (s == 0) {
                    c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                } else if (s == 40) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] front screenshot");
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                } else if (s > 60) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                }
                return;
            }
            if (mace) {
                int n = s / 50;
                int k = s % 50;
                if (n >= MaceEffect.EFFECTS.length) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                } else if (k == 0) {
                    StylesUiConfig.maceEffect = MaceEffect.EFFECTS[n][0];
                    StylesUiConfig.maceColor = 0xFF8A2A;
                    MaceEffect.play(MaceEffect.EFFECTS[n][0],
                            new net.minecraft.util.math.Vec3d(c.player.getX() + 5.5, c.player.getY(), c.player.getZ()), 10, 1.8F);
                } else if (k == 2 || k == 6 || k == 14) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] mace screenshot {} tick {}", MaceEffect.EFFECTS[n][0], k);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                }
                return;
            }
            if (kill) {
                int n = s / 100;
                int k = s % 100;
                if (n >= KILLS.length) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                } else if (k == 0) {
                    StylesUiConfig.killEffect = KILLS[n];
                    StylesUiConfig.killColor = 0x55FF8A;
                    net.minecraft.util.math.Vec3d at = new net.minecraft.util.math.Vec3d(c.player.getX() + 5.5, c.player.getY(), c.player.getZ());
                    KillEffect.play(KILLS[n], at, "Test Dummy", 1);
                } else if (k == 3 || k == 12 || k == 26 || k == 44) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] kill screenshot {} tick {}", KILLS[n], k);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                }
                return;
            }
            if (outline) {
                if (s == 40 || s == 120) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] outline screenshot, width {}", StylesUiConfig.outlineWidth);
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, c.getFramebuffer(), message -> {
                    });
                } else if (s == 80) {
                    StylesUiConfig.outlineWidth = 5;
                    StylesUiConfig.outlineColor = 0x75D4FF;
                } else if (s > 160) {
                    BaulooStylesClient.LOGGER.info("[SkyTest] done");
                    c.scheduleStop();
                }
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
