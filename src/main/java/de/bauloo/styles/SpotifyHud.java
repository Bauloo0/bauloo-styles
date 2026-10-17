package de.bauloo.styles;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Small pill in the top left corner: the song the Spotify app plays right now. */
public final class SpotifyHud {

    private static final long POPUP_MS = 5000;
    private static final long FADE_MS = 400;

    private SpotifyHud() {
    }

    public static void register() {
        SpotifyNowPlaying.start();
        HudElementRegistry.addLast(Identifier.of(BaulooStylesClient.MOD_ID, "spotify"),
                (ctx, tickCounter) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        StylesUiConfig.load();
        String title = SpotifyNowPlaying.title;
        if (!StylesUiConfig.spotifyHud || title == null || mc.options.hudHidden) {
            return;
        }
        float alpha = 1.0F;
        if (StylesUiConfig.spotifyPopup) {
            long age = System.currentTimeMillis() - SpotifyNowPlaying.changedAt;
            if (age > POPUP_MS) {
                return;
            }
            alpha = Math.min(1.0F, Math.min(age, POPUP_MS - age) / (float) FADE_MS);
        }
        // Some mods switch the GUI scale while a menu is open (2 in game, 3 in the inventory).
        // The card keeps the size it has in game: scale it back by the ratio of the two.
        double factor = mc.getWindow().getScaleFactor();
        if (mc.currentScreen == null || worldFactor <= 0) {
            worldFactor = factor;
        }
        float k = (float) (worldFactor / factor);
        float size = StylesUiConfig.spotifyScale * k;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int cardW = Math.round(W * size);
        int cardH = Math.round(H * size);
        int x = StylesUiConfig.spotifyX < 0 ? Math.round(6 * k) : Math.round(StylesUiConfig.spotifyX * Math.max(0, sw - cardW));
        int y = StylesUiConfig.spotifyY < 0 ? Math.round(6 * k) : Math.round(StylesUiConfig.spotifyY * Math.max(0, sh - cardH));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(size, size);
        draw(ctx, mc.textRenderer, 0, 0, title, SpotifyNowPlaying.artist, alpha);
        m.popMatrix();
    }

    /** the GUI scale the game had last time no menu was open */
    private static double worldFactor;

    public static final int W = 200;
    public static final int H = 46;

    /** width and height of the card at the size the player picked */
    public static int w() {
        return Math.round(W * StylesUiConfig.spotifyScale);
    }

    public static int h() {
        return Math.round(H * StylesUiConfig.spotifyScale);
    }

    /** Where the card sits; spotifyX/Y are shares of the free space, -1 = default corner. */
    public static int x(int screenW) {
        return StylesUiConfig.spotifyX < 0 ? 6 : Math.round(StylesUiConfig.spotifyX * Math.max(0, screenW - w()));
    }

    public static int y(int screenH) {
        return StylesUiConfig.spotifyY < 0 ? 6 : Math.round(StylesUiConfig.spotifyY * Math.max(0, screenH - h()));
    }

    /** The card at the picked size, top-left corner at x, y. */
    public static void drawScaled(DrawContext ctx, TextRenderer font, int x, int y, String title, String artist, float alpha) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(StylesUiConfig.spotifyScale, StylesUiConfig.spotifyScale);
        draw(ctx, font, 0, 0, title, artist, alpha);
        m.popMatrix();
    }

    private static final Identifier CIRCLE = Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/hud_circle.png");
    private static final Identifier GLOW = Identifier.of(BaulooStylesClient.MOD_ID, "textures/gui/hud_glow.png");
    private static final Identifier FONT = Identifier.of(BaulooStylesClient.MOD_ID, "hud");

