package com.otectus.immersiveapotheosis.session;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.OutcomeBand;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkResult;
import com.otectus.immersiveapotheosis.minigame.WorkSimulation;
import com.otectus.immersiveapotheosis.network.ModNetwork;
import com.otectus.immersiveapotheosis.network.ResolvedS2C;
import com.otectus.immersiveapotheosis.network.StartS2C;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One authoritative work simulation per player. Only station contexts may apply item and cost changes. */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID)
public final class PerformanceSessions {
    public interface Context {
        Station station();
        int tier();
        Component title();
        List<ItemStack> previews();
        boolean stillValid(ServerPlayer player, AbstractContainerMenu menu);
        Component resolve(ServerPlayer player, AbstractContainerMenu menu, WorkResult result);
        Component abandon(ServerPlayer player, AbstractContainerMenu menu);
    }

    public record Session(UUID id, ServerPlayer player, AbstractContainerMenu menu, Context context,
                          WorkSimulation simulation, InputSequence sequence, long startMs) {
        public long countInEndMs() {
            return startMs + (long) WorkDefinition.COUNT_IN_STEPS * WorkDefinition.STEP_MS;
        }

        public long deadlineMs() {
            return countInEndMs() + (long) WorkDefinition.MAX_STEPS * WorkDefinition.STEP_MS
                    + ServerConfig.get(ServerConfig.SESSION_GRACE_SECONDS) * 1000L;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static Optional<Session> get(ServerPlayer player) {
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    public static boolean locks(ServerPlayer player, AbstractContainerMenu menu) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.menu() == menu;
    }

    public static void start(ServerPlayer player, AbstractContainerMenu menu, Context context) {
        if (SESSIONS.containsKey(player.getUUID()) || player.containerMenu != menu || !menu.stillValid(player)
                || !context.stillValid(player, menu)) return;
        long seed = player.getRandom().nextLong();
        double window = ServerConfig.get(ServerConfig.TIMING_WINDOW_MULTIPLIER);
        WorkDefinition definition = new WorkDefinition(context.station(), context.tier(), seed, window);
        Session session = new Session(UUID.randomUUID(), player, menu, context, WorkSimulation.create(definition),
                new InputSequence(), SessionClock.now());
        SESSIONS.put(player.getUUID(), session);
        ModNetwork.sendTo(player, new StartS2C(session.id(), menu.containerId, context.station(), context.tier(),
                seed, window, context.title(), context.previews()));
    }

    /** Admit and replay a whole bounded batch. The server, never a client finish flag, decides completion. */
    public static void receive(ServerPlayer player, UUID id, int firstStep, List<WorkInput> frames) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id().equals(id)) return;
        long now = SessionClock.now();
        if (!itemsUnchanged(player, session)) {
            changed(player, session);
            return;
        }
        if (!session.menu().stillValid(player) || now > session.deadlineMs()) {
            abandon(player, session);
            return;
        }
        InputSequence.Admission admission = session.sequence().accept(firstStep, frames.size(), now - session.countInEndMs());
        if (admission == InputSequence.Admission.DUPLICATE) return;
        if (admission == InputSequence.Admission.INVALID) {
            // Invalid streams cannot turn an already committed operation into a free retry.
            cancel(player, id);
            return;
        }
        for (WorkInput frame : frames) {
            session.simulation().step(frame);
            if (session.simulation().finished()) {
                resolve(player, session);
                return;
            }
        }
    }

    private static void resolve(ServerPlayer player, Session session) {
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        if (!itemsUnchanged(player, session) || !session.menu().stillValid(player)) {
            send(player, session, null, 0, Component.translatable("message.immersive_apotheosis.performance.changed"));
            return;
        }
        WorkResult result = session.simulation().result();
        Component summary;
        try {
            summary = session.context().resolve(player, session.menu(), result);
        } catch (RuntimeException e) {
            ImmersiveApotheosis.LOGGER.error("Resolving a {} performance failed", session.context().station().id, e);
            summary = Component.translatable("message.immersive_apotheosis.performance.changed");
        }
        session.menu().broadcastChanges();
        send(player, session, result.band(), result.score(), summary);
    }

    public static void cancel(ServerPlayer player, UUID id) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id().equals(id)) return;
        if (SessionClock.now() < session.countInEndMs()) {
            SESSIONS.remove(player.getUUID());
            send(player, session, null, 0, Component.translatable("message.immersive_apotheosis.performance.cancelled"));
        } else {
            abandon(player, session);
        }
    }

    /** Called before menu removal refunds its inputs, including block destruction and disconnect. */
    public static void onMenuRemoved(ServerPlayer player, AbstractContainerMenu menu) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.menu() == menu) cancel(player, session.id());
    }

    private static void abandon(ServerPlayer player, Session session) {
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        if (!itemsUnchanged(player, session)) {
            send(player, session, null, 0, Component.translatable("message.immersive_apotheosis.performance.changed"));
            return;
        }
        Component summary;
        try {
            // Do not require stillValid: a broken table still holds its captured inputs at removed()'s HEAD.
            summary = session.context().abandon(player, session.menu());
        } catch (RuntimeException e) {
            ImmersiveApotheosis.LOGGER.error("Charging an abandoned {} performance failed", session.context().station().id, e);
            summary = Component.translatable("message.immersive_apotheosis.performance.abandoned");
        }
        session.menu().broadcastChanges();
        send(player, session, OutcomeBand.FAILED, 0, summary);
    }

    private static boolean itemsUnchanged(ServerPlayer player, Session session) {
        return player.containerMenu == session.menu() && session.context().stillValid(player, session.menu());
    }

    private static void changed(ServerPlayer player, Session session) {
        if (SESSIONS.remove(player.getUUID(), session)) {
            send(player, session, null, 0, Component.translatable("message.immersive_apotheosis.performance.changed"));
        }
    }

    private static void send(ServerPlayer player, Session session, @Nullable OutcomeBand band, double score, Component summary) {
        ModNetwork.sendTo(player, new ResolvedS2C(session.id(), band, score, summary));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        long now = SessionClock.now();
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (!itemsUnchanged(session.player(), session)) changed(session.player(), session);
            else if (!session.menu().stillValid(session.player()) || now > session.deadlineMs()) {
                cancel(session.player(), session.id());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) cancel(player, session.id());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        SessionClock.useSource(null);
    }

    private PerformanceSessions() {}
}
