package com.otectus.immersiveapotheosis.bridge;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the bridge for each installed sister mod. Every bridge lives in its own package and is reached only by class
 * name after a {@link ModList} check, so no class here links against a mod that is absent. A bridge that fails to
 * initialise is disabled with one warning; the others carry on.
 */
public final class BridgeBootstrap {
    public enum Status { ABSENT, ACTIVE, FAILED }

    private static final Map<String, Status> STATUS = new LinkedHashMap<>();

    public static void commonSetup() {
        load("immersive_smithing", "com.otectus.immersiveapotheosis.bridge.smithing.SmithingBridge");
        load("immersive_tanning", "com.otectus.immersiveapotheosis.bridge.tanning.TanningBridge");
        load("immersive_jewelry", "com.otectus.immersiveapotheosis.bridge.jewelry.JewelryBridge");
    }

    private static void load(String modId, String entryClass) {
        if (!ModList.get().isLoaded(modId)) {
            STATUS.put(modId, Status.ABSENT);
            return;
        }
        try {
            Class.forName(entryClass).getMethod("init").invoke(null);
            STATUS.put(modId, Status.ACTIVE);
            ImmersiveApotheosis.LOGGER.info("Immersive Apotheosis bridge for {} enabled", modId);
        } catch (Throwable t) {
            STATUS.put(modId, Status.FAILED);
            ImmersiveApotheosis.LOGGER.warn("Immersive Apotheosis bridge for {} failed to initialize and is disabled", modId, t);
        }
    }

    public static Map<String, Status> status() {
        return Map.copyOf(STATUS);
    }

    public static boolean active(String modId) {
        return STATUS.get(modId) == Status.ACTIVE;
    }

    private BridgeBootstrap() {}
}
