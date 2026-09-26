package com.otectus.immersiveapotheosis.test;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherworkData;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Bodies of the Tanning bridge tests; loaded only when Immersive Tanning is present. */
final class TanningChecks {

    static void leatherSalvage(GameTestHelper h) {
        ResourceLocation id = new ResourceLocation("apotheosis", "salvaging/leather_horse_armor");
        SalvagingRecipe recipe = h.getLevel().getRecipeManager().byKey(id).filter(r -> r instanceof SalvagingRecipe)
                .map(r -> (SalvagingRecipe) r).orElse(null);
        h.assertTrue(recipe != null, "Apotheosis's leather horse armor salvage exists");
        SalvagingRecipe.OutputData output = recipe.getOutputs().get(0);
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(output.getStack().getItem());
        h.assertTrue(new ResourceLocation("immersive_tanning", "binding_leather").equals(item), "it yields Binding Leather, got " + item);
        h.assertTrue(output.getMin() == 3, "with Apotheosis's own count range, min " + output.getMin());
        h.succeed();
    }

    static void socketFloors(GameTestHelper h) {
        ItemStack master = new ItemStack(Items.LEATHER_CHESTPLATE);
        new LeatherworkData(Finish.HARNESS, 100, 100, null, "Tanner").apply(master);
        LeatherworkData.stampCrafter(master, java.util.UUID.randomUUID());
        h.assertTrue(SocketHelper.getSockets(master) == 2, "bench Masterwork leatherwork has two sockets, got " + SocketHelper.getSockets(master));
        // A named MCA villager's work carries a crafter name but no player: no floor.
        ItemStack village = new ItemStack(Items.LEATHER_CHESTPLATE);
        new LeatherworkData(Finish.HARNESS, 100, 100, null, "Elena Marsh").apply(village);
        h.assertTrue(SocketHelper.getSockets(village) == 0, "villager leatherwork has none, however it is named");
        h.succeed();
    }

    private TanningChecks() {}
}
