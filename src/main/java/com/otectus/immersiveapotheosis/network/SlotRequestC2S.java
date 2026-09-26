package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.station.StationSlots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The client clicked a performed result slot (a reforging choice, a socketing result) and did not take it itself. */
public record SlotRequestC2S(int containerId, int slotId, int button, int clickType) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(slotId);
        buf.writeVarInt(button);
        buf.writeVarInt(clickType);
    }

    public static SlotRequestC2S decode(FriendlyByteBuf buf) {
        return new SlotRequestC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var player = ctx.get().getSender();
        ClickType[] types = ClickType.values();
        if (player != null && clickType >= 0 && clickType < types.length) {
            StationSlots.onRequest(player, containerId, slotId, button, types[clickType]);
        }
        ctx.get().setPacketHandled(true);
    }
}
