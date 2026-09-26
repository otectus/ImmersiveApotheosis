package com.otectus.immersiveapotheosis.session;

import com.otectus.immersiveapotheosis.minigame.WorkDefinition;

/** Pure, bounded stream admission. A batch is accepted whole, ignored whole, or rejected whole. */
public final class InputSequence {
    public static final int MAX_BATCH_STEPS = 64;
    public static final int EARLY_LEEWAY_MS = 400;
    public enum Admission { ACCEPT, DUPLICATE, INVALID }

    private int nextStep;

    public int nextStep() { return nextStep; }

    /** elapsedMs is relative to the end of the server's count-in. */
    public Admission accept(int firstStep, int count, long elapsedMs) {
        if (count < 1 || count > MAX_BATCH_STEPS || firstStep < 0
                || firstStep > WorkDefinition.MAX_STEPS - count) return Admission.INVALID;
        int end = firstStep + count;
        if (end <= nextStep) return Admission.DUPLICATE;
        if (firstStep != nextStep || elapsedMs < -EARLY_LEEWAY_MS
                || (long) end * WorkDefinition.STEP_MS > elapsedMs + EARLY_LEEWAY_MS) return Admission.INVALID;
        nextStep = end;
        return Admission.ACCEPT;
    }
}
