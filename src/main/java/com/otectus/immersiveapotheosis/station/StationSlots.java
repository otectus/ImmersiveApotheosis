package com.otectus.immersiveapotheosis.station;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.mixin.SmithingMenuAccessor;
import com.otectus.immersiveapotheosis.network.ModNetwork;
import com.otectus.immersiveapotheosis.network.SlotRequestC2S;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.session.Permissions;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketingRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmithingMenu;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Performed result slots: a reforging choice, and the smithing table's result when it is Apotheosis's socketing.
 * Taking one is intercepted on both sides at the head of {@code AbstractContainerMenu#clicked}. The client asks the
 * server to begin a performance ({@link SlotRequestC2S}); the server refuses a plain click on such a slot unless the
 * player bypasses performances, in which case the plain click takes the item as usual.
 */
public final class StationSlots {

    @Nullable
    static Station stationFor(AbstractContainerMenu menu, int slotId, ClickType type) {
        if (slotId < 0 || slotId >= menu.slots.size()) return null;
        if (type == ClickType.CLONE || type == ClickType.PICKUP_ALL || type == ClickType.QUICK_CRAFT) return null;
        if (!menu.getSlot(slotId).hasItem()) return null;
        if (menu instanceof ReforgingMenu && menu.getSlot(slotId) instanceof ReforgingMenu.ReforgingResultSlot) {
            return StationRules.performed(Station.REFORGING) ? Station.REFORGING : null;
        }
        if (menu instanceof SmithingMenu smithing && slotId == SmithingMenu.RESULT_SLOT
                && ((SmithingMenuAccessor) smithing).immersiveapotheosis$selectedRecipe() instanceof SocketingRecipe) {
            return StationRules.performed(Station.SOCKETING) ? Station.SOCKETING : null;
        }
        return null;
    }

    /** At the head of {@code AbstractContainerMenu#clicked}; true cancels the click. */
    public static boolean intercept(AbstractContainerMenu menu, int slotId, int button, ClickType type, Player player) {
        if (StationRules.replaying() || stationFor(menu, slotId, type) == null) return false;
        if (player.level().isClientSide) {
            if (player.getAbilities().instabuild && ServerConfig.get(ServerConfig.CREATIVE_BYPASS)) return false;
            ModNetwork.sendToServer(new SlotRequestC2S(menu.containerId, slotId, button, type.ordinal()));
            return true;
        }
        return !(player instanceof ServerPlayer sp) || !Permissions.bypasses(sp);
    }

    /** The client asked to take a performed slot. Bypassing players' own plain click does it instead. */
    public static void onRequest(ServerPlayer player, int containerId, int slotId, int button, ClickType type) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId || !menu.stillValid(player) || Permissions.bypasses(player)) return;
        if (PerformanceSessions.get(player).isPresent()) return;
        Station station = stationFor(menu, slotId, type);
        if (station == null) return;
        Optional<PerformanceSessions.Context> context = switch (station) {
            case REFORGING -> ReforgingStation.capture(player, (ReforgingMenu) menu, slotId);
            case SOCKETING -> SocketingStation.capture(player, (SmithingMenu) menu, slotId);
            default -> Optional.empty();
        };
        context.ifPresent(c -> PerformanceSessions.start(player, menu, c));
    }

    private StationSlots() {}
}
