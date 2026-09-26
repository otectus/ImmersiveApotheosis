package com.otectus.immersiveapotheosis.test;

import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.minigame.*;
import com.otectus.immersiveapotheosis.network.*;
import com.otectus.immersiveapotheosis.session.PerformanceSessions;
import com.otectus.immersiveapotheosis.station.StationSlots;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.*;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/** Real menu lifecycle/codec regressions for valuable items during the longer spatial operations. */
@GameTestHolder(ImmersiveApotheosis.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SessionGameTests {
    private record Fixture(FakePlayer player, AbstractContainerMenu menu, StartS2C start) {}

    private static Fixture open(GameTestHelper h, Station station) {
        FakePlayer p = TestSupport.player(h);
        p.experienceLevel = 100;
        BlockPos local = StationChecks.TABLE;
        BlockPos pos = h.absolutePos(local);
        AbstractContainerMenu menu;
        switch (station) {
            case SALVAGING -> {
                h.setBlock(local, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("apotheosis:salvaging_table")));
                menu = new SalvagingMenu(950, p.getInventory(), pos);
                for (int i = 0; i < 4; i++) menu.getSlot(i).set(TestSupport.reforge(new ItemStack(Items.IRON_SWORD),
                        StationChecks.rarity("rare"), RandomSource.create(i)));
            }
            case REFORGING -> {
                h.setBlock(local, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("apotheosis:reforging_table")));
                menu = new ReforgingMenu(951, p.getInventory(), pos);
                menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
                menu.getSlot(1).set(new ItemStack(StationChecks.item("apotheosis:rare_material"), 32));
                menu.getSlot(2).set(new ItemStack(StationChecks.item("apotheosis:gem_dust"), 32));
            }
            case GEM_CUTTING -> {
                h.setBlock(local, ForgeRegistries.BLOCKS.getValue(new ResourceLocation("apotheosis:gem_cutting_table")));
                menu = new GemCuttingMenu(952, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
                Gem gem = GemRegistry.INSTANCE.getValues().stream().filter(g -> g.getMinRarity().ordinal() == 0).findFirst().orElseThrow();
                menu.getSlot(0).set(GemRegistry.createGemStack(gem, StationChecks.rarity("common")));
                menu.getSlot(1).set(new ItemStack(StationChecks.item("apotheosis:gem_dust"), 64));
                menu.getSlot(2).set(GemRegistry.createGemStack(gem, StationChecks.rarity("common")));
                menu.getSlot(3).set(new ItemStack(StationChecks.item("apotheosis:common_material"), 16));
            }
            case SOCKETING -> {
                h.setBlock(local, Blocks.SMITHING_TABLE);
                menu = new SmithingMenu(953, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                SocketHelper.setSockets(sword, 1);
                RandomSource random = RandomSource.create(4);
                ItemStack gem = ItemStack.EMPTY;
                for (int i = 0; i < 200 && gem.isEmpty(); i++) {
                    ItemStack candidate = GemRegistry.createRandomGemStack(random, h.getLevel(), 0);
                    if (GemInstance.socketed(sword, candidate).isValid()) gem = candidate;
                }
                h.assertTrue(!gem.isEmpty(), "a compatible socket gem exists");
                menu.getSlot(1).set(sword);
                menu.getSlot(2).set(gem);
            }
            default -> throw new IllegalStateException();
        }
        p.containerMenu = menu;
        if (station == Station.SALVAGING || station == Station.GEM_CUTTING) menu.clickMenuButton(p, 0);
        else {
            int slot = station == Station.SOCKETING ? SmithingMenu.RESULT_SLOT : -1;
            if (slot < 0) for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.getSlot(i) instanceof ReforgingMenu.ReforgingResultSlot && menu.getSlot(i).hasItem()) { slot = i; break; }
            }
            StationSlots.onRequest(p, menu.containerId, slot, 0, ClickType.PICKUP);
        }
        StartS2C start = TestSupport.last(p, StartS2C.class);
        h.assertTrue(start != null && PerformanceSessions.get(p).isPresent(), "the station began work");
        return new Fixture(p, menu, start);
    }

    private static void closeOrTimeout(GameTestHelper h, Station station, boolean timeout) {
        Fixture f = open(h, station);
        int delay = timeout ? TestSupport.ticks(68_500) : TestSupport.ticks(3_100);
        h.runAfterDelay(delay, () -> {
            if (!timeout) f.menu().removed(f.player());
            h.assertTrue(PerformanceSessions.get(f.player()).isEmpty(), "the session ended");
            ResolvedS2C verdict = TestSupport.last(f.player(), ResolvedS2C.class);
            h.assertTrue(verdict != null && verdict.band() == OutcomeBand.FAILED, "abandonment follows the failure path");
            String inventory = snapshot(f);
            PerformanceSessions.cancel(f.player(), f.start().session());
            PerformanceSessions.receive(f.player(), f.start().session(), 0, List.of(WorkInput.IDLE));
            h.assertTrue(inventory.equals(snapshot(f)), "duplicate cancellation/input cannot charge or refund twice");
            h.succeed();
        });
    }

    private static String snapshot(Fixture f) {
        StringBuilder out = new StringBuilder();
        for (Slot slot : f.menu().slots) out.append(slot.getItem().save(new net.minecraft.nbt.CompoundTag())).append('|');
        for (ItemStack stack : f.player().getInventory().items) out.append(stack.save(new net.minecraft.nbt.CompoundTag())).append('|');
        return out.append(f.player().experienceLevel).append('/').append(f.player().totalExperience).toString();
    }

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void closingSalvageChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.SALVAGING, false); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void closingSettingChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.SOCKETING, false); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void closingCuttingChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.GEM_CUTTING, false); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void closingReforgeChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.REFORGING, false); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void timedOutSalvageChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.SALVAGING, true); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void timedOutSettingChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.SOCKETING, true); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void timedOutCuttingChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.GEM_CUTTING, true); }
    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void timedOutReforgeChargesOnce(GameTestHelper h) { closeOrTimeout(h, Station.REFORGING, true); }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void inventoryAndReplayAreLockedDuringWork(GameTestHelper h) {
        Fixture f = open(h, Station.GEM_CUTTING);
        String before = snapshot(f);
        f.menu().clicked(0, 0, ClickType.PICKUP, f.player());
        f.menu().clicked(1, 0, ClickType.QUICK_MOVE, f.player());
        f.menu().clickMenuButton(f.player(), 0);
        h.assertTrue(before.equals(snapshot(f)) && f.menu().getCarried().isEmpty(), "slots and repeat button cannot alter work inputs");
        PerformanceSessions.receive(f.player(), UUID.randomUUID(), 0, List.of(WorkInput.IDLE));
        h.assertTrue(PerformanceSessions.get(f.player()).orElseThrow().sequence().nextStep() == 0, "stale session input ignored");
        h.runAfterDelay(TestSupport.ticks(3_300), () -> {
            List<WorkInput> batch = java.util.Collections.nCopies(5, WorkInput.IDLE);
            PerformanceSessions.receive(f.player(), f.start().session(), 0, batch);
            PerformanceSessions.receive(f.player(), f.start().session(), 0, batch);
            h.assertTrue(PerformanceSessions.get(f.player()).orElseThrow().simulation().elapsedSteps() == 5,
                    "a repeated batch cannot replay actions");
            PerformanceSessions.cancel(f.player(), f.start().session());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void prematureWorkIsChargedWithoutCompleting(GameTestHelper h) {
        Fixture f = open(h, Station.GEM_CUTTING);
        h.runAfterDelay(61, () -> {
            PerformanceSessions.receive(f.player(), f.start().session(), 0, java.util.Collections.nCopies(64, WorkInput.IDLE));
            h.assertTrue(PerformanceSessions.get(f.player()).isEmpty(), "implausibly early batch terminates the session");
            h.assertTrue(f.menu().getSlot(1).getItem().getCount() < 64, "committed failure is charged");
            h.assertTrue(f.menu().getSlot(0).hasItem() && f.menu().getSlot(2).hasItem(), "both gems survive");
            h.succeed();
        });
    }

    private static void changedMaterials(GameTestHelper h, Station station) {
        Fixture f = open(h, station);
        int slot = station == Station.REFORGING ? 2 : 1;
        f.menu().getSlot(slot).getItem().shrink(1); // Simulates external inventory mutation, not a permitted player click.
        String changed = snapshot(f);
        PerformanceSessions.receive(f.player(), f.start().session(), 0, List.of(WorkInput.IDLE));
        h.assertTrue(PerformanceSessions.get(f.player()).isEmpty(), "changed captured inputs invalidate the operation");
        h.assertTrue(snapshot(f).equals(changed), "replacement inputs are neither spent nor granted");
        h.assertTrue(TestSupport.last(f.player(), ResolvedS2C.class).band() == null, "changed inputs are not a judged attempt");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void changedCuttingInputsAreNotSpent(GameTestHelper h) { changedMaterials(h, Station.GEM_CUTTING); }
    @GameTest(template = "empty")
    public static void changedReforgingCostsAreNotSpent(GameTestHelper h) { changedMaterials(h, Station.REFORGING); }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void disconnectAfterCommitChargesOnce(GameTestHelper h) {
        Fixture f = open(h, Station.GEM_CUTTING);
        h.runAfterDelay(TestSupport.ticks(3_100), () -> {
            PerformanceSessions.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.player()));
            h.assertTrue(PerformanceSessions.get(f.player()).isEmpty(), "disconnect ends the session");
            h.assertTrue(f.menu().getSlot(1).getItem().getCount() < 64, "disconnect spends the configured failure cost");
            String charged = snapshot(f);
            PerformanceSessions.onMenuRemoved(f.player(), f.menu());
            h.assertTrue(charged.equals(snapshot(f)), "subsequent close does not spend twice");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void inputCodecRejectsInvalidPayloads(GameTestHelper h) {
        int[][] invalid = {{0,-1,0,0,0}, {0,0,0,0,0}, {0,65,0,0,0}, {-1,1,0,0,0},
                {Integer.MAX_VALUE,1,0,0,0}, {3000,1,0,0,0}, {0,1,4096,0,0}, {0,1,0,4096,0}, {0,1,0,0,4}};
        for (int[] value : invalid) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(UUID.randomUUID()); buffer.writeVarInt(value[0]); buffer.writeVarInt(value[1]);
                buffer.writeShort(value[2]); buffer.writeShort(value[3]); buffer.writeByte(value[4]);
                boolean rejected = false;
                try { InputsC2S.decode(buffer); } catch (io.netty.handler.codec.DecoderException | IllegalArgumentException e) { rejected = true; }
                h.assertTrue(rejected, "invalid packet rejected before allocation/replay: " + java.util.Arrays.toString(value));
            } finally { buffer.release(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void startDescriptorRoundTrips(GameTestHelper h) {
        Fixture f = open(h, Station.GEM_CUTTING);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            f.start().encode(buffer);
            StartS2C copy = StartS2C.decode(buffer);
            h.assertTrue(copy.definition().equals(f.start().definition()) && copy.containerId() == f.menu().containerId,
                    "client receives the same simulation and menu identity");
            h.assertTrue(copy.previews().size() == f.start().previews().size()
                    && ItemStack.matches(copy.previews().get(0), f.start().previews().get(0)), "recipe previews survive the packet");
        } finally { buffer.release(); }
        PerformanceSessions.cancel(f.player(), f.start().session());
        h.succeed();
    }
}
