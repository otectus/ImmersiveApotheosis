package com.otectus.immersiveapotheosis.client;

import com.otectus.immersiveapotheosis.minigame.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Object-space workbenches. Every target is drawn from the same geometry the server judges. */
final class PerformanceOverlay {
    private static final String KEY = "performance.immersive_apotheosis.";
    private static final int GOLD = 0xFFE6BC72, LIGHT = 0xFFF2E4CD, MUTED = 0xFFB1A89C;
    private static final int GREEN = 0xFF8ED9B4, BLUE = 0xFF78CEDC, RED = 0xFFF28B70;
    private static Layout last;

    private record Layout(int left, int top, int width, int height, ClientPerformance.Workspace work, int sideX, int sideWidth) {}
    private record Canvas(GuiGraphics g, ClientPerformance.Workspace work) {
        int x(double value) { return work.left() + (int) Math.round(value * work.width() / 4095.0); }
        int y(double value) { return work.top() + (int) Math.round(value * work.height() / 4095.0); }
        int x(WorkPoint point) { return x(point.x()); }
        int y(WorkPoint point) { return y(point.y()); }
        void line(WorkPoint a, WorkPoint b, int color, int width) {
            PerformanceOverlay.line(g, x(a), y(a), x(b), y(b), color, width);
        }
        void marker(WorkPoint point, int radius, int color) { ring(g, x(point), y(point), radius, radius, color); }
        void target(WorkPoint point, boolean active) {
            int px = x(point), py = y(point), r = active ? 6 : 3;
            ring(g, px, py, r, r, active ? GOLD : MUTED);
            if (active) {
                g.fill(px - r - 3, py, px + r + 4, py + 1, LIGHT);
                g.fill(px, py - r - 3, px + 1, py + r + 4, LIGHT);
            }
        }
    }

