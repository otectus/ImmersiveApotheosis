package com.otectus.immersiveapotheosis.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The workbench's frame in Minecraft's own look, shared with Immersive Smithing: the container panel, slots, bars and
 * recessed fields. Every surface is drawn from Minecraft's textures or in their colours, so a resource pack that
 * restyles vanilla containers restyles the workbench too. Text follows vanilla containers: dark grey labels on the
 * light panel, light text inside the dark displays that hold the work and its readouts.
 */
final class VanillaGui {
    static final ResourceLocation PANEL = new ResourceLocation("textures/gui/demo_background.png");
    static final ResourceLocation SLOTS = new ResourceLocation("textures/gui/container/generic_54.png");

    /** Container titles and labels (vanilla {@code 0x404040}). */
    static final int LABEL = 0xFF404040;
    /** Secondary text on the light panel. */
    static final int LABEL_DIM = 0xFF6B6B6B;
    /** Warnings on the light panel ({@code ChatFormatting.DARK_RED}). */
    static final int LABEL_WARN = 0xFFAA0000;
    /** Light text inside dark displays. */
    static final int DISPLAY_TEXT = 0xFFE0E0E0;
    static final int DISPLAY_TEXT_DIM = 0xFFA8A8A8;
    /** Indicator colours, as in Immersive Smithing's minigames. */
    static final int GOOD = 0xFF5AD05A;
    static final int GOLD = 0xFFFFD66B;
    static final int AMBER = 0xFFE0A030;
    static final int MISS = 0xFFE04848;
    /** Vanilla's dimmed screen background behind a modal panel. */
    static final int SCRIM_TOP = 0xC0101010, SCRIM_BOTTOM = 0xD0101010;

    /** A vanilla container panel of any size: the standard bevelled light grey frame. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitNineSliced(PANEL, x, y, w, h, 4, 248, 166, 0, 0);
    }

    /** A recessed field in the colours of a vanilla slot (dark top-left edge, white bottom-right edge). */
    static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    /** A recessed dark display for gameplay that needs contrast (the workpiece, the verdict). */
    static void display(GuiGraphics g, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF262626);
    }

    /** The standard 18 px item slot. */
    static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOTS, x, y, 7, 17, 18, 18);
    }

    /** A vanilla-style bar: recessed track, flat fill and a one-pixel highlight on top. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
        inset(g, x - 1, y - 1, w + 2, h + 2);
        g.fill(x, y, x + w, y + h, 0xFF373737);
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            if (h > 2) g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        }
    }

    /** A thin separating rule in the panel's own shading. */
    static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF8B8B8B);
        g.fill(x, y + 1, x + width, y + 2, 0xFFFFFFFF);
    }

    /** The text cut to {@code width} with an ellipsis. */
    static String clipped(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...";
    }

    /** Wrapped text without shadow, at most {@code maxLines} lines. */
    static void wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(20, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
    }

    static void centered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void centered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static int mix(int a, int b, double fraction) {
        double t = Math.max(0, Math.min(1, fraction));
        return 0xFF000000 | ((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) << 16)
                | ((int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) << 8)
                | (int) ((a & 255) * (1 - t) + (b & 255) * t);
    }

    private VanillaGui() {}
}