    /** The card: cover, title, artist, equaliser, progress. Also used by the menu and the move screen. */
    public static void draw(DrawContext ctx, TextRenderer font, int x, int y, String title, String artist, float alpha) {
        int a = Math.round(alpha * 255);
        if (a < 8) {
            return;
        }
        String key = artist + "\n" + title;
        boolean hasCover = key.equals(SpotifyCover.coverKey);
        int color = hasCover && SpotifyCover.color >= 0 ? SpotifyCover.color : StylesUiConfig.accent & 0xFFFFFF;

        // big cover with a soft shadow; an accent square with the equaliser until it is loaded
        int cs = 42;
        int cy = y + 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GLOW, x - 7, cy - 5, 0, 0, cs + 14, cs + 14,
                64, 64, 64, 64, (Math.round(alpha * 0xB0) << 24));
        if (hasCover) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, SpotifyCover.TEXTURE, x, cy, 0, 0, cs, cs,
                    SpotifyCover.SIZE, SpotifyCover.SIZE, SpotifyCover.SIZE, SpotifyCover.SIZE, (a << 24) | 0xFFFFFF);
        } else {
            roundRect(ctx, x, cy, cs, cs, 6, (Math.round(alpha * 0xC0) << 24) | color);
            equaliser(ctx, x + cs / 2 - 7, cy + cs / 2 + 7, 4, 4, 14, (a << 24) | 0xFFFFFF);
        }

        // the line in the cover colour
        int lx = x + cs + 6;
        ctx.fill(lx, cy + 1, lx + 1, cy + cs - 1, (a << 24) | color);

        int tx = lx + 6;
        int textMax = x + W - tx;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(tx, cy + 1);
        m.scale(0.6F, 0.6F);
        ctx.drawTextWithShadow(font, styled("NOW PLAYING"), 0, 0, (a << 24) | color);
        m.popMatrix();

        m.pushMatrix();
        m.translate(tx, cy + 9);
        m.scale(1.25F, 1.25F);
        ctx.drawTextWithShadow(font, trim(font, title, Math.round(textMax / 1.25F)), 0, 0, (a << 24) | 0xFFFFFF);
        m.popMatrix();
        ctx.drawTextWithShadow(font, trim(font, artist == null ? "" : artist, textMax), tx, cy + 22, (a << 24) | 0xE6E4EE);

        // time: exact from Windows, or counted when we saw the song start
        boolean current = key.equals(SpotifyNowPlaying.artist + "\n" + SpotifyNowPlaying.title);
        boolean exact = current && SpotifyNowPlaying.timeline;
        long length = exact ? SpotifyNowPlaying.timelineLengthMs : SpotifyCover.durationMs;
        boolean known = exact || (current && SpotifyNowPlaying.knownStart && length > 0);
        if (known) {
            long played = exact ? SpotifyNowPlaying.timelineMs() : Math.min(length, SpotifyNowPlaying.elapsedMs());
            m.pushMatrix();
            m.translate(tx, cy + 34);
            m.scale(0.7F, 0.7F);
            ctx.drawTextWithShadow(font, styled(time(played) + "  /  " + time(length)), 0, 0, (a << 24) | 0xD8D6E2);
            m.popMatrix();
        }
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Four bars moving like a music visualiser, standing on baseY. */
    private static void equaliser(DrawContext ctx, int x, int baseY, int bars, int step, int maxH, int color) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < bars; i++) {
            double wave = Math.sin(now / 170.0 + i * 1.7) * 0.5 + 0.5;
            int bh = 2 + (int) Math.round(wave * (maxH - 2));
            ctx.fill(x + i * step, baseY - bh, x + i * step + step - 1, baseY, color);
        }
    }

    /** Rounded rectangle with soft corners (quarters of the circle texture). */
    private static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, 0, 0, r, r, 32, 32, 64, 64, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, CIRCLE, x + w - r, y, 32, 0, r, r, 32, 32, 64, 64, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y + h - r, 0, 32, r, r, 32, 32, 64, 64, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, CIRCLE, x + w - r, y + h - r, 32, 32, r, r, 32, 32, 64, 64, color);
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
    }

    private static Text styled(String value) {
        return Text.literal(value).styled(style -> style.withFont(new StyleSpriteSource.Font(FONT)));
    }

    private static Text trim(TextRenderer font, String value, int max) {
        Text full = styled(value);
        if (font.getWidth(full) <= max) {
            return full;
        }
        String cut = value;
        while (cut.length() > 1 && font.getWidth(styled(cut + "...")) > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return styled(cut.trim() + "...");
    }
}
