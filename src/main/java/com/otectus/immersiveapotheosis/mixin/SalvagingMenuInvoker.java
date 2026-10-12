package com.otectus.immersiveapotheosis.mixin;

import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Apotheosis's own (unobfuscated) protected method that hands a salvage output the table could not hold to the
 * player. Performed salvaging goes through it, exactly as Apotheosis's {@code salvageAll} does, so mods that hook
 * that method (progression bonuses on salvage) see performed salvage too.
 */
@Mixin(value = SalvagingMenu.class, remap = false)
public interface SalvagingMenuInvoker {
    @Invoker("giveItem")
    void immersiveapotheosis$giveItem(Player player, ItemStack stack);
}
