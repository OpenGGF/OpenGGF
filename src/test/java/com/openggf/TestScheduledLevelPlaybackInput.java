package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production load-seam input ownership regression from the stock parity round 2 probe. */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestScheduledLevelPlaybackInput {
    private final PlaybackDebugManager playback = PlaybackDebugManager.getInstance();
    private HeadlessTestFixture fixture;
    private InputHandler input;
    private GameLoop loop;

    @BeforeEach
    void setUp() throws Exception {
        playback.endSession();
        Engine.clearGlobalInstance();
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        input = new InputHandler();
        loop = new GameLoop(input);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        assertNull(Engine.currentGameLoop(), "publication must work without an Engine-global loop");
    }

    @AfterEach
    void tearDown() {
        playback.endSession();
        SessionManager.clear();
        Engine.clearGlobalInstance();
    }

    @ParameterizedTest
    @ValueSource(ints = {AbstractPlayableSprite.INPUT_UP, AbstractPlayableSprite.INPUT_RIGHT})
    void loadActivationPublishesCurrentInputAndReleaseWithoutScriptedLatches(int direction)
            throws Exception {
        AbstractPlayableSprite player = fixture.sprite();
        assertEquals(0, player.getForcedInputMask());
        playback.scheduleSessionAtNextLevelLoad(movie(direction), 0);
        activateAtCommonLoadSeam();

        assertEquals(direction | AbstractPlayableSprite.INPUT_JUMP,
                input.logical().player1().heldMask(), "the first destination input is synchronous");
        assertEquals(0x04, input.logical().player1().actionPressedMask());
        assertEquals(0, player.getForcedInputMask(), "BK2 input is not scripted control");
        assertFalse(player.isForcedJumpPress());
        GameLoopTestStep.invoke(loop, "updateLevelMode", new Class<?>[]{boolean.class}, false);
        assertEquals(1, playback.getCursorFrame(), "the real held body advances exactly one row");

        syncInput();
        GameLoopTestStep.invoke(loop, "updateLevelMode", new Class<?>[]{boolean.class}, false);
        assertEquals(0, input.logical().player1().heldMask());
        assertEquals(0, input.logical().player1().actionPressedMask(), "release has no phantom edge");
        assertEquals(0, player.getLogicalInputState() & (direction | AbstractPlayableSprite.INPUT_JUMP));
        assertEquals(0, player.getForcedInputMask());
        assertFalse(player.isForcedJumpPress());
        assertEquals(2, playback.getCursorFrame(), "the real release body advances exactly one row");

        syncInput();
        assertEquals(direction | AbstractPlayableSprite.INPUT_JUMP, input.logical().player1().heldMask());
        assertEquals(0x04, input.logical().player1().actionPressedMask(), "repress has a new real edge");
    }

    @Test
    void scheduledPlaybackPreservesAnExistingScriptedControlOwner() throws Exception {
        AbstractPlayableSprite player = fixture.sprite();
        player.setForceInputRight(true);
        player.setControlLocked(true);
        playback.scheduleSessionAtNextLevelLoad(movie(AbstractPlayableSprite.INPUT_UP), 0);
        activateAtCommonLoadSeam();

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getForcedInputMask());
        assertTrue(player.isForceInputRight());
        GameLoopTestStep.invoke(loop, "updateLevelMode", new Class<?>[]{boolean.class}, false);
        syncInput();
        GameLoopTestStep.invoke(loop, "updateLevelMode", new Class<?>[]{boolean.class}, false);
        assertEquals(0, input.logical().player1().heldMask());
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getForcedInputMask());
        assertTrue((player.getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT) != 0);
        assertFalse((player.getLogicalInputState() & AbstractPlayableSprite.INPUT_UP) != 0);
    }

    @Test
    void actualLevelReloadPublishesBeforeTheNextLoopTick() throws Exception {
        playback.scheduleSessionAtNextLevelLoad(movie(AbstractPlayableSprite.INPUT_RIGHT), 0);
        GameServices.level().loadZoneAndAct(0, 1);

        assertEquals(0, playback.getCursorFrame(), "load activation does not consume a row");
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                input.logical().player1().heldMask());
        AbstractPlayableSprite destination = (AbstractPlayableSprite) GameServices.sprites()
                .getSprite("sonic");
        assertEquals(0, destination.getForcedInputMask());
        assertFalse(destination.isForcedJumpPress());
    }

    @Test
    void immediatePublicationUsesTheLoopsReplacementInputHandler() throws Exception {
        InputHandler replacement = new InputHandler();
        loop.setInputHandler(replacement);
        playback.scheduleSessionAtNextLevelLoad(movie(AbstractPlayableSprite.INPUT_RIGHT), 0);
        activateAtCommonLoadSeam();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                replacement.logical().player1().heldMask());
        assertEquals(0, input.logical().player1().heldMask());
    }

    @Test
    void detachingAnOlderLoopPreservesTheNewerLoopsPublisher() throws Exception {
        InputHandler newerInput = new InputHandler();
        GameLoop newerLoop = new GameLoop(newerInput);
        newerLoop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        loop.setGameplayMode(new GameplayModeContext(new WorldSession(new Sonic1GameModule())));

        playback.scheduleSessionAtNextLevelLoad(movie(AbstractPlayableSprite.INPUT_RIGHT), 0);
        activateAtCommonLoadSeam();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                newerInput.logical().player1().heldMask());
        assertEquals(0, input.logical().player1().heldMask());
    }

    private void activateAtCommonLoadSeam() throws Exception {
        Method method = LevelManager.class.getDeclaredMethod("activateScheduledPlaybackForLoadedLevel");
        method.setAccessible(true);
        method.invoke(GameServices.level());
    }

    private void syncInput() throws Exception {
        Method method = GameLoop.class.getDeclaredMethod("syncPlaybackInputBridge");
        method.setAccessible(true);
        method.invoke(loop);
    }

    private static Bk2Movie movie(int direction) {
        int held = direction | AbstractPlayableSprite.INPUT_JUMP;
        return new Bk2Movie(Path.of("scheduled-level-input.bk2"), "logkey", Map.of(),
                List.of(new Bk2FrameInput(0, held, 0x04, false, "held"),
                        new Bk2FrameInput(1, 0, 0, false, "release"),
                        new Bk2FrameInput(2, held, 0x04, false, "repress")), 1);
    }
}
