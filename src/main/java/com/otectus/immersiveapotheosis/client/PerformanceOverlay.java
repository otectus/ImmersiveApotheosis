package com.otectus.immersiveapotheosis.client;

import com.otectus.immersiveapotheosis.minigame.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.otectus.immersiveapotheosis.client.VanillaGui.*;

/**
 * Object-space workbenches on a vanilla container panel: the workpiece in a dark display, the meters and the process
 * steps on the panel in vanilla labels, and the count-in and verdict over the work. Every target is drawn from the same
 * geometry the server judges, and the workspace rectangle that maps the pointer never depends on the styling.
 */
final class PerformanceOverlay {
    private static final String KEY = "performance.immersive_apotheosis.";
    private static Layout last;
    private static final WorkStage[] SALVAGE = {WorkStage.CUT_SEAMS, WorkStage.EXTRACT_COMPONENTS};
    private static final WorkStage[] SOCKET = {WorkStage.POSITION_GEM, WorkStage.BEND_PRONGS};
    private static final WorkStage[] CUTTING = {WorkStage.ALIGN_FACETS, WorkStage.GRIND_FACETS, WorkStage.POLISH_FACETS};
    private static final WorkStage[] REFORGE = {WorkStage.HEAT_BINDINGS, WorkStage.STRIKE_ANCHORS, WorkStage.TRACE_SEAL};
    private record Layout(int left, int top, int width, int height, ClientPerformance.Workspace work,
                          int sideX, int sideWidth, int footerY) {}

    private static Layout layout(AbstractContainerScreen<?> screen) {
        int width = Math.min(580, screen.width - 16), height = Math.min(410, screen.height - 16);
        int left = (screen.width - width) / 2, top = (screen.height - height) / 2;
        int side = width < 380 ? 88 : 116;
        // Fixed text reservations keep scored geometry stationary as guidance changes.
        int footer = top + height - 88;
        ClientPerformance.Workspace area = new ClientPerformance.Workspace(left + 12, top + 50,
                Math.max(40, width - side - 40), Math.max(40, footer - top - 59));
        return last = new Layout(left, top, width, height, area, left + width - side - 14, side, footer);
    }

