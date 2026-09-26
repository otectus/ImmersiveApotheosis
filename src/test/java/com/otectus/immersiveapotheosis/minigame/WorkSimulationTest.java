package com.otectus.immersiveapotheosis.minigame;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkSimulationTest {
    @Test
    void perfectReferencePlayIsDeterministicAttainableAndWithinTheWorkWindow() {
        for (Station station : Station.values()) {
            for (int tier = 0; tier <= 5; tier++) {
                for (long seed = 0; seed < 8; seed++) {
                    WorkDefinition definition = new WorkDefinition(station, tier, seed, 1.0);
                    List<WorkInput> first = WorkAutoplay.perfect(definition);
                    List<WorkInput> second = WorkAutoplay.perfect(definition);
                    assertEquals(first, second, definition.toString());
                    assertTrue(first.size() >= 1_000, definition + " was shorter than 20 seconds: " + first.size());
                    assertTrue(first.size() <= 2_000, definition + " exceeded 40 seconds: " + first.size());
                    WorkResult result = WorkAutoplay.drive(WorkSimulation.create(definition), first);
                    assertEquals(100.0, result.score(), 1e-9, definition.toString());
                    assertEquals(OutcomeBand.PERFECT, result.band());
                    assertTrue(result.complete());
                }
            }
        }
    }

    @Test
    void polishingCanCoverSectorsOutOfOrderWithoutStalling() {
        WorkSimulation work = WorkSimulation.create(new WorkDefinition(Station.GEM_CUTTING, 0, 41, 1));
        WorkInput previous = WorkInput.IDLE;
        while (work.state().stage() != WorkStage.POLISH_FACETS && !work.finished()) {
            previous = WorkAutoplay.next(work.state(), previous);
            work.step(previous);
        }
        CuttingState state = (CuttingState) work.state();
        var contact = state.facets().get(state.activeFacet()).contact();
        double angle = -Math.PI + 7.5 * Math.PI / 6;
        previous = new WorkInput((int) Math.round(contact.x() + Math.cos(angle) * 100),
                (int) Math.round(contact.y() + Math.sin(angle) * 100), WorkInput.SECONDARY);
        for (int i = 0; i < 6; i++) work.step(previous);
        assertEquals(1 << 7, ((CuttingState) work.state()).facets().get(0).polishMask());
        while (!work.finished()) {
            previous = WorkAutoplay.next(work.state(), previous);
            work.step(previous);
        }
        assertTrue(work.result().complete());
    }

    @Test
    void elapsedStepsStartWithActiveWorkAndNeverExceedHardLimit() {
        WorkSimulation simulation = WorkSimulation.create(new WorkDefinition(Station.SALVAGING, 0, 4, 1.0));
        assertNotEquals(WorkStage.COUNT_IN, simulation.state().stage());
        simulation.step(WorkInput.IDLE);
        assertEquals(1, simulation.elapsedSteps());
        assertNotEquals(WorkStage.COUNT_IN, simulation.state().stage());
        while (!simulation.finished()) simulation.step(WorkInput.IDLE);
        assertEquals(WorkDefinition.MAX_STEPS, simulation.elapsedSteps());
        assertFalse(simulation.result().complete());
    }

    @Test
    void idleMashAndOneLongHoldCannotEarnQuality() {
        for (Station station : Station.values()) {
            for (int mode = 0; mode < 3; mode++) {
                WorkSimulation simulation = WorkSimulation.create(new WorkDefinition(station, 3, 91, 1.0));
                while (!simulation.finished()) {
                    WorkInput input = switch (mode) {
                        case 0 -> WorkInput.IDLE;
                        case 1 -> new WorkInput(2048, 2048,
                                simulation.elapsedSteps() % 2 == 0 ? WorkInput.PRIMARY : 0);
                        default -> new WorkInput(2048, 2048, WorkInput.PRIMARY);
                    };
                    simulation.step(input);
                }
                assertFalse(simulation.result().complete(), station + " mode " + mode);
                assertTrue(simulation.result().score() < 50.0, station + " mode " + mode + ": " + simulation.result());
            }
        }
    }

    @Test
    void assistanceRangeRemainsReproducibleAndAttainable() {
        for (double assistance : new double[]{0.5, 2.0}) {
            for (Station station : Station.values()) {
                for (int tier : new int[]{0, 3, 5}) {
                    WorkDefinition definition = new WorkDefinition(station, tier, 77, assistance);
                    List<WorkInput> inputs = WorkAutoplay.perfect(definition);
                    WorkResult result = WorkAutoplay.drive(WorkSimulation.create(definition), inputs);
                    assertEquals(100.0, result.score(), 1e-9, definition.toString());
                    assertTrue(inputs.size() <= WorkDefinition.MAX_STEPS);
                }
            }
        }
    }

    @Test
    void rarityAddsPhysicalWork() {
        SalvageState commonSalvage = (SalvageState) WorkSimulation.create(
                new WorkDefinition(Station.SALVAGING, 0, 12, 1)).state();
        SalvageState ancientSalvage = (SalvageState) WorkSimulation.create(
                new WorkDefinition(Station.SALVAGING, 5, 12, 1)).state();
        CuttingState commonCut = (CuttingState) WorkSimulation.create(
                new WorkDefinition(Station.GEM_CUTTING, 0, 12, 1)).state();
        CuttingState ancientCut = (CuttingState) WorkSimulation.create(
                new WorkDefinition(Station.GEM_CUTTING, 5, 12, 1)).state();
        ReforgeState commonReforge = (ReforgeState) WorkSimulation.create(
                new WorkDefinition(Station.REFORGING, 0, 12, 1)).state();
        ReforgeState ancientReforge = (ReforgeState) WorkSimulation.create(
                new WorkDefinition(Station.REFORGING, 5, 12, 1)).state();
        assertEquals(3, commonSalvage.components().size());
        assertEquals(6, ancientSalvage.components().size());
        assertEquals(4, commonCut.facets().size());
        assertEquals(8, ancientCut.facets().size());
        assertEquals(3, commonReforge.anchors().size());
        assertEquals(6, ancientReforge.anchors().size());
    }

    @Test
    void socketGuidesOpposingProngsAndPressureImbalanceLowersScore() {
        assertEquals(List.of(0, 2, 1, 3), visitedProngs(0, false));
        assertEquals(List.of(0, 3, 1, 4, 2, 5), visitedProngs(5, false));
        WorkResult imbalanced = playSocketWithOverpressure();
        assertTrue(imbalanced.complete());
        assertTrue(imbalanced.score() < 90.0, imbalanced.toString());
    }

    @Test
    void cuttingRequiresReleaseAndAllowsRealOvercutDamage() {
        WorkDefinition definition = new WorkDefinition(Station.GEM_CUTTING, 2, 18, 1.0);
        WorkSimulation simulation = WorkSimulation.create(definition);
        WorkInput previous = WorkInput.IDLE;
        while (!simulation.finished()) {
            CuttingState state = (CuttingState) simulation.state();
            WorkInput next = WorkAutoplay.next(state, previous);
            if (state.stage() == WorkStage.GRIND_FACETS) {
                CuttingState.Facet facet = state.facets().get(state.activeFacet());
                if (facet.depth() < facet.targetDepth() + 0.13) {
                    next = new WorkInput(facet.contact().x(), facet.contact().y(), WorkInput.PRIMARY);
                }
            }
            simulation.step(next);
            previous = next;
        }
        assertTrue(simulation.result().complete());
        assertTrue(simulation.result().score() < 90.0, simulation.result().toString());
    }

    @Test
    void underpressureAndUndergrindingRemainCorrectableWhileOverheatingCostsQuality() {
        WorkSimulation socket = WorkSimulation.create(new WorkDefinition(Station.SOCKETING, 0, 31, 1.0));
        WorkInput previous = WorkInput.IDLE;
        int underworkedProng = -1;
        while (underworkedProng < 0) {
            SocketState state = (SocketState) socket.state();
            WorkInput next = WorkAutoplay.next(state, previous);
            if (state.stage() == WorkStage.BEND_PRONGS) {
                SocketState.Prong prong = state.prongs().get(state.activeProng());
                if (prong.pressure() >= 0.52) {
                    underworkedProng = state.activeProng();
                    next = new WorkInput(prong.target().x(), prong.target().y(), 0);
                }
            }
            socket.step(next);
            previous = next;
        }
        SocketState afterRelease = (SocketState) socket.state();
        assertEquals(underworkedProng, afterRelease.activeProng());
        assertFalse(afterRelease.prongs().get(underworkedProng).pressure() >= afterRelease.pressureLow());
        while (!socket.finished()) {
            WorkInput next = WorkAutoplay.next(socket.state(), previous);
            socket.step(next);
            previous = next;
        }
        assertTrue(socket.result().complete());

        WorkSimulation cut = WorkSimulation.create(new WorkDefinition(Station.GEM_CUTTING, 0, 32, 1.0));
        previous = WorkInput.IDLE;
        int undergroundFacet = -1;
        while (undergroundFacet < 0) {
            CuttingState state = (CuttingState) cut.state();
            WorkInput next = WorkAutoplay.next(state, previous);
            if (state.stage() == WorkStage.GRIND_FACETS) {
                CuttingState.Facet facet = state.facets().get(state.activeFacet());
                if (facet.depth() >= facet.targetDepth() * 0.55) {
                    undergroundFacet = state.activeFacet();
                    next = new WorkInput(facet.contact().x(), facet.contact().y(), 0);
                }
            }
            cut.step(next);
            previous = next;
        }
        CuttingState afterCutRelease = (CuttingState) cut.state();
        assertEquals(WorkStage.GRIND_FACETS, afterCutRelease.stage());
        assertFalse(afterCutRelease.facets().get(undergroundFacet).locked());
        while (!cut.finished()) {
            WorkInput next = WorkAutoplay.next(cut.state(), previous);
            cut.step(next);
            previous = next;
        }
        assertTrue(cut.result().complete());

        WorkSimulation reforge = WorkSimulation.create(new WorkDefinition(Station.REFORGING, 2, 33, 1.0));
        previous = WorkInput.IDLE;
        while (!reforge.finished()) {
            ReforgeState state = (ReforgeState) reforge.state();
            WorkInput next = WorkAutoplay.next(state, previous);
            if (state.stage() == WorkStage.HEAT_BINDINGS) {
                ReforgeState.Anchor anchor = state.anchors().get(state.activeAnchor());
                if (anchor.heat() < 0.90) {
                    next = new WorkInput(anchor.point().x(), anchor.point().y(), WorkInput.SECONDARY);
                }
            }
            reforge.step(next);
            previous = next;
        }
        assertTrue(reforge.result().complete());
        assertTrue(reforge.result().score() < 90.0, reforge.result().toString());
    }

    @Test
    void mistakesDegradeButSalvageCanRecoverAndRecoveredPartsSurviveTimeout() {
        WorkDefinition definition = new WorkDefinition(Station.SALVAGING, 3, 22, 1.0);
        WorkSimulation recovered = WorkSimulation.create(definition);
        for (int i = 0; i < 160; i++) recovered.step(new WorkInput(0, 0, WorkInput.PRIMARY));
        WorkInput previous = new WorkInput(0, 0, WorkInput.PRIMARY);
        while (!recovered.finished()) {
            WorkInput next = WorkAutoplay.next(recovered.state(), previous);
            recovered.step(next);
            previous = next;
        }
        assertTrue(recovered.result().complete());
        assertTrue(recovered.result().score() >= 50 && recovered.result().score() < 100, recovered.result().toString());

        WorkSimulation partial = WorkSimulation.create(definition);
        previous = WorkInput.IDLE;
        while (!((SalvageState) partial.state()).components().get(0).removed()) {
            WorkInput next = WorkAutoplay.next(partial.state(), previous);
            partial.step(next);
            previous = next;
        }
        while (!partial.finished()) partial.step(WorkInput.IDLE);
        assertFalse(partial.result().complete());
        assertTrue(partial.result().score() > 0 && partial.result().score() < 100, partial.result().toString());
    }

    @Test
    void partialReforgeCompletesInWeakOrFrayedBand() {
        for (long seed = 0; seed < 20; seed++) {
            WorkDefinition definition = new WorkDefinition(Station.REFORGING, 3, seed, 1.0);
            WorkResult result = WorkAutoplay.drive(WorkSimulation.create(definition), WorkAutoplay.partial(definition));
            assertTrue(result.complete());
            assertTrue(result.band() == OutcomeBand.WEAK || result.band() == OutcomeBand.FRAYED, result.toString());
        }
    }

    @Test
    void validationNormalizationAndEconomicYieldStayBounded() {
        assertThrows(IllegalArgumentException.class, () -> new WorkInput(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorkInput(0, 0, 4));
        assertEquals(new WorkInput(0, 4095, WorkInput.PRIMARY), WorkInput.normalized(-8, 9000, 5));
        assertThrows(IllegalArgumentException.class, () -> new WorkDefinition(Station.SALVAGING, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorkDefinition(Station.SALVAGING, 0, 0, 0.49));
        for (double score : new double[]{-100, 0, 35, 65, 100, 1000}) {
            assertTrue(Performance.yieldShare(score) >= 0 && Performance.yieldShare(score) <= 1);
            assertTrue(Performance.yieldCount(2, 7, score) >= 2 && Performance.yieldCount(2, 7, score) <= 7);
        }
    }

    private static List<Integer> visitedProngs(int tier, boolean overwork) {
        WorkDefinition definition = new WorkDefinition(Station.SOCKETING, tier, 9, 1.0);
        WorkSimulation simulation = WorkSimulation.create(definition);
        WorkInput previous = WorkInput.IDLE;
        List<Integer> order = new ArrayList<>();
        while (!simulation.finished()) {
            SocketState state = (SocketState) simulation.state();
            if (state.stage() == WorkStage.BEND_PRONGS
                    && (order.isEmpty() || order.get(order.size() - 1) != state.activeProng())) {
                order.add(state.activeProng());
            }
            WorkInput next = WorkAutoplay.next(state, previous);
            simulation.step(next);
            previous = next;
        }
        return order;
    }

    private static WorkResult playSocketWithOverpressure() {
        WorkSimulation simulation = WorkSimulation.create(new WorkDefinition(Station.SOCKETING, 3, 41, 1.0));
        WorkInput previous = WorkInput.IDLE;
        while (!simulation.finished()) {
            SocketState state = (SocketState) simulation.state();
            WorkInput next = WorkAutoplay.next(state, previous);
            if (state.stage() == WorkStage.BEND_PRONGS) {
                SocketState.Prong prong = state.prongs().get(state.activeProng());
                if (prong.pressure() > 0.95 && prong.pressure() < 1.32 && previous.primary()) {
                    next = new WorkInput(prong.target().x(), prong.target().y(), WorkInput.PRIMARY);
                }
            }
            simulation.step(next);
            previous = next;
        }
        return simulation.result();
    }
}
