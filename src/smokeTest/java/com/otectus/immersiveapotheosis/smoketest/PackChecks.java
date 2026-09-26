package com.otectus.immersiveapotheosis.smoketest;

import com.otectus.immersiveapotheosis.bridge.BridgeBootstrap;
import com.otectus.immersiveapotheosis.bridge.tanning.LeatherSalvage;
import com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion;
import com.otectus.immersiveapotheosis.category.JewelryCategories;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import com.otectus.immersiveenchanting.enchanting.AdapterRegistry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that only a full modpack can answer (run by the agent in the Ultima pack test, on the server thread). Facts
 * that decide follow-up work are recorded as strings; everything else is pass/fail.
 */
final class PackChecks {

    static void run(ServerPlayer player, Map<String, Object> out) {
        ServerLevel level = player.serverLevel();

        BridgeBootstrap.status().forEach((mod, status) -> out.put("bridge_" + mod, status == BridgeBootstrap.Status.ACTIVE));
        out.put("apothiccurios_exclusion_applied", ApothicCuriosExclusion.status() == ApothicCuriosExclusion.Status.APPLIED);

        // Which enchanting table the pack really has, and who owns its menu (EasyMagic's table would be inert).
        BlockPos table = player.blockPosition().offset(0, 0, -3);
        level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
        BlockEntity tile = level.getBlockEntity(table);
        out.put("fact_enchanting_table_tile", tile == null ? "none" : tile.getClass().getName());
        out.put("enchanting_table_is_apotheosis", tile != null && tile.getClass().getName().startsWith("dev.shadowsoffire.apotheosis"));

        // Global loot modifier order: does Smithing's grading run after Apotheosis adds affix loot?
        List<String> order = lootModifierOrder();
        int affixLoot = order.indexOf("apotheosis:affix_loot");
        int forged = order.indexOf("immersive_smithing:forged_loot");
        out.put("fact_loot_modifier_order", "affix_loot@" + affixLoot + ", forged_loot@" + forged + " of " + order.size());
        out.put("fact_forged_loot_after_affix_loot", affixLoot >= 0 && forged > affixLoot);

        // Socket floors, with the pack's own Apotheosis data.
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        new QualityData(95, 95, false).apply(sword);
        MakersMark.stamp(sword, player);
        out.put("masterwork_socket_floor", SocketHelper.getSockets(sword) == 2);

        out.put("leather_salvage_rewritten", LeatherSalvage.rewritten().contains(new ResourceLocation("apotheosis", "salvaging/leather_horse_armor")));

        // Affixes written through Apotheosis under the pack's patches (fallen_lib patches AffixHelper), on jewelry.
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemData stone = JewelryFactory.sampleCut(defs.gem(new ResourceLocation("immersive_jewelry", "ruby")),
                defs.cut(new ResourceLocation("immersive_jewelry", "round")), 0.95F);
        ItemStack ring = JewelryFactory.jewelry(new ResourceLocation("immersive_jewelry", "ring"),
                JewelryFactory.metal(defs, new ResourceLocation("minecraft", "gold")), List.of(stone), null, null, 0.95F, 0.95F, "PackTest");
        out.put("jewelry_category", JewelryCategories.isJewelry(LootCategory.forItem(ring)));
        ItemStack reforged = LootController.createLootItem(ring.copy(), RarityRegistry.INSTANCE.getValue(new ResourceLocation("apotheosis", "rare")),
                RandomSource.create(3));
        out.put("jewelry_affixes_written", !AffixHelper.getAffixes(reforged).isEmpty());
        out.put("jewelry_affixes_apply", WornAffixes.of(reforged).map(w -> !w.modifiers().isEmpty()).orElse(false));
        out.put("jewelry_socket_cap", SocketHelper.getSockets(ring) == 1);

        // Enchanting's adapter registry sees Apotheosis in this pack.
        out.put("fact_enchanting_adapters", AdapterRegistry.adapters().stream().map(a -> a.id().toString()).toList().toString());
    }

    /** The loot modifiers in the order Forge applies them. */
    @SuppressWarnings("unchecked")
    static List<String> lootModifierOrder() {
        try {
            // Package-private in Forge 47; a Forge method, so its name is the same in production.
            java.lang.reflect.Method getter = net.minecraftforge.common.ForgeInternalHandler.class.getDeclaredMethod("getLootModifierManager");
            getter.setAccessible(true);
            Object manager = getter.invoke(null);
            for (Field field : manager.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Map<ResourceLocation, ?> map = (Map<ResourceLocation, ?>) field.get(manager);
                List<String> out = new ArrayList<>();
                map.keySet().forEach(k -> out.add(k.toString()));
                return out;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("unreadable: " + e);
        }
        return List.of();
    }

    private PackChecks() {}
}
