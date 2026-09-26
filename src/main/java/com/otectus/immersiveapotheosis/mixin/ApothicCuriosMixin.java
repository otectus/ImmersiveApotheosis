package com.otectus.immersiveapotheosis.mixin;

import com.otectus.immersiveapotheosis.category.JewelryCategories;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Leaves Immersive Jewelry out of ApothicCurios's hook dispatch (see {@code ApothicCuriosExclusion}). Optional target. */
@Pseudo
@Mixin(targets = "daripher.apothiccurios.ApothicCuriosMod", remap = false)
public abstract class ApothicCuriosMixin {

    @Inject(method = "getEquippedCurios", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void immersiveapotheosis$excludeJewelry(LivingEntity entity, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> curios = cir.getReturnValue();
        if (curios == null || curios.stream().noneMatch(s -> JewelryCategories.formOf(s) != null)) return;
        cir.setReturnValue(curios.stream().filter(s -> JewelryCategories.formOf(s) == null).toList());
    }
}
