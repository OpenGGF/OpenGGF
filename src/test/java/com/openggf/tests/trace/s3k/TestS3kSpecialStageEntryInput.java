package com.openggf.tests.trace.s3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceSegmentCarriedState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Native entry lifetime and BK2 boundary controls, independent of any recorded route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageEntryInput {
    private static final int OFFSET = 5;
    private static final int UP = AbstractPlayableSprite.INPUT_UP;
    private static final int JUMP = AbstractPlayableSprite.INPUT_JUMP;
    private Sonic3kSpecialStageProvider provider;
    private int fadeRows;

    @BeforeEach
    void bootNativeLifetime() throws Exception {
        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();
        Rom rom = new Rom();
        rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath());
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        provider = spy(new Sonic3kSpecialStageProvider());
        provider.initializeStage(0);
        // Ask the native lifetime how much entry input exists. No trace row or
        // fitted fade count determines the test's boundary.
        while (provider.isEntryFadeToWhiteActive()) {
            provider.handleInput(0, 0);
            provider.update();
            fadeRows++;
        }
        assertTrue(fadeRows > 1);
        clearInvocations(provider);
    }

    @Test
    void neutralFinalFadeWinsOverUpAtFirstInteractiveRow() throws Exception {
        var frames = neutralFrames(fadeRows + 9);
        int interactive = fadeRows + 7; // arbitrary masked-load gap, not a ROM duration
        frames.set(OFFSET + interactive, input(UP, 0, JUMP, 2));
        var harness = harness(frames);
        var before = harness.capture();
        assertEquals(0, before.frameCounter(), "fade samples must not step SS sprites");
        assertFalse(provider.isEntryFadeToWhiteActive());

        harness.stepFrame(interactive);

        var boot = harness.capture();
        assertEquals(1, boot.frameCounter());
        assertFalse(boot.started(), "loc_8454 uses the final fade poll, before first interactive Up");
        assertEquals(before.playerY(), boot.playerY());
        assertEquals(0, boot.velocity());
        assertEquals(0, provider.getManager().getTailsJumpHeight(), "P2 also uses its pre-load latch");
        verify(provider).handlePlayer2Input(0x10, 0x10);
        harness.closeHardwareTiming();
    }

    @Test
    void finalFadePressSurvivesLoadAndPreservesBothPlayersNextPhysicalEdges() throws Exception {
        var frames = neutralFrames(fadeRows + 9);
        frames.set(OFFSET + fadeRows - 1, input(UP | JUMP, 1, JUMP, 2));
        int interactive = fadeRows + 7;
        // The physical row before interactive has C held, but no stepped row
        // has it. C must therefore NOT acquire a synthetic just-pressed edge.
        frames.set(OFFSET + interactive - 1, input(JUMP, 4, JUMP, 4));
        frames.set(OFFSET + interactive, input(JUMP, 4, JUMP, 4));
        var harness = harness(frames);
        assertEquals(0, harness.capture().frameCounter());
        clearInvocations(provider);

        harness.stepFrame(interactive);

        var boot = harness.capture();
        assertTrue(boot.started(), "the final fade Up must survive a released first interactive Up");
        assertEquals(0x200, boot.velocity());
        assertEquals(0x80, boot.jumping(), "the final fade A press survives the masked load");
        assertTrue(provider.getManager().getTailsJumpHeight() < 0, "final fade P2 B survives too");
        verify(provider).handleInput(0x20, 0);
        verify(provider).handlePlayer2Input(0x20, 0x20);
        harness.closeHardwareTiming();
    }

    @Test
    void heldFinalFadeActionDoesNotBecomeANewPress() throws Exception {
        var frames = neutralFrames(fadeRows + 9);
        frames.set(OFFSET + fadeRows - 2, input(UP | JUMP, 1, 0, 0));
        frames.set(OFFSET + fadeRows - 1, input(UP | JUMP, 1, 0, 0));
        var harness = harness(frames);
        verify(provider).handleInput(0x41, 1 | 0x40);
        verify(provider).handleInput(0x41, 0);

        harness.stepFrame(fadeRows + 7);

        assertTrue(harness.capture().started());
        assertEquals(0, harness.capture().jumping(), "physical predecessor already held A");
        harness.closeHardwareTiming();
    }

    private S3kSpecialStageReplayHarness harness(List<Bk2FrameInput> frames) throws Exception {
        var movie = new Bk2Movie(Path.of("entry-boundary.bk2"), "", Map.of(), frames, 0);
        var carried = new TraceSegmentCarriedState("declared boundary test", null, 0,
                List.of(0, 0, 0, 0, 0, 0, 0), 0, List.of(), null, 0, false,
                null, null, null, null, null, List.of(), List.of());
        return new S3kSpecialStageReplayHarness(movie, OFFSET, carried, provider);
    }

    private static List<Bk2FrameInput> neutralFrames(int localRows) {
        var frames = new ArrayList<Bk2FrameInput>();
        for (int i = 0; i < OFFSET + localRows; i++) {
            frames.add(input(0, 0, 0, 0));
        }
        return frames;
    }

    private static Bk2FrameInput input(int p1, int action1, int p2, int action2) {
        return new Bk2FrameInput(0, p1, action1, false, p2, action2, false, "declared input");
    }
}
