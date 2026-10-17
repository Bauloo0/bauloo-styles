package de.bauloo.styles;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;

/**
 * Inventory extras: the "bauloo styles" watermark in the top left corner and the social badge,
 * your platform logo on the recipe book button with your name next to it.
 */
public final class Watermark {

    private static final Identifier GLOW = Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/watermark_glow.png");
    private static final Identifier BADGE = Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/watermark_badge.png");
    /** the platforms you can pick, in menu order */
    public static final String[] SOCIALS = {"youtube", "tiktok", "twitch", "instagram", "x", "kick"};

    private Watermark() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
                    StylesUiConfig.load();
                    if (StylesUiConfig.watermark) {
                        draw(ctx, 6, 6, StylesUiConfig.watermarkStyle);
                    }
                });
            }
        });
    }

    /** style 1 = logo with glow, 2 = badge */
    public static void draw(DrawContext ctx, int x, int y, int style) {
        if (style == 2) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BADGE, x, y, 0, 0, 100, 28, 400, 112, 400, 112);
        } else {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0, 0, 110, 30, 440, 120, 440, 120);
        }
    }

    public static Identifier icon(String social) {
        return Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/social/" + social + ".png");
    }

    public static boolean validSocial(String social) {
        for (String s : SOCIALS) {
            if (s.equals(social)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called while the recipe book button draws itself: your logo instead of the book, your name
     * to the right. Returns false when the badge is off, then the book stays.
     */
    /** room for the name between the recipe button and the right edge of the inventory */
    private static final int NAME_SPACE = 43;

    public static boolean drawBadge(DrawContext ctx, net.minecraft.client.gui.widget.ClickableWidget button) {
        StylesUiConfig.load();
        String social = StylesUiConfig.socialLogo;
        if (!validSocial(social)) {
            return false;
        }
        int bx = button.getX();
        int by = button.getY();
        int size = Math.min(button.getWidth(), button.getHeight());
        int tint = button.isHovered() ? 0xFFFFFFFF : 0xFFEDEDED;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon(social), bx + (button.getWidth() - size) / 2,
                by + (button.getHeight() - size) / 2, 0, 0, size, size, 128, 128, 128, 128, tint);
        String name = StylesUiConfig.socialName;
        if (name != null && !name.isBlank()) {
            var font = MinecraftClient.getInstance().textRenderer;
            int left = bx + button.getWidth() + 3;
            int width = font.getWidth(name);
            int top = by + (button.getHeight() - 8) / 2;
            if (width <= NAME_SPACE) {
                ctx.drawText(font, name, left, top, 0xFF404040, false);
            } else {
                // a long name is shown whole and still: smaller, but only in steps where every font
                // pixel lands on whole screen pixels, so the letters stay sharp
                double gui = MinecraftClient.getInstance().getWindow().getScaleFactor();
                int k = Math.max(1, (int) Math.floor(gui * NAME_SPACE / (double) width));
                float scale = (float) (k / gui);
                float y = (float) (Math.round((by + (button.getHeight() - 8 * scale) / 2.0F) * gui) / gui);
                var m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(left, y);
                m.scale(scale, scale);
                ctx.drawText(font, font.trimToWidth(name, (int) (NAME_SPACE / scale)), 0, 0, 0xFF404040, false);
                m.popMatrix();
            }
        }
        return true;
    }
}
