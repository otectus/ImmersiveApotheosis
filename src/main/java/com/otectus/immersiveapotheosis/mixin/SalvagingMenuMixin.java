package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.station.SalvagingStation;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla overrides in an Apotheosis class: both the development and the production (SRG) names are listed. */
@Mixin(value = SalvagingMenu.class, remap = false)
public abstract class SalvagingMenuMixin {
    @Inject(method = {"clickMenuButton", "m_6366_"}, at = @At("HEAD"), cancellable = true)
    private void immersiveapotheosis$perform(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (id == 0 && SalvagingStation.onButton((SalvagingMenu) (Object) this, player)) cir.setReturnValue(true);
    }

    @Inject(method = {"removed", "m_6877_"}, at = @At("HEAD"))
    private void immersiveapotheosis$abandon(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) PerformanceSessions.onMenuRemoved(sp, (AbstractContainerMenu) (Object) this);
    }
}
