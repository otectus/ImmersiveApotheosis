package com.otectus.immersiveapotheosis.smoketest;

import com.google.gson.GsonBuilder;
import com.otectus.immersiveapotheosis.ImmersiveApotheosis;
import com.otectus.immersiveapotheosis.client.ClientPerformance;
import com.otectus.immersiveapotheosis.minigame.Station;
import com.otectus.immersiveapotheosis.minigame.WorkDefinition;
import com.otectus.immersiveapotheosis.minigame.WorkStage;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.Util;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in visual evidence recorder for the input-driven client smoke test. Enable it with
 * {@code -Dimmersive_apotheosis.visual.output=<dir>}. The 960x720 framebuffer at GUI scale 3 exercises an exact
 * 320x240 scaled GUI while retaining useful screenshot resolution. Every station menu, count-in, work stage and
 * verdict is captured, and {@code visual-result.json} fails when evidence or overlay bounds are missing.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class VisualValidationHarness {
    private static final String OUTPUT = System.getProperty("immersive_apotheosis.visual.output");
    private static final int FRAMEBUFFER_WIDTH = 960;
    private static final int FRAMEBUFFER_HEIGHT = 720;
    private static final int GUI_SCALE = 3;
    private static final Map<Station, Set<WorkStage>> EXPECTED_STAGES = Map.of(
            Station.GEM_CUTTING, EnumSet.of(WorkStage.ALIGN_FACETS, WorkStage.GRIND_FACETS, WorkStage.POLISH_FACETS),
            Station.REFORGING, EnumSet.of(WorkStage.HEAT_BINDINGS, WorkStage.STRIKE_ANCHORS, WorkStage.TRACE_SEAL),
            Station.SOCKETING, EnumSet.of(WorkStage.POSITION_GEM, WorkStage.BEND_PRONGS),
            Station.SALVAGING, EnumSet.of(WorkStage.CUT_SEAMS, WorkStage.EXTRACT_COMPONENTS));
    private static final Set<WorkStage> PAIRED_STAGES = EnumSet.of(WorkStage.GRIND_FACETS, WorkStage.POLISH_FACETS,
            WorkStage.HEAT_BINDINGS, WorkStage.STRIKE_ANCHORS, WorkStage.POSITION_GEM, WorkStage.BEND_PRONGS,
            WorkStage.CUT_SEAMS, WorkStage.EXTRACT_COMPONENTS);

    private static final Set<Station> MENUS = EnumSet.noneOf(Station.class);
    private static final Set<Station> COUNT_INS = EnumSet.noneOf(Station.class);
    private static final Set<Station> VERDICTS = EnumSet.noneOf(Station.class);
    private static final Set<Station> LARGE_VERDICTS = EnumSet.noneOf(Station.class);
    private static final Map<Station, Long> COUNT_IN_SEEN_AT = new EnumMap<>(Station.class);
    private static final Map<Station, Set<WorkStage>> EARLY_STAGES = new EnumMap<>(Station.class);
    private static final Map<Station, Set<WorkStage>> LATE_STAGES = new EnumMap<>(Station.class);
    private static final Map<String, Integer> EARLY_STAGE_STEPS = new LinkedHashMap<>();
    private static final Map<Station, Integer> MENU_FRAMES = new EnumMap<>(Station.class);
    private static final Map<String, Object> CAPTURES = new LinkedHashMap<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static WorkStage observedStage;
    private static Station observedStation;
    private static int stageEnteredAt;
    private static long verdictSeenAt;
    private static Station verdictStation;
    private static Station largeVerdictPending;
    private static boolean configured;

    static boolean enabled() {
        return OUTPUT != null && !OUTPUT.isBlank();
    }

    static void configure(Minecraft mc) {
        if (!enabled() || configured) return;
        configured = true;
        try {
            Files.createDirectories(Path.of(OUTPUT));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create visual validation output", e);
        }
        mc.getWindow().setWindowed(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT);
        mc.options.guiScale().set(GUI_SCALE);
        mc.resizeDisplay();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capture(ScreenEvent.Render.Post event) {
        if (!enabled() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Minecraft mc = Minecraft.getInstance();
        configure(mc);

        var view = ClientPerformance.view();
        var definition = ClientPerformance.definition();
        // setWindowed is asynchronous under XWayland. guiScale may have been clamped against the old 854x480 window,
        // so reapply it only after the 960x720 framebuffer has settled and wait for the resized screen before evidence.
        if ((view.isEmpty() || ClientPerformance.step() < WorkDefinition.COUNT_IN_STEPS) && !ensureCompact(mc)) return;
        if (view.isEmpty() || definition.isEmpty()) {
            Station menu = menuStation(screen.getMenu());
            if (menu != null && !MENUS.contains(menu) && MENU_FRAMES.merge(menu, 1, Integer::sum) >= 3) {
                shot(mc, menu, "menu", null);
                MENUS.add(menu);
            }
            return;
        }

        Station station = definition.orElseThrow().station();
        if (largeVerdictPending == station && ClientPerformance.verdict().isPresent()) {
            shot(mc, station, "verdict_large", null);
            LARGE_VERDICTS.add(station);
            largeVerdictPending = null;
            mc.options.guiScale().set(GUI_SCALE);
            mc.resizeDisplay();
            return;
        }
        if (ClientPerformance.step() < WorkDefinition.COUNT_IN_STEPS) {
            long firstSeen = COUNT_IN_SEEN_AT.computeIfAbsent(station, ignored -> Util.getMillis());
            if (Util.getMillis() - firstSeen >= 1_000 && COUNT_INS.add(station)) shot(mc, station, "count_in", null);
            return;
        }
        if (ClientPerformance.verdict().isPresent()) {
            if (station != verdictStation) {
                verdictStation = station;
                verdictSeenAt = Util.getMillis();
            }
            if (Util.getMillis() - verdictSeenAt >= 650 && VERDICTS.add(station)) {
                shot(mc, station, "verdict", null);
                largeVerdictPending = station;
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
            }
            return;
        }

        WorkStage stage = view.orElseThrow().stage();
        if (station != observedStation || stage != observedStage) {
            observedStation = station;
            observedStage = stage;
            stageEnteredAt = view.orElseThrow().elapsedSteps();
        }
        if (!EXPECTED_STAGES.getOrDefault(station, Set.of()).contains(stage)) return;
        int age = view.orElseThrow().elapsedSteps() - stageEnteredAt;
        if (!isScaledGui(mc, 320, 240)) return; // The reforge smoke deliberately probes scale 2 during scoring.
        String stageKey = name(station) + ":" + stage.name();
        if (age >= 10 && EARLY_STAGES.computeIfAbsent(station, ignored -> EnumSet.noneOf(WorkStage.class)).add(stage)) {
            shot(mc, station, stage.name().toLowerCase(Locale.ROOT) + "_early", stage);
            EARLY_STAGE_STEPS.put(stageKey, view.orElseThrow().elapsedSteps());
        }
        Integer earlyAt = EARLY_STAGE_STEPS.get(stageKey);
        if (age >= 30 && earlyAt != null && view.orElseThrow().elapsedSteps() >= earlyAt + 20
                && LATE_STAGES.computeIfAbsent(station, ignored -> EnumSet.noneOf(WorkStage.class)).add(stage)) {
            shot(mc, station, stage.name().toLowerCase(Locale.ROOT) + "_late", stage);
        }
    }

    static boolean finish(Minecraft mc) {
        if (!enabled()) return true;
        for (Station station : Station.values()) {
            if (!MENUS.contains(station)) FAILURES.add(name(station) + ": menu capture missing");
            if (!COUNT_INS.contains(station)) FAILURES.add(name(station) + ": count-in capture missing");
            if (!VERDICTS.contains(station)) FAILURES.add(name(station) + ": verdict capture missing");
            if (!LARGE_VERDICTS.contains(station)) FAILURES.add(name(station) + ": large verdict capture missing");
            for (WorkStage stage : EXPECTED_STAGES.get(station)) {
                if (!EARLY_STAGES.getOrDefault(station, Set.of()).contains(stage))
                    FAILURES.add(name(station) + ": " + stage.name().toLowerCase(Locale.ROOT) + " early capture missing");
                if (PAIRED_STAGES.contains(stage) && !LATE_STAGES.getOrDefault(station, Set.of()).contains(stage))
                    FAILURES.add(name(station) + ": " + stage.name().toLowerCase(Locale.ROOT) + " late capture missing");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pass", FAILURES.isEmpty());
        result.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
        result.put("scaled_gui", mc.screen == null ? "closed" : mc.screen.width + "x" + mc.screen.height);
        result.put("gui_scale", mc.getWindow().getGuiScale());
        result.put("captures", CAPTURES);
        result.put("failures", FAILURES);
        try {
            Files.writeString(Path.of(OUTPUT, "visual-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ImmersiveApotheosis.LOGGER.error("VISUALTEST could not write result", e);
            return false;
        }
        ImmersiveApotheosis.LOGGER.info("VISUALTEST finished: pass={} captures={} failures={}",
                FAILURES.isEmpty(), CAPTURES.size(), FAILURES.size());
        return FAILURES.isEmpty();
    }

    private static void shot(Minecraft mc, Station station, String moment, WorkStage stage) {
        String key = name(station) + "_" + moment;
        ClientPerformance.Workspace area = ClientPerformance.workspace().orElse(null);
        boolean inBounds = area == null || mc.screen != null && area.left() >= 0 && area.top() >= 0
                && area.left() + area.width() <= mc.screen.width && area.top() + area.height() <= mc.screen.height;
        if (!inBounds) FAILURES.add(key + ": workspace outside scaled GUI");
        int expectedWidth = moment.endsWith("_large") ? 480 : 320;
        int expectedHeight = moment.endsWith("_large") ? 360 : 240;
        boolean expectedScale = mc.screen != null && mc.screen.width == expectedWidth && mc.screen.height == expectedHeight;
        if (!expectedScale) FAILURES.add(key + ": expected scaled GUI " + expectedWidth + "x" + expectedHeight
                + " but got " + (mc.screen == null ? "closed" : mc.screen.width + "x" + mc.screen.height));
        String file = String.format("%02d_%s.png", CAPTURES.size() + 1, key);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(Path.of(OUTPUT, file));
        } catch (Exception e) {
            throw new IllegalStateException("Visual screenshot " + key + " failed", e);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("file", file);
        evidence.put("framebuffer", mc.getMainRenderTarget().width + "x" + mc.getMainRenderTarget().height);
        evidence.put("scaled_gui", mc.screen.width + "x" + mc.screen.height);
        evidence.put("gui_scale", mc.getWindow().getGuiScale());
        evidence.put("workspace_in_bounds", inBounds);
        evidence.put("expected_scaled_gui", expectedScale);
        if (area != null) evidence.put("workspace", area.left() + "," + area.top() + " " + area.width() + "x" + area.height());
        if (stage != null) evidence.put("stage", stage.name());
        CAPTURES.put(key, evidence);
        ImmersiveApotheosis.LOGGER.info("VISUALTEST screenshot {}", file);
    }

    private static Station menuStation(Object menu) {
        if (menu instanceof GemCuttingMenu) return Station.GEM_CUTTING;
        if (menu instanceof ReforgingMenu) return Station.REFORGING;
        if (menu instanceof SmithingMenu) return Station.SOCKETING;
        if (menu instanceof SalvagingMenu) return Station.SALVAGING;
        return null;
    }

    private static String name(Station station) {
        return switch (station) {
            case GEM_CUTTING -> "cut";
            case REFORGING -> "reforge";
            case SOCKETING -> "setting";
            case SALVAGING -> "salvage";
        };
    }

    private static boolean isScaledGui(Minecraft mc, int width, int height) {
        return mc.screen != null && mc.screen.width == width && mc.screen.height == height;
    }

    private static boolean ensureCompact(Minecraft mc) {
        if (mc.getMainRenderTarget().width != FRAMEBUFFER_WIDTH || mc.getMainRenderTarget().height != FRAMEBUFFER_HEIGHT) {
            mc.getWindow().setWindowed(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT);
            return false;
        }
        if (!isScaledGui(mc, 320, 240)) {
            mc.options.guiScale().set(GUI_SCALE);
            mc.resizeDisplay();
            return false;
        }
        return true;
    }

    private VisualValidationHarness() {}
}
