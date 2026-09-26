package com.otectus.immersiveapotheosis.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.client.ClientPerformance;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.minigame.WorkStage;
import com.otectus.immersiveapotheosis.minigame.WorkAutoplay;
import com.otectus.immersiveapotheosis.network.ResolvedS2C;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingMenu;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Drives a dev client through Apotheosis's stations, performed: a gem cut (the table's button), a reforge (a choice
 * slot clicked through the screen's mouse path), a gem setting, and completed batch dismantling, followed by a free
 * count-in cancel. Pointer/tool gestures use real Forge GUI hooks and the native cursor. Server
 * state is checked after each; screenshots go to {@code run/screenshots/smoketest_*.png}, the verdict to
 * {@code smoketest-result.json}, then the client quits.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    /** Set by tools/packtest: the agent runs inside the Ultima modpack and adds the pack-only checks. */
    private static final boolean PACK = Boolean.getBoolean("immersive_apotheosis.smoketest.pack");
    private static int settledTicks;
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final Map<String, Object> server = new ConcurrentHashMap<>();
    private static final long START = Util.getMillis();

    private static final List<Step> steps = new ArrayList<>();
    private static int stepIndex;
    private static int stepTicks;
    private static int titleTicks;
    private static boolean worldRequested;
    private static boolean finished;
    private static BlockPos cutting;
    private static BlockPos reforging;
    private static BlockPos salvaging;
    private static BlockPos smithing;

    private static int scriptIndex;
    private static boolean playingWork;
    private static String workName;
    private static WorkStage captureStage;
    private static int captureStageStep;
    private static final java.util.Set<WorkStage> capturedStages = java.util.EnumSet.noneOf(WorkStage.class);
    private static WorkInput scriptPrevious = WorkInput.IDLE;
    private static int scriptActions;
    /** What was actually delivered, at the step it was delivered, and whether a handler took it. */
    private static final List<WorkInput> delivered = new ArrayList<>();
    private static int consumed;
    private static long focusProbeAt;
    private static boolean focusProbed;
    private static int resizeProbes;

    private record Step(String name, int timeoutTicks, Predicate<Minecraft> action) {}

    static {
        plan();
    }

    private static void step(String name, int timeoutTicks, Predicate<Minecraft> action) {
        steps.add(new Step(name, timeoutTicks, action));
    }

    private static void act(String name, int waitTicks, java.util.function.Consumer<Minecraft> action) {
        steps.add(new Step(name, waitTicks + 5, mc -> {
            if (stepTicks == 0) action.accept(mc);
            return stepTicks >= waitTicks;
        }));
    }

    private static void plan() {
        if (PACK) {
            // Packs open their own screens on first join (Ultima: the Origins choice); settle them first.
            step("settle pack screens", 600, mc -> {
                if (mc.screen != null) {
                    settlePackScreen(mc);
                    settledTicks = 0;
                    return false;
                }
                return ++settledTicks >= 60;
            });
        }
        act("stage", 20, ClientSmokeTest::stageWorld);

        // 1. A perfect gem cut through the table's button.
        open(() -> cutting, GemCuttingMenu.class);
        act("load cut", 10, mc -> server(s -> {
            AbstractContainerMenu menu = player(s).containerMenu;
            Gem gem = GemRegistry.INSTANCE.getValues().stream().filter(g -> g.getMinRarity().ordinal() == 0).findFirst().orElseThrow();
            menu.getSlot(0).set(GemRegistry.createGemStack(gem, rarity("common")));
            menu.getSlot(1).set(new ItemStack(item("apotheosis:gem_dust"), 64));
            menu.getSlot(2).set(GemRegistry.createGemStack(gem, rarity("common")));
            menu.getSlot(3).set(new ItemStack(item("apotheosis:common_material"), 16));
            menu.broadcastChanges();
        }));
        act("click cut", 2, mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0));
        perform("cut", () -> server(s -> {
            ItemStack main = player(s).containerMenu.getSlot(0).getItem();
            server.put("cut_upgraded", GemInstance.unsocketed(main).rarity().isBound() && GemInstance.unsocketed(main).rarity().get().ordinal() == 1);
        }));
        check("cut_upgraded");
        close();

        // 2. A perfect reforge: a choice slot clicked through the screen's mouse path.
        open(() -> reforging, ReforgingMenu.class);
        act("load reforge", 10, mc -> server(s -> {
            AbstractContainerMenu menu = player(s).containerMenu;
            menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            menu.getSlot(1).set(new ItemStack(item("apotheosis:rare_material"), 32));
            menu.getSlot(2).set(new ItemStack(item("apotheosis:gem_dust"), 32));
            menu.broadcastChanges();
        }));
        step("choices", 60, mc -> choiceSlot(mc) != null);
        act("click choice", 2, mc -> clickSlot(mc, choiceSlot(mc)));
        perform("reforge", () -> server(s -> {
            ServerPlayer p = player(s);
            ItemStack sword = p.getInventory().items.stream().filter(i -> i.is(Items.IRON_SWORD)).findFirst().orElse(ItemStack.EMPTY);
            server.put("reforge_received", !sword.isEmpty() && !AffixHelper.getAffixes(sword).isEmpty());
        }));
        check("reforge_received");
        close();

        // 3. A perfect gem setting at the smithing table.
        open(() -> smithing, SmithingMenu.class);
        act("load setting", 10, mc -> server(s -> {
            ServerPlayer p = player(s);
            AbstractContainerMenu menu = p.containerMenu;
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            SocketHelper.setSockets(sword, 1);
            RandomSource random = RandomSource.create(4);
            ItemStack gem = ItemStack.EMPTY;
            for (int i = 0; i < 200 && gem.isEmpty(); i++) {
                ItemStack candidate = GemRegistry.createRandomGemStack(random, s.overworld(), 0F);
                if (GemInstance.socketed(sword, candidate).isValid()) gem = candidate;
            }
            menu.getSlot(1).set(sword);
            menu.getSlot(2).set(gem);
            menu.broadcastChanges();
        }));
        step("socketing result", 60, mc -> mc.player.containerMenu.getSlot(SmithingMenu.RESULT_SLOT).hasItem());
        act("click result", 2, mc -> clickSlot(mc, mc.player.containerMenu.getSlot(SmithingMenu.RESULT_SLOT)));
        perform("setting", () -> server(s -> {
            ItemStack sword = player(s).getInventory().items.stream().filter(i -> i.is(Items.IRON_SWORD) && SocketHelper.getGems(i)
                    .streamValidGems().findAny().isPresent()).findFirst().orElse(ItemStack.EMPTY);
            server.put("setting_socketed", !sword.isEmpty());
        }));
        check("setting_socketed");
        close();

        // 4. Complete a real dismantling, then independently prove the count-in remains free.
        open(() -> salvaging, SalvagingMenu.class);
        act("load salvage", 10, mc -> server(s -> {
            AbstractContainerMenu menu = player(s).containerMenu;
            for (int i = 0; i < 4; i++) {
                menu.getSlot(i).set(LootController.createLootItem(new ItemStack(Items.IRON_SWORD), rarity("rare"), RandomSource.create(i)));
            }
            menu.broadcastChanges();
        }));
        act("click salvage", 2, mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0));
        perform("salvage", () -> server(s -> {
            AbstractContainerMenu menu = player(s).containerMenu;
            boolean consumedInputs = true;
            int outputs = 0;
            for (int i = 0; i < 12; i++) consumedInputs &= menu.getSlot(i).getItem().isEmpty();
            for (int i = 12; i < 18; i++) outputs += menu.getSlot(i).getItem().getCount();
            server.put("salvage_completed", consumedInputs && outputs > 0);
            server.put("salvage_output_items", outputs);
        }));
        check("salvage_completed");
        close();

        open(() -> salvaging, SalvagingMenu.class);
        act("reload salvage cancel", 10, mc -> server(s -> {
            AbstractContainerMenu menu = player(s).containerMenu;
            for (int i = 0; i < 4; i++) menu.getSlot(i).set(LootController.createLootItem(new ItemStack(Items.IRON_SWORD), rarity("rare"), RandomSource.create(99 + i)));
            menu.broadcastChanges();
        }));
        act("click salvage cancel", 2, mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, 0));
        step("salvage started", 100, mc -> ClientPerformance.view().isPresent());
        act("shot count-in", 3, mc -> shot(mc, "smoketest_salvage_count_in"));
        act("escape", 20, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
        step("cancelled", 100, mc -> ClientPerformance.verdict().isPresent());
        act("cancel checked", 10, mc -> {
            ResolvedS2C v = ClientPerformance.verdict().orElseThrow();
            checks.put("salvage_cancel_free_band", v.band() == null);
            server(s -> server.put("salvage_inputs_kept", !player(s).containerMenu.getSlot(0).getItem().isEmpty()));
        });
        check("salvage_inputs_kept");

        if (PACK) {
            act("pack checks", 10, mc -> server(s -> {
                Map<String, Object> pack = new LinkedHashMap<>();
                PackChecks.run(player(s), pack);
                server.put("pack", pack);
            }));
            step("pack checks collected", 60, mc -> {
                Object pack = server.get("pack");
                if (!(pack instanceof Map<?, ?> m)) return false;
                m.forEach((k, v) -> checks.put("pack_" + k, v));
                return true;
            });
        }
    }

    /**
     * Origins (Ultima's heritage choice) reopens its screen until an origin is chosen, so its Select button takes the
     * offered origin as a player would; any other pack screen is closed. (As Immersive Smithing's pack agent does.)
     */
    private static void settlePackScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen.getClass().getName().contains(".origins.")) {
            for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button && button.active
                        && "Select".equals(button.getMessage().getString())) {
                    log("choosing the offered origin on " + screen.getClass().getName());
                    button.onPress();
                    return;
                }
            }
            return;
        }
        log("closing pack screen " + screen.getClass().getName());
        mc.setScreen(null);
    }

    /** Opens a table through its block, as a right click would, and waits for its menu on the client. */
    private static void open(java.util.function.Supplier<BlockPos> pos, Class<?> menu) {
        act("open " + menu.getSimpleName(), 5, mc -> server(s -> {
            ServerPlayer p = player(s);
            BlockPos at = pos.get();
            s.overworld().getBlockState(at).use(s.overworld(), p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        }));
        step(menu.getSimpleName() + " open", 60, mc -> menu.isInstance(mc.player.containerMenu) && mc.screen instanceof AbstractContainerScreen<?>);
    }

    /** Plays a started workbench through real GUI input, then checks its authoritative result. */
    private static void perform(String name, Runnable verify) {
        step(name + " started", 160, mc -> ClientPerformance.view().isPresent());
        act(name + " script", 1, mc -> {
            // The next gesture is selected from the actual client prediction after each sampled frame.
            playingWork = true;
            workName = name;
            captureStage = null;
            capturedStages.clear();
            scriptIndex = -1;
            scriptPrevious = WorkInput.IDLE;
            scriptActions = 0;
            delivered.clear();
            consumed = 0;
        });
        step(name + " midway", 1800, mc -> ClientPerformance.step() > WorkDefinition.COUNT_IN_STEPS + 400);
        act(name + " shot run", 2, mc -> shot(mc, "smoketest_" + name + "_run"));
        step(name + " verdict", 3600, mc -> ClientPerformance.verdict().isPresent());
        act(name + " verdict checked", 12, mc -> {
            playingWork = false;
            ResolvedS2C v = ClientPerformance.verdict().orElseThrow();
            checks.put(name + "_band", v.band() == null ? "none" : v.band().id);
            checks.put(name + "_score", v.score());
            checks.put(name + "_full_result", v.band() != null && v.band().grantsFull());
            checks.put(name + "_delivered", delivered.size() + " reactive frames (consumed " + consumed + ")");
            ClientPerformance.view().ifPresent(p -> checks.put(name + "_stage", p.stage().name()));
            shot(mc, "smoketest_" + name + "_verdict");
            verify.run();
            if (v.band() == null || !v.band().grantsFull()) finish(mc, false, name + " gesture failed");
        });
        step(name + " overlay cleared", 200, mc -> ClientPerformance.view().isEmpty());
    }

    private static void check(String key) {
        step("check " + key, 60, mc -> {
            if (!server.containsKey(key)) return false;
            checks.put(key, server.get(key));
            return true;
        });
    }

    private static void close() {
        act("close", 10, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
        step("closed", 40, mc -> mc.screen == null);
    }

    // ---- driving ----------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (OUTPUT == null || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player == null || mc.level == null) {
                if (mc.screen instanceof TitleScreen && ++titleTicks == 1) {
                    mc.options.pauseOnLostFocus = false;
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(120);
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    sizeWindow(mc, 1280, 720);
                }
                if (mc.screen instanceof TitleScreen && titleTicks == 40 && !worldRequested) {
                    worldRequested = true;
                    createWorld(mc);
                }
                return;
            }
            if (stepIndex >= steps.size()) {
                finish(mc, allPassed(), "complete");
                return;
            }
            Step step = steps.get(stepIndex);
            boolean done = step.action().test(mc);
            stepTicks++;
            if (done) {
                log("step done: " + step.name() + " (" + stepTicks + " ticks)");
                stepIndex++;
                stepTicks = 0;
            } else if (stepTicks > step.timeoutTicks()) {
                checks.put("timeout", step.name());
                checks.put("timeout_screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
                finish(mc, false, "timed out at " + step.name());
            }
        } catch (Exception e) {
            ImmersiveApotheosis.LOGGER.error("SMOKETEST failed at step {}", stepIndex < steps.size() ? steps.get(stepIndex).name() : "end", e);
            checks.put("agent_exception", e.toString());
            finish(mc, false, "agent exception");
        }
    }

    /** Delivers reactive gestures after the workbench observes this rendered frame's native pointer. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        deliver(event.getScreen());
    }

    private static void deliver(Screen screen) {
        if (!playingWork || ClientPerformance.view().isEmpty() || ClientPerformance.verdict().isPresent()) return;
        int activeStep = ClientPerformance.view().orElseThrow().elapsedSteps();
        Minecraft mc = Minecraft.getInstance();
        if ("cut".equals(workName) && !focusProbed && activeStep > 120
                && ClientPerformance.input().map(WorkInput::primary).orElse(false)) {
            focusProbeAt = Util.getMillis();
            focusProbed = true;
            focusCallback(mc, false);
            return;
        }
        if (focusProbeAt != 0) {
            if (Util.getMillis() - focusProbeAt < 350) return;
            checks.put("focus_loss_releases_tools", ClientPerformance.input().orElseThrow().actions() == 0);
            focusCallback(mc, true);
            focusProbeAt = 0;
            scriptActions = 0;
            var cursor = ClientPerformance.view().orElseThrow().cursor();
            scriptPrevious = new WorkInput(cursor.x(), cursor.y(), 0);
        }
        if (!mc.isWindowActive()) {
            // Keep this synthetic-input session active even when the desktop user works in another window.
            // The explicit probe above separately verifies the real focus-loss release behavior.
            focusCallback(mc, true);
            scriptActions = 0;
            var cursor = ClientPerformance.view().orElseThrow().cursor();
            scriptPrevious = new WorkInput(cursor.x(), cursor.y(), 0);
        }
        if ("reforge".equals(workName) && resizeProbes < 2 && activeStep >= 400 + resizeProbes * 250) {
            mc.options.guiScale().set(resizeProbes == 0 ? 2 : 3);
            mc.resizeDisplay();
            resizeProbes++;
            checks.put("gui_scale_changed_during_work", resizeProbes == 2);
        }
        if (activeStep <= scriptIndex) return;
        while (scriptIndex < activeStep) {
            WorkInput e = WorkAutoplay.next(ClientPerformance.view().orElseThrow(), scriptPrevious);
            if (activeStep / 100 > scriptIndex / 100) {
                var view = ClientPerformance.view().orElseThrow();
                log("gesture " + workName + " frame=" + activeStep + " stage=" + view.stage()
                        + " observed=" + ClientPerformance.input() + " requested=" + e + " progress=" + view.progress()
                        + " focused=" + Minecraft.getInstance().isWindowActive());
            }
            scriptIndex = activeStep;
            ClientPerformance.Workspace area = ClientPerformance.workspace().orElseThrow();
            double x = area.left() + e.x() * area.width() / 4095.0;
            double y = area.top() + e.y() * area.height() / 4095.0;
            // Keep the actual native pointer in sync so Render.Pre observes the same position between gestures.
            var window = Minecraft.getInstance().getWindow();
            double nativeX = x * window.getScreenWidth() / screen.width;
            double nativeY = y * window.getScreenHeight() / screen.height;
            GLFW.glfwSetCursorPos(window.getWindow(), nativeX, nativeY);
            // XWayland may suppress cursor-warp notifications. Dispatch the registered native movement callback
            // as well, so MouseHandler and subsequent Render.Pre events observe the same input as the GUI hooks.
            var callback = GLFW.glfwSetCursorPosCallback(window.getWindow(), null);
            GLFW.glfwSetCursorPosCallback(window.getWindow(), callback);
            if (callback != null) callback.invoke(window.getWindow(), nativeX, nativeY);
            delivered.add(e);
            int changed = scriptActions ^ e.actions();
            // A synthetic press/release moves the pointer through the same Forge screen hooks when no tool is held.
            // With a held tool, use the real drag route so ClientPerformance receives every new coordinate.
            if (e.actions() != 0 && scriptActions == e.actions()) {
                int button = (e.actions() & WorkInput.PRIMARY) != 0 ? 0 : 1;
                if (!ForgeHooksClient.onScreenMouseDragPre(screen, x, y, button, 0, 0)) screen.mouseDragged(x, y, button, 0, 0);
            } else if (e.actions() == 0 && changed == 0) {
                if (ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0)) consumed++;
                else screen.mouseClicked(x, y, 0);
                if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, 0)) screen.mouseReleased(x, y, 0);
            }
            for (int button = 0; button < 2; button++) {
                int bit = button == 0 ? WorkInput.PRIMARY : WorkInput.SECONDARY;
                if ((changed & bit) == 0) continue;
                boolean down = (e.actions() & bit) != 0;
                if ("reforge".equals(workName)) {
                    int keyCode = button == 0 ? GLFW.GLFW_KEY_SPACE : GLFW.GLFW_KEY_LEFT_SHIFT;
                    if (down) {
                        if (ForgeHooksClient.onScreenKeyPressedPre(screen, keyCode, 0, 0)) consumed++;
                        else screen.keyPressed(keyCode, 0, 0);
                    } else {
                        if (ForgeHooksClient.onScreenKeyReleasedPre(screen, keyCode, 0, 0)) consumed++;
                        else screen.keyReleased(keyCode, 0, 0);
                    }
                } else if (down) {
                    if (ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, button)) consumed++;
                    else screen.mouseClicked(x, y, button);
                } else {
                    if (ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, button)) consumed++;
                    else screen.mouseReleased(x, y, button);
                }
            }
            scriptActions = e.actions();
            scriptPrevious = e;
        }
    }

    private static void focusCallback(Minecraft mc, boolean focused) {
        long window = mc.getWindow().getWindow();
        var callback = GLFW.glfwSetWindowFocusCallback(window, null);
        GLFW.glfwSetWindowFocusCallback(window, callback);
        if (callback == null) throw new IllegalStateException("Missing native focus callback");
        callback.invoke(window, focused);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void captureStage(ScreenEvent.Render.Post event) {
        if (!playingWork || ClientPerformance.view().isEmpty()) return;
        var state = ClientPerformance.view().orElseThrow();
        if (state.stage() != captureStage) {
            captureStage = state.stage();
            captureStageStep = state.elapsedSteps();
        }
        if (state.elapsedSteps() >= captureStageStep + 30 && capturedStages.add(captureStage)) {
            shot(Minecraft.getInstance(), "smoketest_" + workName + "_" + captureStage.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static void key(Minecraft mc, int key) {
        Screen screen = mc.screen;
        if (screen == null) return;
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, key, 0, 0)) screen.keyPressed(key, 0, 0);
        if (mc.screen == screen && !ForgeHooksClient.onScreenKeyReleasedPre(screen, key, 0, 0)) screen.keyReleased(key, 0, 0);
    }

    /** Clicks a slot through Forge's pre-click event and the screen's own mouse handling, as the mouse handler does. */
    private static void clickSlot(Minecraft mc, Slot slot) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || slot == null) return;
        double x = screen.getGuiLeft() + slot.x + 8;
        double y = screen.getGuiTop() + slot.y + 8;
        if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, 0)) screen.mouseClicked(x, y, 0);
        if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, 0)) screen.mouseReleased(x, y, 0);
    }

    private static Slot choiceSlot(Minecraft mc) {
        for (Slot slot : mc.player.containerMenu.slots) {
            if (slot instanceof ReforgingMenu.ReforgingResultSlot && slot.hasItem()) return slot;
        }
        return null;
    }

    /**
     * A predictable, visible test window (as Immersive Enchanting's agent does). A tiling Wayland compositor ignores
     * the requested size and throttles windows it does not show, which stalls the performance clock, so on Hyprland
     * this client's own window (matched by process id) is floated, sized and moved; nothing else is touched.
     */
    private static void sizeWindow(Minecraft mc, int width, int height) {
        mc.getWindow().setWindowed(width, height);
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") == null) return;
        String target = "window = \"pid:" + ProcessHandle.current().pid() + "\"";
        Thread thread = new Thread(() -> {
            for (String dispatch : new String[] {"hl.dsp.window.float({ action = \"enable\", " + target + " })",
                    "hl.dsp.window.resize({ x = " + width + ", y = " + height + ", " + target + " })",
                    "hl.dsp.window.move({ x = 40, y = 40, " + target + " })"}) {
                try {
                    new ProcessBuilder("hyprctl", "dispatch", dispatch).redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                } catch (IOException e) {
                    log("hyprctl unavailable: " + e.getMessage());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "smoketest-window");
        thread.setDaemon(true);
        thread.start();
    }

    private static void createWorld(Minecraft mc) {
        String name = "SmokeTest" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260926L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void stageWorld(Minecraft mc) {
        server(s -> {
            ServerLevel level = s.overworld();
            ServerPlayer player = player(s);
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
            BlockPos base = player.blockPosition();
            for (int x = -5; x <= 5; x++) for (int z = -2; z <= 6; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            cutting = base.offset(-3, 0, 3);
            reforging = base.offset(-1, 0, 3);
            smithing = base.offset(1, 0, 3);
            salvaging = base.offset(3, 0, 3);
            level.setBlockAndUpdate(cutting, block("apotheosis:gem_cutting_table").defaultBlockState());
            level.setBlockAndUpdate(reforging, block("apotheosis:reforging_table").defaultBlockState());
            level.setBlockAndUpdate(smithing, Blocks.SMITHING_TABLE.defaultBlockState());
            level.setBlockAndUpdate(salvaging, block("apotheosis:salvaging_table").defaultBlockState());
            player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 1.5, 0F, 30F);
            player.setExperienceLevels(100);
        });
    }

    private static Block block(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    private static LootRarity rarity(String id) {
        return RarityRegistry.INSTANCE.getValue(new ResourceLocation("apotheosis", id));
    }

    private static void server(java.util.function.Consumer<IntegratedServer> action) {
        IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> action.accept(s));
    }

    private static ServerPlayer player(IntegratedServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> log("screenshot " + name));
    }

    private static boolean allPassed() {
        for (Object value : checks.values()) if (value instanceof Boolean b && !b) return false;
        return !checks.containsKey("agent_exception") && !checks.containsKey("timeout");
    }

    private static void finish(Minecraft mc, boolean pass, String reason) {
        finished = true;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", pass);
        result.put("reason", reason);
        result.put("seconds", (Util.getMillis() - START) / 1000);
        result.put("checks", checks);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "smoketest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            ImmersiveApotheosis.LOGGER.error("SMOKETEST could not write result", e);
        }
        log("finished: pass=" + pass + " (" + reason + ")");
        mc.stop();
    }

    private static void log(String message) {
        ImmersiveApotheosis.LOGGER.info("SMOKETEST {}", message);
    }

    private ClientSmokeTest() {}
}
