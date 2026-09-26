package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A reforging performance abandoned by closing the table is charged while the table still holds its items. */
@Mixin(value = ReforgingMenu.class, remap = false)
public abstract class ReforgingMenuMixin {
    @Inject(method = {"removed", "m_6877_"}, at = @At("HEAD"))
    private void immersiveapotheosis$abandon(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) PerformanceSessions.onMenuRemoved(sp, (AbstractContainerMenu) (Object) this);
    }
}
