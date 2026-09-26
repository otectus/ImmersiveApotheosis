package com.otectus.immersiveapotheosis.minigame;

/**
 * Pure deterministic 20 ms station simulation. The client predicts with the same code the server judges with, so
 * transcendental functions use {@link StrictMath}: bit-identical on every JVM.
 */
public abstract class WorkSimulation {
    public static WorkSimulation create(WorkDefinition definition) {
        return switch (definition.station()) {
            case SALVAGING -> new SalvageSimulation(definition);
            case SOCKETING -> new SocketSimulation(definition);
            case GEM_CUTTING -> new CuttingSimulation(definition);
            case REFORGING -> new ReforgeSimulation(definition);
        };
    }

    public abstract WorkDefinition definition();
    public abstract int elapsedSteps();
    public abstract boolean finished();
    public abstract void step(WorkInput input);
    public abstract WorkResult result();
    public abstract WorkState state();
}
