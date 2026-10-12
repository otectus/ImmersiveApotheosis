package com.otectus.immersiveapotheosis.api;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.minigame.Station;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Public entry points for other mods. Register during mod construction or {@code FMLCommonSetupEvent}, or send an
 * IMC message to {@code immersive_apotheosis} whose method is {@link #IMC_ASSIST_SOURCE} and whose payload is a
 * {@code Supplier<IPerformanceAssistSource>}.
 */
public final class ImmersiveApotheosisAPI {
    public static final String IMC_ASSIST_SOURCE = "assist_source";

    /** The narrowest and widest assistance a performance accepts; the same bounds as {@code WorkDefinition}. */
    public static final double MIN_ASSISTANCE = 0.5;
    public static final double MAX_ASSISTANCE = 2.0;

    private static final List<IPerformanceAssistSource> ASSIST_SOURCES = new CopyOnWriteArrayList<>();

    public static void registerAssistSource(IPerformanceAssistSource source) {
        if (source != null) ASSIST_SOURCES.add(source);
    }

    /** Removes a source registered earlier. Meant for tests and for integrations that switch themselves off. */
    public static void unregisterAssistSource(IPerformanceAssistSource source) {
        ASSIST_SOURCES.remove(source);
    }

    public static List<IPerformanceAssistSource> assistSources() {
        return Collections.unmodifiableList(ASSIST_SOURCES);
    }

    /**
     * The combined assistance multiplier for {@code player}'s next performance at {@code station}: the product of
     * every registered source, each clamped to {@code 0.5..2.0}, and the product clamped to the same range. With no
     * sources this is exactly {@code 1.0}.
     */
    public static double assistance(ServerPlayer player, Station station) {
        double product = 1.0;
        for (IPerformanceAssistSource source : ASSIST_SOURCES) {
            double value;
            try {
                value = source.windowMultiplier(player, station);
            } catch (RuntimeException e) {
                ImmersiveApotheosis.LOGGER.error("Assist source {} failed for {}", source.getClass().getName(),
                        player.getGameProfile().getName(), e);
                continue;
            }
            if (!Double.isFinite(value)) continue;
            product *= clamp(value);
        }
        return clamp(product);
    }

    private static double clamp(double value) {
        return Math.max(MIN_ASSISTANCE, Math.min(MAX_ASSISTANCE, value));
    }

    /** Drains IMC messages. Called from the mod-bus {@link InterModProcessEvent}. */
    @SuppressWarnings("unchecked")
    public static void processIMC(InterModProcessEvent event) {
        InterModComms.getMessages(ImmersiveApotheosis.MOD_ID).forEach(msg -> {
            try {
                Object payload = ((Supplier<Object>) msg.messageSupplier()).get();
                if (IMC_ASSIST_SOURCE.equals(msg.method())) {
                    registerAssistSource((IPerformanceAssistSource) payload);
                } else {
                    ImmersiveApotheosis.LOGGER.warn("Unknown IMC method '{}' from {}", msg.method(), msg.senderModId());
                }
            } catch (ClassCastException e) {
                ImmersiveApotheosis.LOGGER.error("IMC message '{}' from {} has the wrong payload type", msg.method(), msg.senderModId());
            }
        });
    }

    private ImmersiveApotheosisAPI() {}
}
