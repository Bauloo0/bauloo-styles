package de.bauloo.styles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Cobweb Outline: a clean box around every cobweb near you, so webs are easy to see in fights.
 * Drawn live like the block outline, no resource reload; the switch sits in the Cobwebs tab.
 */
public final class CobwebOutline {

    private static final int COLOR = 0xF0FFFFFF;
    /** chunks around you that are searched, sideways */
    private static final int CHUNK_RADIUS = 3;
    /** sections (16 blocks) above and below you that are searched */
    private static final int SECTION_RADIUS = 3;
    private static final int MAX_BOXES = 600;

    private static List<BlockPos> webs = List.of();

    private CobwebOutline() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StylesUiConfig.load();
            if (client.world == null || client.player == null || !StylesUiConfig.cobwebOutline) {
                if (!webs.isEmpty()) {
                    webs = List.of();
                }
                return;
            }
            webs = scan(client.world, client.player.getBlockPos());
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            try {
                if (webs.isEmpty() || !StylesUiConfig.cobwebOutline) {
                    return;
                }
                MatrixStack matrices = context.matrices();
                VertexConsumerProvider consumers = context.consumers();
                Vec3d cam = context.worldState().cameraRenderState.pos;
                if (matrices == null || consumers == null || cam == null) {
                    return;
                }
                VertexConsumer buffer = consumers.getBuffer(RenderLayers.debugQuads());
                Matrix4f m = matrices.peek().getPositionMatrix();
                tint = webColor();
                for (BlockPos p : webs) {
                    frame(buffer, m, (float) (p.getX() - cam.x), (float) (p.getY() - cam.y), (float) (p.getZ() - cam.z));
                }
            } catch (Throwable ignored) {
                // never break world rendering over an outline
            }
        });
    }

    /** colour of the frame, from the cobweb texture in use (a red web gets a red frame) */
    private static int tint = 0xFFFFFF;
    private static Object tintSource;
    private static int tintCached = 0xFFFFFF;

    /**
     * The average colour of the web's visible pixels, pushed to full brightness. A grey or white
     * web (vanilla) stays white; anything coloured gives a frame in that colour.
     */
    /** the frame colour for the menu preview */
    public static int color() {
        return webColor();
    }

    private static int webColor() {
        try {
            var sprite = MinecraftClient.getInstance().getAtlasManager()
                    .getSprite(net.minecraft.client.render.TexturedRenderLayers.BLOCK_SPRITE_MAPPER.mapVanilla("cobweb"));
            var image = ((de.bauloo.styles.mixin.SpriteContentsAccessor) sprite.getContents()).baulooStyles$image();
            if (image == null) {
                return 0xFFFFFF;
            }
            if (image == tintSource) {
                return tintCached;
            }
            int w = sprite.getContents().getWidth();
            int h = sprite.getContents().getHeight();
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = image.getColorArgb(x, y);
                    int a = c >>> 24;
                    if (a < 0x40) {
                        continue;
                    }
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                    n++;
                }
            }
            int color = 0xFFFFFF;
            if (n > 0) {
                float[] hsb = java.awt.Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (b / n), null);
                // barely coloured = white; otherwise the web's hue, strong and bright
                color = hsb[1] < 0.10F ? 0xFFFFFF
                        : java.awt.Color.HSBtoRGB(hsb[0], Math.max(0.55F, hsb[1]), 1.0F) & 0xFFFFFF;
            }
            tintSource = image;
            tintCached = color;
            return color;
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    /** The 12 edges of the block as bars (0.25 to 4 texture pixels thick), so every side shows a square frame. */
    private static void frame(VertexConsumer b, Matrix4f m, float x, float y, float z) {
        float T = StylesUiConfig.cobwebOutlineWidth / 64.0F;
        float i = 1 - T;
        for (float a : new float[]{0, i}) {
            for (float c : new float[]{0, i}) {
                bar(b, m, x, y + a, z + c, x + 1, y + a + T, z + c + T);         // along x
                bar(b, m, x + a, y, z + c, x + a + T, y + 1, z + c + T);         // along y
                bar(b, m, x + a, y + c, z, x + a + T, y + c + T, z + 1);         // along z
            }
        }
    }

    /** A small box, lighter on top and darker below like a block in the world. */
    private static void bar(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1) {
        int a = COLOR >>> 24;
        quad(b, m, a, 1.0F, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);   // up
        quad(b, m, a, 0.55F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);  // down
        quad(b, m, a, 0.8F, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);   // north
        quad(b, m, a, 0.8F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);   // south
        quad(b, m, a, 0.68F, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);  // west
        quad(b, m, a, 0.68F, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);  // east
    }

    private static void quad(VertexConsumer b, Matrix4f m, int alpha, float shade,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        int r = Math.round(((tint >> 16) & 0xFF) * shade);
        int g = Math.round(((tint >> 8) & 0xFF) * shade);
        int bl = Math.round((tint & 0xFF) * shade);
        b.vertex(m, ax, ay, az).color(r, g, bl, alpha);
        b.vertex(m, bx, by, bz).color(r, g, bl, alpha);
        b.vertex(m, cx, cy, cz).color(r, g, bl, alpha);
        b.vertex(m, dx, dy, dz).color(r, g, bl, alpha);
    }

    /** Only sections whose palette holds a cobweb are looked at block by block. */
    private static List<BlockPos> scan(ClientWorld world, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        int csy = center.getY() >> 4;
        for (int cx = ccx - CHUNK_RADIUS; cx <= ccx + CHUNK_RADIUS; cx++) {
            for (int cz = ccz - CHUNK_RADIUS; cz <= ccz + CHUNK_RADIUS; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                    continue;
                }
                WorldChunk chunk = world.getChunk(cx, cz);
                for (int sy = Math.max(world.getBottomSectionCoord(), csy - SECTION_RADIUS);
                     sy <= Math.min(world.getTopSectionCoord(), csy + SECTION_RADIUS); sy++) {
                    ChunkSection section = chunk.getSection(world.sectionCoordToIndex(sy));
                    if (section.isEmpty() || !section.hasAny(s -> s.isOf(Blocks.COBWEB))) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).isOf(Blocks.COBWEB)) {
                                    found.add(new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z));
                                    if (found.size() >= MAX_BOXES) {
                                        return found;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
