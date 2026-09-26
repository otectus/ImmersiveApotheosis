package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;

/**
 * Clients report the slot they clicked, pointer/tool samples and cancellations; the server owns the seed,
 * simulation and result. Both sides must run the same simulation version.
 */
public final class ModNetwork {
    public static final String VERSION = "2." + WorkDefinition.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveApotheosis.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** Test hook: receives every server-to-client message sent to a fake player (automated tests only). */
    public static volatile BiConsumer<ServerPlayer, Object> fakePlayerSink;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SlotRequestC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlotRequestC2S::encode).decoder(SlotRequestC2S::decode).consumerMainThread(SlotRequestC2S::handle).add();
        CHANNEL.messageBuilder(InputsC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(InputsC2S::encode).decoder(InputsC2S::decode).consumerMainThread(InputsC2S::handle).add();
        CHANNEL.messageBuilder(CancelC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelC2S::encode).decoder(CancelC2S::decode).consumerMainThread(CancelC2S::handle).add();
        CHANNEL.messageBuilder(StartS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartS2C::encode).decoder(StartS2C::decode).consumerMainThread(StartS2C::handle).add();
        CHANNEL.messageBuilder(ResolvedS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResolvedS2C::encode).decoder(ResolvedS2C::decode).consumerMainThread(ResolvedS2C::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        if (player instanceof FakePlayer) {
            BiConsumer<ServerPlayer, Object> sink = fakePlayerSink;
            if (sink != null) sink.accept(player, message);
            return;
        }
        if (player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private ModNetwork() {}
}
