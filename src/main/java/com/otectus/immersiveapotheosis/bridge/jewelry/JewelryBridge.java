package com.otectus.immersiveapotheosis.bridge.jewelry;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.bridge.CraftGrades;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.api.integration.IJewelryEffectContributor;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.quality.WorkmanshipGrade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Immersive Jewelry: worn pieces run their Apotheosis affixes and gems through Jewelry's effect system, and pieces a
 * jeweler made get socket floors.
 */
public final class JewelryBridge {
    public static final ResourceLocation EFFECT_ID = ImmersiveApotheosis.id("apotheosis");

    public static void init() {
        CraftGrades.register("immersive_jewelry", ServerConfig.SOCKETS_FROM_JEWELRY, JewelryBridge::grade);
        ImmersiveJewelryAPI.registerEffectContributor(JewelryBridge::contribute);
    }

    static List<IJewelryEffectContributor.ContributedEffect> contribute(ItemStack stack, JewelryData data) {
        return WornAffixes.of(stack)
                .map(worn -> List.of(new IJewelryEffectContributor.ContributedEffect(EFFECT_ID, EffectCategory.UTILITY,
                        new ApotheosisJewelryEffect(worn), 1F)))
                .orElse(List.of());
    }

    /** Jewelry's scale is finer than Smithing's: Fine and Exceptional give the Fine floor, Masterwork the Masterwork floor. */
    static CraftGrades.Grade grade(ItemStack stack) {
        Optional<JewelryData> data = JewelryData.get(stack);
        if (data.isEmpty() || data.get().maker() == null) return CraftGrades.Grade.NONE;
        return switch (WorkmanshipGrade.of(data.get().workmanship())) {
            case MASTERWORK -> CraftGrades.Grade.MASTERWORK;
            case FINE, EXCEPTIONAL -> CraftGrades.Grade.FINE;
            default -> CraftGrades.Grade.NONE;
        };
    }

    private JewelryBridge() {}
}
