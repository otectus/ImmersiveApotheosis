package com.otectus.immersiveapotheosis.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class ReforgeSimulation extends AbstractWorkSimulation {
    private static final int TRACE_SAMPLES = 180;
    static final double HEAT_LOW = 0.62;
    static final double HEAT_HIGH = 0.78;
    static final double FORCE_TOLERANCE = 0.08;

    private static final class MutableAnchor {
        final WorkPoint point;
        final double targetForce;
        double heat;
        double alignment;
        boolean heatLocked;
        boolean struck;

        MutableAnchor(WorkPoint point, double targetForce) {
            this.point = point;
            this.targetForce = targetForce;
        }
    }

    private final List<MutableAnchor> anchors = new ArrayList<>();
    private final List<WorkPoint> sealPath;
    private int active;
    private double hammerCharge;
    private boolean charging;
    private int traceSample;
    private int traceDwell;
    private WorkStage stage = WorkStage.HEAT_BINDINGS;

    ReforgeSimulation(WorkDefinition definition) {
        super(definition);
        Random random = new Random(definition.seed() ^ 0xAEF04E6A7B1D3911L ^ ((long) definition.tier() << 42));
        int count = 3 + definition.tier() * 3 / 5;
        int offset = random.nextInt(360);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(offset + 360.0 * i / count);
            double radius = 720 + random.nextInt(360);
            WorkPoint p = new WorkPoint(clampCoord(2048 + StrictMath.cos(angle) * radius),
                    clampCoord(2048 + StrictMath.sin(angle) * radius));
            anchors.add(new MutableAnchor(p, 0.62 + random.nextDouble() * 0.22));
        }
        sealPath = generateSealPath();
    }

    @Override
    protected void advance(WorkInput input, boolean travelled) {
        if (stage == WorkStage.HEAT_BINDINGS) heat(input, travelled);
        else if (stage == WorkStage.STRIKE_ANCHORS) strike(input, travelled);
        else trace(input, travelled);
    }

    private void heat(WorkInput input, boolean travelled) {
        MutableAnchor a = anchors.get(active);
        for (int i = 0; i < anchors.size(); i++) {
            if (i != active) anchors.get(i).heat = Math.max(0.0, anchors.get(i).heat - 0.00020);
        }
        if (input.secondary() && travelled && distance(cursor, a.point) <= tolerance(145)) {
            a.heat += 1.0 / 112.0 / definition.assistance();
            if (a.heat > HEAT_HIGH) harm((a.heat - HEAT_HIGH) * 0.006);
        } else if (input.secondary()) {
            harm(0.0015);
        } else {
            a.heat = Math.max(0.0, a.heat - 0.004);
            if (previous.secondary() && a.heat >= 0.50) {
                a.heatLocked = true;
                if (a.heat < HEAT_LOW || a.heat > HEAT_HIGH) harm(Math.abs(a.heat - 0.70) * 0.08);
                active++;
                if (active >= anchors.size()) {
                    active = 0;
                    stage = WorkStage.STRIKE_ANCHORS;
                }
            }
        }
    }

    private void strike(WorkInput input, boolean travelled) {
        MutableAnchor a = anchors.get(active);
        if (input.secondary()) {
            if (travelled && distance(cursor, a.point) <= tolerance(145)) {
                a.heat += 1.0 / 112.0 / definition.assistance();
                if (a.heat > HEAT_HIGH) harm((a.heat - HEAT_HIGH) * 0.006);
            }
            return;
        }
        a.heat = Math.max(0.0, a.heat - 0.0002);
        if (input.primary()) {
            if (!charging && !previous.primary() && distance(cursor, a.point) <= tolerance(150)) charging = true;
            if (charging) {
                if (!travelled || distance(cursor, a.point) > tolerance(180)) {
                    harm(0.008);
                    charging = false;
                    hammerCharge = 0;
                } else {
                    hammerCharge += 1.0 / 125.0;
                    if (hammerCharge > 1.05) harm(0.003);
                }
            }
        } else if (charging) {
            charging = false;
            if (hammerCharge >= 0.35) {
                double forceAccuracy = clamp01(1.0 - Math.abs(hammerCharge - a.targetForce) / 0.55);
                double heatAccuracy = clamp01(1.0 - Math.abs(a.heat - 0.70) / 0.50);
                a.alignment = 0.8 * forceAccuracy + 0.2 * heatAccuracy;
                a.struck = true;
                if (hammerCharge > a.targetForce + 0.15) harm((hammerCharge - a.targetForce - 0.15) * 0.15);
                hammerCharge = 0;
                active++;
                if (active >= anchors.size()) {
                    active = 0;
                    stage = WorkStage.TRACE_SEAL;
                }
            } else {
                hammerCharge = 0;
            }
        }
    }

    private void trace(WorkInput input, boolean travelled) {
        WorkPoint target = sealPath.get(traceSample);
        if (input.primary() && travelled && distance(cursor, target) <= tolerance(100)) {
            if (++traceDwell >= 2) {
                traceDwell = 0;
                traceSample++;
                if (traceSample >= sealPath.size()) finish(finalScore(), true);
            }
        } else {
            traceDwell = 0;
            if (input.primary()) harm(0.0025);
        }
    }

    private double finalScore() {
        double alignment = anchors.stream().mapToDouble(a -> a.alignment).average().orElse(0);
        double heatQuality = anchors.stream().mapToDouble(a -> clamp01(1.0 - Math.abs(a.heat - 0.70) / 0.70)).average().orElse(0);
        double quality = clamp01(0.68 * alignment + 0.22 + 0.10 * heatQuality - 0.72 * damage);
        return quality >= 0.95 && damage == 0.0 ? 100.0 : 100.0 * quality;
    }

    private double completion() {
        if (stage == WorkStage.HEAT_BINDINGS) {
            return 0.25 * anchors.stream().mapToDouble(a -> Math.min(1.0, a.heat / 0.68)).sum() / anchors.size();
        }
        double struck = anchors.stream().filter(a -> a.struck).count() / (double) anchors.size();
        double trace = traceSample / (double) sealPath.size();
        return 0.25 + 0.45 * struck + 0.30 * trace;
    }

    @Override protected double timeoutScore() { return Math.min(64.0, 45.0 * completion() * (1.0 - damage)); }

    @Override
    public WorkState state() {
        List<ReforgeState.Anchor> view = new ArrayList<>(anchors.size());
        for (int i = 0; i < anchors.size(); i++) {
            MutableAnchor a = anchors.get(i);
            view.add(new ReforgeState.Anchor(i, a.point, a.targetForce, a.heat, a.alignment, a.heatLocked, a.struck));
        }
        WorkStage shown = displayStage(stage);
        String guide = shown == WorkStage.HEAT_BINDINGS ? "performance.immersive_apotheosis.guide.reforge_heat"
                : shown == WorkStage.STRIKE_ANCHORS ? "performance.immersive_apotheosis.guide.reforge_strike"
                : shown == WorkStage.TRACE_SEAL ? "performance.immersive_apotheosis.guide.reforge_trace"
                : shown == WorkStage.COMPLETE ? "performance.immersive_apotheosis.guide.complete"
                : "performance.immersive_apotheosis.guide.failed";
        double activeHeat = anchors.get(Math.min(active, anchors.size() - 1)).heat;
        return new ReforgeState(Station.REFORGING, elapsed, shown, guide, completion(), damage, cursor,
                activeHeat, HEAT_LOW, HEAT_HIGH, FORCE_TOLERANCE, hammerCharge, traceSample / (double) sealPath.size(),
                Math.min(active, anchors.size() - 1), view, sealPath);
    }

    private List<WorkPoint> generateSealPath() {
        List<WorkPoint> path = new ArrayList<>(TRACE_SAMPLES);
        for (int sample = 0; sample < TRACE_SAMPLES; sample++) {
            double overall = sample / (double) (TRACE_SAMPLES - 1) * anchors.size();
            int edge = Math.min(anchors.size() - 1, (int) overall);
            double t = overall - edge;
            MutableAnchor a = anchors.get(edge);
            MutableAnchor b = anchors.get((edge + 1) % anchors.size());
            path.add(new WorkPoint(clampCoord(a.point.x() + (b.point.x() - a.point.x()) * t),
                    clampCoord(a.point.y() + (b.point.y() - a.point.y()) * t)));
        }
        return List.copyOf(path);
    }
}
