package com.otectus.immersiveapotheosis.network;

import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.session.InputSequence;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Contiguous 20 ms pointer/tool samples. No client-supplied result or item ever crosses this boundary. */
public record InputsC2S(UUID session, int firstStep, List<WorkInput> frames) {
    public static final int MAX_FRAMES = InputSequence.MAX_BATCH_STEPS;

    public InputsC2S {
        Objects.requireNonNull(session);
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > MAX_FRAMES || firstStep < 0
                || firstStep > WorkDefinition.MAX_STEPS - frames.size()) {
            throw new IllegalArgumentException("Invalid work input batch");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
        buf.writeVarInt(firstStep);
        buf.writeVarInt(frames.size());
        for (WorkInput frame : frames) {
            buf.writeShort(frame.x());
            buf.writeShort(frame.y());
            buf.writeByte(frame.actions());
        }
    }

    public static InputsC2S decode(FriendlyByteBuf buf) {
        UUID session = buf.readUUID();
        int first = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 1 || count > MAX_FRAMES || first < 0 || first > WorkDefinition.MAX_STEPS - count) {
            throw new DecoderException("Invalid work input batch bounds");
        }
        List<WorkInput> frames = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                frames.add(new WorkInput(buf.readUnsignedShort(), buf.readUnsignedShort(), buf.readUnsignedByte()));
            }
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid work pointer or action", e);
        }
        return new InputsC2S(session, first, frames);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var player = ctx.get().getSender();
        if (player != null) PerformanceSessions.receive(player, session, firstStep, frames);
        ctx.get().setPacketHandled(true);
    }
}
