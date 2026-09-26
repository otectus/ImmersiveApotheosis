package com.otectus.immersiveapotheosis.session;

import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import org.junit.jupiter.api.Test;

import static com.otectus.immersiveapotheosis.session.InputSequence.Admission.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSequenceTest {
    @Test
    void admitsContiguousFramesAndIgnoresWholeDuplicates() {
        InputSequence stream = new InputSequence();
        assertEquals(ACCEPT, stream.accept(0, 5, 100));
        assertEquals(DUPLICATE, stream.accept(0, 5, 200));
        assertEquals(5, stream.nextStep());
        assertEquals(ACCEPT, stream.accept(5, 5, 200));
        assertEquals(10, stream.nextStep());
    }

    @Test
    void neverPartiallyAcceptsGapsOverlapsOrFramesAheadOfTime() {
        InputSequence stream = new InputSequence();
        assertEquals(INVALID, stream.accept(1, 5, 100));
        assertEquals(INVALID, stream.accept(0, 64, 0));
        assertEquals(0, stream.nextStep());
        assertEquals(ACCEPT, stream.accept(0, 5, 100));
        assertEquals(INVALID, stream.accept(3, 5, 200));
        assertEquals(5, stream.nextStep());
        assertEquals(ACCEPT, stream.accept(5, 5, 200));
    }

    @Test
    void invalidBoundsCannotOverflowOrAdvanceTheStream() {
        InputSequence stream = new InputSequence();
        for (int first : new int[] {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, WorkDefinition.MAX_STEPS}) {
            assertEquals(INVALID, stream.accept(first, 1, 60_000));
        }
        for (int count : new int[] {-1, 0, 65, Integer.MAX_VALUE}) {
            assertEquals(INVALID, stream.accept(0, count, 60_000));
        }
        assertEquals(INVALID, stream.accept(0, 1, -500));
        assertEquals(0, stream.nextStep());
    }

    @Test
    void acceptsDelayedCatchupAndExactlyOneFullLengthSession() {
        InputSequence stream = new InputSequence();
        for (int step = 0; step < WorkDefinition.MAX_STEPS; step += 64) {
            int count = Math.min(64, WorkDefinition.MAX_STEPS - step);
            assertEquals(ACCEPT, stream.accept(step, count, 60_000));
        }
        assertEquals(WorkDefinition.MAX_STEPS, stream.nextStep());
        assertEquals(INVALID, stream.accept(WorkDefinition.MAX_STEPS, 1, 60_000));
    }
}
