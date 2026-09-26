package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.category.JewelryCategories;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bodies of the jewelry tests; loaded only when Immersive Jewelry is present. */
final class JewelryChecks {
    private static final Map<UUID, List<ItemStack>> WORN = new ConcurrentHashMap<>();
    private static boolean providerRegistered;

    static ItemStack ring(String maker) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemData stone = JewelryFactory.sampleCut(defs.gem(new ResourceLocation("immersive_jewelry", "ruby")),
                defs.cut(new ResourceLocation("immersive_jewelry", "round")), 0.95F);
        return JewelryFactory.jewelry(new ResourceLocation("immersive_jewelry", "ring"),
                JewelryFactory.metal(defs, new ResourceLocation("minecraft", "gold")), List.of(stone), null,
                new ResourceLocation("immersive_jewelry", "polished"), 0.95F, 0.95F, maker);
    }

    static LootRarity rarity(String id) {
        return RarityRegistry.INSTANCE.getValue(new ResourceLocation("apotheosis", id));
    }

    private static void wear(Player player, ItemStack... pieces) {
        if (!providerRegistered) {
            providerRegistered = true;
            ImmersiveJewelryAPI.registerEquipmentProvider((p, out) -> {
                List<ItemStack> list = WORN.get(p.getUUID());
                if (list != null) for (int i = 0; i < list.size(); i++) out.accept("apotheosis_test:" + i, list.get(i));
            });
        }
        WORN.put(player.getUUID(), new ArrayList<>(List.of(pieces)));
    }

    private static List<AttributeModifier> ours(Player player, WornAffixes.Mod mod) {
        AttributeInstance instance = player.getAttribute(mod.attribute());
        if (instance == null) return List.of();
        return instance.getModifiers().stream().filter(m -> m.getName().equals("Immersive Apotheosis")).toList();
    }

    static void categoriesAndAffixes(GameTestHelper h) {
        ItemStack ring = ring("Jeweler");
        LootCategory category = LootCategory.forItem(ring);
        h.assertTrue(JewelryCategories.isJewelry(category), "a ring has the jewelry ring category, got " + category);
        ItemStack reforged = TestSupport.reforge(ring, rarity("rare"), RandomSource.create(11));
        h.assertTrue(!AffixHelper.getAffixes(reforged).isEmpty(), "a reforged ring has affixes");
        AffixHelper.getAffixes(reforged).keySet().forEach(affix -> h.assertTrue(
                affix.getId().toString().startsWith("immersive_apotheosis:jewelry/ring/"), "only ring affixes roll on rings, got " + affix.getId()));
        h.assertTrue(JewelryData.get(reforged).isPresent(), "the reforge keeps the piece's jewelry data");
        h.succeed();
    }

    static void wornAffixesFollowTheWearer(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        ItemStack ring = TestSupport.reforge(ring("Jeweler"), rarity("rare"), RandomSource.create(5));
        WornAffixes worn = WornAffixes.of(ring).orElse(null);
        h.assertTrue(worn != null && !worn.modifiers().isEmpty(), "the ring grants attributes when worn");
        WornAffixes.Mod first = worn.modifiers().get(0);

        wear(player, ring);
        JewelryWearers.refreshNow(player);
        List<AttributeModifier> applied = ours(player, first);
        h.assertTrue(applied.size() == 1 && Math.abs(applied.get(0).getAmount() - first.amount()) < 1e-9,
                "the scaled modifier is applied once: " + applied + " expected " + first);

        wear(player);
        JewelryWearers.refreshNow(player);
        h.assertTrue(ours(player, first).isEmpty(), "and removed when the ring comes off");

        ItemStack broken = ring.copy();
        JewelryData.get(broken).orElseThrow().withIntegrity(0F).apply(broken);
        wear(player, broken);
        JewelryWearers.refreshNow(player);
        h.assertTrue(ours(player, first).isEmpty(), "a broken ring is inert, its affixes too");
        wear(player);
        JewelryWearers.refreshNow(player);
        h.succeed();
    }

    static void capsAndDormancy(GameTestHelper h) {
        ItemStack ring = TestSupport.reforge(ring("Jeweler"), rarity("mythic"), RandomSource.create(9));
        WornAffixes worn = WornAffixes.of(ring).orElseThrow();
        h.assertTrue(worn.active().size() <= 2, "at most two affixes act (maxActiveAffixes), got " + worn.active().size());
        h.assertTrue(!worn.dormant().isEmpty(), "the rest are dormant");
        h.assertTrue(worn.rarityCap() != null && worn.rarityCap().ordinal() == rarity("epic").ordinal(), "a mythic ring counts as epic");
        worn.active().forEach(a -> h.assertTrue(a.rarity().get().ordinal() <= rarity("epic").ordinal(), "active affixes are capped"));
        h.succeed();
    }

    static void gemsAndSockets(GameTestHelper h) {
        ItemStack ring = ring("Jeweler");
        RandomSource random = RandomSource.create(1);
        boolean fits = false;
        for (int i = 0; i < 200 && !fits; i++) fits = GemInstance.socketed(ring, GemRegistry.createRandomGemStack(random, h.getLevel(), 0F)).isValid();
        h.assertTrue(fits, "Apotheosis gems fit jewelry (borrowed bonuses)");

        ItemStack socketed = ring("Jeweler");
        SocketHelper.setSockets(socketed, 3);
        h.assertTrue(SocketHelper.getSockets(socketed) == 1, "jewelry is capped at one socket, got " + SocketHelper.getSockets(socketed));
        h.assertTrue(SocketHelper.getSockets(ring("Jeweler")) == 1, "a jeweler's Masterwork ring has its floor, capped to one");
        h.assertTrue(SocketHelper.getSockets(ring(null)) == 0, "an unsigned ring has no floor");
        h.succeed();
    }

    private JewelryChecks() {}
}
