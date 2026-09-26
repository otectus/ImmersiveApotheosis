package com.otectus.immersiveapotheosis.category;

import com.otectus.immersiveapotheosis.config.ServerConfig;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketedGems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a jewelry piece's Apotheosis data does when worn, resolved once: the active affixes, each capped at
 * {@code maxRarity}, the socketed gems, and the scaled attribute modifiers. The worn effect, the tooltip and the tests
 * all read this one resolution.
 *
 * <p>Only {@code maxActiveAffixes} affixes act, stats first (then by affix id): jewelry's abilities come from its own
 * traits, so Apotheosis adds stats first, and its ability affixes act only when a pack allows more. The rest are dormant.
 *
 * @param rarityCap the rarity the affixes were capped to, or null when the piece is within the cap
 */
public record WornAffixes(ItemStack piece, LootCategory category, List<AffixInstance> active, List<AffixInstance> dormant,
                          SocketedGems gems, @Nullable LootRarity rarityCap) {

    public record Mod(Attribute attribute, double amount, AttributeModifier.Operation operation) {}

    /** Empty for anything that is not enabled jewelry, or jewelry with neither affixes nor gems. */
    public static Optional<WornAffixes> of(ItemStack stack) {
        JewelryCategories.Form form = JewelryCategories.formOf(stack);
        if (form == null || !form.enabled() || !stack.hasTag()) return Optional.empty();
        ItemStack piece = stack.copy();
        LootCategory category = JewelryCategories.categories().get(form);
        SocketedGems gems = SocketHelper.getGems(piece);
        List<AffixInstance> all = new ArrayList<>(AffixHelper.getAffixes(piece).values());
        if (all.isEmpty() && gems.streamValidGems().findAny().isEmpty()) return Optional.empty();

        all.sort(Comparator.comparing((AffixInstance a) -> a.affix().isBound() ? a.affix().get().getType().ordinal() : Integer.MAX_VALUE)
                .thenComparing(a -> a.affix().getId().toString()));
        int limit = Math.min(all.size(), ServerConfig.get(ServerConfig.JEWELRY_MAX_ACTIVE_AFFIXES));
        LootRarity cap = cap();
        LootRarity capped = null;
        List<AffixInstance> active = new ArrayList<>(limit);
        for (AffixInstance a : all.subList(0, limit)) {
            if (cap != null && a.rarity().isBound() && a.rarity().get().ordinal() > cap.ordinal()) {
                a = new AffixInstance(a.affix(), a.stack(), RarityRegistry.INSTANCE.holder(cap), a.level());
                capped = cap;
            }
            if (a.isValid()) active.add(a);
        }
        return Optional.of(new WornAffixes(piece, category, List.copyOf(active), List.copyOf(all.subList(limit, all.size())), gems, capped));
    }

    /** The configured rarity cap, or null when the configured id is not an Apotheosis rarity. */
    @Nullable
    static LootRarity cap() {
        ResourceLocation id = ResourceLocation.tryParse(ServerConfig.get(ServerConfig.JEWELRY_MAX_RARITY));
        return id == null ? null : RarityRegistry.INSTANCE.getValue(id);
    }

    /** Every attribute modifier the piece grants when worn, already scaled by the jewelry multipliers. */
    public List<Mod> modifiers() {
        double affixScale = ServerConfig.get(ServerConfig.JEWELRY_AFFIX_VALUE_MULTIPLIER);
        double gemScale = ServerConfig.get(ServerConfig.JEWELRY_GEM_VALUE_MULTIPLIER);
        List<Mod> out = new ArrayList<>();
        for (AffixInstance a : active) {
            a.addModifiers(EquipmentSlot.LEGS, (attribute, m) -> out.add(new Mod(attribute, m.getAmount() * affixScale, m.getOperation())));
        }
        gems.addModifiers(category, EquipmentSlot.LEGS, (attribute, m) -> out.add(new Mod(attribute, m.getAmount() * gemScale, m.getOperation())));
        return out;
    }
}
