package com.openggf.tests.trace.runs;

import com.openggf.GameLoop;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production dispatch while a loaded destination awaits its first input row. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHeadlessPlainLevelGap extends AbstractRunChainTest {
    @AfterEach
    void stop() {
        PlaybackDebugManager.getInstance().endSession();
        SessionManager.clear();
    }

    @Test
    void deniedAdmissionAtAdvertisedOffsetCannotConsumeItsPhysicsRow() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
        var context = SessionManager.getCurrentGameplayMode();
        var loop = new GameLoop(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        loop.step();
        var player = fixture.sprite();
        int clock = context.getSpriteManager().getFrameCounter();
        var playback = PlaybackDebugManager.getInstance();
        playback.startSession(new Bk2Movie(Path.of("denied-gap.bk2"), "logkey", Map.of(),
                IntStream.range(0, 4).mapToObj(i ->
                        new Bk2FrameInput(i, 0, 0, false, "neutral")).toList(), 1), 1);

        AssertionError failure = assertThrows(AssertionError.class, () ->
                admitPlainLevelBoundaryWhenReady(context, loop, playback,
                        1, 0, 4, consumed -> false));
        assertTrue(failure.getMessage().contains("advertised input row"));
        assertEquals(1, playback.getCursorFrame(), "denial must retain row 0's input");
        assertEquals(clock, context.getSpriteManager().getFrameCounter());
        assertEquals(0, player.getYSubpixelRaw());
        assertEquals(0, player.getYSpeed());
        assertTrue(context.traceRunFrameDriver().isEmpty());
    }

    @Test
    void preWindowGapAdvancesInputWithoutRunningDestinationPhysics() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
        var context = SessionManager.getCurrentGameplayMode();
        var player = fixture.sprite();
        var loop = new GameLoop(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        // loc_6468 initializes Sonic's routine without applying MoveSprite.
        loop.step();
        assertEquals(0, player.getYSubpixelRaw());
        assertEquals(0, player.getYSpeed());
        int levelClock = context.getSpriteManager().getFrameCounter();
        var playback = PlaybackDebugManager.getInstance();
        var movie = new Bk2Movie(Path.of("plain-gap.bk2"), "logkey", Map.of(),
                IntStream.range(0, 4).mapToObj(i ->
                        new Bk2FrameInput(i, 0, 0, false, "neutral")).toList(), 1);
        playback.startSession(movie, 0);
        List<Integer> inputs = new ArrayList<>();
        playback.setFrameObserver(new PlaybackDebugManager.PlaybackFrameObserver() {
            @Override
            public boolean shouldSkipGameplayTick(Bk2FrameInput input) {
                return false;
            }

            @Override
            public void afterFrameAdvanced(Bk2FrameInput input, boolean skipped) {
                inputs.add(input.frameIndex());
            }
        });

        // Input 0 is the unrecorded arm row; destination row 0 starts at input 1.
        stepLoadedPlainLevelGap(context, loop, playback);
        assertEquals(1, playback.getCursorFrame());
        assertEquals(List.of(0), inputs);
        assertEquals(levelClock, context.getSpriteManager().getFrameCounter(),
                "the source ended before this gap; destination Process_Sprites cannot run");
        assertEquals(0, player.getYSubpixelRaw());
        assertEquals(0, player.getYSpeed());
        assertTrue(context.traceRunFrameDriver().isEmpty());

        stepEngineFrame(loop);
        assertEquals(2, playback.getCursorFrame());
        assertEquals(List.of(0, 1), inputs);
        // MoveSprite integrates the old zero velocity, then adds $38.
        assertEquals(0x20, player.getCentreY());
        assertEquals(0, player.getYSubpixelRaw());
        assertEquals(0x38, player.getYSpeed());
        assertEquals(levelClock + 1, context.getSpriteManager().getFrameCounter());
    }
}
