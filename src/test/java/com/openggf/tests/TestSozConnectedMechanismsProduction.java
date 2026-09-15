package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Positioned starts; every subsequent transition comes from production controls and objects. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozConnectedMechanismsProduction {
    @ParameterizedTest
    @EnumSource(SozConnectedMechanismRoute.Scene.class)
    void corkCarryWrapAndPlacedSwitchUseTheProductionGraph(SozConnectedMechanismRoute.Scene scene) {
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
                .startPosition((short) scene.x, (short) scene.y).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.sprite().setRingCount(99);
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        var route = new SozConnectedMechanismRoute(scene);
        var registry = fixture.gameplayMode().getRewindRegistry();
        boolean activated = false, carried = false, wrapped = false, charged = false, fallenRock = false;
        int replayChecks = 0;
        Bk2FrameInput previousInput = new Bk2FrameInput(-1, 0, 0, false, "");
        for (int frame = 0; frame < scene.frames; frame++) {
            var input = route.input(frame, fixture.sprite());
            var before = registry.capture();
            int previousY = fixture.sprite().getCentreY() & 0x7FF;
            boolean previousCollision = state().events().backgroundCollision();
            step(fixture, input);
            var events = state().events();
            int y = fixture.sprite().getCentreY() & 0x7FF;
            boolean activation = !previousCollision && events.backgroundCollision();
            boolean wrap = previousY < 0x80 && y > 0x700;
            activated |= activation;
            wrapped |= wrap;
            carried |= events.backgroundCollision() && !fixture.sprite().getAir()
                    && y < previousY && (scene == SozConnectedMechanismRoute.Scene.UPPER ? y < 0xA0 : y < 0x500);
            charged |= SozZoneRuntimeState.trigger(11) == 128;
            fallenRock |= GameServices.level().getObjectManager()
                    .activeObjectsOfType(SozPushableRockObjectInstance.class).stream()
                    .anyMatch(rock -> rock.getSpawn().x() == 0x4770 && rock.getX() > 0x4790 && rock.getY() == 0x5EC);
            if (activation || wrap || frame == 100 || frame == scene.frames - 1
                    || (scene == SozConnectedMechanismRoute.Scene.LOWER && frame == 1000)) {
                var after = registry.capture();
                GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
                registry.restore(before);
                same(before, registry.capture());
                // The fixture's external controller cursor is outside the gameplay snapshot.
                fixture.runner().primeInputState(previousInput);
                step(fixture, input);
                same(after, registry.capture());
                replayChecks++;
            }
            assertFalse(fixture.sprite().getDead(), scene + " at frame " + frame);
            previousInput = input;
        }
        assertTrue(replayChecks >= 2);
        switch (scene) {
            case UPPER -> {
                assertTrue(activated, "real rolling contact breaks the upper cork");
                assertTrue(carried, "rising background collision carries the grounded player");
                assertTrue(wrapped, "carry crosses the native 0x800 Y boundary");
                assertEquals(0x14, state().events().backgroundRoutine());
            }
            case LOWER -> {
                assertTrue(activated, "real jump contact breaks the lower cork");
                assertTrue(carried, "translated background rows must wrap before absent-row rejection");
                assertTrue((fixture.sprite().getCentreY() & 0x7FF) < 0x380, "player climbs two room levels");
                assertEquals(0x18, state().events().backgroundRoutine());
            }
            case ROCK -> {
                // Native subtype87 track: 05EC,47F0,FFFF. It leaves PUSH before switch4830.
                assertTrue(fallenRock, "placed rock falls onto its lower ROM track before reaching the switch");
                assertFalse(charged, "falling/stopped rocks cannot retain the native push-switch link");
            }
            case SWITCH -> assertTrue(charged, "placed subtype9B charges trigger B through player pushing");
        }
    }
    private static SozZoneRuntimeState state() {
        return S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }
    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int bits = input.p1InputMask();
        fixture.stepFrame(false, false, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), key + ": " + diff);
        }
    }
}