    private static Layout layout(AbstractContainerScreen<?> screen) {
        int width = Math.max(180, Math.min(560, screen.width - 12));
        int height = Math.max(160, Math.min(400, screen.height - 12));
        int left = (screen.width - width) / 2, top = (screen.height - height) / 2;
        int side = width < 380 ? 90 : 122;
        ClientPerformance.Workspace area = new ClientPerformance.Workspace(left + 12, top + 42,
                Math.max(55, width - side - 36), Math.max(35, height - 132));
        return last = new Layout(left, top, width, height, area, left + width - side - 12, side);
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
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, screen.width, screen.height, 0xB0000000);
        g.fill(l.left() - 2, l.top() - 2, l.left() + l.width() + 2, l.top() + l.height() + 2, 0xFF75614A);
        g.fill(l.left(), l.top(), l.left() + l.width(), l.top() + l.height(), 0xFF181D23);
        g.fill(l.left() + 3, l.top() + 3, l.left() + l.width() - 3, l.top() + 35, 0xFF2B3036);
        // Keep the heading between the common top-left status overlays and the captured item previews.
        int headingLeft = l.left() + 68, headingWidth = Math.max(50, l.width() - 156);
        int headingCenter = headingLeft + headingWidth / 2;
        g.drawCenteredString(font, font.plainSubstrByWidth(run.start.title().getString(), headingWidth),
                headingCenter, l.top() + 9, GOLD);
        g.drawCenteredString(font, font.plainSubstrByWidth(
                tr("stage." + state.stage().name().toLowerCase(Locale.ROOT)).getString(), headingWidth),
                headingCenter, l.top() + 23, MUTED);
        previews(g, run.start.previews(), l);
        drawSurface(g, l, state.station());
        Canvas canvas = new Canvas(g, l.work());
        g.enableScissor(l.work().left(), l.work().top(), l.work().left() + l.work().width(), l.work().top() + l.work().height());
        if (state instanceof SalvageState salvage) salvage(canvas, salvage);
        else if (state instanceof SocketState socket) socket(canvas, socket);
        else if (state instanceof CuttingState cutting) cutting(canvas, cutting);
        else if (state instanceof ReforgeState reforge) reforge(canvas, reforge);
        damage(canvas, state.damage());
        tool(canvas, state, run.actions());
        g.disableScissor();
        meters(g, font, l, state, run.simulation.definition().assistance());
        footer(g, font, l, run, state);
        if (run.clockSteps() < WorkDefinition.COUNT_IN_STEPS && run.verdict == null) {
            int seconds = Math.max(1, (WorkDefinition.COUNT_IN_STEPS - run.clockSteps() + 49) / 50);
            int cx = l.work().left() + l.work().width() / 2;
            g.fill(cx - 18, l.work().top() + 5, cx + 18, l.work().top() + 22, 0xE0181D23);
            g.drawCenteredString(font, Component.literal(Integer.toString(seconds)), cx, l.work().top() + 10, GOLD);
        }
        if (run.verdict != null) verdict(g, font, l, run);
        g.pose().popPose();
    }

    private static void previews(GuiGraphics g, List<ItemStack> stacks, Layout l) {
        int count = Math.min(4, stacks.size());
        for (int i = 0; i < count; i++) {
            int x = l.left() + l.width() - 18 * (count - i) - 8;
            g.fill(x - 1, l.top() + 8, x + 17, l.top() + 27, 0xFF161B21);
            g.renderItem(stacks.get(i), x, l.top() + 10);
        }
    }

    private static void drawSurface(GuiGraphics g, Layout l, Station station) {
        var a = l.work();
        int bg = switch (station) {
            case SALVAGING -> 0xFF393028;
            case SOCKETING -> 0xFF263B39;
            case GEM_CUTTING -> 0xFF263443;
            case REFORGING -> 0xFF342936;
        };
        g.fill(a.left() - 1, a.top() - 1, a.left() + a.width() + 1, a.top() + a.height() + 1, 0xFF665846);
        g.fill(a.left(), a.top(), a.left() + a.width(), a.top() + a.height(), bg);
        // A restrained bench grain/grid gives depth without hiding the working targets.
        for (int y = a.top() + 12; y < a.top() + a.height(); y += 18) {
            g.fill(a.left() + 2, y, a.left() + a.width() - 2, y + 1, 0x183F657A);
        }
        g.fill(l.sideX() - 4, a.top(), l.sideX() + l.sideWidth() + 4, a.top() + a.height(), 0xFF21272E);
    }

    private static void salvage(Canvas c, SalvageState s) {
        // Backing plate and severable bindings hold the components together.
        ellipse(c.g(), c.x(2048), c.y(2048), Math.max(10, c.work().width() * 33 / 100),
                Math.max(10, c.work().height() * 35 / 100), 0xFF4E4841);
        for (var part : s.components()) {
            int done = (int) Math.floor(part.seamProgress() * part.seam().size());
            WorkPoint center = lerp(part.center(), part.extractionTarget(), part.extractionProgress());
            if (part.removed()) {
                gem(c.g(), c.x(part.extractionTarget()), c.y(part.extractionTarget()), 7, 6, part.index() * 30, GREEN);
                continue;
            }
            int rx = Math.max(7, part.radius() * c.work().width() / 4095);
            int ry = Math.max(6, part.radius() * c.work().height() / 4095);
            ellipse(c.g(), c.x(center) + 2, c.y(center) + 3, rx, ry, 0xFF161A1C);
            gem(c.g(), c.x(center), c.y(center), rx, ry, part.index() * 23, part.index() == s.activeComponent() ? 0xFFC0A077 : 0xFF7E7969);
            for (int i = 1; i < part.seam().size(); i++) {
                c.line(part.seam().get(i - 1), part.seam().get(i), i < done ? 0xFF172323 : 0xFFE1AD77, i < done ? 2 : 1);
            }
            if (part.index() == s.activeComponent()) {
                if (s.stage() == WorkStage.CUT_SEAMS) c.target(part.seam().get(Math.min(done, part.seam().size() - 1)), true);
                else {
                    arrow(c.g(), c.x(center), c.y(center), c.x(part.extractionTarget()), c.y(part.extractionTarget()), BLUE);
                    c.marker(center, rx + 3, GOLD);
                }
            }
            number(c, center, part.index() + 1);
        }
    }

    private static void socket(Canvas c, SocketState s) {
        int cx = c.x(s.seat()), cy = c.y(s.seat());
        int rx = Math.max(14, c.work().width() * 600 / 4095), ry = Math.max(12, c.work().height() * 600 / 4095);
        ellipse(c.g(), cx, cy + 3, rx + 10, ry + 8, 0xFF151D22);
        ellipse(c.g(), cx, cy, rx + 6, ry + 5, 0xFFA28A5E);
        ellipse(c.g(), cx, cy, rx, ry, 0xFF152C32);
        ring(c.g(), cx, cy, rx + 2, ry + 2, GOLD);
        rotationMark(c, s.seat(), s.targetRotation(), 850, GOLD);
        WorkPoint tilted = new WorkPoint(bound(s.gemCenter().x() + s.tiltX() * 170), bound(s.gemCenter().y() + s.tiltY() * 170));
        gem(c.g(), c.x(tilted), c.y(tilted), Math.max(10, rx * 3 / 4), Math.max(8, ry * 3 / 4), s.gemRotation(), 0xFF68C6CA);
        rotationMark(c, s.gemCenter(), s.gemRotation(), 450, LIGHT);
        for (var prong : s.prongs()) {
            WorkPoint tip = lerp(prong.hinge(), prong.target(), Math.min(1.12, prong.bend()));
            c.line(prong.hinge(), tip, 0xFF191E20, 5);
            c.line(prong.hinge(), tip, prong.pressure() > s.pressureHigh() ? RED : GOLD, 3);
            c.marker(prong.hinge(), 3, 0xFFEED7A0);
            if (s.stage() == WorkStage.BEND_PRONGS && prong.index() == s.activeProng()) {
                arrow(c.g(), c.x(prong.hinge()), c.y(prong.hinge()), c.x(prong.target()), c.y(prong.target()), BLUE);
                c.target(prong.target(), true);
            }
            number(c, prong.hinge(), prong.index() + 1);
        }
        if (s.stage() == WorkStage.POSITION_GEM) c.marker(s.seat(), 5, BLUE);
    }

    private static void cutting(Canvas c, CuttingState s) {
        int cx = c.x(s.gemCenter()), cy = c.y(s.gemCenter());
        int rx = Math.max(16, c.work().width() * 690 / 4095), ry = Math.max(12, c.work().height() * 690 / 4095);
        // Grinding wheel touches the right edge of the mounted gem.
        int wheelX = c.x(3300), wheelY = cy;
        ellipse(c.g(), wheelX, wheelY, Math.max(15, c.work().width() / 8), Math.max(20, c.work().height() / 3), 0xFF69737A);
        ring(c.g(), wheelX, wheelY, Math.max(12, c.work().width() / 10), Math.max(17, c.work().height() / 3 - 4), 0xFFABB1AB);
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 60 + s.elapsedSteps() * 5);
            line(c.g(), wheelX, wheelY, wheelX + (int) (Math.cos(angle) * c.work().width() / 10),
                    wheelY + (int) (Math.sin(angle) * c.work().height() / 3), 0xFF424B53, 1);
        }
        gem(c.g(), cx, cy, rx, ry, s.gemRotation(), 0xFF75ACD5);
        for (var facet : s.facets()) {
            int px = c.x(facet.contact()), py = c.y(facet.contact());
            line(c.g(), cx, cy, px, py, facet.locked() ? GREEN : 0xFFACC6D9, 1);
            ring(c.g(), px, py, 4, 4, facet.polish() >= .99 ? LIGHT : facet.locked() ? GREEN : MUTED);
            if (facet.index() == s.activeFacet()) {
                if (s.stage() == WorkStage.POLISH_FACETS) {
                    ring(c.g(), px, py, Math.max(1, 100 * c.work().width() / 4095), Math.max(1, 100 * c.work().height() / 4095), GOLD);
                    for (int j = 0; j < 12; j++) {
                        double angle = -Math.PI + (j + .5) * Math.PI / 6;
                        int sx = c.x(facet.contact().x() + Math.cos(angle) * 100);
                        int sy = c.y(facet.contact().y() + Math.sin(angle) * 100);
                        int color = (facet.polishMask() & (1 << j)) != 0 ? LIGHT : 0xFF665846;
                        c.g().fill(sx, sy, sx + 1, sy + 1, color);
                    }
                } else c.target(facet.contact(), true);
            }
        }
        if (s.stage() == WorkStage.ALIGN_FACETS) {
            rotationMark(c, s.gemCenter(), s.targetRotation(), 800, GOLD);
            rotationMark(c, s.gemCenter(), s.gemRotation(), 560, LIGHT);
        }
        if (s.stage() == WorkStage.GRIND_FACETS && s.heat() > .15) {
            WorkPoint contact = s.facets().get(s.activeFacet()).contact();
            sparks(c.g(), c.x(contact), c.y(contact), s.elapsedSteps(), s.heat() > .76 ? RED : LIGHT);
        }
    }

    private static void reforge(Canvas c, ReforgeState s) {
        int cx = c.x(2048), cy = c.y(2048);
        // Anvil face carries an arcane impression of the chosen item, not literal metal damage.
        polygon(c.g(), new int[] {c.x(450), c.x(700), c.x(3450), c.x(3670), c.x(3170), c.x(900)},
                new int[] {c.y(2250), c.y(1300), c.y(1300), c.y(2200), c.y(2750), c.y(2750)}, 0xFF575360);
        line(c.g(), c.x(700), c.y(1400), c.x(3450), c.y(1400), 0xFF8B8090, 2);
        int traced = (int) Math.floor(s.traceProgress() * s.sealPath().size());
        for (int i = 1; i < s.sealPath().size(); i++) {
            c.line(s.sealPath().get(i - 1), s.sealPath().get(i), i < traced ? BLUE : 0xFF807194, i < traced ? 2 : 1);
        }
        gem(c.g(), cx, cy, Math.max(12, c.work().width() / 12), Math.max(7, c.work().height() / 9), 45, 0xFFAD91B1);
        for (var anchor : s.anchors()) {
            int x = c.x(anchor.point()), y = c.y(anchor.point());
            int heatColor = mix(0xFF665181, 0xFFF9BE73, anchor.heat());
            ellipse(c.g(), x, y, 9, 7, heatColor);
            ring(c.g(), x, y, 11, 9, anchor.struck() ? GREEN : 0xFF857D95);
            double offset = anchor.struck() ? (1 - anchor.alignment()) * 5 : 4;
            line(c.g(), x - 4 + (int) offset, y - 3, x + 4 + (int) offset, y + 3, LIGHT, 1);
            line(c.g(), x - 4 + (int) offset, y + 3, x + 4 + (int) offset, y - 3, LIGHT, 1);
            if (anchor.index() == s.activeAnchor() && s.stage() != WorkStage.TRACE_SEAL) c.target(anchor.point(), true);
            if (anchor.heat() > s.heatHigh()) sparks(c.g(), x, y, s.elapsedSteps(), RED);
        }
        if (s.stage() == WorkStage.TRACE_SEAL) {
            c.target(s.sealPath().get(Math.min(traced, s.sealPath().size() - 1)), true);
        }
    }

    private static void meters(GuiGraphics g, Font font, Layout l, WorkState state, double assist) {
        int x = l.sideX(), y = l.work().top() + 7, w = l.sideWidth(), row = Math.min(34, Math.max(24, l.work().height() / 3));
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
        String name = font.plainSubstrByWidth(tr("meter." + label).getString(), w - font.width(number) - 4);
        g.drawString(font, name, x, y, MUTED, false);
        g.drawString(font, number, x + w - font.width(number), y, LIGHT, false);
        y += 11;
        g.fill(x, y, x + w, y + 8, 0xFF11171D);
        int a = x + (int) (clamp(low / max) * (w - 1)), b = x + (int) (clamp(high / max) * (w - 1));
        g.fill(a, y + 1, Math.max(a + 1, b + 1), y + 7, 0xFF416959);
        g.fill(a, y - 1, a + 1, y + 9, GREEN);
        g.fill(b, y - 1, b + 1, y + 9, GREEN);
        int marker = x + (int) (clamp(value / max) * (w - 1));
        g.fill(marker - 1, y - 1, marker + 2, y + 9, value > high + .02 ? RED : LIGHT);
    }

    private static void footer(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run, WorkState state) {
        int x = l.left() + 12, y = l.top() + l.height() - 82, w = l.width() - 24;
        g.fill(x, y, x + w, y + 5, 0xFF0E1419);
        g.fill(x, y, x + (int) Math.round(w * clamp(state.progress())), y + 5, GREEN);
        g.drawString(font, tr("progress", Math.round(state.progress() * 100)), x, y + 10, LIGHT, false);
        int remaining = Math.max(0, (WorkDefinition.MAX_STEPS - state.elapsedSteps() + 49) / 50);
        Component right = state.damage() > .001 ? tr("strain_time", Math.round(state.damage() * 100), remaining)
                : tr("remaining", remaining);
        g.drawString(font, right, x + w - font.width(right), y + 10, state.damage() > .001 ? RED : MUTED, false);
        Component guidance = run.submitted && run.verdict == null ? tr("awaiting") : Component.translatable(state.guidance());
        wrap(g, font, guidance, x, y + 24, w, 3, LIGHT);
        Component controls = run.verdict != null ? tr("controls_done") : run.confirmAbandon ? tr("esc_confirm") : tr("controls",
                ClientPerformance.PERFORM.getTranslatedKeyMessage(), ClientPerformance.CONTEXT.getTranslatedKeyMessage(),
                tr(run.clockSteps() < WorkDefinition.COUNT_IN_STEPS ? "cancel" : "abandon"));
        wrap(g, font, controls, x, l.top() + l.height() - 23, w, 2, run.confirmAbandon ? RED : MUTED);
    }

    private static void verdict(GuiGraphics g, Font font, Layout l, ClientPerformance.Run run) {
        int x = l.left() + 4, y = l.top() + 37, w = l.width() - 8, h = Math.max(70, l.height() - 64);
        g.fill(x, y, x + w, y + h, 0xFD151B22);
        if (run.verdict.band() != null) {
            int color = run.verdict.band().isFailure() ? RED : GOLD;
            ring(g, x + w / 2, y + 22, 15, 15, color);
            sparkle(g, x + w / 2, y + 22, 8, color);
            g.drawCenteredString(font, tr("band." + run.verdict.band().id), x + w / 2, y + 43, color);
            g.drawCenteredString(font, tr("score", Math.round(run.verdict.score())), x + w / 2, y + 56, LIGHT);
        }
        wrap(g, font, run.verdict.summary(), x + 10, y + (run.verdict.band() == null ? 20 : 73), w - 20, 5, LIGHT);
    }

    private static void tool(Canvas c, WorkState state, int actions) {
        int x = c.x(state.cursor()), y = c.y(state.cursor());
        int color = actions == 0 ? 0xFFB1A89C : LIGHT;
        if (state.station() == Station.REFORGING && state.stage() == WorkStage.STRIKE_ANCHORS) {
            double charge = ((ReforgeState) state).hammerCharge();
            int lift = (int) (Math.min(1, charge) * 13);
            line(c.g(), x + 2, y - 3 - lift, x + 12, y - 22 - lift, 0xFFBE965B, 3);
            line(c.g(), x + 5, y - 25 - lift, x + 19, y - 18 - lift, color, 5);
        } else if (state.station() == Station.SALVAGING && state.stage() == WorkStage.CUT_SEAMS) {
            polygon(c.g(), new int[] {x, x + 4, x + 10}, new int[] {y, y - 15, y - 11}, color);
            line(c.g(), x + 8, y - 13, x + 14, y - 23, 0xFFAE7951, 3);
        } else if (state.stage() == WorkStage.POLISH_FACETS) {
            ellipse(c.g(), x, y, 7, 5, 0xFFDACBAC);
            ring(c.g(), x, y, 8, 6, color);
        } else if (state.stage() == WorkStage.HEAT_BINDINGS || (state.station() == Station.REFORGING && (actions & WorkInput.SECONDARY) != 0)) {
            line(c.g(), x + 3, y - 4, x + 14, y - 19, 0xFFAD7956, 4);
            if (actions != 0) sparkle(c.g(), x, y, 5 + state.elapsedSteps() % 3, GOLD);
        } else {
            line(c.g(), x - 3, y - 2, x - 9, y - 18, color, 2);
            line(c.g(), x + 3, y - 2, x + 9, y - 18, color, 2);
        }
        ring(c.g(), x, y, 3, 3, actions == 0 ? MUTED : GOLD);
    }

    private static void damage(Canvas c, double amount) {
        for (int i = 0; i < Math.min(12, (int) (amount * 18)); i++) {
            int x = c.work().left() + c.work().width() / 3 + (i * 37 % Math.max(1, c.work().width() / 3));
            int y = c.work().top() + c.work().height() / 4 + (i * 19 % Math.max(1, c.work().height() / 2));
            line(c.g(), x, y, x + 4, y + 5, RED, 1);
            line(c.g(), x + 4, y + 5, x + 2, y + 10, RED, 1);
        }
    }

    private static void rotationMark(Canvas c, WorkPoint center, int degrees, int radius, int color) {
        double a = Math.toRadians(degrees);
        WorkPoint point = new WorkPoint(bound(center.x() + Math.cos(a) * radius), bound(center.y() + Math.sin(a) * radius));
        line(c.g(), c.x(center), c.y(center), c.x(point), c.y(point), color, 1);
        c.marker(point, 4, color);
    }
    private static void number(Canvas c, WorkPoint point, int number) {
        c.g().drawCenteredString(Minecraft.getInstance().font, Integer.toString(number), c.x(point), c.y(point) - 4, 0xFF19212A);
    }
    private static void wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int maxLines, int color) {
        var lines = font.split(text, Math.max(20, width));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
    }
    private static Component tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static double clamp(double n) { return Math.max(0, Math.min(1, n)); }
    private static int bound(double n) { return (int) Math.round(Math.max(0, Math.min(4095, n))); }
    private static double angleDelta(double a, double b) { return (a - b + 540) % 360 - 180; }
    private static WorkPoint lerp(WorkPoint a, WorkPoint b, double t) {
        return new WorkPoint(bound(a.x() + (b.x() - a.x()) * t), bound(a.y() + (b.y() - a.y()) * t));
    }
    private static int mix(int a, int b, double fraction) {
        double t = clamp(fraction);
        return 0xFF000000 | ((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) << 16)
                | ((int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) << 8)
                | (int) ((a & 255) * (1 - t) + (b & 255) * t);
    }

    private static void gem(GuiGraphics g, int cx, int cy, int rx, int ry, int degrees, int color) {
        int n = 6;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(degrees + i * 360.0 / n);
            xs[i] = cx + (int) (Math.cos(a) * rx);
            ys[i] = cy + (int) (Math.sin(a) * ry);
        }
        polygon(g, xs, ys, color);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            polygon(g, new int[] {cx, xs[i], xs[j]}, new int[] {cy, ys[i], ys[j]}, mix(color, i < 3 ? 0xFFEDF2D5 : 0xFF193950, .15 + i * .035));
            line(g, xs[i], ys[i], xs[j], ys[j], mix(color, LIGHT, .5), 1);
            line(g, cx, cy, xs[i], ys[i], mix(color, LIGHT, .25), 1);
        }
        sparkle(g, cx - rx / 4, cy - ry / 4, 3, 0xCCFFFFFF);
    }

    private static void polygon(GuiGraphics g, int[] x, int[] y, int color) {
        int min = y[0], max = y[0];
        for (int value : y) { min = Math.min(min, value); max = Math.max(max, value); }
        int[] crossings = new int[x.length];
        for (int row = min; row <= max; row++) {
            int count = 0;
            for (int i = 0; i < x.length; i++) {
                int j = (i + 1) % x.length;
                if ((y[i] <= row && y[j] > row) || (y[j] <= row && y[i] > row)) {
                    crossings[count++] = x[i] + (int) ((long) (row - y[i]) * (x[j] - x[i]) / (y[j] - y[i]));
                }
            }
            java.util.Arrays.sort(crossings, 0, count);
            for (int i = 0; i + 1 < count; i += 2) g.fill(crossings[i], row, crossings[i + 1] + 1, row + 1, color);
        }
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - dy * (double) dy / Math.max(1, ry * ry))));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }
    private static void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        int steps = Math.max(16, Math.min(90, Math.max(rx, ry) * 3));
        int px = cx + rx, py = cy;
        for (int i = 1; i <= steps; i++) {
            double angle = i * Math.PI * 2 / steps;
            int nx = cx + (int) Math.round(Math.cos(angle) * rx), ny = cy + (int) Math.round(Math.sin(angle) * ry);
            line(g, px, py, nx, ny, color, 1); px = nx; py = ny;
        }
    }
    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) { g.fill(x1, y1, x1 + thickness, y1 + thickness, color); return; }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            g.fill(x - thickness / 2, y - thickness / 2, x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }
    private static void arrow(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        line(g, x1, y1, x2, y2, color, 1);
        double a = Math.atan2(y2 - y1, x2 - x1);
        line(g, x2, y2, x2 - (int) (Math.cos(a - .55) * 8), y2 - (int) (Math.sin(a - .55) * 8), color, 2);
        line(g, x2, y2, x2 - (int) (Math.cos(a + .55) * 8), y2 - (int) (Math.sin(a + .55) * 8), color, 2);
    }
    private static void sparkle(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y, x + r + 1, y + 1, color);
        g.fill(x, y - r, x + 1, y + r + 1, color);
    }
    private static void sparks(GuiGraphics g, int x, int y, int step, int color) {
        for (int i = 0; i < 4; i++) {
            int age = (step + i * 7) % 24;
            sparkle(g, x + age * (i % 2 == 0 ? 1 : -1), y - age / 2 - i * 2, 1, color);
        }
    }
    private PerformanceOverlay() {}
}
