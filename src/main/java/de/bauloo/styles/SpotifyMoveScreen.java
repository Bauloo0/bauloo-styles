package de.bauloo.styles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Drag the Spotify card to where you want it, scroll to resize. Esc saves, R resets place and size. */
public class SpotifyMoveScreen extends Screen {

    private final Screen parent;
    private boolean dragging;
    private double grabX;
    private double grabY;

    public SpotifyMoveScreen(Screen parent) {
        super(Text.literal("Move music card"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x66000000);
        int x = SpotifyHud.x(width);
        int y = SpotifyHud.y(height);
        String title = SpotifyNowPlaying.title != null ? SpotifyNowPlaying.title : "Song name";
        String artist = SpotifyNowPlaying.title != null ? SpotifyNowPlaying.artist : "Artist";
        SpotifyHud.drawScaled(ctx, textRenderer, x, y, title, artist, 1.0F);
        int edge = dragging || inside(mouseX, mouseY) ? 0xFFFFFFFF : 0x88FFFFFF;
        ctx.fill(x - 2, y - 2, x + SpotifyHud.w() + 2, y - 1, edge);
        ctx.fill(x - 2, y + SpotifyHud.h() + 1, x + SpotifyHud.w() + 2, y + SpotifyHud.h() + 2, edge);
        ctx.fill(x - 2, y - 1, x - 1, y + SpotifyHud.h() + 1, edge);
        ctx.fill(x + SpotifyHud.w() + 1, y - 1, x + SpotifyHud.w() + 2, y + SpotifyHud.h() + 1, edge);
        ctx.drawCenteredTextWithShadow(textRenderer, "Drag to move.  Scroll = size (" + Math.round(StylesUiConfig.spotifyScale * 100) + "%).  Esc = save,  R = reset",
                width / 2, height - 24, 0xFFFFFFFF);
    }

    private boolean inside(double mx, double my) {
        int x = SpotifyHud.x(width);
        int y = SpotifyHud.y(height);
        return mx >= x && mx < x + SpotifyHud.w() && my >= y && my < y + SpotifyHud.h();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (inside(click.x(), click.y())) {
            dragging = true;
            grabX = click.x() - SpotifyHud.x(width);
            grabY = click.y() - SpotifyHud.y(height);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!dragging) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        int freeW = Math.max(1, width - SpotifyHud.w());
        int freeH = Math.max(1, height - SpotifyHud.h());
        StylesUiConfig.spotifyX = (float) Math.max(0, Math.min(1, (click.x() - grabX) / freeW));
        StylesUiConfig.spotifyY = (float) Math.max(0, Math.min(1, (click.y() - grabY) / freeH));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) {
            return false;
        }
        float next = StylesUiConfig.spotifyScale + (vertical > 0 ? 0.05F : -0.05F);
        StylesUiConfig.spotifyScale = Math.round(Math.max(0.5F, Math.min(1.5F, next)) * 20) / 20.0F;
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_R) {
            StylesUiConfig.spotifyX = -1;
            StylesUiConfig.spotifyY = -1;
            StylesUiConfig.spotifyScale = 1.0F;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        StylesUiConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
