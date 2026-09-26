package com.otectus.immersiveapotheosis.category;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import dev.shadowsoffire.apotheosis.adventure.event.GetItemSocketsEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Item-level rules for jewelry in Apotheosis: the socket cap, and no stray modifiers in the unused legs slot. */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID)
public final class JewelryRules {

    /** After every other listener (the craft floors included): jewelry never exceeds its socket cap. Idempotent. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capSockets(GetItemSocketsEvent event) {
        JewelryCategories.Form form = JewelryCategories.formOf(event.getStack());
        if (form == null || !form.enabled()) return;
        int cap = ServerConfig.get(ServerConfig.JEWELRY_MAX_SOCKETS);
        if (event.getSockets() > cap) event.setSockets(cap);
    }

    /**
     * Jewelry's categories use the legs slot only as a key. Apotheosis would otherwise list affixes "When on Legs";
     * worn jewelry applies them through Immersive Jewelry instead, and has no legs-slot modifiers of its own.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void clearLegsModifiers(ItemAttributeModifierEvent event) {
        if (event.getSlotType() == EquipmentSlot.LEGS && JewelryCategories.formOf(event.getItemStack()) != null) event.clearModifiers();
    }

    private JewelryRules() {}
}
