package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.minigame.OutcomeBand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The server's verdict: the band and score it judged (null band when nothing was performed, as for a free cancel or
 * a changed table), and what happened.
 */
public record ResolvedS2C(UUID session, @Nullable OutcomeBand band, double score, Component summary) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
        buf.writeVarInt(band == null ? -1 : band.ordinal());
        buf.writeDouble(score);
        buf.writeComponent(summary);
    }

    public static ResolvedS2C decode(FriendlyByteBuf buf) {
        UUID session = buf.readUUID();
        int band = buf.readVarInt();
        return new ResolvedS2C(session, band < 0 || band >= OutcomeBand.values().length ? null : OutcomeBand.values()[band],
                buf.readDouble(), buf.readComponent());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.otectus.immersiveapotheosis.client.ClientPerformance.resolved(this));
        ctx.get().setPacketHandled(true);
    }
}
