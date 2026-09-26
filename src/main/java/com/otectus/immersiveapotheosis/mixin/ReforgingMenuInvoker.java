package com.otectus.immersiveapotheosis.mixin;

import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Apotheosis's own (unobfuscated) method: re-reads the player's reforge seed and re-rolls the choices. */
@Mixin(value = ReforgingMenu.class, remap = false)
public interface ReforgingMenuInvoker {
    @Invoker("updateSeed")
    void immersiveapotheosis$updateSeed();
}
