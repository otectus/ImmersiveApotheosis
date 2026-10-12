package com.otectus.immersiveapotheosis.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersiveapotheosis.client.ClientPerformance;
import com.otectus.immersiveapotheosis.minigame.WorkAutoplay;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkStage;
import com.otectus.immersiveapotheosis.network.ResolvedS2C;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Opt-in CurseForge screenshot capture, confined to the test agent and enabled only by
 * {@code -Dimmersive_apotheosis.screenshots.output=<dir>}. It builds a hall of Apotheosis tables and performs the gem
 * cut, reforge, setting and salvage through real GUI input from the reference autoplay, saving framebuffer captures
 * without HUD, chat or toasts.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class StoreScreenshots {
    private static final String OUTPUT = System.getProperty("immersive_apotheosis.screenshots.output");
    private static final int WIDTH = 1920, HEIGHT = 1080;
    private static final Map<String, Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int step, action, baseY;
    private static long entered, started;
    private static boolean finished, renamed;
    private static volatile float lockYaw = Float.NaN, lockPitch;
    private static BlockPos cutting, reforging, smithing, salvaging;
    // Performance driver, as the smoke agent's, without its focus and resize probes.
    private static boolean playing;
    private static String work;
    private static int scriptIndex, scriptActions;
    private static WorkInput scriptPrevious = WorkInput.IDLE;
    private static WorkStage stage;
    private static int stageStep;
    private static final Set<WorkStage> captured = EnumSet.noneOf(WorkStage.class);

    static boolean enabled() { return OUTPUT != null; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (enabled()) event.setCanceled(true);
    }

    /** The player profile is built from the session user when the world is created, so rename it on the title screen. */
    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (!enabled() || renamed || !(event.getScreen() instanceof TitleScreen)) return;
        renamed = true;
        try {
            var name = net.minecraft.client.User.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(Minecraft.getInstance().getUser(), "Otectus");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not rename the showcase player", e);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (enabled() && event.phase == TickEvent.Phase.START) lockView(Minecraft.getInstance());
    }

    /** Delivers reactive gestures after the workbench observes this rendered frame's native pointer. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!enabled() || !playing || ClientPerformance.view().isEmpty() || ClientPerformance.verdict().isPresent()) return;
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        int activeStep = ClientPerformance.view().orElseThrow().elapsedSteps();
        if (!mc.isWindowActive()) {
            // Keep the synthetic session active when the nested display does not hold the desktop focus.
            focusCallback(mc);
            scriptActions = 0;
            var cursor = ClientPerformance.view().orElseThrow().cursor();
            scriptPrevious = new WorkInput(cursor.x(), cursor.y(), 0);
        }
        if (activeStep <= scriptIndex) return;
        while (scriptIndex < activeStep) {
            WorkInput e = WorkAutoplay.next(ClientPerformance.view().orElseThrow(), scriptPrevious);
            scriptIndex = activeStep;
            ClientPerformance.Workspace area = ClientPerformance.workspace().orElseThrow();
            double x = area.left() + e.x() * area.width() / 4095.0;
            double y = area.top() + e.y() * area.height() / 4095.0;
            var window = mc.getWindow();
            double nativeX = x * window.getScreenWidth() / screen.width;
            double nativeY = y * window.getScreenHeight() / screen.height;
            GLFW.glfwSetCursorPos(window.getWindow(), nativeX, nativeY);
            var callback = GLFW.glfwSetCursorPosCallback(window.getWindow(), null);
            GLFW.glfwSetCursorPosCallback(window.getWindow(), callback);
            if (callback != null) callback.invoke(window.getWindow(), nativeX, nativeY);
            int changed = scriptActions ^ e.actions();
            if (e.actions() != 0 && scriptActions == e.actions()) {
                int button = (e.actions() & WorkInput.PRIMARY) != 0 ? 0 : 1;
                if (!ForgeHooksClient.onScreenMouseDragPre(screen, x, y, button, 0, 0)) screen.mouseDragged(x, y, button, 0, 0);
            } else if (e.actions() == 0 && changed == 0) {
                if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0)) screen.mouseClicked(x, y, 0);
                if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, 0)) screen.mouseReleased(x, y, 0);
            }
            for (int button = 0; button < 2; button++) {
                int bit = button == 0 ? WorkInput.PRIMARY : WorkInput.SECONDARY;
                if ((changed & bit) == 0) continue;
                boolean down = (e.actions() & bit) != 0;
                if ("reforge".equals(work)) {
                    int keyCode = button == 0 ? GLFW.GLFW_KEY_SPACE : GLFW.GLFW_KEY_LEFT_SHIFT;
                    if (down) {
                        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, keyCode, 0, 0)) screen.keyPressed(keyCode, 0, 0);
                    } else if (!ForgeHooksClient.onScreenKeyReleasedPre(screen, keyCode, 0, 0)) {
                        screen.keyReleased(keyCode, 0, 0);
                    }
                } else if (down) {
                    if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, button)) screen.mouseClicked(x, y, button);
                } else if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, button)) {
                    screen.mouseReleased(x, y, button);
                }
            }
            scriptActions = e.actions();
            scriptPrevious = e;
        }
    }

    /** One frame per work stage, taken once the stage is well under way. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!enabled() || !playing || ClientPerformance.view().isEmpty() || ClientPerformance.verdict().isPresent()) return;
        var state = ClientPerformance.view().orElseThrow();
        if (state.stage() != stage) {
            stage = state.stage();
            stageStep = state.elapsedSteps();
        }
        if (state.elapsedSteps() >= stageStep + 90 && captured.add(stage))
            shot(Minecraft.getInstance(), work + "_" + stage.name().toLowerCase(Locale.ROOT));
    }

    static void tick(Minecraft mc) throws Exception {
        if (finished) return;
        if (step == 0 && mc.screen != null) return;
        if (started == 0) started = Util.getMillis();
        if (Util.getMillis() - started > 420_000) throw new IllegalStateException("Store screenshots stalled at step " + step);
        mc.getToasts().clear();
        lockView(mc);
        if (pending != null && !pending.isDone()) return;
        if (pending != null) { pending.join(); pending = null; }
        long age = Util.getMillis() - entered;
        switch (step) {
            case 0 -> {
                Files.createDirectories(Path.of(OUTPUT));
                baseY = mc.player.blockPosition().getY();
                cutting = new BlockPos(-3, baseY, -3);
                reforging = new BlockPos(-1, baseY, -4);
                smithing = new BlockPos(1, baseY, -4);
                salvaging = new BlockPos(3, baseY, -3);
                mc.getWindow().setWindowed(WIDTH, HEIGHT);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.options.fov().set(70);
                mc.options.bobView().set(false);
                mc.options.renderDistance().set(12);
                mc.options.gamma().set(1.0D);
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0D);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                mc.getTutorial().setStep(TutorialSteps.NONE);
                server(mc, StoreScreenshots::buildHall);
                advance(1);
            }
            case 1 -> {
                if (mc.getWindow().getWidth() != WIDTH && age > 1500 && action == 0) { mc.getWindow().toggleFullScreen(); action++; }
                if (age > 7000) {
                    EVIDENCE.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
                    shot(mc, "hall");
                    server(mc, (level, player) -> look(player, -1.6, 1.55, -1.3, -2.6, 0.7, -3.3));
                    advance(2);
                }
            }
            // Gem cutting.
            case 2 -> {
                if (action == 0 && age > 2500) { shot(mc, "cutting_table"); open(mc, cutting); action++; }
                if (action == 1 && mc.screen instanceof AbstractContainerScreen<?> && age > 3500) {
                    server(mc, (level, player) -> {
                        AbstractContainerMenu menu = player.containerMenu;
                        Gem gem = GemRegistry.INSTANCE.getValues().stream().filter(g -> g.getMinRarity().ordinal() == 0).findFirst().orElseThrow();
                        menu.getSlot(0).set(GemRegistry.createGemStack(gem, rarity("common")));
                        menu.getSlot(1).set(new ItemStack(item("apotheosis:gem_dust"), 64));
                        menu.getSlot(2).set(GemRegistry.createGemStack(gem, rarity("common")));
                        menu.getSlot(3).set(new ItemStack(item("apotheosis:common_material"), 16));
                        menu.broadcastChanges();
                    });
                    action++;
                }
                if (action == 2 && age > 4500) { shot(mc, "cutting_menu"); mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0); begin("cut"); advance(3); }
            }
            case 3 -> perform(mc, age, 4);
            // Reforging.
            case 4 -> {
                if (action == 0) { open(mc, reforging); action++; }
                if (action == 1 && mc.screen instanceof AbstractContainerScreen<?> && age > 1200) {
                    server(mc, (level, player) -> {
                        AbstractContainerMenu menu = player.containerMenu;
                        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
                        menu.getSlot(1).set(new ItemStack(item("apotheosis:rare_material"), 32));
                        menu.getSlot(2).set(new ItemStack(item("apotheosis:gem_dust"), 32));
                        menu.broadcastChanges();
                    });
                    action++;
                }
                if (action == 2 && choiceSlot(mc) != null && age > 2600) {
                    shot(mc, "reforge_menu");
                    clickSlot(mc, choiceSlot(mc));
                    begin("reforge");
                    advance(5);
                }
            }
            case 5 -> perform(mc, age, 6);
            // Socketing at the smithing table.
            case 6 -> {
                if (action == 0) { open(mc, smithing); action++; }
                if (action == 1 && mc.screen instanceof AbstractContainerScreen<?> && age > 1200) {
                    server(mc, (level, player) -> {
                        AbstractContainerMenu menu = player.containerMenu;
                        // The blade just reforged moves onto the table; a fresh one stands in if the reforge kept it.
                        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                        var items = player.getInventory().items;
                        for (int i = 0; i < items.size(); i++) if (items.get(i).is(Items.DIAMOND_SWORD)) { sword = items.get(i); items.set(i, ItemStack.EMPTY); break; }
                        SocketHelper.setSockets(sword, Math.max(1, SocketHelper.getSockets(sword)));
                        RandomSource random = RandomSource.create(11);
                        ItemStack gem = ItemStack.EMPTY;
                        for (int i = 0; i < 400 && gem.isEmpty(); i++) {
                            ItemStack candidate = GemRegistry.createRandomGemStack(random, level, 0F);
                            if (GemInstance.socketed(sword, candidate).isValid()) gem = candidate;
                        }
                        menu.getSlot(1).set(sword);
                        menu.getSlot(2).set(gem);
                        menu.broadcastChanges();
                    });
                    action++;
                }
                if (action == 2 && mc.player.containerMenu.getSlot(SmithingMenu.RESULT_SLOT).hasItem() && age > 2600) {
                    shot(mc, "setting_menu");
                    clickSlot(mc, mc.player.containerMenu.getSlot(SmithingMenu.RESULT_SLOT));
                    begin("setting");
                    advance(7);
                }
            }
            case 7 -> perform(mc, age, 8);
            // Salvaging.
            case 8 -> {
                if (action == 0) { open(mc, salvaging); action++; }
                if (action == 1 && mc.screen instanceof AbstractContainerScreen<?> && age > 1200) {
                    server(mc, (level, player) -> {
                        AbstractContainerMenu menu = player.containerMenu;
                        Item[] items = {Items.IRON_SWORD, Items.DIAMOND_AXE, Items.IRON_CHESTPLATE, Items.BOW};
                        for (int i = 0; i < 4; i++)
                            menu.getSlot(i).set(LootController.createLootItem(new ItemStack(items[i]), rarity(i < 2 ? "rare" : "epic"), RandomSource.create(i)));
                        menu.broadcastChanges();
                    });
                    action++;
                }
                if (action == 2 && age > 2600) {
                    shot(mc, "salvage_menu");
                    mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0);
                    begin("salvage");
                    advance(9);
                }
            }
            case 9 -> perform(mc, age, 10);
            // The finished blade.
            case 10 -> {
                if (action == 0 && age > 600) {
                    int slot = -1;
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (stack.is(Items.DIAMOND_SWORD) && !AffixHelper.getAffixes(stack).isEmpty()) slot = i;
                    }
                    EVIDENCE.put("affixed_sword_slot", slot);
                    if (slot >= 0) {
                        hideJeiOverlay();
                        final int s = slot;
                        mc.setScreen(new InventoryScreen(mc.player) {
                            @Override public void render(GuiGraphics g, int mx, int my, float partial) {
                                int x = s < 9 ? leftPos + 8 + s * 18 + 8 : leftPos + 8 + (s % 9) * 18 + 8;
                                int y = s < 9 ? topPos + 142 + 8 : topPos + 84 + (s / 9 - 1) * 18 + 8;
                                super.render(g, x, y, partial);
                            }
                        });
                    }
                    action++;
                }
                if (action == 1 && age > 2400) {
                    if (mc.screen instanceof InventoryScreen) shot(mc, "tooltip");
                    mc.setScreen(null);
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    server(mc, (level, player) -> look(player, 2.8, 1.9, 0.6, -0.3, 0.6, -3.6));
                    advance(11);
                }
            }
            case 11 -> {
                if (age > 3500) {
                    shot(mc, "hall_outro");
                    EVIDENCE.put("shots", SHOTS);
                    EVIDENCE.put("pass", true);
                    Files.writeString(Path.of(OUTPUT, "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
                    finished = true;
                    mc.stop();
                }
            }
        }
    }

    // ------------------------------------------------------------------ performances

    private static void begin(String name) {
        work = name;
        playing = true;
        scriptIndex = -1;
        scriptActions = 0;
        scriptPrevious = WorkInput.IDLE;
        stage = null;
        captured.clear();
    }

    /** Waits for the verdict, captures it, dismisses it and closes the table. */
    private static void perform(Minecraft mc, long age, int next) {
        if (action == 0 && ClientPerformance.verdict().isPresent()) {
            playing = false;
            ResolvedS2C v = ClientPerformance.verdict().orElseThrow();
            EVIDENCE.put(work + "_band", v.band() == null ? "none" : v.band().id);
            EVIDENCE.put(work + "_score", v.score());
            action = 1;
            entered = Util.getMillis();
            return;
        }
        if (action == 1 && age > 1500) { shot(mc, work + "_verdict"); key(mc, GLFW.GLFW_KEY_ENTER); action++; }
        if (action == 2 && ClientPerformance.view().isEmpty() && age > 2000) { key(mc, GLFW.GLFW_KEY_ESCAPE); action++; }
        if (action == 3 && mc.screen == null && age > 2600) advance(next);
    }

    private static void open(Minecraft mc, BlockPos pos) {
        server(mc, (level, player) -> level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static Slot choiceSlot(Minecraft mc) {
        for (Slot slot : mc.player.containerMenu.slots)
            if (slot instanceof ReforgingMenu.ReforgingResultSlot && slot.hasItem()) return slot;
        return null;
    }

    /** Clicks a slot through Forge's pre-click event and the screen's own mouse handling, as the mouse handler does. */
    private static void clickSlot(Minecraft mc, Slot slot) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || slot == null) return;
        double x = screen.getGuiLeft() + slot.x + 8, y = screen.getGuiTop() + slot.y + 8;
        if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0)) screen.mouseClicked(x, y, 0);
        if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, 0)) screen.mouseReleased(x, y, 0);
    }

    private static void key(Minecraft mc, int key) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, key, 0, 0)) screen.keyPressed(key, 0, 0);
        if (mc.screen == screen && !ForgeHooksClient.onScreenKeyReleasedPre(screen, key, 0, 0)) screen.keyReleased(key, 0, 0);
    }

    private static void focusCallback(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        var callback = GLFW.glfwSetWindowFocusCallback(window, null);
        GLFW.glfwSetWindowFocusCallback(window, callback);
        if (callback != null) callback.invoke(window, true);
    }

    // ------------------------------------------------------------------ helpers

    private static net.minecraft.world.level.block.Block block(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    private static LootRarity rarity(String id) {
        return RarityRegistry.INSTANCE.getValue(new ResourceLocation("apotheosis", id));
    }

    private static void shot(Minecraft mc, String name) {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            String file = String.format("%02d_%s.png", SHOTS.size() + 1, name);
            image.writeToFile(Path.of(OUTPUT, file));
            SHOTS.add(file);
        } catch (Exception e) {
            throw new IllegalStateException("Screenshot " + name + " failed", e);
        }
    }

    /** JEI's item list is noise in a tooltip shot; its toggle is internal, so reach it reflectively and carry on without it. */
    private static void hideJeiOverlay() {
        try {
            Object state = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            if ((boolean) state.getClass().getMethod("isOverlayEnabled").invoke(state))
                state.getClass().getMethod("toggleOverlayEnabled").invoke(state);
            EVIDENCE.put("jei_overlay_hidden", true);
        } catch (Exception | LinkageError e) {
            EVIDENCE.put("jei_overlay_hidden", e.toString());
        }
    }

    /** Pins the view to the last scripted camera so pointer motion reaching the window cannot turn the shot. */
    private static void lockView(Minecraft mc) {
        var player = mc.player;
        if (player == null || Float.isNaN(lockYaw)) return;
        player.setYRot(lockYaw);
        player.setXRot(lockPitch);
        player.yRotO = lockYaw;
        player.xRotO = lockPitch;
        player.setYHeadRot(lockYaw);
        player.yHeadRotO = lockYaw;
        player.yBodyRot = lockYaw;
        player.yBodyRotO = lockYaw;
    }

    private static void server(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Store screenshots need an integrated server");
        pending = CompletableFuture.runAsync(() -> work.accept(server.overworld(), server.getPlayerList().getPlayers().get(0)), server);
    }

    /** Places the eye at (x, baseY + y, z) looking at (tx, baseY + ty, tz); flight keeps elevated cameras still. */
    private static void look(ServerPlayer player, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        lockYaw = yaw;
        lockPitch = pitch;
        player.teleportTo(player.serverLevel(), x, baseY + y - player.getEyeHeight(), z, yaw, pitch);
    }

    private static void cmd(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4), command);
    }

    private static void fill(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        cmd(server, "fill " + x1 + " " + (baseY + y1) + " " + z1 + " " + x2 + " " + (baseY + y2) + " " + z2 + " " + block);
    }

    private static void set(MinecraftServer server, int x, int y, int z, String block) {
        cmd(server, "setblock " + x + " " + (baseY + y) + " " + z + " " + block);
    }

    private static void buildHall(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        level.setDayTime(5000);
        level.setWeatherParameters(60000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
        cmd(server, "kill @e[type=!player]");
        fill(server, -16, 0, -16, 16, 10, 12, "air");
        fill(server, -16, -1, -16, 16, -1, 12, "grass_block");
        RandomSource random = RandomSource.create(20260927L);
        for (int x = -7; x <= 7; x++) for (int z = -8; z <= 3; z++) {
            float r = random.nextFloat();
            set(server, x, -1, z, r < .5F ? "polished_blackstone_bricks" : r < .75F ? "polished_blackstone" : r < .9F ? "cracked_polished_blackstone_bricks" : "gilded_blackstone");
        }
        fill(server, -2, -1, -5, 2, -1, -1, "chiseled_polished_blackstone");
        fill(server, -1, -1, -4, 1, -1, -2, "polished_blackstone");
        set(server, 0, -1, -3, "gold_block");
        // Walls of deepslate with blackstone pillars and gilded trim.
        fill(server, -7, 0, -8, 7, 5, -8, "deepslate_bricks");
        fill(server, -7, 0, -8, -7, 5, 3, "deepslate_bricks");
        fill(server, 7, 0, -8, 7, 5, 3, "deepslate_bricks");
        for (int x : new int[]{-7, -4, 0, 4, 7}) fill(server, x, 0, -8, x, 5, -8, "polished_blackstone_bricks");
        for (int z : new int[]{-8, -4, 0, 3}) {
            fill(server, -7, 0, z, -7, 5, z, "polished_blackstone_bricks");
            fill(server, 7, 0, z, 7, 5, z, "polished_blackstone_bricks");
        }
        fill(server, -7, 5, -8, 7, 5, -8, "chiseled_deepslate");
        fill(server, -3, 1, -8, -1, 4, -8, "glass_pane");
        fill(server, 1, 1, -8, 3, 4, -8, "glass_pane");
        fill(server, -7, 1, -3, -7, 3, -1, "glass_pane");
        fill(server, 7, 1, -3, 7, 3, -1, "glass_pane");
        fill(server, -7, 6, -8, 7, 6, 3, "deepslate_tiles");
        for (int x = -5; x <= 5; x += 2) for (int z = -7; z <= 1; z += 2) set(server, x, 6, z, "shroomlight");
        for (int[] p : new int[][]{{-4, -3}, {4, -3}, {0, 1}, {-4, 1}, {4, 1}}) {
            set(server, p[0], 5, p[1], "chain");
            set(server, p[0], 4, p[1], "chain");
            set(server, p[0], 3, p[1], "lantern[hanging=true]");
        }
        // Braziers, a forge corner and the spoils of adventure.
        for (int[] p : new int[][]{{-6, -7}, {6, -7}}) {
            set(server, p[0], 0, p[1], "polished_blackstone_bricks");
            set(server, p[0], 1, p[1], "soul_campfire");
        }
        set(server, -6, 0, -3, "anvil[facing=east]");
        set(server, -6, 0, -1, "grindstone[face=floor,facing=east]");
        set(server, 6, 0, -3, "chest[facing=west]");
        set(server, 6, 0, -2, "barrel[facing=up]");
        set(server, 6, 1, -2, "amethyst_cluster[facing=up]");
        set(server, 5, 0, -7, "enchanting_table");
        set(server, -5, 0, -7, "lodestone");
        cmd(server, "summon armor_stand 5.5 " + baseY + " 0.5 {Rotation:[120f,0f],ShowArms:1b,"
                + "ArmorItems:[{id:\"minecraft:diamond_boots\",Count:1b},{id:\"minecraft:diamond_leggings\",Count:1b},"
                + "{id:\"minecraft:diamond_chestplate\",Count:1b,tag:{Enchantments:[{id:\"minecraft:protection\",lvl:4s}]}},{id:\"minecraft:diamond_helmet\",Count:1b}],"
                + "HandItems:[{id:\"minecraft:diamond_sword\",Count:1b,tag:{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}},{}]}");
        // The four Apotheosis tables.
        level.setBlockAndUpdate(cutting, block("apotheosis:gem_cutting_table").defaultBlockState());
        level.setBlockAndUpdate(reforging, block("apotheosis:reforging_table").defaultBlockState());
        level.setBlockAndUpdate(smithing, Blocks.SMITHING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(salvaging, block("apotheosis:salvaging_table").defaultBlockState());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.setExperienceLevels(100);
        player.inventoryMenu.broadcastChanges();
        look(player, 0.5, 1.8, 0.3, 0.3, 0.6, -3.6);
    }

    private static void advance(int next) {
        step = next;
        action = 0;
        entered = Util.getMillis();
    }

    private StoreScreenshots() {}
}
