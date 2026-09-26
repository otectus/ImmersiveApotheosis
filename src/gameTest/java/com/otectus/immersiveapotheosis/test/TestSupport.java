package com.otectus.immersiveapotheosis.test;

import com.mojang.authlib.GameProfile;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketedGems;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import java.util.List;
import java.util.UUID;

final class TestSupport {
    private static final java.util.Map<UUID, java.util.List<Object>> SENT = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        com.otectus.immersiveapotheosis.network.ModNetwork.fakePlayerSink =
                (player, message) -> SENT.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayList<>()).add(message);
        // The test server runs ticks faster than real time; performances follow game time here (50 ms per tick).
        com.otectus.immersiveapotheosis.session.SessionClock.useSource(
                () -> net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getTickCount() * 50L);
    }

    /** The last message of a type sent to a fake player. */
    static <T> T last(net.minecraft.world.entity.player.Player player, Class<T> type) {
        T found = null;
        for (Object o : SENT.getOrDefault(player.getUUID(), java.util.List.of())) if (type.isInstance(o)) found = type.cast(o);
        return found;
    }

    /** Game ticks to wait for {@code ms} of the performance clock, with a margin. */
    static int ticks(long ms) {
        return (int) (ms / 50) + 6;
    }

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ApotheosisTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        net.minecraft.core.BlockPos pos = h.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    /** What the Reforging Table does to its input: a copy rolled again at {@code rarity}. */
    static ItemStack reforge(ItemStack stack, LootRarity rarity, RandomSource random) {
        return LootController.createLootItem(stack.copy(), rarity, random);
    }

    static LootRarity common() {
        return RarityRegistry.getMinRarity().get();
    }

    /** Sets one gem that the item accepts into a single socket, as the smithing table's socketing would. */
    static boolean socketAGem(GameTestHelper h, ItemStack stack) {
        RandomSource random = RandomSource.create(42);
        for (int attempt = 0; attempt < 200; attempt++) {
            ItemStack gem = GemRegistry.createRandomGemStack(random, h.getLevel(), 0F);
            GemInstance instance = GemInstance.socketed(stack, gem);
            if (!instance.isValid()) continue;
            SocketHelper.setSockets(stack, Math.max(1, SocketHelper.getSockets(stack)));
            SocketHelper.setGems(stack, new SocketedGems(List.of(instance)));
            return SocketHelper.getGems(stack).streamValidGems().findAny().isPresent();
        }
        return false;
    }

    private TestSupport() {}
}
