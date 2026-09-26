package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The server's seed and display snapshots. These previews are never used to grant items. */
public record StartS2C(UUID session, int containerId, Station station, int tier, long seed,
                       double windowMultiplier, Component title, List<ItemStack> previews) {
    public static final int MAX_PREVIEWS = 12;

    public StartS2C {
        if (previews.size() > MAX_PREVIEWS) throw new IllegalArgumentException("Too many work previews");
        previews = previews.stream().map(ItemStack::copy).toList();
        new WorkDefinition(station, tier, seed, windowMultiplier);
    }

    public WorkDefinition definition() { return new WorkDefinition(station, tier, seed, windowMultiplier); }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
        buf.writeVarInt(containerId);
        buf.writeVarInt(station.ordinal());
        buf.writeVarInt(tier);
        buf.writeLong(seed);
        buf.writeDouble(windowMultiplier);
        buf.writeComponent(title);
        buf.writeVarInt(previews.size());
        for (ItemStack stack : previews) buf.writeItem(stack);
    }

    public static StartS2C decode(FriendlyByteBuf buf) {
        UUID session = buf.readUUID();
        int container = buf.readVarInt();
        int ordinal = buf.readVarInt();
        if (ordinal < 0 || ordinal >= Station.values().length) throw new DecoderException("Invalid station");
        int tier = buf.readVarInt();
        long seed = buf.readLong();
        double window = buf.readDouble();
        Component title = buf.readComponent();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PREVIEWS) throw new DecoderException("Invalid work preview count");
        List<ItemStack> previews = new ArrayList<>(count);
        for (int i = 0; i < count; i++) previews.add(buf.readItem());
        return new StartS2C(session, container, Station.values()[ordinal], tier, seed, window, title, previews);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.otectus.immersiveapotheosis.client.ClientPerformance.start(this));
        ctx.get().setPacketHandled(true);
    }
}
