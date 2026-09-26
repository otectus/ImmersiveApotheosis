package com.otectus.immersiveapotheosis.minigame;

import java.util.ArrayList;
import java.util.List;

/** Deterministic reference gestures used by unit, GameTest, and real-input smoke drivers. */
public final class WorkAutoplay {
    private static final double MOVE = 50.0;

    public static List<WorkInput> perfect(WorkDefinition definition) {
        return generate(definition, false);
    }

    /** A completed reforge with deliberately weak hammer force; intended to land Weak or Frayed. */
    public static List<WorkInput> partialReforge(WorkDefinition definition) {
        if (definition.station() != Station.REFORGING) throw new IllegalArgumentException("reforge definition required");
        return generate(definition, true);
    }

    /** Compatibility name used by station integration tests; currently defined only for reforging. */
    public static List<WorkInput> partial(WorkDefinition definition) {
        return partialReforge(definition);
    }

    /** Applies one dense input per simulation step and returns the authoritative finished result. */
    public static WorkResult drive(WorkSimulation simulation, Iterable<WorkInput> inputs) {
        for (WorkInput input : inputs) {
            if (simulation.finished()) break;
            simulation.step(input);
        }
        if (!simulation.finished()) throw new IllegalArgumentException("input stream did not finish the work");
        return simulation.result();
    }

    /** One reactive reference step for real-input smoke drivers. */
    public static WorkInput next(WorkState state, WorkInput previous) {
        return next(state, previous, false);
    }

    /** One reactive reference step; partial reforge mode deliberately undercharges hammer strikes. */
    public static WorkInput next(WorkState state, WorkInput previous, boolean partialReforge) {
        Bot bot = new Bot(partialReforge);
        bot.last = previous == null ? WorkInput.IDLE : previous;
        return bot.next(state);
    }

    public static Driver driver() { return new Driver(false); }
    public static Driver partialReforgeDriver() { return new Driver(true); }

    /** Stateful reactive reference player for smoke tests that deliver one real input frame at a time. */
    public static final class Driver {
        private final boolean partialReforge;
        private WorkInput previous = WorkInput.IDLE;

        private Driver(boolean partialReforge) { this.partialReforge = partialReforge; }

        public WorkInput next(WorkState state) {
            previous = WorkAutoplay.next(state, previous, partialReforge);
            return previous;
        }

        public WorkInput previous() { return previous; }
    }

    private static List<WorkInput> generate(WorkDefinition definition, boolean partialReforge) {
        WorkSimulation simulation = WorkSimulation.create(definition);
        Bot bot = new Bot(partialReforge);
        while (!simulation.finished() && bot.inputs.size() < WorkDefinition.MAX_STEPS) {
            WorkInput next = bot.next(simulation.state());
            bot.inputs.add(next);
            bot.last = next;
            simulation.step(next);
        }
        if (!simulation.finished() || !simulation.result().complete()) {
            throw new IllegalStateException("reference gestures did not complete " + definition + " in " + bot.inputs.size()
                    + " steps; state=" + simulation.state());
        }
        return List.copyOf(bot.inputs);
    }

    private static final class Bot {
        final List<WorkInput> inputs = new ArrayList<>();
        final boolean partialReforge;
        WorkInput last = WorkInput.IDLE;

        Bot(boolean partialReforge) {
            this.partialReforge = partialReforge;
        }

        WorkInput next(WorkState state) {
            return switch (state.stage()) {
                case COUNT_IN -> WorkInput.IDLE;
                case CUT_SEAMS, EXTRACT_COMPONENTS -> salvage((SalvageState) state);
                case POSITION_GEM, BEND_PRONGS -> socket((SocketState) state);
                case ALIGN_FACETS, GRIND_FACETS, POLISH_FACETS -> cutting((CuttingState) state);
                case HEAT_BINDINGS, STRIKE_ANCHORS, TRACE_SEAL -> reforge((ReforgeState) state);
                case COMPLETE, FAILED -> WorkInput.IDLE;
            };
        }

        private WorkInput salvage(SalvageState state) {
            SalvageState.Component c = state.components().get(state.activeComponent());
            if (state.stage() == WorkStage.CUT_SEAMS) {
                int index = Math.min(c.seam().size() - 1, (int) Math.floor(c.seamProgress() * c.seam().size()));
                return touch(c.seam().get(index), WorkInput.PRIMARY);
            }
            if (c.extractionProgress() <= 1e-9 && distance(state.cursor(), c.center()) > 4) return move(c.center(), 0);
            if (!last.primary() && distance(state.cursor(), c.center()) <= 4) return at(c.center(), WorkInput.PRIMARY);
            double next = Math.min(1.0, c.extractionProgress() + 0.055);
            WorkPoint p = lerp(c.center(), c.extractionTarget(), next);
            return move(p, WorkInput.PRIMARY);
        }

