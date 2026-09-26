package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A socketing performance abandoned by closing the table is charged before the table hands its items back. */
@Mixin(ItemCombinerMenu.class)
public abstract class ItemCombinerMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void immersiveapotheosis$abandon(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) PerformanceSessions.onMenuRemoved(sp, (AbstractContainerMenu) (Object) this);
    }
}
