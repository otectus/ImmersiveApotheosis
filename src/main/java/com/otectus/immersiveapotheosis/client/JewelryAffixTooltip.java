package com.otectus.immersiveapotheosis.client;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.attributeslib.api.IFormattableAttribute;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Worn jewelry's Apotheosis attributes, as they apply: after the rarity cap, the active-affix limit and the jewelry
 * multipliers. Apotheosis itself would list them for the legs slot, which jewelry never uses. Reads the same
 * resolution as the worn effect, so the numbers shown are the numbers applied.
 */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID, value = Dist.CLIENT)
public final class JewelryAffixTooltip {
    private static final String KEY = "tooltip.immersive_apotheosis.jewelry.";
    private static final UUID DISPLAY = new UUID(0L, 0L);

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        WornAffixes.of(event.getItemStack()).ifPresent(worn -> {
            List<WornAffixes.Mod> mods = worn.modifiers();
            if (mods.isEmpty() && worn.dormant().isEmpty()) return;
            List<Component> tip = event.getToolTip();
            tip.add(Component.translatable(KEY + "worn").withStyle(ChatFormatting.GRAY));
            for (WornAffixes.Mod m : mods) {
                AttributeModifier modifier = new AttributeModifier(DISPLAY, "display", m.amount(), m.operation());
                tip.add(Component.literal(" ").append(IFormattableAttribute.toComponent(m.attribute(), modifier, event.getFlags())));
            }
            if (worn.rarityCap() != null) {
                tip.add(Component.translatable(KEY + "capped", worn.rarityCap().toComponent()).withStyle(ChatFormatting.DARK_GRAY));
            }
            for (AffixInstance a : worn.dormant()) {
                tip.add(Component.translatable(KEY + "dormant", a.getName(true)).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private JewelryAffixTooltip() {}
}
