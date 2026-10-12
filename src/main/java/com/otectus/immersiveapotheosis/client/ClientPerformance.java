package com.otectus.immersiveapotheosis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.minigame.WorkInput;
import com.otectus.immersiveapotheosis.minigame.WorkSimulation;
import com.otectus.immersiveapotheosis.minigame.WorkState;
import com.otectus.immersiveapotheosis.minigame.WorkStage;
import com.otectus.immersiveapotheosis.minigame.ReforgeState;
import com.otectus.immersiveapotheosis.network.CancelC2S;
import com.otectus.immersiveapotheosis.network.InputsC2S;
import com.otectus.immersiveapotheosis.network.ModNetwork;
import com.otectus.immersiveapotheosis.network.ResolvedS2C;
import com.otectus.immersiveapotheosis.network.StartS2C;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/**
 * Captures real GUI pointer work and predicts the server-owned deterministic workbench. The server's verdict stays on
 * the overlay until the player dismisses it (Esc, Enter, the work key or a click, after a short grace so the stroke
 * that finished the work cannot dismiss it); a key still held from the work, or its auto-repeat, never counts. If the
 * menu closes first - or the verdict arrives after it closed, as an abandonment charged on closing does - it is written
 * to chat instead, so the outcome can always be read.
 */
@Mod.EventBusSubscriber(modid = ImmersiveApotheosis.MOD_ID, value = Dist.CLIENT)
public final class ClientPerformance {
    public static final KeyMapping PERFORM = new KeyMapping("key.immersive_apotheosis.perform",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, "key.categories.immersive_apotheosis");
    public static final KeyMapping CONTEXT = new KeyMapping("key.immersive_apotheosis.context",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.immersive_apotheosis");
    private static final int MAX_CATCHUP = 25;
    private static final int FLUSH_STEPS = 5;
    private static final long VERDICT_DISMISS_GRACE_MS = 750;
    @Nullable private static Run run;
    /** A run whose overlay closed before its verdict arrived: the late verdict goes to chat. */
    @Nullable private static StartS2C closedBeforeVerdict;
    @Nullable private static Component lastChatVerdict;

    static final class Run {
        final StartS2C start;
        final WorkSimulation simulation;
        final long startedMs = Util.getMillis();
        final List<WorkInput> pending = new ArrayList<>();
        int pendingFirstStep;
        int sampledSteps;
        int pointerX = 2048;
        int pointerY = 2048;
        boolean primaryMouse, primaryKey, secondaryMouse, secondaryKey;
        boolean submitted, confirmAbandon, inside;
        private record Observation(int step, WorkInput input) {}
        final ArrayDeque<Observation> observations = new ArrayDeque<>();
        WorkInput heldSample = WorkInput.IDLE;
        long soundMs;
        WorkStage soundedStage;
        @Nullable ResolvedS2C verdict;
        long verdictMs;
        /** Keys down since the work began; a repeat of one of these is not a new press. */
        final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();

        Run(StartS2C start) {
            this.start = start;
            simulation = WorkSimulation.create(start.definition());
        }

        int clockSteps() {
            return (int) Math.min(WorkDefinition.COUNT_IN_STEPS + WorkDefinition.MAX_STEPS,
                    Math.max(0L, (Util.getMillis() - startedMs) / WorkDefinition.STEP_MS));
        }

        int actions() {
            if (!inside || submitted) return 0;
            return (primaryMouse || primaryKey ? WorkInput.PRIMARY : 0)
                    | (secondaryMouse || secondaryKey ? WorkInput.SECONDARY : 0);
        }

        WorkInput sample() {
            return WorkInput.normalized(pointerX, pointerY, actions());
        }

        void observe() {
            if (submitted || verdict != null) return;
            int step = Math.max(0, clockSteps() - WorkDefinition.COUNT_IN_STEPS);
            WorkInput input = sample();
            if (!observations.isEmpty() && observations.peekLast().step() == step) observations.removeLast();
            observations.addLast(new Observation(step, input));
        }

        void advance() {
            if (submitted || verdict != null) return;
            if (!Minecraft.getInstance().isWindowActive() && actions() != 0) {
                releaseTools();
                observe();
            }
            int wanted = Math.max(0, clockSteps() - WorkDefinition.COUNT_IN_STEPS);
            int count = Math.min(MAX_CATCHUP, Math.max(0, wanted - sampledSteps));
            while (count-- > 0 && !simulation.finished()) {
                if (pending.isEmpty()) pendingFirstStep = sampledSteps;
                while (!observations.isEmpty() && observations.peekFirst().step() <= sampledSteps) {
                    heldSample = observations.removeFirst().input();
                }
                WorkInput frame = heldSample;
                pending.add(frame);
                simulation.step(frame);
                sampledSteps++;
                if (pending.size() >= FLUSH_STEPS) flush();
            }
            processSound();
            if (simulation.finished()) {
                flush();
                submitted = true;
            }
        }

        void flush() {
            if (pending.isEmpty()) return;
            ModNetwork.sendToServer(new InputsC2S(start.session(), pendingFirstStep, List.copyOf(pending)));
            pending.clear();
        }

        void releaseTools() {
            primaryMouse = primaryKey = secondaryMouse = secondaryKey = false;
        }

        void processSound() {
            if (clockSteps() < WorkDefinition.COUNT_IN_STEPS) return;
            WorkState state = simulation.state();
            long now = Util.getMillis();
            boolean transition = soundedStage != null && soundedStage != state.stage();
            if (transition) sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, .22F);
            soundedStage = state.stage();
            if (actions() == 0 || now - soundMs < 450) return;
            soundMs = now;
            switch (state.stage()) {
                case CUT_SEAMS, EXTRACT_COMPONENTS -> sound(SoundEvents.STONE_HIT, .8F, .18F);
                case BEND_PRONGS -> sound(SoundEvents.CHAIN_STEP, 1.2F, .2F);
                case GRIND_FACETS -> sound(SoundEvents.GRINDSTONE_USE, 1.2F, .22F);
                case POLISH_FACETS -> sound(SoundEvents.SAND_HIT, 1.4F, .16F);
                case HEAT_BINDINGS -> sound(SoundEvents.FIRE_AMBIENT, 1.3F, .16F);
                case TRACE_SEAL -> sound(SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, .16F);
                default -> { }
            }
        }

        void pointer(double mouseX, double mouseY, AbstractContainerScreen<?> screen) {
            int left = PerformanceOverlay.workspaceLeft(screen);
            int top = PerformanceOverlay.workspaceTop(screen);
            int width = PerformanceOverlay.workspaceWidth(screen);
            int height = PerformanceOverlay.workspaceHeight(screen);
            if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height) {
                inside = false;
                releaseTools();
                return;
            }
            inside = true;
            pointerX = Math.round(((float) mouseX - left) * WorkInput.MAX_COORD / Math.max(1, width));
            pointerY = Math.round(((float) mouseY - top) * WorkInput.MAX_COORD / Math.max(1, height));
        }
    }

    public static void start(StartS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu.containerId != message.containerId()
                || !(minecraft.screen instanceof AbstractContainerScreen<?>)) return;
        run = new Run(message);
        sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F);
    }

    public static void resolved(ResolvedS2C message) {
        if (run == null || !run.start.session().equals(message.session())) {
            if (closedBeforeVerdict != null && closedBeforeVerdict.session().equals(message.session())) {
                toChat(closedBeforeVerdict, message);
                closedBeforeVerdict = null;
            }
            return;
        }
        run.releaseTools();
        run.pending.clear();
        run.observations.clear();
        run.verdict = message;
        run.verdictMs = Util.getMillis();
        run.submitted = true;
        boolean good = message.band() != null && !message.band().isFailure();
        sound(good ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ANVIL_LAND, good ? 1.4F : .8F);
    }

    public static Optional<WorkState> view() { return run == null ? Optional.empty() : Optional.of(run.simulation.state()); }
    public static Optional<WorkDefinition> definition() { return run == null ? Optional.empty() : Optional.of(run.simulation.definition()); }
    public static Optional<WorkInput> input() { return run == null ? Optional.empty() : Optional.of(run.heldSample); }
    public static int step() { return run == null ? Integer.MIN_VALUE : run.clockSteps(); }
    public static Optional<ResolvedS2C> verdict() { return run == null ? Optional.empty() : Optional.ofNullable(run.verdict); }
    /** The last verdict written to chat because its overlay had closed. For the client smoke test. */
    public static Optional<Component> lastChatVerdict() { return Optional.ofNullable(lastChatVerdict); }
    public record Workspace(int left, int top, int width, int height) {}
    public static Optional<Workspace> workspace() { return run == null ? Optional.empty() : Optional.of(PerformanceOverlay.workspace()); }

    private static boolean active(ScreenEvent event) {
        return run != null && event.getScreen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().containerId == run.start.containerId();
    }

    private static void updatePointer(double x, double y, AbstractContainerScreen<?> screen) {
        run.advance(); // Always score the previously observed pointer state before replacing it.
        run.pointer(x, y, screen);
        run.observe();
    }

    /** The overlay goes away without the player dismissing it: a verdict (present or still to come) goes to chat. */
    private static void discard() {
        if (run == null) return;
        if (run.verdict != null) toChat(run.start, run.verdict);
        else closedBeforeVerdict = run.start;
        run = null;
    }

    private static void toChat(StartS2C start, ResolvedS2C verdict) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Component line = verdict.band() == null
                ? Component.translatable("performance.immersive_apotheosis.chat_verdict_plain", start.title(), verdict.summary())
                : Component.translatable("performance.immersive_apotheosis.chat_verdict", start.title(),
                        Component.translatable("performance.immersive_apotheosis.band." + verdict.band().id)
                                .withStyle(verdict.band().isFailure() ? ChatFormatting.RED : ChatFormatting.GOLD),
                        Math.round(verdict.score()), verdict.summary());
        minecraft.gui.getChat().addMessage(line);
        lastChatVerdict = line;
    }

    private static boolean dismissable() {
        return run.verdict != null && Util.getMillis() - run.verdictMs >= VERDICT_DISMISS_GRACE_MS;
    }

    private static void escape() {
        run.advance();
        if (run.verdict != null) { run = null; return; }
        if (run.submitted) return;
        if (run.clockSteps() < WorkDefinition.COUNT_IN_STEPS || run.confirmAbandon) {
            run.releaseTools();
            run.flush();
            run.submitted = true;
            ModNetwork.sendToServer(new CancelC2S(run.start.session()));
        } else run.confirmAbandon = true;
    }

    @SubscribeEvent public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (!active(event)) return;
        // Render events round to GUI pixels. Preserve native pointer precision for small polishing and seam targets,
        // especially at large GUI scales, exactly as the mouse-drag events do.
        Minecraft mc = Minecraft.getInstance();
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        updatePointer(mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth(),
                mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight(), screen);
    }

    @SubscribeEvent public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!active(event)) return;
        updatePointer(event.getMouseX(), event.getMouseY(), (AbstractContainerScreen<?>) event.getScreen());
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!active(event)) return;
        if (run.verdict != null) {
            if (dismissable()) run = null;
            event.setCanceled(true);
            return;
        }
        updatePointer(event.getMouseX(), event.getMouseY(), (AbstractContainerScreen<?>) event.getScreen());
        if (run.inside && !run.submitted && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) run.primaryMouse = true;
        if (run.inside && !run.submitted && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) run.secondaryMouse = true;
        run.observe();
        run.flush();
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!active(event)) return;
        updatePointer(event.getMouseX(), event.getMouseY(), (AbstractContainerScreen<?>) event.getScreen());
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (run.primaryMouse && run.simulation.state() instanceof ReforgeState state && state.hammerCharge() >= .35)
                sound(SoundEvents.ANVIL_USE, .95F, .35F);
            run.primaryMouse = false;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) run.secondaryMouse = false;
        run.observe();
        run.flush();
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!active(event)) return;
        boolean fresh = run.heldKeys.add(event.getKeyCode());
        run.advance();
        InputConstants.Key pressed = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            // A held Esc repeats: only a second, separate press confirms an abandon.
            if (fresh) escape();
        } else if (run.verdict != null) {
            boolean dismissKey = event.getKeyCode() == GLFW.GLFW_KEY_ENTER || event.getKeyCode() == GLFW.GLFW_KEY_KP_ENTER
                    || PERFORM.isActiveAndMatches(pressed);
            if (fresh && dismissKey && dismissable()) run = null;
        } else if (run.inside && !run.submitted && PERFORM.isActiveAndMatches(pressed)) run.primaryKey = true;
        else if (run.inside && !run.submitted && CONTEXT.isActiveAndMatches(pressed)) run.secondaryKey = true;
        if (run != null) { run.observe(); run.flush(); }
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (!active(event)) return;
        run.heldKeys.remove(event.getKeyCode());
        run.advance();
        if (PERFORM.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            if (run.primaryKey && run.simulation.state() instanceof ReforgeState state && state.hammerCharge() >= .35)
                sound(SoundEvents.ANVIL_USE, .95F, .35F);
            run.primaryKey = false;
        }
        if (CONTEXT.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) run.secondaryKey = false;
        run.observe();
        run.flush();
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (active(event)) event.setCanceled(true);
    }

    @SubscribeEvent public static void onRender(ScreenEvent.Render.Post event) {
        if (run != null && event.getScreen() instanceof AbstractContainerScreen<?> screen) PerformanceOverlay.render(event.getGuiGraphics(), screen, run);
    }

    @SubscribeEvent public static void onTooltip(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        if (run != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void onClosing(ScreenEvent.Closing event) {
        if (run != null && event.getScreen() instanceof AbstractContainerScreen<?>) {
            run.advance();
            run.releaseTools();
            run.flush();
            discard();
        }
    }

    @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || run == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.screen instanceof AbstractContainerScreen<?>)) {
            run.releaseTools();
            discard();
            return;
        }
        run.advance();
    }

    private static void sound(net.minecraft.sounds.SoundEvent event, float pitch) {
        sound(event, pitch, .5F);
    }

    private static void sound(net.minecraft.sounds.SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private ClientPerformance() {}
}
