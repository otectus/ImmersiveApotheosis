package com.otectus.immersiveapotheosis.minigame;

/** Immutable read-only projection used by clients; simulations retain no mutable objects through it. */
public sealed interface WorkState permits SalvageState, SocketState, CuttingState, ReforgeState {
    Station station();
    int elapsedSteps();
    WorkStage stage();
    String guidance();
    double progress();
    double damage();
    WorkPoint cursor();
}
