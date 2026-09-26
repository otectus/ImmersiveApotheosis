package com.otectus.immersiveapotheosis.minigame;

import java.util.Objects;

abstract class AbstractWorkSimulation extends WorkSimulation {
    protected final WorkDefinition definition;
    protected int elapsed;
    protected boolean finished;
    protected WorkResult result;
    protected WorkInput previous = WorkInput.IDLE;
    protected WorkPoint cursor = new WorkPoint(WorkInput.IDLE.x(), WorkInput.IDLE.y());
    protected double damage;

    AbstractWorkSimulation(WorkDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    @Override public final WorkDefinition definition() { return definition; }
    @Override public final int elapsedSteps() { return elapsed; }
    @Override public final boolean finished() { return finished; }

    @Override
    public final void step(WorkInput input) {
        Objects.requireNonNull(input, "input");
        if (finished) return;
        elapsed++;
        cursor = new WorkPoint(input.x(), input.y());
        advance(input, travelled(input));
        previous = input;
        if (!finished && elapsed >= WorkDefinition.MAX_STEPS) finish(timeoutScore(), false);
    }

    protected abstract void advance(WorkInput input, boolean travelled);

    protected boolean travelled(WorkInput input) {
        if (previous.actions() == 0 || input.actions() == 0) return true;
        double limit = 112.0 * definition.assistance();
        return distance(previous.x(), previous.y(), input.x(), input.y()) <= limit;
    }

    protected double tolerance(double base) { return base * definition.assistance(); }

    protected void harm(double amount) {
        damage = clamp01(damage + amount / definition.assistance());
    }

    protected void finish(double score, boolean complete) {
        finished = true;
        result = WorkResult.of(score, complete);
    }

    protected double timeoutScore() { return 0.0; }

    @Override
    public final WorkResult result() {
        if (!finished) throw new IllegalStateException("work is not finished");
        return result;
    }

    protected WorkStage displayStage(WorkStage active) {
        if (finished) return result.complete() ? WorkStage.COMPLETE : WorkStage.FAILED;
        return active;
    }

    protected static double distance(WorkPoint a, WorkPoint b) { return distance(a.x(), a.y(), b.x(), b.y()); }
    protected static double distance(int ax, int ay, int bx, int by) {
        return StrictMath.hypot(ax - bx, ay - by);
    }
    protected static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    protected static int clampCoord(double value) { return (int) Math.round(Math.max(0.0, Math.min(4095.0, value))); }
    protected static double angleDifference(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