    static int workspaceLeft(AbstractContainerScreen<?> screen) { return layout(screen).work().left(); }
    static int workspaceTop(AbstractContainerScreen<?> screen) { return layout(screen).work().top(); }
    static int workspaceWidth(AbstractContainerScreen<?> screen) { return layout(screen).work().width(); }
    static int workspaceHeight(AbstractContainerScreen<?> screen) { return layout(screen).work().height(); }
    static ClientPerformance.Workspace workspace() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) return layout(screen).work();
        return last == null ? new ClientPerformance.Workspace(0, 0, 1, 1) : last.work();
    }

    static void render(GuiGraphics g, AbstractContainerScreen<?> screen, ClientPerformance.Run run) {
        Layout l = layout(screen);
        Font font = Minecraft.getInstance().font;
        WorkState state = run.simulation.state();
        double time = run.submitted ? state.elapsedSteps() * .02
                : Math.max(0, (Util.getMillis() - run.startedMs) / 1000.0 - WorkDefinition.COUNT_IN_STEPS * .02);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fillGradient(0, 0, screen.width, screen.height, SCRIM_TOP, SCRIM_BOTTOM);
        panel(g, l.left(), l.top(), l.width(), l.height());
        header(g, font, l, run, state, time);
        drawSurface(g, l, state.station());
        g.enableScissor(l.work().left(), l.work().top(), l.work().left() + l.work().width(), l.work().top() + l.work().height());
        WorkbenchArt.render(g, l.work(), state, run.actions(), time);
        g.disableScissor();
        meters(g, font, l, state, run.simulation.definition().assistance());
        toolPlaque(g, font, l, state.station());
        footer(g, font, l, run, state);
        if (run.clockSteps() < WorkDefinition.COUNT_IN_STEPS || run.verdict != null) {
            // Font glyphs carry a small depth offset. Later fills at the same Z do not cover them,
            // so modal panels need their own layer above all workpiece text and tool geometry.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (run.verdict != null) verdict(g, font, l, run);
            else countdown(g, font, l, run);
            g.flush();
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private static WorkStage[] stages(Station station) {
        return switch (station) {
            case SALVAGING -> SALVAGE;
            case SOCKETING -> SOCKET;
            case GEM_CUTTING -> CUTTING;
            case REFORGING -> REFORGE;
        };
    }

    private static void header(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run, WorkState state, double time) {
        int x = l.left(), y = l.top(), w = l.width();
        int count = Math.min(4, run.start.previews().size());
        // Keep pack status overlays and captured item previews clear of the title.
        int headingLeft = x + 68, headingWidth = Math.max(30, w - 84 - count * 22);
        g.drawString(font, clipped(font, run.start.title(), headingWidth), headingLeft, y + 11, LABEL, false);
        Component stage = tr("stage." + state.stage().name().toLowerCase(Locale.ROOT));
        g.drawString(font, clipped(font, stage, headingWidth), headingLeft, y + 25, LABEL_DIM, false);
        for (int i = 0; i < count; i++) {
            int px = x + w - 22 * (count - i) - 8;
            slot(g, px + 1, y + 9);
            g.renderItem(run.start.previews().get(i), px + 2, y + 10);
        }
        // One recessed step per phase of the process: done, current (gently pulsing) and still to come.
        WorkStage[] phases = stages(state.station());
        int active = state.stage() == WorkStage.COMPLETE ? phases.length : 0;
        for (int i = 0; i < phases.length; i++) if (phases[i] == state.stage()) active = i;
        int railX = x + 12, railW = w - 24, railY = y + 39;
        for (int i = 0; i < phases.length; i++) {
            int sx = railX + i * railW / phases.length;
            int ex = railX + (i + 1) * railW / phases.length - (i < phases.length - 1 ? 2 : 0);
            inset(g, sx, railY, ex - sx, 6);
            int fill = i < active ? GOOD : i == active ? mix(AMBER, GOLD, .5 + .5 * Math.sin(time * 3)) : 0xFF373737;
            g.fill(sx + 1, railY + 1, ex - 1, railY + 5, fill);
            if (i <= active) g.fill(sx + 1, railY + 1, ex - 1, railY + 2, 0x60FFFFFF);
        }
    }

    private static void drawSurface(GuiGraphics g, Layout l, Station station) {
        var a = l.work();
        // Each station keeps a faint tint of its own inside the shared dark display.
        int tint = switch (station) {
            case SALVAGING -> 0xFF393128;
            case SOCKETING -> 0xFF293530;
            case GEM_CUTTING -> 0xFF2B3337;
            case REFORGING -> 0xFF332D36;
        };
        display(g, a.left() - 2, a.top() - 2, a.width() + 4, a.height() + 4);
        g.fillGradient(a.left(), a.top(), a.left() + a.width(), a.top() + a.height(), mix(0xFF262626, tint, .45), mix(0xFF262626, tint, .8));
        for (int y = a.top() + 9; y < a.top() + a.height() - 3; y += 12) {
            int inset = (y / 12 % 3) * 5;
            g.fill(a.left() + 5 + inset, y, a.left() + a.width() - 7, y + 1, 0x10FFFFFF);
        }
    }

    private static void meters(GuiGraphics g, Font font, Layout l, WorkState state, double assist) {
        int x = l.sideX(), y = l.work().top() + 5, w = l.sideWidth(), row = Math.min(40, Math.max(24, (l.work().height() - 6) / 3));
        if (state instanceof SalvageState s) {
            var part = s.components().get(s.activeComponent());
            meter(g, font, x, y, w, "seam", part.seamProgress(), .99, 1, 1);
            meter(g, font, x, y + row, w, "extraction", part.extractionProgress(), .99, 1, 1);
            meter(g, font, x, y + row * 2, w, "integrity", 1 - s.damage(), .8, 1, 1);
        } else if (state instanceof SocketState s) {
            if (s.stage() == WorkStage.POSITION_GEM) {
                double angle = Math.abs(angleDelta(s.gemRotation(), s.targetRotation()));
                meter(g, font, x, y, w, "alignment", 1 - angle / 180, 1 - 5 * assist / 180, 1, 1);
                double distance = Math.hypot(s.gemCenter().x() - s.seat().x(), s.gemCenter().y() - s.seat().y());
                meter(g, font, x, y + row, w, "centering", Math.max(0, 1 - distance / 1600), .95, 1, 1);
                meter(g, font, x, y + row * 2, w, "seating", s.seating(), .99, 1, 1);
            } else {
                meter(g, font, x, y, w, "pressure", s.prongs().get(s.activeProng()).pressure(), s.pressureLow(), s.pressureHigh(), 1.4);
                meter(g, font, x, y + row, w, "balance", Math.max(0, 1 - Math.hypot(s.tiltX(), s.tiltY())), .9, 1, 1);
                meter(g, font, x, y + row * 2, w, "integrity", 1 - s.damage(), .8, 1, 1);
            }
        } else if (state instanceof CuttingState s) {
            var facet = s.facets().get(s.activeFacet());
            if (s.stage() == WorkStage.ALIGN_FACETS) {
                meter(g, font, x, y, w, "alignment", 1 - Math.abs(angleDelta(s.gemRotation(), s.targetRotation())) / 180, .98, 1, 1);
            } else {
                meter(g, font, x, y, w, "depth", facet.depth(), facet.targetDepth() - .04 * assist, facet.targetDepth() + .04 * assist, 1.3);
            }
            meter(g, font, x, y + row, w, "heat", s.heat(), 0, .76, 1.1);
            meter(g, font, x, y + row * 2, w, "polish", facet.polish(), .99, 1, 1);
        } else if (state instanceof ReforgeState s) {
            var anchor = s.anchors().get(s.activeAnchor());
            meter(g, font, x, y, w, "heat", s.heat(), s.heatLow(), s.heatHigh(), 1.1);
            meter(g, font, x, y + row, w, "force", s.hammerCharge(), anchor.targetForce() - s.forceTolerance(), anchor.targetForce() + s.forceTolerance(), 1.15);
            meter(g, font, x, y + row * 2, w, "seal", s.traceProgress(), .99, 1, 1);
        }
    }

    private static void meter(GuiGraphics g, Font font, int x, int y, int w, String label, double value, double low, double high, double max) {
        String number = Math.round(value * 100) + "%";
        String name = clipped(font, tr("meter." + label), w - font.width(number) - 5);
        g.drawString(font, name, x, y, LABEL, false);
        g.drawString(font, number, x + w - font.width(number), y, LABEL, false);
        y += 11;
        inset(g, x, y, w, 9);
        g.fill(x + 1, y + 1, x + w - 1, y + 8, 0xFF373737);
        int trackX = x + 2, trackW = w - 5;
        int a = trackX + (int) (clamp(low / max) * trackW), b = trackX + (int) (clamp(high / max) * trackW);
        int marker = trackX + (int) (clamp(value / max) * trackW);
        int color = value > high + .02 ? MISS : value >= low ? GOOD : AMBER;
        g.fill(trackX, y + 3, marker + 1, y + 6, mix(0xFF373737, color, .55));
        // The target window: a darker green band with bright edges, as readable without colour as with it.
        g.fill(a, y + 2, Math.max(a + 1, b + 1), y + 7, 0xFF3D5E3D);
        g.fill(a, y + 1, a + 1, y + 8, GOOD);
        g.fill(b, y + 1, b + 1, y + 8, GOOD);
        for (int i = 1; i < 4; i++) g.fill(trackX + trackW * i / 4, y + 6, trackX + trackW * i / 4 + 1, y + 8, 0xFF8B8B8B);
        // Live needle stays unsmoothed so timing windows match the work state exactly.
        g.fill(marker - 1, y + 1, marker + 2, y + 8, 0xFF000000);
        g.fill(marker, y + 1, marker + 1, y + 8, 0xFFFFFFFF);
        for (int i = 0; i < 3; i++) g.fill(marker - 2 + i, y - 3 + i, marker + 3 - i, y - 2 + i, color);
    }

    private static void toolPlaque(GuiGraphics g, Font font, Layout l, Station station) {
        if (l.work().height() < 160) return;
        int x = l.sideX(), y = l.work().top() + l.work().height() - 38, w = l.sideWidth();
        rule(g, x + 7, y + 14, w - 14);
        String key = station == Station.SALVAGING ? "salvage" : station.id;
        centered(g, font, clipped(font, tr(key + ".tool"), w - 8), x + w / 2, y + 23, LABEL_DIM);
    }

    private static void footer(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run, WorkState state) {
        int x = l.left() + 12, y = l.footerY(), w = l.width() - 24;
        bar(g, x + 1, y + 1, w - 2, 4, state.progress(), GOOD);
        Component progress = tr("progress", Math.round(state.progress() * 100));
        g.drawString(font, progress, x, y + 10, LABEL, false);
        int remaining = Math.max(0, (WorkDefinition.MAX_STEPS - state.elapsedSteps() + 49) / 50);
        Component right = state.damage() > .001 ? tr("strain_time", Math.round(state.damage() * 100), remaining) : tr("remaining", remaining);
        String rightText = clipped(font, right, w - font.width(progress) - 8);
        g.drawString(font, rightText, x + w - font.width(rightText), y + 10, state.damage() > .001 ? LABEL_WARN : LABEL_DIM, false);
        Component guidance = run.submitted && run.verdict == null ? tr("awaiting") : Component.translatable(state.guidance());
        wrapped(g, font, guidance, x, y + 24, w, 3, LABEL);
        rule(g, x, y + 59, w);
        Component controls = run.verdict != null ? tr("controls_done") : run.confirmAbandon ? tr("esc_confirm") : tr("controls",
                ClientPerformance.PERFORM.getTranslatedKeyMessage(), ClientPerformance.CONTEXT.getTranslatedKeyMessage(),
                tr(run.clockSteps() < WorkDefinition.COUNT_IN_STEPS ? "cancel" : "abandon"));
        wrapped(g, font, controls, x, y + 65, w, 2, run.confirmAbandon ? LABEL_WARN : LABEL_DIM);
    }

    private static void countdown(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run) {
        double left = Math.max(0, WorkDefinition.COUNT_IN_STEPS * .02 - (Util.getMillis() - run.startedMs) / 1000.0);
        int seconds = Math.max(1, (int) Math.ceil(left));
        var a = l.work();
        int w = Math.min(168, a.width() - 12), cx = a.left() + a.width() / 2, cy = a.top() + a.height() / 2;
        g.fill(a.left(), a.top(), a.left() + a.width(), a.top() + a.height(), 0x90000000);
        centered(g, font, clipped(font, tr("count_in", seconds), w - 16), cx, cy - 9, GOLD);
        for (int i = 0; i < 3; i++) {
            int px = cx + (i - 1) * 12 - 3, py = cy + 5;
            inset(g, px, py, 7, 7);
            g.fill(px + 1, py + 1, px + 6, py + 6, i < 3 - seconds ? GOOD : i == 3 - seconds ? GOLD : 0xFF373737);
        }
    }

    private static void verdict(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run) {
        int x = l.left() + 8, y = l.top() + 48, w = l.width() - 16, h = l.height() - 78;
        display(g, x, y, w, h);
        int cx = x + w / 2, textY = y + 13;
        if (run.verdict.band() != null) {
            int color = switch (run.verdict.band()) {
                case PERFECT -> GOLD;
                case STABLE -> GOOD;
                case FRAYED, WEAK -> DISPLAY_TEXT_DIM;
                case FAILED, SHATTERED -> MISS;
            };
            double reveal = clamp((Util.getMillis() - run.verdictMs) / 550.0);
            // The band is the headline, at twice the size like the title of vanilla's death screen.
            Component band = tr("band." + run.verdict.band().id);
            if (font.width(band) * 2 <= w - 24) {
                g.pose().pushPose();
                g.pose().translate(cx, y + 18, 0);
                g.pose().scale(2F, 2F, 1F);
                g.drawString(font, band, -font.width(band) / 2, 0, color, true);
                g.pose().popPose();
            } else {
                centered(g, font, clipped(font, band, w - 24), cx, y + 22, color);
            }
            centered(g, font, tr("score", Math.round(run.verdict.score())), cx, y + 45, DISPLAY_TEXT);
            int barWidth = Math.min(160, w - 36), bx = cx - barWidth / 2;
            bar(g, bx + 1, y + 59, barWidth - 2, 3, clamp(run.verdict.score() / 100) * (1 - Math.pow(1 - reveal, 3)), color);
            textY = y + 73;
        }
        int lines = Math.max(1, (y + h - 10 - textY) / 10);
        wrapped(g, font, run.verdict.summary(), x + 12, textY, w - 24, lines, DISPLAY_TEXT);
    }

    private static Component tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static double clamp(double n) { return Math.max(0, Math.min(1, n)); }
    private static double angleDelta(double a, double b) { return (a - b + 540) % 360 - 180; }
    private PerformanceOverlay() {}
}
