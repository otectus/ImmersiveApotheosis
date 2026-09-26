package com.otectus.immersiveapotheosis.bridge.tanning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.config.ServerConfig;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Salvaging recipes that yield vanilla leather (Apotheosis salvages leather horse armor into leather) yield Binding
 * Leather instead, with the same count range, so every route to leather still ends at the Tanner's Rack.
 *
 * <p>Outputs are rebuilt through Apotheosis's own output codec, so count ranges and any stack NBT carry over
 * unchanged. Runs after tags bind (server start and every datapack sync), like Immersive Tanning's own rebuild.
 */
public final class LeatherSalvage {
    static final ResourceLocation LEATHER = new ResourceLocation("minecraft", "leather");
    static final ResourceLocation BINDING_LEATHER = new ResourceLocation("immersive_tanning", "binding_leather");

    private static volatile List<ResourceLocation> rewritten = List.of();

    public static void rewrite(RecipeManager manager) {
        if (!ServerConfig.get(ServerConfig.BINDING_LEATHER_SALVAGE) || !ForgeRegistries.ITEMS.containsKey(BINDING_LEATHER)) {
            rewritten = List.of();
            return;
        }
        List<Recipe<?>> next = new ArrayList<>();
        List<ResourceLocation> changed = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (recipe instanceof SalvagingRecipe salvaging && yieldsLeather(salvaging)) {
                next.add(new SalvagingRecipe(salvaging.getId(), convert(salvaging.getOutputs()), salvaging.getInput()));
                changed.add(salvaging.getId());
            } else {
                next.add(recipe);
            }
        }
        if (!changed.isEmpty()) manager.replaceRecipes(next);
        rewritten = List.copyOf(changed);
        if (!changed.isEmpty()) ImmersiveApotheosis.LOGGER.info("Salvaging yields Binding Leather instead of leather in {}", changed);
    }

    /** Recipes rewritten by the last run, for the report. */
    public static List<ResourceLocation> rewritten() {
        return rewritten;
    }

    private static boolean yieldsLeather(SalvagingRecipe recipe) {
        for (SalvagingRecipe.OutputData output : recipe.getOutputs()) {
            if (LEATHER.equals(ForgeRegistries.ITEMS.getKey(output.getStack().getItem()))) return true;
        }
        return false;
    }

    private static List<SalvagingRecipe.OutputData> convert(List<SalvagingRecipe.OutputData> outputs) {
        List<SalvagingRecipe.OutputData> out = new ArrayList<>(outputs.size());
        for (SalvagingRecipe.OutputData output : outputs) {
            if (!LEATHER.equals(ForgeRegistries.ITEMS.getKey(output.getStack().getItem()))) {
                out.add(output);
                continue;
            }
            JsonElement json = SalvagingRecipe.OutputData.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow(false, e -> {});
            replaceItemIds(json);
            out.add(SalvagingRecipe.OutputData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, e -> {}));
        }
        return out;
    }

    /** Swaps every item id field naming vanilla leather, whatever the stack codec calls it. */
    private static void replaceItemIds(JsonElement json) {
        if (json instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> e : object.entrySet()) {
                JsonElement value = e.getValue();
                if ((e.getKey().equals("item") || e.getKey().equals("id")) && value.isJsonPrimitive()
                        && LEATHER.toString().equals(value.getAsString())) {
                    e.setValue(new com.google.gson.JsonPrimitive(BINDING_LEATHER.toString()));
                } else {
                    replaceItemIds(value);
                }
            }
        } else if (json != null && json.isJsonArray()) {
            json.getAsJsonArray().forEach(LeatherSalvage::replaceItemIds);
        }
    }

    private LeatherSalvage() {}
}
