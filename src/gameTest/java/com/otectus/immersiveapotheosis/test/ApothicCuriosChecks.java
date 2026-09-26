package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * With ApothicCurios installed, worn jewelry's hook affixes run once, through Immersive Jewelry: ApothicCurios no
 * longer sees jewelry. Loaded only when ApothicCurios, Curios and Immersive Jewelry are all present.
 */
final class ApothicCuriosChecks {

    @SuppressWarnings("unchecked")
    static void jewelryLeavesApothicCurios(GameTestHelper h) throws ReflectiveOperationException {
        h.assertTrue(ApothicCuriosExclusion.status() == ApothicCuriosExclusion.Status.APPLIED,
                "the exclusion reached ApothicCurios, status " + ApothicCuriosExclusion.status());
        FakePlayer player = TestSupport.player(h);
        ItemStack ring = TestSupport.reforge(JewelryChecks.ring("Jeweler"), JewelryChecks.rarity("rare"), RandomSource.create(5));
        boolean equipped = CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(inventory -> inventory.getStacksHandler("ring"))
                .map(handler -> {
                    handler.getStacks().setStackInSlot(0, ring);
                    return true;
                }).orElse(false);
        h.assertTrue(equipped, "the ring is in the player's Curios ring slot");

        List<AffixInstance> seen = (List<AffixInstance>) Class.forName("daripher.apothiccurios.ApothicCuriosMod")
                .getMethod("getCuriosAffixes", LivingEntity.class).invoke(null, player);
        h.assertTrue(seen.isEmpty(), "ApothicCurios dispatches none of the ring's affixes, got " + seen);

        JewelryWearers.refreshNow(player);
        WornAffixes.Mod first = WornAffixes.of(ring).orElseThrow().modifiers().get(0);
        AttributeInstance instance = player.getAttribute(first.attribute());
        long applied = instance == null ? 0 : instance.getModifiers().stream().filter(m -> m.getName().equals("Immersive Apotheosis")).count();
        h.assertTrue(applied == 1, "Immersive Jewelry applies the ring's attributes once, got " + applied);
        h.succeed();
    }

    private ApothicCuriosChecks() {}
}
