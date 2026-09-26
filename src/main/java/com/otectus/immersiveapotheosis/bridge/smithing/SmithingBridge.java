package com.otectus.immersiveapotheosis.bridge.smithing;

import com.otectus.immersiveapotheosis.bridge.CraftGrades;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Immersive Smithing: socket floors for marked work, and a forge that will not melt socketed gems. */
public final class SmithingBridge {

    public static void init() {
        CraftGrades.register("immersive_smithing", ServerConfig.SOCKETS_FROM_SMITHING, SmithingBridge::grade);
        ImmersiveSmithingAPI.registerMeltVeto(SmithingBridge::meltVeto);
    }

    /** Fine or Masterwork, for pieces a player forged (a Maker's Mark) that are not Faulty. */
    static CraftGrades.Grade grade(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains(QualityData.ROOT)) return CraftGrades.Grade.NONE;
        Optional<QualityData> quality = QualityData.get(stack);
        if (quality.isEmpty() || quality.get().faulty() || MakersMark.get(stack).isEmpty()) return CraftGrades.Grade.NONE;
        return switch (quality.get().quality()) {
            case MASTERWORK -> CraftGrades.Grade.MASTERWORK;
            case FINE -> CraftGrades.Grade.FINE;
            default -> CraftGrades.Grade.NONE;
        };
    }

    /** Melting keeps only the metal; socketed gems would be lost, as at Apotheosis's own Salvaging Table. */
    static Optional<Component> meltVeto(ItemStack stack) {
        if (!ServerConfig.get(ServerConfig.REFUSE_SOCKETED_MELTING)) return Optional.empty();
        if (SocketHelper.getGems(stack).streamValidGems().findAny().isEmpty()) return Optional.empty();
        return Optional.of(Component.translatable("message.immersive_apotheosis.melt_socketed", stack.getHoverName()));
    }

    private SmithingBridge() {}
}
