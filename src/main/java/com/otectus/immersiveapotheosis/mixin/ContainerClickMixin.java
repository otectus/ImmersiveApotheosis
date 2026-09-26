package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.station.StationSlots;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Performed result slots (reforging choices, socketing) are taken through a performance, on both sides. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void immersiveapotheosis$performedSlots(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && PerformanceSessions.locks(sp, (AbstractContainerMenu) (Object) this)) {
            ci.cancel();
            return;
        }
        if (StationSlots.intercept((AbstractContainerMenu) (Object) this, slotId, button, type, player)) ci.cancel();
    }
}
