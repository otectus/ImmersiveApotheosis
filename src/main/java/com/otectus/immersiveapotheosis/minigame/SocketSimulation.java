package com.otectus.immersiveapotheosis.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SocketSimulation extends AbstractWorkSimulation {
    private static final double PRESSURE_CENTER = 1.04;
    private static final class MutableProng {
        final WorkPoint hinge;
        final WorkPoint target;
        final int targetSteps;
        double bend;
        double pressure;
        boolean grabbed;

        MutableProng(WorkPoint hinge, WorkPoint target, int targetSteps) {
            this.hinge = hinge;
            this.target = target;
            this.targetSteps = targetSteps;
        }
    }

    private final WorkPoint seat = new WorkPoint(2048, 2048);
    private final List<MutableProng> prongs = new ArrayList<>();
    private WorkPoint gemCenter;
    private double gemRotation;
    private final int targetRotation;
    private double seating;
    private double tiltX;
    private double tiltY;
    private boolean gemGrabbed;
    private boolean rotating;
    private double lastRotateAngle;
    private final int[] bendOrder;
    private final double pressureLow;
    private final double pressureHigh;
    private int orderIndex;
    private WorkStage stage = WorkStage.POSITION_GEM;

    SocketSimulation(WorkDefinition definition) {
        super(definition);
        Random random = new Random(definition.seed() ^ 0x50C7E7A6C45D2121L ^ ((long) definition.tier() << 44));
        gemCenter = new WorkPoint(650 + random.nextInt(350), 2700 + random.nextInt(500));
        gemRotation = random.nextInt(360);
        targetRotation = random.nextInt(360);
        int count = definition.tier() >= 3 ? 6 : 4;
        pressureLow = PRESSURE_CENTER - 0.08 * definition.assistance();
        pressureHigh = PRESSURE_CENTER + 0.08 * definition.assistance();
        bendOrder = count == 4 ? new int[]{0, 2, 1, 3} : new int[]{0, 3, 1, 4, 2, 5};
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            WorkPoint hinge = point(seat, angle, 650);
            WorkPoint target = point(seat, angle, 330);
            prongs.add(new MutableProng(hinge, target, 190 + random.nextInt(41)));
        }
    }

    @Override
    protected void advance(WorkInput input, boolean travelled) {
        if (stage == WorkStage.POSITION_GEM) position(input, travelled);
        else bend(input, travelled);
    }

    private void position(WorkInput input, boolean travelled) {
        if (input.primary()) {
            rotating = false;
            if (!gemGrabbed && !previous.primary() && distance(cursor, gemCenter) <= tolerance(320)) gemGrabbed = true;
            if (gemGrabbed) {
                if (travelled) gemCenter = cursor;
                else { harm(0.012); gemGrabbed = false; }
            }
        } else {
            gemGrabbed = false;
        }

        double ring = distance(cursor, gemCenter);
        if (input.secondary() && ring >= 230 && ring <= 620) {
            double a = Math.toDegrees(StrictMath.atan2(input.y() - gemCenter.y(), input.x() - gemCenter.x()));
            if (!rotating || !previous.secondary()) {
                rotating = true;
                lastRotateAngle = a;
            } else if (travelled) {
                gemRotation = normalizeDegrees(gemRotation + angleDifference(a, lastRotateAngle));
                lastRotateAngle = a;
            }
        } else {
            rotating = false;
        }

        boolean centered = distance(gemCenter, seat) <= tolerance(85);
        boolean aligned = Math.abs(angleDifference(gemRotation, targetRotation)) <= tolerance(5.0);
        if (centered && aligned && input.primary() && distance(cursor, seat) <= tolerance(120)) {
            seating = Math.min(1.0, seating + 1.0 / 120.0);
        } else if (!centered || !aligned) {
            seating = Math.max(0.0, seating - 1.0 / 80.0);
        }
        if (seating >= 0.999) {
            gemCenter = seat;
            stage = WorkStage.BEND_PRONGS;
        }
    }

    private void bend(WorkInput input, boolean travelled) {
        MutableProng p = prongs.get(bendOrder[orderIndex]);
        if (input.primary()) {
            if (!p.grabbed && !previous.primary() && distance(cursor, p.hinge) <= tolerance(125)) p.grabbed = true;
            if (!p.grabbed) return;
            if (!travelled) {
                harm(0.015);
                p.grabbed = false;
            } else if (distance(cursor, p.target) <= tolerance(115)) {
                p.pressure += 1.0 / p.targetSteps;
                p.bend = Math.max(p.bend, Math.min(1.0, p.pressure));
                updateTilt();
                if (p.pressure > pressureHigh + 0.06) harm(0.004);
            } else if (distance(cursor, p.hinge) > tolerance(470)) {
                harm(0.004);
            }
        } else if (p.grabbed) {
            p.grabbed = false;
            if (p.pressure >= pressureLow) {
                if (p.pressure > pressureHigh) harm((p.pressure - pressureHigh) * 0.25);
                orderIndex++;
                updateTilt();
                if (orderIndex >= prongs.size()) {
                    double pressureError = prongs.stream().mapToDouble(q -> Math.abs(q.pressure - PRESSURE_CENTER)).average().orElse(1.0);
                    double imbalance = StrictMath.hypot(tiltX, tiltY);
                    double quality = clamp01(1.0 - damage * 0.65 - pressureError * 0.32 - imbalance * 0.25);
                    finish(quality >= 0.94 ? 100.0 : 100.0 * quality, true);
                }
            }
        }
    }

    private void updateTilt() {
        double x = 0, y = 0;
        for (int i = 0; i < prongs.size(); i++) {
            double angle = Math.PI * 2.0 * i / prongs.size();
            x += StrictMath.cos(angle) * prongs.get(i).pressure;
            y += StrictMath.sin(angle) * prongs.get(i).pressure;
        }
        tiltX = x / prongs.size();
        tiltY = y / prongs.size();
    }

    private double completion() {
        if (stage == WorkStage.POSITION_GEM) {
            double centered = 1.0 - Math.min(1.0, distance(gemCenter, seat) / 1600.0);
            double aligned = 1.0 - Math.min(1.0, Math.abs(angleDifference(gemRotation, targetRotation)) / 180.0);
            return 0.2 * centered + 0.1 * aligned + 0.1 * seating;
        }
        return 0.4 + 0.6 * prongs.stream().mapToDouble(p -> p.bend).sum() / prongs.size();
    }

    @Override protected double timeoutScore() { return 25.0 * completion() * (1.0 - damage); }

    @Override
    public WorkState state() {
        List<SocketState.Prong> view = new ArrayList<>(prongs.size());
        for (int i = 0; i < prongs.size(); i++) {
            MutableProng p = prongs.get(i);
            view.add(new SocketState.Prong(i, p.hinge, p.target, p.bend, p.pressure));
        }
        WorkStage shown = displayStage(stage);
        String guide = shown == WorkStage.POSITION_GEM ? "performance.immersive_apotheosis.guide.socket_position"
                : shown == WorkStage.BEND_PRONGS ? "performance.immersive_apotheosis.guide.socket_prongs"
                : shown == WorkStage.COMPLETE ? "performance.immersive_apotheosis.guide.complete"
                : "performance.immersive_apotheosis.guide.failed";
        return new SocketState(Station.SOCKETING, elapsed, shown, guide, completion(), damage, cursor, seat,
                gemCenter, (int) Math.round(normalizeDegrees(gemRotation)), targetRotation, seating, tiltX, tiltY,
                pressureLow, pressureHigh, bendOrder[Math.min(orderIndex, bendOrder.length - 1)], view);
    }

    private static double normalizeDegrees(double angle) {
        angle %= 360.0;
        return angle < 0 ? angle + 360.0 : angle;
    }

    private static WorkPoint point(WorkPoint center, double angle, double radius) {
        return new WorkPoint(clampCoord(center.x() + StrictMath.cos(angle) * radius), clampCoord(center.y() + StrictMath.sin(angle) * radius));
    }
}
