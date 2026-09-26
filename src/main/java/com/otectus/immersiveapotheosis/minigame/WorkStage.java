package com.otectus.immersiveapotheosis.minigame;

/** Guided phases shared by the simulation and renderer. */
public enum WorkStage {
    COUNT_IN,
    CUT_SEAMS, EXTRACT_COMPONENTS,
    POSITION_GEM, BEND_PRONGS,
    ALIGN_FACETS, GRIND_FACETS, POLISH_FACETS,
    HEAT_BINDINGS, STRIKE_ANCHORS, TRACE_SEAL,
    COMPLETE, FAILED
}
