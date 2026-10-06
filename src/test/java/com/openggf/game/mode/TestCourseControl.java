package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The controller capability is reusable on a stock module, without loading golf. */
@RequiresRom(SonicGame.SONIC_3K)
class TestCourseControl {
    private SharedLevel bootstrap;
    private com.openggf.game.session.GameplayModeContext runtime;

    @AfterEach void close() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    private CourseControl course() throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        runtime = fixture.runtime();
        return new CourseControl(fixture.runtime());
    }

    @Test void replayAudioHasIndependentOwnershipAndForgottenLeasesCloseWithTheSession() throws Exception {
        var course = course();
        var replay = course.recordAudioReplay(1);
        GameServices.audio().presentFrame(com.openggf.audio.presentation.PresentationMode.FORWARD);
        assertThrows(IllegalArgumentException.class, () -> replay.beginReverse(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> replay.beginReverse(65));
        assertFalse(GameServices.audio().isReverseAudioOutputActive());
        replay.beginReverse(2);
        assertTrue(GameServices.audio().isReverseAudioOutputActive());
        assertFalse(GameServices.audio().isReverseAudioPresentationActive(), "a view owns no logical restore");
        runtime.tearDownManagers();
        assertTrue(replay.isClosed());
        assertFalse(GameServices.audio().isReverseAudioOutputActive());
        replay.close();
        assertThrows(IllegalStateException.class, () -> course.recordAudioReplay(1));
    }

    @Test void failedSinkFlushCannotStrandReverseAudioAfterSessionTeardown() throws Exception {
        var course = course();
        var audio = GameServices.audio();
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        audio.setBackend(new com.openggf.audio.NullAudioBackend() {
            @Override public com.openggf.audio.output.AudioPresentationSink createPresentationSink(
                    java.util.function.Consumer<Throwable> failures, java.util.function.Consumer<String> warnings) {
                return new com.openggf.audio.output.AudioPresentationSink() {
                    @Override public int sampleRate() { return 48_000; }
                    @Override public void accept(com.openggf.audio.presentation.AudioPresentationFrameView frame) { }
                    @Override public void onReverseBoundary() {
                        if (fail.get()) throw new IllegalStateException("failed replay sink flush");
                    }
                    @Override public void close() { }
                };
            }
        });
        var replay = course.recordAudioReplay(1);
        replay.beginReverse(1);
        fail.set(true);
        var failure = assertThrows(IllegalStateException.class, runtime::tearDownManagers);
        assertEquals("failed replay sink flush", failure.getMessage());
        assertTrue(replay.isClosed());
        assertFalse(audio.isReverseAudioOutputActive(), "a discarded session cannot keep selecting REVERSE");
        assertFalse(audio.isReverseAudioPresentationActive());
        assertDoesNotThrow(runtime::tearDownManagers);
        assertDoesNotThrow(replay::close);
        fail.set(false);
        try (var next = audio.recordPresentationAudio(1)) {
            next.beginReverse(1); // The next session can acquire the same producer.
        }
    }

    @Test void rollingImpulseAcceptsCreatorVelocityWithoutGolfLoftOrPowerLimits() throws Exception {
        var course = course();
        var before = course.playerState();
        course.launchRolling(-1, -0x40, -0x3000, 0, true);
        var launched = course.playerState();
        assertEquals(-0x40, launched.xSpeed());
        assertEquals(-0x3000, launched.ySpeed());
        assertEquals(0, launched.groundSpeed());
        assertTrue(launched.rolling());
        assertTrue(launched.airborne());
        assertEquals(before.x(), launched.x(), "flat rolling-radius change preserves X");
        assertEquals(before.y() + before.radius() - launched.radius(), launched.y());
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(0, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(1, 32768, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> course.launchRolling(1, 0, -32769, 0, true));
        assertEquals(launched, course.playerState(), "invalid impulses make no partial changes");
    }

    @Test void registeredKnucklesAndExplicitHydrocityDestinationNeedNoGolfCarveouts() throws Exception {
        var course = course();
        course.selectCharacter("knuckles");
        assertEquals("knuckles", course.playerState().character());
        var player = GameServices.camera().getFocusedSprite();
        assertThrows(IllegalArgumentException.class, () -> course.selectCharacter("missing-author:missing"));
        assertSame(player, GameServices.camera().getFocusedSprite());
        assertEquals("knuckles", GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        course.loadLevel(1, 0);
        assertEquals(1, GameServices.level().getCurrentZone());
        assertEquals(0, course.actIndex());
        assertThrows(IllegalArgumentException.class, () -> course.loadLevel(-1, 0));
        assertEquals(1, GameServices.level().getCurrentZone());
    }

    @Test void characterReplacementPreservesTheEntireCameraViewAndRebindsItsTarget() throws Exception {
        var course = course();
        var camera = GameServices.camera();
        var before = camera.capture();
        var oldPlayer = camera.getFocusedSprite();
        course.selectCharacter("knuckles");
        assertEquals(before, camera.capture(), "character selection must not reset a held camera to sprite top-left");
        assertNotSame(oldPlayer, camera.getFocusedSprite());
        assertEquals("knuckles", camera.getFocusedSprite().getCode());
        assertSame(GameServices.sprites().getSprite("knuckles"), camera.getFocusedSprite());
    }
}
