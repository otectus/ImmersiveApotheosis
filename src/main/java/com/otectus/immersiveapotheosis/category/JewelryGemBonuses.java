package com.otectus.immersiveapotheosis.category;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.AttributeBonus;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.DamageReductionBonus;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.MultiAttrBonus;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.PotionBonus;
import dev.shadowsoffire.placebo.reload.RegistryCallback;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every gem, from Apotheosis or any pack, also fits jewelry. After each gem reload (on the server, and on clients
 * after the registry sync), each gem that has no bonus for a jewelry category borrows one of its own bonuses:
 *
 * <ul>
 *   <li>rings prefer a melee bonus, bracelets a ranged one, amulets a body-armor one and circlets a helmet one;</li>
 *   <li>otherwise the gem's first attribute, damage-reduction or mob-effect bonus is used;</li>
 *   <li>special bonuses and durability or enchantment bonuses never transfer, because jewelry has no durability,
 *   enchantments or special triggers.</li>
 * </ul>
 *
 * <p>The borrowed bonus keeps the gem's own values; worn jewelry scales its attributes by {@code gemValueMultiplier}.
 * Nothing is written to data, so a pack's own gem changes carry over. A gem that already defines bonuses for a jewelry
 * category (a pack's data) keeps them.
 */
public final class JewelryGemBonuses {
    private static final Map<JewelryCategories.Form, List<String>> PREFERRED = Map.of(
            JewelryCategories.Form.RING, List.of("light_weapon", "sword", "melee", "heavy_weapon", "weapon"),
            JewelryCategories.Form.BRACELET, List.of("ranged", "bow", "crossbow", "light_weapon", "weapon"),
            JewelryCategories.Form.AMULET, List.of("core_armor", "chestplate", "armor", "shield"),
            JewelryCategories.Form.CIRCLET, List.of("helmet", "armor", "core_armor"));

    private static volatile int gems;
    private static volatile int borrowed;
    private static volatile String failure;

    public static void register() {
        GemRegistry.INSTANCE.addCallback(RegistryCallback.reloadOnly(registry -> augment()));
    }

    @SuppressWarnings("unchecked")
    static void augment() {
        int count = 0;
        int added = 0;
        try {
            Field field = Gem.class.getDeclaredField("bonusMap");
            field.setAccessible(true);
            for (Gem gem : GemRegistry.INSTANCE.getValues()) {
                count++;
                Map<LootCategory, GemBonus> map = (Map<LootCategory, GemBonus>) field.get(gem);
                for (Map.Entry<JewelryCategories.Form, LootCategory> e : JewelryCategories.categories().entrySet()) {
                    if (map.containsKey(e.getValue())) continue;
                    Optional<GemBonus> bonus = pick(gem.getBonuses(), e.getKey());
                    if (bonus.isPresent()) {
                        map.put(e.getValue(), bonus.get());
                        added++;
                    }
                }
            }
            failure = null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failure = e.toString();
            ImmersiveApotheosis.LOGGER.warn("Could not give Apotheosis gems jewelry bonuses; gems will not fit jewelry", e);
        }
        gems = count;
        borrowed = added;
    }

    static Optional<GemBonus> pick(List<GemBonus> bonuses, JewelryCategories.Form form) {
        for (String key : PREFERRED.get(form)) {
            for (GemBonus b : bonuses) {
                if (transferable(b) && b.getGemClass().key().contains(key)) return Optional.of(b);
            }
        }
        return bonuses.stream().filter(JewelryGemBonuses::transferable).findFirst();
    }

    private static boolean transferable(GemBonus bonus) {
        return bonus instanceof AttributeBonus || bonus instanceof MultiAttrBonus || bonus instanceof DamageReductionBonus
                || bonus instanceof PotionBonus;
    }

    /** For the report: gems seen, jewelry bonuses borrowed, and the failure if augmentation broke. */
    public static String status() {
        return failure != null ? "failed: " + failure : gems + " gems, " + borrowed + " jewelry bonuses";
    }

    private JewelryGemBonuses() {}
}
