package com.otectus.immersiveapotheosis.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class CuttingSimulation extends AbstractWorkSimulation {
    private static final WorkPoint CENTER = new WorkPoint(2048, 2048);

    private static final class MutableFacet {
        final int angle;
        final double targetDepth;
        double depth;
        boolean locked;
        int polishMask;
        int polishSector = -1;
        int polishDwell;

        MutableFacet(int angle, double targetDepth) {
            this.angle = angle;
            this.targetDepth = targetDepth;
        }

        double polish() { return Integer.bitCount(polishMask) / 12.0; }
    }

    private final List<MutableFacet> facets = new ArrayList<>();
    private double rotation;
    private int targetRotation;
    private double heat;
    private int active;
    private boolean rotating;
    private double lastRotateAngle;
    private WorkStage stage = WorkStage.ALIGN_FACETS;

    CuttingSimulation(WorkDefinition definition) {
        super(definition);
        Random random = new Random(definition.seed() ^ 0xC0771A6F4CE75211L ^ ((long) definition.tier() << 43));
        int count = 4 + definition.tier() * 4 / 5;
        int offset = random.nextInt(360);
        for (int i = 0; i < count; i++) {
            int angle = Math.floorMod(offset + (int) Math.round(360.0 * i / count), 360);
            facets.add(new MutableFacet(angle, 0.92 + random.nextDouble() * 0.08));
        }
        rotation = random.nextInt(360);
        updateTarget();
    }

    @Override
    protected void advance(WorkInput input, boolean travelled) {
        if (!input.primary()) heat = Math.max(0.0, heat - 0.014 * definition.assistance());
        if (stage == WorkStage.ALIGN_FACETS) align(input, travelled);
        else if (stage == WorkStage.GRIND_FACETS) grind(input, travelled);
        else polish(input, travelled);
    }

    private void align(WorkInput input, boolean travelled) {
        double ring = distance(cursor, CENTER);
        if (input.secondary() && ring >= 320 && ring <= 900) {
            double angle = Math.toDegrees(StrictMath.atan2(input.y() - CENTER.y(), input.x() - CENTER.x()));
            if (!rotating || !previous.secondary()) {
                rotating = true;
                lastRotateAngle = angle;
            } else if (travelled) {
                rotation = normalize(rotation + angleDifference(angle, lastRotateAngle));
                lastRotateAngle = angle;
            }
        } else rotating = false;
        if (Math.abs(angleDifference(rotation, targetRotation)) <= tolerance(4.0)) stage = WorkStage.GRIND_FACETS;
    }

    private void grind(WorkInput input, boolean travelled) {
        MutableFacet f = facets.get(active);
        if (Math.abs(angleDifference(rotation, targetRotation)) > tolerance(8.0)) {
            stage = WorkStage.ALIGN_FACETS;
            return;
        }
        WorkPoint contact = contact(f);
        if (!input.primary() && previous.primary() && f.depth >= f.targetDepth * 0.90) {
            f.locked = true;
            active++;
            if (active >= facets.size()) {
                active = 0;
                stage = WorkStage.POLISH_FACETS;
            } else {
                updateTarget();
                stage = WorkStage.ALIGN_FACETS;
            }
            return;
        }
        if (input.primary()) {
            if (!travelled) {
                harm(0.012);
            } else if (distance(cursor, contact) <= tolerance(125)) {
                heat += 0.012;
                int grindSteps = definition.tier() == 5 ? 90 : 100;
                if (heat <= 0.76) f.depth += 1.0 / grindSteps;
                else {
                    f.depth += 1.0 / 180.0;
                    harm((heat - 0.76) * 0.008);
                }
                double overcut = f.depth - (f.targetDepth + 0.04 * definition.assistance());
                if (overcut > 0) harm(overcut * 0.012);
                if (heat > 0.94) harm(0.006);
            } else {
                harm(0.002);
            }
        }
    }

    private void polish(WorkInput input, boolean travelled) {
        MutableFacet f = facets.get(active);
        WorkPoint contact = contact(f);
        if (!input.secondary() || !travelled || distance(cursor, contact) > tolerance(230)) {
            f.polishSector = -1;
            f.polishDwell = 0;
            if (input.secondary()) harm(0.001);
            return;
        }
        double angle = StrictMath.atan2(input.y() - contact.y(), input.x() - contact.x());
        int sector = Math.floorMod((int) Math.floor((angle + Math.PI) / (Math.PI * 2.0) * 12.0), 12);
        if (sector != f.polishSector) {
            f.polishSector = sector;
            f.polishDwell = 1;
        } else if (++f.polishDwell >= (definition.tier() == 5 ? 4 : 5)) {
            f.polishMask |= 1 << sector;
            f.polishDwell = 0;
            if (f.polishMask == 0xFFF) {
                active++;
                if (active >= facets.size()) finish(finalScore(), true);
            }
        }
    }

    private void updateTarget() {
        targetRotation = Math.floorMod(360 - facets.get(active).angle, 360);
    }

    private double finalScore() {
        double depthAccuracy = facets.stream().mapToDouble(f -> clamp01(1.0 - Math.abs(f.depth / f.targetDepth - 1.0))).average().orElse(0);
        double finish = facets.stream().mapToDouble(MutableFacet::polish).average().orElse(0);
        double mean = facets.stream().mapToDouble(f -> f.depth / f.targetDepth).average().orElse(0);
        double variance = facets.stream().mapToDouble(f -> StrictMath.pow(f.depth / f.targetDepth - mean, 2)).average().orElse(1);
        double quality = clamp01(0.45 * depthAccuracy + 0.30 * (1.0 - Math.sqrt(variance)) + 0.25 * finish - 0.70 * damage);
        return quality >= 0.99 ? 100.0 : 100.0 * quality;
    }

    private double completion() {
        double depth = facets.stream().mapToDouble(f -> Math.min(1.0, f.depth / f.targetDepth)).average().orElse(0);
        double polish = facets.stream().mapToDouble(MutableFacet::polish).average().orElse(0);
        return 0.75 * depth + 0.25 * polish;
    }

    @Override protected double timeoutScore() { return Math.min(79.0, 60.0 * completion() * (1.0 - damage)); }

    @Override
    public WorkState state() {
        List<CuttingState.Facet> view = new ArrayList<>(facets.size());
        for (int i = 0; i < facets.size(); i++) {
            MutableFacet f = facets.get(i);
            view.add(new CuttingState.Facet(i, f.angle, contact(f), f.targetDepth, f.depth,
                    f.polish(), f.polishMask, f.locked));
        }
        WorkStage shown = displayStage(stage);
        String guide = shown == WorkStage.ALIGN_FACETS ? "performance.immersive_apotheosis.guide.cut_align"
                : shown == WorkStage.GRIND_FACETS ? "performance.immersive_apotheosis.guide.cut_grind"
                : shown == WorkStage.POLISH_FACETS ? "performance.immersive_apotheosis.guide.cut_polish"
                : shown == WorkStage.COMPLETE ? "performance.immersive_apotheosis.guide.complete"
                : "performance.immersive_apotheosis.guide.failed";
        return new CuttingState(Station.GEM_CUTTING, elapsed, shown, guide, completion(), damage, cursor, CENTER,
                (int) Math.round(normalize(rotation)), targetRotation, heat, Math.min(active, facets.size() - 1), view);
    }

    private static double normalize(double angle) {
        angle %= 360.0;
        return angle < 0 ? angle + 360.0 : angle;
    }

    private WorkPoint contact(MutableFacet facet) {
        double radians = Math.toRadians(facet.angle + rotation);
        return new WorkPoint(clampCoord(CENTER.x() + StrictMath.cos(radians) * 690),
                clampCoord(CENTER.y() + StrictMath.sin(radians) * 690));
    }
}
