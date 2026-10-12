package com.otectus.immersiveapotheosis.client;

import com.otectus.immersiveapotheosis.minigame.CuttingState;
import com.otectus.immersiveapotheosis.minigame.ReforgeState;
import com.otectus.immersiveapotheosis.minigame.SalvageState;
import com.otectus.immersiveapotheosis.minigame.SocketState;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkPoint;
import com.otectus.immersiveapotheosis.minigame.WorkStage;
import com.otectus.immersiveapotheosis.minigame.WorkState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Procedural workpieces for each station, drawn inside the workbench's dark display. The workpieces keep their own
 * stone, iron and brass; the markers laid over them (targets, numbered badges, the goal and the tool ring) follow
 * Immersive Smithing's minigame indicators. Gameplay geometry always remains in the simulation's 0..4095 space.
 */
final class WorkbenchArt {
    private static final int EDGE = 0xFF151310;
    private static final int EDGE_LIGHT = 0xFF675D4E;
    private static final int BRASS = 0xFFBD862B;
    private static final int BRASS_DARK = 0xFF8C6420;
    private static final int BRASS_LIGHT = 0xFFE0B250;
    private static final int TEXT = VanillaGui.DISPLAY_TEXT;
    private static final int TEXT_DIM = VanillaGui.DISPLAY_TEXT_DIM;
    /** Marker colours shared with Immersive Smithing: a gold target, a white ring, a dark halo for contrast. */
    private static final int TARGET = VanillaGui.GOLD;
    private static final int MARKER = 0xFFFFFFFF;
    private static final int HALO = 0xC0000000;
    private static final int JADE = 0xFF70B8A5;
    private static final int JADE_LIGHT = 0xFFA7E1CB;
    private static final int GEM = 0xFF65AFC1;
    private static final int GEM_LIGHT = 0xFFB3ECDF;
    private static final int BLUE = 0xFF91B7C2;
    private static final int HEAT = 0xFFF08A55;
    private static final int HEAT_LIGHT = 0xFFFFD078;

    // Rendering happens on one client thread. Reusing these buffers avoids arrays for every faceted shape.
    private static final int[] PX = new int[16];
    private static final int[] PY = new int[16];
    private static final int[] TRI_X = new int[3];
    private static final int[] TRI_Y = new int[3];
    private static final int[] CROSSINGS = new int[16];

    static void render(GuiGraphics g, ClientPerformance.Workspace work, WorkState state, int actions, double time) {
        Canvas c = new Canvas(g, work);
        if (state instanceof SalvageState salvage) salvage(c, salvage, actions, time);
        else if (state instanceof SocketState socket) socket(c, socket, actions, time);
        else if (state instanceof CuttingState cutting) cutting(c, cutting, actions, time);
        else if (state instanceof ReforgeState reforge) reforge(c, reforge, actions, time);

        damage(c, state.damage());
        tool(c, state, actions, time);
        targets(c, state, time); // Targets are always the final layer and cannot be hidden by sparks or tools.
    }

    private static void salvage(Canvas c, SalvageState s, int actions, double time) {
        // Deep octagonal back plate, raised rim, corner hardware, and a softly burnished center.
        octagon(c, 470, 520, 3625, 3540, EDGE);
        octagon(c, 520, 570, 3575, 3460, EDGE_LIGHT);
        octagon(c, 590, 640, 3505, 3385, 0xFF464038);
        octagon(c, 700, 760, 3395, 3270, 0xFF302D29);
        ellipse(c.g, c.x(2048), c.y(2048), c.sx(1260), c.sy(1080), 0x5537352F);
        ring(c.g, c.x(2048), c.y(2048), c.sx(1220), c.sy(1040), 0xFF595147);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = c.x(2048 + Math.cos(a) * 1420);
            int y = c.y(2048 + Math.sin(a) * 1230);
            fastener(c.g, x, y, 3, i % 2 == 0);
        }

        for (SalvageState.Component part : s.components()) {
            WorkPoint center = lerp(part.center(), part.extractionTarget(), part.extractionProgress());
            int x = c.x(center), y = c.y(center);
            int rx = Math.max(7, c.sx(part.radius()));
            int ry = Math.max(5, c.sy(part.radius()));
            boolean active = part.index() == s.activeComponent() && !part.removed();

            if (part.removed()) {
                component(c.g, c.x(part.extractionTarget()), c.y(part.extractionTarget()), rx, ry,
                        part.index() * 31, JADE, false);
                continue;
            }

            ellipse(c.g, x + 2, y + 3, rx + 3, ry + 3, 0xB0000000);
            component(c.g, x, y, rx, ry, part.index() * 31,
                    active ? 0xFFB58B55 : 0xFF797368, active);

            int done = Math.min(part.seam().size(), (int) Math.floor(part.seamProgress() * part.seam().size()));
            for (int i = 1; i < part.seam().size(); i++) {
                WorkPoint a = part.seam().get(i - 1), b = part.seam().get(i);
                c.line(a, b, i < done ? 0xFF182321 : i == done ? BRASS_LIGHT : 0xFFD49852, i < done ? 2 : 1);
            }
            if (!active || !showsTarget(s.stage())) badge(c.g, x, y, part.index() + 1, TEXT_DIM);

            if (active && s.stage() == WorkStage.EXTRACT_COMPONENTS) {
                arrow(c.g, x, y, c.x(part.extractionTarget()), c.y(part.extractionTarget()), BLUE);
                ring(c.g, c.x(part.extractionTarget()), c.y(part.extractionTarget()), rx + 3, ry + 3, 0xAA70B8A5);
            }
        }

