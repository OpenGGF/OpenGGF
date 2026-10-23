package com.openggf.game.resources;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.rules.DynamicArtDmaServiceModel;
import com.openggf.game.sonic1.Sonic1PlayerArt;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real ROM DPLCs staged through the production phase owner, with no replay data. */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1PaletteFadePlayerArtService {
    private SpriteDplcFrame rollingArt;
    private SpriteDplcFrame waitingArt;

    @BeforeEach
    void loadProductionPlayerArt() throws IOException {
        var art = new Sonic1PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom())).loadSonic();
        rollingArt = art.dplcFrames().get(0x32);
        waitingArt = art.dplcFrames().get(1);
    }

    @ParameterizedTest
    @EnumSource(value = PlcLifecyclePhase.class, names = {
            "ORDINARY_LEVEL", "LEVEL_TITLE_CARD", "NORMAL_PAUSE", "SPECIAL_STAGE"})
    void paletteFadeRetainsStagingUntilAnEligibleNativeHandler(PlcLifecyclePhase eligible) {
        var service = new DynamicArtLifecycleService();
        service.beginRun();
        var coordinator = coordinator(service, DynamicArtDmaServiceModel.SONIC_1_VBLANK_SONIC_GFX);
        service.observePlayerDplc(GameId.S1, "sonic", 0x32, rollingArt);
        var pending = service.capture().s1Preparation();
        assertNotNull(pending, "Sonic_LoadGfx has prepared art, not submitted a DMA");

        for (int row = 10; row < 13; row++) {
            service.setMovieLogicalFrame(row);
            claim(coordinator, PlcLifecyclePhase.PALETTE_FADE);
            assertEquals(pending, service.capture().s1Preparation(),
                    "VBlank_PaletteFade neither uploads nor drops Sonic's staged buffer");
            assertTrue(service.gapEdges().isEmpty(), "no submit/completion is invented during the fade");
            assertEquals(0L, service.capture().nextTransferId());
        }

        service.setMovieLogicalFrame(13);
        claim(coordinator, eligible);
        assertTransfer(service, 0x32, 13, List.of(
                DynamicArtDiagnosticsSnapshot.Request.rom(0x28190, 732, 0xF000, 512)));
        assertNull(service.capture().s1Preparation(), "eligible service consumes staging once");
        claim(coordinator, eligible);
        assertEquals(2, service.gapEdges().size(), "another VBlank cannot duplicate the consumed transfer");
    }

    @Test
    void aLaterPlayerPreparationReplacesTheUnservicedBufferWithoutPublishingTheOldOne() {
        var service = new DynamicArtLifecycleService();
        service.beginRun();
        var coordinator = coordinator(service, DynamicArtDmaServiceModel.SONIC_1_VBLANK_SONIC_GFX);
        service.observePlayerDplc(GameId.S1, "sonic", 0x32, rollingArt);
        claim(coordinator, PlcLifecyclePhase.PALETTE_FADE);
        assertTrue(service.gapEdges().isEmpty());

        // Sonic_LoadGfx owns one buffer: a genuine later decision replaces it.
        // This models overwrite only; it does not stand in for a load's RAM clear.
        service.clearPlayerDplcDedupRegistersForLevelLoad();
        service.observePlayerDplc(GameId.S1, "sonic", 1, waitingArt);
        assertEquals(1, service.capture().s1Preparation().mappingFrame());
        claim(coordinator, PlcLifecyclePhase.PALETTE_FADE);
        assertTrue(service.gapEdges().isEmpty());
        service.setMovieLogicalFrame(20);
        claim(coordinator, PlcLifecyclePhase.LEVEL_TITLE_CARD);

        assertTransfer(service, 1, 20, List.of(
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22610, 0, 0xF000, 96),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22670, 3, 0xF060, 256),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x22770, 11, 0xF160, 96),
                DynamicArtDiagnosticsSnapshot.Request.rom(0x227D0, 14, 0xF1C0, 96)));
    }

    @Test
    void anEveryClaimModelStillServicesPaletteFadeUsingTheSameProductionCoordinator() {
        var service = new DynamicArtLifecycleService();
        service.beginRun();
        var coordinator = coordinator(service, DynamicArtDmaServiceModel.EVERY_CLAIM);
        service.observePlayerDplc(GameId.S1, "sonic", 0x32, rollingArt);
        service.setMovieLogicalFrame(30);

        claim(coordinator, PlcLifecyclePhase.PALETTE_FADE);

        assertTransfer(service, 0x32, 30, List.of(
                DynamicArtDiagnosticsSnapshot.Request.rom(0x28190, 732, 0xF000, 512)));
    }

    @Test
    void theSonic1PolicyChangesOnlyTheProvenPaletteFadeException() {
        var model = DynamicArtDmaServiceModel.SONIC_1_VBLANK_SONIC_GFX;
        assertFalse(model.services(null));
        assertFalse(model.services(PlcLifecyclePhase.LAG));
        assertFalse(model.services(PlcLifecyclePhase.PALETTE_FADE));
        for (PlcLifecyclePhase phase : PlcLifecyclePhase.values()) {
            if (phase != PlcLifecyclePhase.LAG && phase != PlcLifecyclePhase.PALETTE_FADE) {
                assertTrue(model.services(phase), "retain existing phase policy for " + phase);
            }
        }
    }

    private static PlcFrameLifecycleCoordinator coordinator(
            DynamicArtLifecycleService service, DynamicArtDmaServiceModel model) {
        var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null, service, model);
        coordinator.acquireExternalComparisonSegmentOwnership();
        return coordinator;
    }

    private static void claim(PlcFrameLifecycleCoordinator coordinator, PlcLifecyclePhase phase) {
        coordinator.runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(phase));
            frame.prepareAfterLoop(phase);
            return null;
        });
    }

    private static void assertTransfer(DynamicArtLifecycleService service, int mapping, int movieRow,
                                       List<DynamicArtDiagnosticsSnapshot.Request> submittedRequests) {
        var edges = service.gapEdges();
        assertEquals(2, edges.size(), "the complete journal contains one unfiltered transfer pair");
        assertEquals(List.of("submitted", "completed"), edges.stream().map(edge -> edge.phase()).toList());
        assertEquals(List.of(0L, 0L), edges.stream().map(edge -> edge.transferId()).toList());
        assertEquals(List.of(0L, 1L), edges.stream().map(edge -> edge.edgeOrdinal()).toList());
        assertTrue(edges.stream().allMatch(edge -> edge.owner().equals("sonic")
                && edge.mappingFrame() == mapping && edge.movieLogicalFrame() == movieRow));
        assertEquals(submittedRequests, edges.getFirst().requests());
        assertEquals(List.of(DynamicArtDiagnosticsSnapshot.Request.ram(0xC800, 0xF000, 0x2E0)),
                edges.getLast().requests());
        var transitions = service.gapTransitions();
        assertTrue(transitions.getFirst().beforeOutstandingTransferIds().isEmpty());
        assertEquals(List.of(0L), transitions.getFirst().afterOutstandingTransferIds());
        assertEquals(List.of(0L), transitions.getLast().beforeOutstandingTransferIds());
        assertTrue(transitions.getLast().afterOutstandingTransferIds().isEmpty());
    }
}
