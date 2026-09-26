package com.otectus.immersiveapotheosis.minigame;

/** The four performed Apotheosis stations. */
public enum Station {
    REFORGING("reforging"),
    SALVAGING("salvaging"),
    SOCKETING("socketing"),
    GEM_CUTTING("gem_cutting");

    public final String id;

    Station(String id) {
        this.id = id;
    }

    public static Station byOrdinal(int ordinal) {
        Station[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REFORGING;
    }
}