        if (actions != 0 && s.stage() == WorkStage.CUT_SEAMS) {
            SalvageState.Component part = s.components().get(s.activeComponent());
            int done = Math.min(part.seam().size() - 1, (int) Math.floor(part.seamProgress() * part.seam().size()));
            sparks(c.g, c.x(part.seam().get(done)), c.y(part.seam().get(done)), time, BRASS_LIGHT, -1);
        }
    }

    private static void socket(Canvas c, SocketState s, int actions, double time) {
        int cx = c.x(s.seat()), cy = c.y(s.seat());
        int rx = Math.max(15, c.sx(730)), ry = Math.max(12, c.sy(730));

        // Shadowed stone mount and stepped brass bezel.
        ellipse(c.g, cx + 2, cy + 4, rx + 15, ry + 13, 0xA0000000);
        ellipse(c.g, cx, cy, rx + 15, ry + 11, EDGE);
        ellipse(c.g, cx, cy - 1, rx + 12, ry + 8, BRASS_DARK);
        ellipse(c.g, cx, cy - 1, rx + 8, ry + 5, BRASS_LIGHT);
        ellipse(c.g, cx, cy, rx + 5, ry + 3, BRASS);
        ellipse(c.g, cx, cy, rx, ry, 0xFF17312F);
        ring(c.g, cx, cy, rx + 7, ry + 4, 0xFFE9C66E);
        ring(c.g, cx, cy, Math.max(4, rx - 3), Math.max(4, ry - 3), 0xFF31544E);

        // Cardinal filigree is decorative and stays outside the playable seat.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int ix = cx + (int) Math.round(Math.cos(a) * (rx + 10));
            int iy = cy + (int) Math.round(Math.sin(a) * (ry + 7));
            int ox = cx + (int) Math.round(Math.cos(a) * (rx + 17));
            int oy = cy + (int) Math.round(Math.sin(a) * (ry + 13));
            line(c.g, ix, iy, ox, oy, i % 2 == 0 ? BRASS_LIGHT : BRASS_DARK, i % 2 == 0 ? 2 : 1);
        }

        rotationMark(c, s.seat(), s.targetRotation(), 850, BRASS_LIGHT, true);
        WorkPoint tilted = new WorkPoint(bound(s.gemCenter().x() + s.tiltX() * 170),
                bound(s.gemCenter().y() + s.tiltY() * 170));
        int gemRx = Math.max(10, rx * 3 / 4), gemRy = Math.max(8, ry * 3 / 4);
        ellipse(c.g, c.x(tilted) + 2, c.y(tilted) + 3, gemRx, gemRy, 0x99000000);
        gem(c.g, c.x(tilted), c.y(tilted), gemRx, gemRy, s.gemRotation(), GEM);
        rotationMark(c, s.gemCenter(), s.gemRotation(), 450, TEXT, false);

        for (SocketState.Prong prong : s.prongs()) {
            WorkPoint tip = lerp(prong.hinge(), prong.target(), Math.min(1.12, prong.bend()));
            int hx = c.x(prong.hinge()), hy = c.y(prong.hinge());
            int tx = c.x(tip), ty = c.y(tip);
            line(c.g, hx + 1, hy + 2, tx + 1, ty + 2, EDGE, 6);
            line(c.g, hx, hy, tx, ty, prong.pressure() > s.pressureHigh() ? HEAT : BRASS_DARK, 4);
            line(c.g, hx, hy - 1, tx, ty - 1, prong.pressure() > s.pressureHigh() ? HEAT_LIGHT : BRASS_LIGHT, 1);
            fastener(c.g, hx, hy, 3, true);
            if (s.stage() != WorkStage.BEND_PRONGS || prong.index() != s.activeProng()) {
                badge(c.g, hx, hy, prong.index() + 1,
                        prong.index() == s.activeProng() ? TARGET : TEXT_DIM);
            }

            if (prong.index() == s.activeProng() && s.stage() == WorkStage.BEND_PRONGS) {
                arrow(c.g, hx, hy, c.x(prong.target()), c.y(prong.target()), JADE_LIGHT);
                if (actions != 0 && prong.pressure() > s.pressureHigh()) {
                    sparks(c.g, c.x(prong.target()), c.y(prong.target()), time, HEAT_LIGHT, -1);
                }
            }
        }

        if (s.seating() > 0) {
            int glow = (int) Math.round(60 + 110 * clamp(s.seating()));
            ring(c.g, cx, cy, Math.max(6, gemRx + 2), Math.max(5, gemRy + 2), glow << 24 | 0x70D8C2);
        }
    }

    private static void cutting(Canvas c, CuttingState s, int actions, double time) {
        int cx = c.x(s.gemCenter()), cy = c.y(s.gemCenter());
        int rx = Math.max(16, c.sx(690)), ry = Math.max(12, c.sy(690));

        // Mounted dop and gem shadow.
        ellipse(c.g, cx, cy + ry + 5, Math.max(7, rx / 2), 4, 0x88000000);
        gfill(c.g, cx - 3, cy + ry - 1, cx + 4, cy + ry + 12, BRASS_DARK);
        gfill(c.g, cx - 2, cy + ry, cx + 3, cy + ry + 11, BRASS_LIGHT);

        int wheelX = c.x(3300), wheelY = cy;
        int wheelRx = Math.max(17, c.sx(530)), wheelRy = Math.max(22, c.sy(1380));
        ellipse(c.g, wheelX + 3, wheelY + 3, wheelRx + 4, wheelRy + 3, 0x99000000);
        ellipse(c.g, wheelX, wheelY, wheelRx + 3, wheelRy + 2, 0xFF34383A);
        ellipse(c.g, wheelX - 2, wheelY, wheelRx, wheelRy, 0xFF71746E);
        ring(c.g, wheelX - 2, wheelY, Math.max(5, wheelRx - 4), Math.max(8, wheelRy - 5), 0xFFA8A68F);
        ring(c.g, wheelX - 2, wheelY, Math.max(4, wheelRx - 10), Math.max(7, wheelRy - 12), 0xFF85867C);
        ring(c.g, wheelX - 2, wheelY, Math.max(4, wheelRx * 2 / 3), Math.max(6, wheelRy * 2 / 3), 0xFF5C615E);

        double spin = time * 8.0;
        for (int i = 0; i < 6; i++) {
            double a = spin + i * Math.PI / 3.0;
            int ix = wheelX - 2 + (int) Math.round(Math.cos(a) * Math.max(4, wheelRx / 3));
            int iy = wheelY + (int) Math.round(Math.sin(a) * Math.max(5, wheelRy / 3));
            int ox = wheelX - 2 + (int) Math.round(Math.cos(a) * Math.max(6, wheelRx - 8));
            int oy = wheelY + (int) Math.round(Math.sin(a) * Math.max(9, wheelRy - 10));
            line(c.g, ix + 1, iy + 1, ox + 1, oy + 1, 0xFF424744, 3);
            line(c.g, ix, iy, ox, oy, i % 2 == 0 ? 0xFFA4A696 : 0xFF8C8F84, 1);
        }
        ring(c.g, wheelX - 2, wheelY, Math.max(4, wheelRx / 2), Math.max(5, wheelRy / 3), EDGE);
        ellipse(c.g, wheelX - 2, wheelY, Math.max(3, wheelRx / 5), Math.max(3, wheelRy / 8), BRASS_DARK);
        fastener(c.g, wheelX - 2, wheelY, 3, false);

        for (int i = 0; i < 12; i++) {
            double a = spin + i * Math.PI / 6.0;
            int ex = wheelX - 2 + (int) Math.round(Math.cos(a) * (wheelRx + 3));
            int ey = wheelY + (int) Math.round(Math.sin(a) * (wheelRy + 2));
            int ix = wheelX - 2 + (int) Math.round(Math.cos(a) * Math.max(4, wheelRx - 5));
            int iy = wheelY + (int) Math.round(Math.sin(a) * Math.max(7, wheelRy - 6));
            line(c.g, ix, iy, ex, ey, i % 3 == 0 ? TEXT_DIM : 0xFF4C514F, 1);
        }

        ellipse(c.g, cx + 2, cy + 3, rx + 1, ry + 1, 0x88000000);
        gem(c.g, cx, cy, rx, ry, s.gemRotation(), 0xFF609EC1);

        for (CuttingState.Facet facet : s.facets()) {
            int x = c.x(facet.contact()), y = c.y(facet.contact());
            int facetColor = facet.polish() >= .99 ? GEM_LIGHT : facet.locked() ? JADE : 0xFF9AB8C0;
            line(c.g, cx, cy, x, y, facetColor, 1);
            ring(c.g, x, y, 3, 3, facetColor);
            if (facet.locked()) sparkle(c.g, x, y, 2, 0xAAE5DFD2);
        }

        if (s.stage() == WorkStage.ALIGN_FACETS) {
            rotationMark(c, s.gemCenter(), s.targetRotation(), 800, BRASS_LIGHT, true);
            rotationMark(c, s.gemCenter(), s.gemRotation(), 560, TEXT, false);
        }

        CuttingState.Facet active = s.facets().get(s.activeFacet());
        if (s.stage() == WorkStage.GRIND_FACETS && actions != 0 && s.heat() > .12) {
            int color = s.heat() > .76 ? HEAT : BRASS_LIGHT;
            sparks(c.g, c.x(active.contact()), c.y(active.contact()), time, color, -1);
        }
    }

    private static void reforge(Canvas c, ReforgeState s, int actions, double time) {
        // Layered anvil planes: shadow, foot, body, face, then the engraved working surface.
        poly(c.g, EDGE, c.x(500), c.y(2780), c.x(900), c.y(2510), c.x(3150), c.y(2510),
                c.x(3560), c.y(2840), c.x(3160), c.y(3120), c.x(890), c.y(3120));
        poly(c.g, 0xFF37333B, c.x(830), c.y(1850), c.x(1190), c.y(1390), c.x(3160), c.y(1390),
                c.x(3350), c.y(2350), c.x(3000), c.y(2740), c.x(1010), c.y(2740));
        poly(c.g, 0xFF504B55, c.x(430), c.y(2190), c.x(700), c.y(1250), c.x(3440), c.y(1250),
                c.x(3710), c.y(2140), c.x(3330), c.y(2440), c.x(760), c.y(2440));
        poly(c.g, 0xFF77707A, c.x(700), c.y(1250), c.x(3440), c.y(1250), c.x(3290), c.y(1540), c.x(790), c.y(1540));
        line(c.g, c.x(760), c.y(1320), c.x(3380), c.y(1320), 0xFFA39AA0, 2);
        line(c.g, c.x(900), c.y(2390), c.x(3240), c.y(2390), 0xFF27232B, 2);
        anvilDetails(c, s);

        // A restrained arcane medallion under the exact seal path.
        int cx = c.x(2048), cy = c.y(2048);
        ellipse(c.g, cx, cy, c.sx(660), c.sy(580), 0xFF403747);
        ring(c.g, cx, cy, c.sx(610), c.sy(530), BRASS_DARK);
        ring(c.g, cx, cy, c.sx(450), c.sy(390), 0xFF695274);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3.0;
            line(c.g, cx, cy, cx + (int) (Math.cos(a) * c.sx(430)),
                    cy + (int) (Math.sin(a) * c.sy(370)), 0xFF76627C, 1);
        }

        int traced = Math.min(s.sealPath().size(), (int) Math.floor(s.traceProgress() * s.sealPath().size()));
        for (int i = 1; i < s.sealPath().size(); i++) {
            c.line(s.sealPath().get(i - 1), s.sealPath().get(i),
                    i < traced ? GEM_LIGHT : 0xFF806D88, i < traced ? 2 : 1);
        }

        gem(c.g, cx, cy, Math.max(12, c.sx(340)), Math.max(7, c.sy(430)), 45, 0xFF92759B);
        for (ReforgeState.Anchor anchor : s.anchors()) {
            int x = c.x(anchor.point()), y = c.y(anchor.point());
            int heatColor = mix(0xFF61516B, HEAT_LIGHT, anchor.heat());
            ellipse(c.g, x, y + 1, 10, 8, EDGE);
            ellipse(c.g, x, y, 8, 6, heatColor);
            ring(c.g, x, y, 10, 8, anchor.struck() ? JADE_LIGHT : BRASS_DARK);
            double offset = anchor.struck() ? (1 - anchor.alignment()) * 5 : 4;
            line(c.g, x - 4 + (int) offset, y - 3, x + 4 + (int) offset, y + 3, TEXT, 1);
            line(c.g, x - 4 + (int) offset, y + 3, x + 4 + (int) offset, y - 3, TEXT, 1);
            if (anchor.index() != s.activeAnchor() || !showsTarget(s.stage())) {
                badge(c.g, x, y, anchor.index() + 1, TEXT_DIM);
            }
            if (anchor.heat() > s.heatHigh()) sparks(c.g, x, y, time + anchor.index() * .13, HEAT, -1);
            else if (anchor.struck() && actions == 0) sparkle(c.g, x + 7, y - 5, 2, 0xAAE5DFD2);
        }

        if (s.stage() == WorkStage.STRIKE_ANCHORS && actions != 0 && s.hammerCharge() > .25) {
            ReforgeState.Anchor active = s.anchors().get(s.activeAnchor());
            int glow = 4 + (int) Math.round(Math.min(1, s.hammerCharge()) * 5);
            ring(c.g, c.x(active.point()), c.y(active.point()), glow + 7, glow + 5, 0x99FFD078);
        }
    }

    private static void anvilDetails(Canvas c, ReforgeState s) {
        // Forged edges follow the silhouette rather than adding texture across the playable seal.
        line(c.g, c.x(430), c.y(2190), c.x(700), c.y(1250), 0xFF8C858E, 2);
        line(c.g, c.x(3440), c.y(1250), c.x(3710), c.y(2140), 0xFF37323B, 2);
        line(c.g, c.x(430), c.y(2190), c.x(760), c.y(2440), 0xFF322D36, 2);
        line(c.g, c.x(760), c.y(2440), c.x(3330), c.y(2440), 0xFF37323B, 2);
        line(c.g, c.x(3330), c.y(2440), c.x(3710), c.y(2140), 0xFF817983, 1);

        // Parallel wear grooves on the raised face remain quiet behind any final-layer target.
        for (int i = 0; i < 3; i++) {
            int y = 1370 + i * 58;
            line(c.g, c.x(900 + i * 110), c.y(y), c.x(3210 - i * 120), c.y(y),
                    i == 1 ? 0xFF66606A : 0xFF938B94, 1);
        }

        // Fixed hammer marks give the horn and heel forged grain; keep them off the seal and anchor plates.
        for (int i = 0; i < 12; i++) {
            int vx = 620 + i * 811 % 2860;
            int vy = 1630 + i * 457 % 690;
            double dx = (vx - 2048) / 1120.0, dy = (vy - 2048) / 820.0;
            if (dx * dx + dy * dy < 1.0 || nearAnchor(s, vx, vy, 300)) continue;
            int slant = (i & 1) == 0 ? 28 : -28;
            line(c.g, c.x(vx - 34), c.y(vy), c.x(vx + 34), c.y(vy + slant), 0xFF454049, 1);
            line(c.g, c.x(vx - 20), c.y(vy - 18), c.x(vx + 18), c.y(vy - 3), 0xFF69636D, 1);
        }

        // Two shallow maker's chevrons break up the apron without competing with the central seal.
        engravedChevron(c, 930, 2180, 1);
        engravedChevron(c, 3160, 2180, -1);
    }

    private static boolean nearAnchor(ReforgeState state, int x, int y, int radius) {
        long limit = (long) radius * radius;
        for (ReforgeState.Anchor anchor : state.anchors()) {
            long dx = x - anchor.point().x(), dy = y - anchor.point().y();
            if (dx * dx + dy * dy <= limit) return true;
        }
        return false;
    }

    private static void engravedChevron(Canvas c, int x, int y, int direction) {
        line(c.g, c.x(x - direction * 100), c.y(y - 70), c.x(x), c.y(y), 0xFF332E37, 2);
        line(c.g, c.x(x), c.y(y), c.x(x - direction * 100), c.y(y + 70), 0xFF332E37, 2);
        line(c.g, c.x(x - direction * 92), c.y(y - 68), c.x(x), c.y(y), 0xFF77707A, 1);
    }

    private static void targets(Canvas c, WorkState state, double time) {
        if (state.stage() == WorkStage.COMPLETE || state.stage() == WorkStage.FAILED || state.stage() == WorkStage.COUNT_IN) return;
        if (state instanceof SalvageState s) {
            SalvageState.Component part = s.components().get(s.activeComponent());
            if (s.stage() == WorkStage.CUT_SEAMS) {
                int done = Math.min(part.seam().size() - 1, (int) Math.floor(part.seamProgress() * part.seam().size()));
                target(c, part.seam().get(done), part.index() + 1, time);
            } else {
                target(c, lerp(part.center(), part.extractionTarget(), part.extractionProgress()), part.index() + 1, time);
            }
        } else if (state instanceof SocketState s) {
            if (s.stage() == WorkStage.POSITION_GEM) {
                goal(c, s.seat(), time);
                target(c, s.gemCenter(), 1, time);
            } else {
                SocketState.Prong prong = s.prongs().get(s.activeProng());
                target(c, prong.target(), prong.index() + 1, time);
            }
        } else if (state instanceof CuttingState s) {
            if (s.stage() == WorkStage.ALIGN_FACETS) {
                target(c, radial(s.gemCenter(), s.targetRotation(), 800), s.activeFacet() + 1, time);
            } else if (s.stage() == WorkStage.POLISH_FACETS) {
                CuttingState.Facet facet = s.facets().get(s.activeFacet());
                polishArc(c, facet);
                polishTarget(c, facet.contact(), facet.index() + 1, time);
            } else {
                target(c, s.facets().get(s.activeFacet()).contact(), s.activeFacet() + 1, time);
            }
        } else if (state instanceof ReforgeState s) {
            if (s.stage() == WorkStage.TRACE_SEAL) {
                int traced = Math.min(s.sealPath().size() - 1,
                        (int) Math.floor(s.traceProgress() * s.sealPath().size()));
                target(c, s.sealPath().get(traced), s.activeAnchor() + 1, time);
            } else {
                target(c, s.anchors().get(s.activeAnchor()).point(), s.activeAnchor() + 1, time);
            }
        }
    }

    private static boolean showsTarget(WorkStage stage) {
        return stage != WorkStage.COUNT_IN && stage != WorkStage.COMPLETE && stage != WorkStage.FAILED;
    }

    private static void tool(Canvas c, WorkState state, int actions, double time) {
        int x = c.x(state.cursor()), y = c.y(state.cursor());
        int color = actions == 0 ? TEXT_DIM : TEXT;
        if (state.station() == Station.REFORGING && state.stage() == WorkStage.STRIKE_ANCHORS) {
            int lift = (int) (Math.min(1, ((ReforgeState) state).hammerCharge()) * 13);
            line(c.g, x + 2, y - 3 - lift, x + 12, y - 22 - lift, 0xFF9A683A, 4);
            line(c.g, x + 5, y - 25 - lift, x + 19, y - 18 - lift, EDGE, 7);
            line(c.g, x + 5, y - 26 - lift, x + 19, y - 19 - lift, color, 4);
        } else if (state.station() == Station.SALVAGING && state.stage() == WorkStage.CUT_SEAMS) {
            triangle(c.g, x, y, x + 4, y - 15, x + 10, y - 11, color);
            line(c.g, x + 8, y - 13, x + 14, y - 23, 0xFF9A683A, 4);
            line(c.g, x + 9, y - 14, x + 14, y - 23, BRASS_LIGHT, 1);
        } else if (state.stage() == WorkStage.POLISH_FACETS) {
            ellipse(c.g, x, y, 7, 5, 0xFFD2C6AA);
            ring(c.g, x, y, 8, 6, color);
        } else if (state.stage() == WorkStage.HEAT_BINDINGS
                || state.station() == Station.REFORGING && (actions & WorkInput.SECONDARY) != 0) {
            line(c.g, x + 3, y - 4, x + 14, y - 19, 0xFF9A6042, 5);
            line(c.g, x + 2, y - 3, x + 12, y - 17, BRASS_LIGHT, 1);
            if (actions != 0) {
                int r = 4 + (int) Math.round((Math.sin(time * 8) + 1) * 1.5);
                sparkle(c.g, x, y, r, HEAT_LIGHT);
            }
        } else {
            line(c.g, x - 3, y - 2, x - 9, y - 18, EDGE, 4);
            line(c.g, x + 3, y - 2, x + 9, y - 18, EDGE, 4);
            line(c.g, x - 3, y - 2, x - 9, y - 18, color, 2);
            line(c.g, x + 3, y - 2, x + 9, y - 18, color, 2);
        }
        ring(c.g, x, y, 3, 3, actions == 0 ? TEXT_DIM : TARGET);
    }

    private static void damage(Canvas c, double amount) {
        int count = Math.min(10, (int) Math.floor(amount * 16));
        for (int i = 0; i < count; i++) {
            int vx = 850 + i * 379 % 2450;
            int vy = 950 + i * 613 % 2100;
            int x = c.x(vx), y = c.y(vy);
            line(c.g, x, y, x + 3, y + 4, 0xCCF06D55, 1);
            line(c.g, x + 3, y + 4, x + 1, y + 8, 0xCCF06D55, 1);
        }
    }

    private static void polishArc(Canvas c, CuttingState.Facet facet) {
        WorkPoint contact = facet.contact();
        for (int i = 0; i < 12; i++) {
            double a0 = -Math.PI + i * Math.PI / 6.0 + .08;
            double a1 = -Math.PI + (i + 1) * Math.PI / 6.0 - .08;
            int color = (facet.polishMask() & 1 << i) != 0 ? GEM_LIGHT : 0xFF675D4E;
            int x0 = c.x(contact.x() + Math.cos(a0) * 100);
            int y0 = c.y(contact.y() + Math.sin(a0) * 100);
            int x1 = c.x(contact.x() + Math.cos(a1) * 100);
            int y1 = c.y(contact.y() + Math.sin(a1) * 100);
            line(c.g, x0, y0, x1, y1, color, 1);
            gfill(c.g, x0, y0, x0 + 1, y0 + 1, color);
        }
    }

    /** Polishing uses the nearby radius-100 sector orbit, so its target cue must not cross that path. */
    private static void polishTarget(Canvas c, WorkPoint point, int number, double time) {
        int x = c.x(point), y = c.y(point);
        int orbitX = Math.max(Math.abs(c.x(point.x() + 100) - x), Math.abs(c.x(point.x() - 100) - x));
        int orbitY = Math.max(Math.abs(c.y(point.y() + 100) - y), Math.abs(c.y(point.y() - 100) - y));
        int pulse = (int) Math.round((Math.sin(time * 4.5) + 1) * 1.5);
        int outerX = orbitX + 5 + pulse, outerY = orbitY + 5 + pulse;
        ring(c.g, x, y, outerX, outerY, 0x70FFFFFF);

        int right = c.work.left() + c.work.width() - 1;
        int top = c.work.top(), bottom = c.work.top() + c.work.height() - 1;
        int badgeX = x + outerX + 8 <= right ? x + outerX + 5 : x - outerX - 5;
        int badgeY = y - outerY - 5 >= top ? y - outerY : Math.min(bottom - 5, y + outerY);
        badge(c.g, badgeX, badgeY, number, TARGET);
    }

    private static void target(Canvas c, WorkPoint point, int number, double time) {
        int x = c.x(point), y = c.y(point);
        int pulse = 9 + (int) Math.round((Math.sin(time * 4.5) + 1) * 1.5);
        int left = c.work.left(), top = c.work.top();
        int right = c.work.left() + c.work.width() - 1, bottom = c.work.top() + c.work.height() - 1;
        int pulseX = Math.max(2, Math.min(pulse, Math.min(x - left, right - x)));
        int pulseY = Math.max(2, Math.min(pulse, Math.min(y - top, bottom - y)));
        int coreX = Math.max(2, Math.min(6, Math.min(x - left, right - x)));
        int coreY = Math.max(2, Math.min(6, Math.min(y - top, bottom - y)));
        ring(c.g, x, y, pulseX, pulseY, 0x70FFFFFF);
        // A dark halo keeps the target readable over any workpiece.
        ring(c.g, x, y, coreX + 1, coreY + 1, HALO);
        ring(c.g, x, y, coreX, coreY, MARKER);
        gfill(c.g, Math.max(left, x - 10), y, x - coreX, y + 1, TARGET);
        gfill(c.g, x + coreX + 1, y, Math.min(right + 1, x + 11), y + 1, TARGET);
        gfill(c.g, x, Math.max(top, y - 10), x + 1, y - coreY, TARGET);
        gfill(c.g, x, y + coreY + 1, x + 1, Math.min(bottom + 1, y + 11), TARGET);
        gfill(c.g, x - 1, y - 1, x + 2, y + 2, TARGET);
        int badgeX = x + 15 <= right ? x + 9 : x - 9;
        int badgeY = y - 14 >= top ? y - 9 : Math.min(bottom - 5, y + 9);
        badge(c.g, badgeX, badgeY, number, TARGET);
    }

    private static void goal(Canvas c, WorkPoint point, double time) {
        int x = c.x(point), y = c.y(point);
        int pulse = 11 + (int) Math.round((Math.sin(time * 3.5) + 1));
        int left = c.work.left(), top = c.work.top();
        int right = c.work.left() + c.work.width() - 1, bottom = c.work.top() + c.work.height() - 1;
        int rx = Math.max(2, Math.min(pulse, Math.min(x - left, right - x)));
        int ry = Math.max(2, Math.min(pulse, Math.min(y - top, bottom - y)));
        ring(c.g, x, y, rx, ry, 0x905AD05A);
        gfill(c.g, Math.max(left, x - 5), y, Math.min(right + 1, x + 6), y + 1, VanillaGui.GOOD);
        gfill(c.g, x, Math.max(top, y - 5), x + 1, Math.min(bottom + 1, y + 6), VanillaGui.GOOD);
    }

    private static void badge(GuiGraphics g, int x, int y, int number, int border) {
        String label = Integer.toString(number);
        int w = Math.max(9, Minecraft.getInstance().font.width(label) + 4);
        int left = x - w / 2;
        // A small dark display, edged in the marker's colour.
        g.fill(left, y - 5, left + w, y + 5, 0xFF000000);
        g.fill(left + 1, y - 4, left + w - 1, y + 4, border);
        g.fill(left + 2, y - 3, left + w - 2, y + 3, 0xFF262626);
        g.drawCenteredString(Minecraft.getInstance().font, label, x, y - 4, TEXT);
    }

    private static void component(GuiGraphics g, int cx, int cy, int rx, int ry, int degrees, int color, boolean active) {
        gem(g, cx, cy, rx, ry, degrees, color);
        int insetX = Math.max(3, rx / 2), insetY = Math.max(2, ry / 2);
        ring(g, cx, cy, insetX, insetY, active ? BRASS_LIGHT : 0xFFAAA18E);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, active ? JADE_LIGHT : EDGE_LIGHT);
    }

    private static void gem(GuiGraphics g, int cx, int cy, int rx, int ry, int degrees, int color) {
        int n = 8;
        double start = Math.toRadians(degrees);
        for (int i = 0; i < n; i++) {
            double angle = start + i * Math.PI * 2.0 / n;
            PX[i] = cx + (int) Math.round(Math.cos(angle) * rx);
            PY[i] = cy + (int) Math.round(Math.sin(angle) * ry);
        }
        polygon(g, PX, PY, n, color);
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            int shade = mix(color, i < 4 ? 0xFFF1F0D4 : 0xFF173844, .12 + i * .035);
            triangle(g, cx, cy, PX[i], PY[i], PX[next], PY[next], shade);
            line(g, cx, cy, PX[i], PY[i], mix(color, TEXT, .22), 1);
            line(g, PX[i], PY[i], PX[next], PY[next], mix(color, TEXT, .42), 1);
        }
        triangle(g, cx - Math.max(1, rx / 3), cy - Math.max(1, ry / 3), cx, cy - 1,
                cx - Math.max(1, rx / 6), cy, 0x99FFFFFF);
    }

    private static void rotationMark(Canvas c, WorkPoint center, int degrees, int radius, int color, boolean ornate) {
        WorkPoint point = radial(center, degrees, radius);
        line(c.g, c.x(center), c.y(center), c.x(point), c.y(point), color, 1);
        ring(c.g, c.x(point), c.y(point), ornate ? 5 : 3, ornate ? 5 : 3, color);
        if (ornate) sparkle(c.g, c.x(point), c.y(point), 3, color);
    }

    private static WorkPoint radial(WorkPoint center, int degrees, int radius) {
        double angle = Math.toRadians(degrees);
        return new WorkPoint(bound(center.x() + Math.cos(angle) * radius),
                bound(center.y() + Math.sin(angle) * radius));
    }

    private static void octagon(Canvas c, int left, int top, int right, int bottom, int color) {
        int cutX = (right - left) / 8, cutY = (bottom - top) / 8;
        poly(c.g, color, c.x(left + cutX), c.y(top), c.x(right - cutX), c.y(top),
                c.x(right), c.y(top + cutY), c.x(right), c.y(bottom - cutY),
                c.x(right - cutX), c.y(bottom), c.x(left + cutX), c.y(bottom),
                c.x(left), c.y(bottom - cutY), c.x(left), c.y(top + cutY));
    }

    private static void fastener(GuiGraphics g, int x, int y, int radius, boolean diagonal) {
        ellipse(g, x + 1, y + 1, radius + 1, radius + 1, EDGE);
        ellipse(g, x, y, radius, radius, BRASS_DARK);
        g.fill(x - radius + 1, y - radius + 1, x + radius, y, BRASS_LIGHT);
        if (diagonal) line(g, x - 2, y - 2, x + 2, y + 2, EDGE, 1);
        else line(g, x - 2, y, x + 2, y, EDGE, 1);
    }

    private static void sparks(GuiGraphics g, int x, int y, double time, int color, int direction) {
        int phase = (int) Math.floor(time * 18.0);
        for (int i = 0; i < 4; i++) {
            int age = Math.floorMod(phase + i * 5, 17);
            int side = (i & 1) == 0 ? 1 : -1;
            int sx = x + direction * age / 2 + side * (i + age / 5);
            int sy = y - age / 2 - i;
            sparkle(g, sx, sy, age < 5 ? 2 : 1, age > 11 ? color & 0x66FFFFFF : color);
        }
    }

    private static void sparkle(GuiGraphics g, int x, int y, int radius, int color) {
        g.fill(x - radius, y, x + radius + 1, y + 1, color);
        g.fill(x, y - radius, x + 1, y + radius + 1, color);
    }

    private static void arrow(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        line(g, x1, y1, x2, y2, color, 1);
        double a = Math.atan2(y2 - y1, x2 - x1);
        line(g, x2, y2, x2 - (int) Math.round(Math.cos(a - .55) * 7),
                y2 - (int) Math.round(Math.sin(a - .55) * 7), color, 2);
        line(g, x2, y2, x2 - (int) Math.round(Math.cos(a + .55) * 7),
                y2 - (int) Math.round(Math.sin(a + .55) * 7), color, 2);
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        rx = Math.max(1, rx);
        ry = Math.max(1, ry);
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - dy * (double) dy / (ry * ry))));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        rx = Math.max(1, rx);
        ry = Math.max(1, ry);
        int steps = Math.max(16, Math.min(72, Math.max(rx, ry) * 3));
        int px = cx + rx, py = cy;
        for (int i = 1; i <= steps; i++) {
            double angle = i * Math.PI * 2.0 / steps;
            int nx = cx + (int) Math.round(Math.cos(angle) * rx);
            int ny = cy + (int) Math.round(Math.sin(angle) * ry);
            line(g, px, py, nx, ny, color, 1);
            px = nx;
            py = ny;
        }
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            gfill(g, x1 - thickness / 2, y1 - thickness / 2,
                    x1 + (thickness + 1) / 2, y1 + (thickness + 1) / 2, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            gfill(g, x - thickness / 2, y - thickness / 2,
                    x + (thickness + 1) / 2, y + (thickness + 1) / 2, color);
        }
    }

    private static void triangle(GuiGraphics g, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        TRI_X[0] = x1; TRI_Y[0] = y1;
        TRI_X[1] = x2; TRI_Y[1] = y2;
        TRI_X[2] = x3; TRI_Y[2] = y3;
        polygon(g, TRI_X, TRI_Y, 3, color);
    }

    private static void poly(GuiGraphics g, int color, int... points) {
        int n = Math.min(PX.length, points.length / 2);
        for (int i = 0; i < n; i++) {
            PX[i] = points[i * 2];
            PY[i] = points[i * 2 + 1];
        }
        polygon(g, PX, PY, n, color);
    }

    private static void polygon(GuiGraphics g, int[] xs, int[] ys, int count, int color) {
        int minY = ys[0], maxY = ys[0];
        for (int i = 1; i < count; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        for (int row = minY; row <= maxY; row++) {
            int intersections = 0;
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                if ((ys[i] <= row && ys[next] > row) || (ys[next] <= row && ys[i] > row)) {
                    CROSSINGS[intersections++] = xs[i]
                            + (int) ((long) (row - ys[i]) * (xs[next] - xs[i]) / (ys[next] - ys[i]));
                }
            }
            java.util.Arrays.sort(CROSSINGS, 0, intersections);
            for (int i = 0; i + 1 < intersections; i += 2) {
                g.fill(CROSSINGS[i], row, CROSSINGS[i + 1] + 1, row + 1, color);
            }
        }
    }

    private static void gfill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), color);
    }

    private static WorkPoint lerp(WorkPoint a, WorkPoint b, double amount) {
        return new WorkPoint(bound(a.x() + (b.x() - a.x()) * amount),
                bound(a.y() + (b.y() - a.y()) * amount));
    }

    private static int mix(int a, int b, double amount) {
        double t = clamp(amount);
        return 0xFF000000 | ((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t) << 16)
                | ((int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t) << 8)
                | (int) ((a & 255) * (1 - t) + (b & 255) * t);
    }

    private static int bound(double value) {
        return (int) Math.round(Math.max(0, Math.min(4095, value)));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static final class Canvas {
        final GuiGraphics g;
        final ClientPerformance.Workspace work;

        Canvas(GuiGraphics g, ClientPerformance.Workspace work) {
            this.g = g;
            this.work = work;
        }

        int x(double value) { return work.left() + (int) Math.round(value * work.width() / 4095.0); }
        int y(double value) { return work.top() + (int) Math.round(value * work.height() / 4095.0); }
        int x(WorkPoint point) { return x(point.x()); }
        int y(WorkPoint point) { return y(point.y()); }
        int sx(double value) { return Math.max(1, (int) Math.round(value * work.width() / 4095.0)); }
        int sy(double value) { return Math.max(1, (int) Math.round(value * work.height() / 4095.0)); }
        void line(WorkPoint a, WorkPoint b, int color, int thickness) {
            WorkbenchArt.line(g, x(a), y(a), x(b), y(b), color, thickness);
        }
    }

    private WorkbenchArt() {}
}
