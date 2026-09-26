package com.otectus.immersiveapotheosis.minigame;

/** Pointer position and held action buttons for one 20 ms simulation step. */
public record WorkInput(int x, int y, int actions) {
    public static final int MIN_COORD = 0;
    public static final int MAX_COORD = 4095;
    public static final int PRIMARY = 1;
    public static final int SECONDARY = 2;
    public static final int ACTION_MASK = PRIMARY | SECONDARY;
    public static final WorkInput IDLE = new WorkInput(2048, 2048, 0);

    public WorkInput {
        if (x < MIN_COORD || x > MAX_COORD || y < MIN_COORD || y > MAX_COORD) {
            throw new IllegalArgumentException("coordinates must be 0..4095");
        }
        if ((actions & ~ACTION_MASK) != 0) throw new IllegalArgumentException("unknown action flags");
    }

    public static WorkInput normalized(int x, int y, int actions) {
        return new WorkInput(clamp(x), clamp(y), actions & ACTION_MASK);
    }

    public boolean primary() { return (actions & PRIMARY) != 0; }
    public boolean secondary() { return (actions & SECONDARY) != 0; }

    private static int clamp(int value) {
        return Math.max(MIN_COORD, Math.min(MAX_COORD, value));
    }
}
