package com.otectus.immersiveapotheosis.category;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Apotheosis loot categories for Immersive Jewelry's four forms ({@code immersive_apotheosis:ring} and so on), so
 * jewelry reforges, sockets and salvages like gear and affix and gem data can target it.
 *
 * <p>They are registered at common setup, before any affix or gem data loads, and always (a category that matches
 * nothing is harmless, while data naming a missing category would drop that data). The predicates match only
 * Immersive Jewelry's items and follow the config live. Slots are {@code LEGS}, ApothicCurios's convention for
 * accessories: vanilla never equips jewelry there, and {@link JewelryRules} hides the resulting modifiers.
 */
public final class JewelryCategories {
    public enum Form {
        RING("ring", ServerConfig.JEWELRY_RINGS),
        AMULET("amulet", ServerConfig.JEWELRY_AMULETS),
        BRACELET("bracelet", ServerConfig.JEWELRY_BRACELETS),
        CIRCLET("circlet", ServerConfig.JEWELRY_CIRCLETS);

        public final String id;
        private final ForgeConfigSpec.BooleanValue enabled;
        private volatile Item item;
        private volatile boolean resolved;

        Form(String id, ForgeConfigSpec.BooleanValue enabled) {
            this.id = id;
            this.enabled = enabled;
        }

        /** Immersive Jewelry's item for this form, or null when Immersive Jewelry is not installed. */
        @Nullable
        public Item item() {
            if (!resolved) {
                ResourceLocation key = new ResourceLocation("immersive_jewelry", id);
                item = ForgeRegistries.ITEMS.containsKey(key) ? ForgeRegistries.ITEMS.getValue(key) : null;
                resolved = true;
            }
            return item;
        }

        public boolean enabled() {
            return ServerConfig.get(ServerConfig.JEWELRY_ENABLED) && ServerConfig.get(enabled);
        }
    }

    private static final Map<Form, LootCategory> CATEGORIES = new EnumMap<>(Form.class);

    public static void register() {
        for (Form form : Form.values()) {
            LootCategory category = LootCategory.register(null, ImmersiveApotheosis.MOD_ID + ":" + form.id,
                    stack -> form.enabled() && is(stack, form), new EquipmentSlot[] {EquipmentSlot.LEGS});
            CATEGORIES.put(form, category);
        }
    }

    /** The piece's form whatever the config says, or null for anything that is not Immersive Jewelry. */
    @Nullable
    public static Form formOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Form form : Form.values()) {
            if (is(stack, form)) return form;
        }
        return null;
    }

    private static boolean is(ItemStack stack, Form form) {
        Item item = form.item();
        return item != null && stack.is(item);
    }

    public static boolean isJewelry(LootCategory category) {
        return CATEGORIES.containsValue(category);
    }

    public static Map<Form, LootCategory> categories() {
        return Collections.unmodifiableMap(CATEGORIES);
    }

    private JewelryCategories() {}
}
