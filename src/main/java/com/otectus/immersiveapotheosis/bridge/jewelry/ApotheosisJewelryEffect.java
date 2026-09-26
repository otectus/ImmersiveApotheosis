package com.otectus.immersiveapotheosis.bridge.jewelry;

import com.otectus.immersiveapotheosis.category.ApothicCuriosExclusion;
import com.otectus.immersiveapotheosis.category.WornAffixes;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A worn piece's Apotheosis affixes and socketed gems, run as one Immersive Jewelry effect: attribute modifiers on
 * equip (scaled, removed on unequip), and the affix and gem hooks through Jewelry's own dispatch, so Jewelry's rules
 * (broken pieces inert, per-form caps) apply to them.
 */
final class ApotheosisJewelryEffect extends JewelryEffect {
    private static final Set<JewelryHook> HOOKS = EnumSet.of(JewelryHook.OUTGOING_DAMAGE, JewelryHook.INCOMING_DAMAGE,
            JewelryHook.DAMAGE_TAKEN, JewelryHook.BLOCK_BREAK, JewelryHook.PROJECTILE_FIRED);

    private final WornAffixes worn;
    private final List<WornAffixes.Mod> modifiers;
    private final boolean runsHooks;

    ApotheosisJewelryEffect(WornAffixes worn) {
        this.worn = worn;
        this.modifiers = worn.modifiers();
        this.runsHooks = ApothicCuriosExclusion.jewelryRunsHooks();
    }

    @Override
    public Set<JewelryHook> hooks() {
        return runsHooks ? HOOKS : Set.of();
    }

    @Override
    public void describe(EffectDescription out) {
        // Shown by the hub's own tooltip section (client/JewelryAffixTooltip), from the same resolution.
    }

    private static UUID uuid(JewelryContext ctx, int index) {
        return UUID.nameUUIDFromBytes(("immersive_apotheosis:" + ctx.slot() + "/" + index).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onEquip(JewelryContext ctx) {
        for (int i = 0; i < modifiers.size(); i++) {
            WornAffixes.Mod m = modifiers.get(i);
            AttributeInstance instance = ctx.player().getAttribute(m.attribute());
            if (instance == null) continue;
            UUID id = uuid(ctx, i);
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, "Immersive Apotheosis", m.amount(), m.operation()));
        }
    }

    @Override
    public void onUnequip(JewelryContext ctx) {
        for (int i = 0; i < modifiers.size(); i++) {
            AttributeInstance instance = ctx.player().getAttribute(modifiers.get(i).attribute());
            if (instance != null) instance.removeModifier(uuid(ctx, i));
        }
    }

    @Override
    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {
        for (AffixInstance a : worn.active()) a.doPostAttack(ctx.player(), event.getEntity());
        worn.gems().doPostAttack(ctx.player(), event.getEntity());
    }

    @Override
    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {
        float amount = event.getAmount();
        for (AffixInstance a : worn.active()) amount = a.onHurt(event.getSource(), ctx.player(), amount);
        amount = worn.gems().onHurt(event.getSource(), ctx.player(), amount);
        event.setAmount(amount);
    }

    @Override
    public void onDamageTaken(JewelryContext ctx, LivingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;
        for (AffixInstance a : worn.active()) a.doPostHurt(ctx.player(), attacker);
        worn.gems().doPostHurt(ctx.player(), attacker);
    }

    @Override
    public void onBlockBreak(JewelryContext ctx, BlockEvent.BreakEvent event) {
        for (AffixInstance a : worn.active()) a.onBlockBreak(ctx.player(), event.getLevel(), event.getPos(), event.getState());
        worn.gems().onBlockBreak(ctx.player(), event.getLevel(), event.getPos(), event.getState());
    }

    @Override
    public void onProjectileFired(JewelryContext ctx, Projectile projectile) {
        if (!(projectile instanceof AbstractArrow arrow)) return;
        for (AffixInstance a : worn.active()) a.onArrowFired(ctx.player(), arrow);
        worn.gems().onArrowFired(ctx.player(), arrow);
    }

    /** The modifiers this effect applies (tests). */
    List<WornAffixes.Mod> modifiers() {
        return new ArrayList<>(modifiers);
    }
}
