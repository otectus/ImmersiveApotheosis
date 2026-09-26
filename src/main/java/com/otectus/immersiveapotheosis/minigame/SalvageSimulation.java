package com.otectus.immersiveapotheosis.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SalvageSimulation extends AbstractWorkSimulation {
    private static final int SEAM_SAMPLES = 60;

    private static final class Part {
        final WorkPoint center;
        final int radius;
        final List<WorkPoint> seam;
        final WorkPoint target;
        int seamSample;
        int seamDwell;
        double extraction;
        boolean grabbed;
        boolean removed;

        Part(WorkPoint center, int radius, List<WorkPoint> seam, WorkPoint target) {
            this.center = center;
            this.radius = radius;
            this.seam = seam;
            this.target = target;
        }
    }

    private final List<Part> parts = new ArrayList<>();
    private int active;
    private WorkStage stage = WorkStage.CUT_SEAMS;

    SalvageSimulation(WorkDefinition definition) {
        super(definition);
        Random random = new Random(definition.seed() ^ 0x51A6A6E5D3B4C291L ^ ((long) definition.tier() << 48));
        int count = 3 + definition.tier() * 3 / 5;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count + (random.nextDouble() - 0.5) * 0.12;
            int radius = 250 + random.nextInt(100);
            int cx = clampCoord(2048 + StrictMath.cos(angle) * 850);
            int cy = clampCoord(2048 + StrictMath.sin(angle) * 850);
            List<WorkPoint> seam = new ArrayList<>(SEAM_SAMPLES);
            for (int s = 0; s < SEAM_SAMPLES; s++) {
                double a = Math.PI * 2.0 * s / (SEAM_SAMPLES - 1);
                seam.add(new WorkPoint(clampCoord(cx + StrictMath.cos(a) * radius), clampCoord(cy + StrictMath.sin(a) * radius)));
            }
            double outward = StrictMath.atan2(cy - 2048.0, cx - 2048.0);
            WorkPoint target = new WorkPoint(clampCoord(cx + StrictMath.cos(outward) * 980), clampCoord(cy + StrictMath.sin(outward) * 980));
            parts.add(new Part(new WorkPoint(cx, cy), radius, List.copyOf(seam), target));
        }
    }

    @Override
    protected void advance(WorkInput input, boolean travelled) {
        if (stage == WorkStage.CUT_SEAMS) cut(input, travelled);
        else extract(input, travelled);
    }

    private void cut(WorkInput input, boolean travelled) {
        Part p = parts.get(active);
        WorkPoint next = p.seam.get(p.seamSample);
        if (input.primary()) {
            if (!travelled) {
                harm(0.008);
            } else if (distance(cursor, next) <= tolerance(105)) {
                int dwell = definition.tier() == 5 ? 4 : 5;
                if (++p.seamDwell >= dwell) {
                    p.seamDwell = 0;
                    p.seamSample++;
                    if (p.seamSample >= p.seam.size()) {
                        active++;
                        if (active >= parts.size()) {
                            stage = WorkStage.EXTRACT_COMPONENTS;
                            active = 0;
                        }
                    }
                }
            } else {
                harm(0.0015);
            }
        } else {
            p.seamDwell = 0;
        }
    }

    private void extract(WorkInput input, boolean travelled) {
        Part p = parts.get(active);
        if (!input.primary()) {
            p.grabbed = false;
            return;
        }
        WorkPoint expected = lerp(p.center, p.target, p.extraction);
        if (!p.grabbed) {
            if (distance(cursor, expected) <= tolerance(p.radius * 0.7)) p.grabbed = true;
            else harm(0.004);
            return;
        }
        if (!travelled) {
            harm(0.018);
            p.grabbed = false;
            return;
        }
        double total = distance(p.center, p.target);
        double projection = ((input.x() - p.center.x()) * (p.target.x() - p.center.x())
                + (input.y() - p.center.y()) * (p.target.y() - p.center.y())) / (total * total);
        if (projection + 0.035 < p.extraction || distance(cursor, lerp(p.center, p.target, clamp01(projection))) > tolerance(155)) {
            harm(0.012);
            p.extraction = Math.max(0.0, p.extraction - 0.01);
            return;
        }
        p.extraction = Math.min(1.0, Math.max(p.extraction, projection));
        if (p.extraction >= 0.995) {
            p.removed = true;
            p.grabbed = false;
            active++;
            if (active >= parts.size()) finish(100.0 * (1.0 - 0.72 * damage), true);
        }
    }

    private static WorkPoint lerp(WorkPoint a, WorkPoint b, double t) {
        return new WorkPoint(clampCoord(a.x() + (b.x() - a.x()) * t), clampCoord(a.y() + (b.y() - a.y()) * t));
    }

    @Override
    protected double timeoutScore() {
        long recovered = parts.stream().filter(p -> p.removed).count();
        return 100.0 * recovered / parts.size() * (1.0 - 0.72 * damage);
    }

    private double completion() {
        double sum = 0;
        for (Part p : parts) sum += 0.65 * p.seamSample / (double) p.seam.size() + 0.35 * p.extraction;
        return sum / parts.size();
    }

    @Override
    public WorkState state() {
        List<SalvageState.Component> view = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            view.add(new SalvageState.Component(i, p.center, p.radius, p.seam, p.target,
                    p.seamSample / (double) p.seam.size(), p.extraction, p.removed));
        }
        WorkStage shown = displayStage(stage);
        String guide = shown == WorkStage.CUT_SEAMS ? "performance.immersive_apotheosis.guide.salvage_cut"
                : shown == WorkStage.EXTRACT_COMPONENTS ? "performance.immersive_apotheosis.guide.salvage_extract"
                : shown == WorkStage.COMPLETE ? "performance.immersive_apotheosis.guide.complete"
                : "performance.immersive_apotheosis.guide.failed";
        return new SalvageState(Station.SALVAGING, elapsed, shown, guide, completion(), damage, cursor,
                Math.min(active, parts.size() - 1), view);
    }
}
