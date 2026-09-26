package com.otectus.immersiveapotheosis.session;

import net.minecraft.Util;

import java.util.function.LongSupplier;

/**
 * Milliseconds for session timing: real time, because the client's performance clock is real time. Automated tests
 * switch to game time, since a GameTest server runs ticks faster than real time.
 */
public final class SessionClock {
    private static volatile LongSupplier source = Util::getMillis;

    public static long now() {
        return source.getAsLong();
    }

    /** Automated tests only. {@code null} restores real time. */
    public static void useSource(LongSupplier clock) {
        source = clock == null ? Util::getMillis : clock;
    }

    private SessionClock() {}
}