        private WorkInput socket(SocketState state) {
            if (state.stage() == WorkStage.POSITION_GEM) {
                if (distance(state.gemCenter(), state.seat()) > 30) {
                    if (!last.primary() && distance(state.cursor(), state.gemCenter()) > 4) return move(state.gemCenter(), 0);
                    if (!last.primary()) return at(state.gemCenter(), WorkInput.PRIMARY);
                    return move(state.seat(), WorkInput.PRIMARY);
                }
                double diff = angleDifference(state.targetRotation(), state.gemRotation());
                if (Math.abs(diff) > 1.0) return rotateAround(state.seat(), diff, 470);
                return at(state.seat(), WorkInput.PRIMARY);
            }
            SocketState.Prong p = state.prongs().get(state.activeProng());
            if (p.pressure() >= 1.0) return at(p.target(), 0);
            if (p.pressure() <= 1e-9 && distance(state.cursor(), p.hinge()) > 340) return move(p.hinge(), 0);
            if (p.pressure() <= 1e-9 && !last.primary() && distance(state.cursor(), p.hinge()) > 4) return move(p.hinge(), 0);
            if (!last.primary()) return at(p.hinge(), WorkInput.PRIMARY);
            if (distance(state.cursor(), p.target()) > 4) return move(p.target(), WorkInput.PRIMARY);
            return at(p.target(), WorkInput.PRIMARY);
        }

        private WorkInput cutting(CuttingState state) {
            if (state.stage() == WorkStage.ALIGN_FACETS) {
                return rotateAround(state.gemCenter(), angleDifference(state.targetRotation(), state.gemRotation()), 560);
            }
            CuttingState.Facet f = state.facets().get(state.activeFacet());
            if (state.stage() == WorkStage.GRIND_FACETS) {
                double ratio = f.depth() / f.targetDepth();
                if (ratio >= 0.995) return at(f.contact(), 0);
                if (ratio > 0.78 && ratio < 0.89 && last.primary()) return at(f.contact(), 0);
                if (ratio < 0.90 && (state.heat() > 0.58 || (last.actions() == 0 && state.heat() > 0.18))) {
                    return at(f.contact(), 0);
                }
                return touch(f.contact(), WorkInput.PRIMARY);
            }
            int sector = Integer.numberOfTrailingZeros(~f.polishMask() & 0xFFF);
            sector = Math.min(11, sector);
            double angle = -Math.PI + Math.PI * 2.0 * (sector + 0.5) / 12.0;
            WorkPoint target = new WorkPoint(clamp(f.contact().x() + Math.cos(angle) * 100),
                    clamp(f.contact().y() + Math.sin(angle) * 100));
            return touch(target, WorkInput.SECONDARY);
        }

        private WorkInput reforge(ReforgeState state) {
            ReforgeState.Anchor a = state.anchors().get(state.activeAnchor());
            if (state.stage() == WorkStage.HEAT_BINDINGS) {
                if (a.heat() >= 0.69) return at(a.point(), 0);
                return touch(a.point(), WorkInput.SECONDARY);
            }
            if (state.stage() == WorkStage.STRIKE_ANCHORS) {
                double target = partialReforge ? 0.40 : a.targetForce();
                if (state.hammerCharge() <= 1e-9 && !last.primary()) {
                    if (last.secondary()) return at(a.point(), 0);
                    if (a.heat() < 0.69) return touch(a.point(), WorkInput.SECONDARY);
                }
                if (state.hammerCharge() + 0.004 >= target) return at(a.point(), 0);
                return touch(a.point(), WorkInput.PRIMARY);
            }
            int index = Math.min(state.sealPath().size() - 1,
                    (int) Math.floor(state.traceProgress() * state.sealPath().size()));
            return touch(state.sealPath().get(index), WorkInput.PRIMARY);
        }

        private WorkInput touch(WorkPoint target, int actions) {
            if (distance(new WorkPoint(last.x(), last.y()), target) > MOVE) return move(target, 0);
            return at(target, actions);
        }

        private WorkInput rotateAround(WorkPoint center, double remainingDegrees, int radius) {
            WorkPoint cursor = new WorkPoint(last.x(), last.y());
            double ring = distance(cursor, center);
            if (!last.secondary() || ring < radius - 35 || ring > radius + 35) {
                WorkPoint start = new WorkPoint(clamp(center.x() + radius), center.y());
                if (distance(cursor, start) > 4) return move(start, 0);
                return at(start, WorkInput.SECONDARY);
            }
            double current = Math.atan2(last.y() - center.y(), last.x() - center.x());
            double delta = Math.toRadians(Math.max(-5.0, Math.min(5.0, remainingDegrees)));
            WorkPoint next = new WorkPoint(clamp(center.x() + Math.cos(current + delta) * radius),
                    clamp(center.y() + Math.sin(current + delta) * radius));
            return at(next, WorkInput.SECONDARY);
        }

        private WorkInput move(WorkPoint target, int actions) {
            double dx = target.x() - last.x();
            double dy = target.y() - last.y();
            double d = Math.hypot(dx, dy);
            if (d <= MOVE) return at(target, actions);
            return WorkInput.normalized((int) Math.round(last.x() + dx * MOVE / d),
                    (int) Math.round(last.y() + dy * MOVE / d), actions);
        }

        private static WorkInput at(WorkPoint p, int actions) { return new WorkInput(p.x(), p.y(), actions); }
    }

    private static WorkPoint lerp(WorkPoint a, WorkPoint b, double t) {
        return new WorkPoint(clamp(a.x() + (b.x() - a.x()) * t), clamp(a.y() + (b.y() - a.y()) * t));
    }
    private static double distance(WorkPoint a, WorkPoint b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static double angleDifference(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
    private static int clamp(double v) { return (int) Math.round(Math.max(0, Math.min(4095, v))); }

    private WorkAutoplay() {}
}
