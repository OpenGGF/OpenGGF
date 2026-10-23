package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameId;
import com.openggf.game.GameMode;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1PlayerArt;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Actual GM_Special entry keeps the palette handler through both native fades. */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1SpecialStageEntryPlcBoundary {
    private GameLoop loop;

    @AfterEach
    void closeOwnedMode() {
        try { if (loop != null) loop.closePresence(); }
        finally { loop = null; SessionManager.clear(); }
    }

    @Test
    void actualEntryKeepsBothTwentyTwoVblankFadesOutOfPlayerDmaService() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var mode = fixture.gameplayMode();
        var level = mode.getLevelManager();
        level.consumeTitleCardRequest();
        loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.step();
        loop.step();
        loop.enterSpecialStage();
        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
        var provider = assertInstanceOf(Sonic1SpecialStageProvider.class,
                mode.getRegisteredSpecialStageProvider());
        assertTrue(provider.isEntryFadeToWhiteActive());

        // A real ROM DPLC submitted through the production staging port makes
        // wrong-handler DMA visible without a movie or comparison authority.
        var art = new Sonic1PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadSonic().dplcFrames();
        var dynamicArt = mode.dynamicArtLifecycle();
        dynamicArt.observePlayerDplc(GameId.S1, "sonic", 0x32, art.get(0x32));
        var prepared = dynamicArt.capture();
        var pending = prepared.s1Preparation();
        assertNotNull(pending);
        var journal = List.copyOf(dynamicArt.gapEdges());
        int sourceObjectPasses = level.getObjectManager().getFrameCounter();
        List<PlcLifecyclePhase> phases = new ArrayList<>();
        List<Integer> changedPreparations = new ArrayList<>();

        // PaletteWhiteOut and PaletteWhiteIn each execute MOVE.W #22-1/DBF.
        // This is their native loop count, not a measured replay duration.
        for (int vblank = 0; vblank < 22 + 22; vblank++) {
            phases.add(provider.specialStagePlcLifecyclePhase());
            loop.step();
            assertEquals(sourceObjectPasses, level.getObjectManager().getFrameCounter(),
                    "entry fades do not execute the parked level's objects");
            if (!pending.equals(dynamicArt.capture().s1Preparation())) changedPreparations.add(vblank);
            if (vblank == 21) {
                assertFalse(provider.isEntryFadeToWhiteActive());
                assertTrue(provider.isEntryPresentationReady());
            }
        }
        assertAll(
                () -> assertEquals(Collections.nCopies(44, PlcLifecyclePhase.PALETTE_FADE), phases),
                () -> assertEquals(List.of(), changedPreparations,
                        "neither entry fade runs VBlank_SpecialStage's Sonic buffer transfer"),
                () -> assertEquals(journal, dynamicArt.gapEdges(), "retain the whole gap journal"),
                () -> assertEquals(prepared.nextTransferId(), dynamicArt.capture().nextTransferId()),
                () -> assertEquals(prepared.nextEdgeOrdinal(), dynamicArt.capture().nextEdgeOrdinal()),
                () -> assertEquals(PlcLifecyclePhase.SPECIAL_STAGE, provider.specialStagePlcLifecyclePhase()));
        // This stops before the first active stage VBlank: GM_Special's separate
        // setup clear and the staged buffer's later lifetime are not certified.
    }
}
