package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Esc: free during the count-in, a failure after it. */
public record CancelC2S(UUID session) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
    }

    public static CancelC2S decode(FriendlyByteBuf buf) {
        return new CancelC2S(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var player = ctx.get().getSender();
        if (player != null) PerformanceSessions.cancel(player, session);
        ctx.get().setPacketHandled(true);
    }
}
