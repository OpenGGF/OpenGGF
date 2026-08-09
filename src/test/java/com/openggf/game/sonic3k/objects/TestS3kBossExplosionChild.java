package com.openggf.game.sonic3k.objects;

import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBossExplosionChild {
    @BeforeEach
    void configureGame() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @Test
    void nativeInitSfxFactoryIsSilentUntilFirstOwnEntryAndPlaysOnce() {
        RecordingServices services = new RecordingServices();
        S3kBossExplosionChild child =
                S3kBossExplosionChild.createWithNativeInitSfx(0x4400, 0x680);
        child.setServices(services);

        assertTrue(services.sfx.isEmpty(), "allocation/constructor must be silent");

        child.update(0, null);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx,
                "Obj_BossExplosion1 owns sfx_Explode on its first own entry");

        for (int entry = 1; entry < 8; entry++) {
            child.update(entry, null);
        }
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx,
                "the native-init SFX is a one-shot, not an animation-frame effect");
    }

    @Test
    void legacyConstructorPreservesCallerOwnedSilence() {
        RecordingServices services = new RecordingServices();
        S3kBossExplosionChild child = new S3kBossExplosionChild(0x4400, 0x680);
        child.setServices(services);

        for (int entry = 0; entry < 32 && !child.isDestroyed(); entry++) {
            child.update(entry, null);
        }

        assertTrue(services.sfx.isEmpty(),
                "ordinary construction preserves every established caller's audio choice");
    }

    @Test
    void genericInitFallsThroughToCursorTwoMappingZeroTimerOneAndSameEntrySfx() {
        RecordingServices services = new RecordingServices();
        S3kBossExplosionChild child =
                S3kBossExplosionChild.createWithNativeInitSfx(0x4400, 0x680);
        child.setServices(services);

        child.update(0, null);

        assertEquals(2, child.rawCursorForTest());
        assertEquals(0, child.mappingFrameForTest());
        assertEquals(1, child.rawTimerForTest());
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx);
    }

    @Test
    void nativeInitSfxOneShotRoundTripsBeforeAndAfterFirstEntry() {
        RecordingServices services = new RecordingServices();
        S3kBossExplosionChild source =
                S3kBossExplosionChild.createWithNativeInitSfx(0x4400, 0x680);
        source.setServices(services);
        PerObjectRewindSnapshot beforeFirstEntry = source.captureRewindState();

        S3kBossExplosionChild restoredBefore = new S3kBossExplosionChild(0, 0);
        restoredBefore.setServices(services);
        restoredBefore.restoreRewindState(beforeFirstEntry);
        assertTrue(restoredBefore.nativeInitSfxForTest());
        assertEquals(false, restoredBefore.nativeInitSfxPlayedForTest());
        assertEquals(0, restoredBefore.rawCursorForTest());

        restoredBefore.update(0, null);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx);
        PerObjectRewindSnapshot afterFirstEntry = restoredBefore.captureRewindState();

        S3kBossExplosionChild restoredAfter = new S3kBossExplosionChild(0, 0);
        restoredAfter.setServices(services);
        restoredAfter.restoreRewindState(afterFirstEntry);
        assertTrue(restoredAfter.nativeInitSfxForTest());
        assertTrue(restoredAfter.nativeInitSfxPlayedForTest());
        assertEquals(2, restoredAfter.rawCursorForTest());
        assertEquals(0, restoredAfter.mappingFrameForTest());
        assertEquals(1, restoredAfter.rawTimerForTest());

        restoredAfter.update(1, null);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), services.sfx,
                "restoring after the first own entry must not replay native init audio");
    }

    @Test
    void terminalRawCustomCodeDrawsOldFrameThenDeletesNextEntry() {
        assertTerminalRawBoundary(false);
        assertTerminalRawBoundary(true);
    }

    private static void assertTerminalRawBoundary(boolean nativeInitSfx) {
        RecordingServices services = new RecordingServices();
        S3kBossExplosionChild child = nativeInitSfx
                ? S3kBossExplosionChild.createWithNativeInitSfx(0x4400, 0x680)
                : new S3kBossExplosionChild(0x4400, 0x680);
        child.setServices(services);
        int oldMapping = child.mappingFrameForTest();

        for (int entry = 0; entry < 0x80; entry++) {
            oldMapping = child.mappingFrameForTest();
            child.update(entry, null);
            if (entry > 0 && child.rawCursorForTest() == 0) {
                assertFalse(child.isDestroyed(),
                        "$F4 installs Go_Delete_Sprite but retains the SST on this entry");
                assertEquals(oldMapping, child.mappingFrameForTest());
                assertEquals(0, child.rawTimerForTest());

                clearInvocations(services.renderer);
                child.appendRenderCommands(new ArrayList<>());
                verify(services.renderer, times(1)).drawFrameIndex(
                        oldMapping, child.getX(), child.getY(), false, false);

                PerObjectRewindSnapshot pending = child.captureRewindState();
                S3kBossExplosionChild restored = new S3kBossExplosionChild(0, 0);
                restored.setServices(services);
                restored.restoreRewindState(pending);
                assertFalse(restored.isDestroyed());
                restored.update(entry + 1, null);
                assertTrue(restored.isDestroyed(),
                        "Delete_Current_Sprite runs without another animation entry");
                assertEquals(nativeInitSfx ? List.of(Sonic3kSfx.EXPLODE.id) : List.of(),
                        services.sfx);
                return;
            }
        }
        throw new AssertionError("AniRaw_BossExplosion did not reach $F4");
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> sfx = new ArrayList<>();
        private final RomByteReader reader = testRomReader();
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        private RecordingServices() {
            when(renderer.isReady()).thenReturn(true);
            when(renderManager.getBossExplosionRenderer()).thenReturn(renderer);
        }

        @Override
        public void playSfx(int soundId) {
            sfx.add(soundId);
        }

        @Override
        public RuntimeArtCoordinator runtimeArtCoordinator() {
            return TestEnvironment.activeGameplayMode().runtimeArtCoordinator();
        }

        @Override
        public RomByteReader romReader() {
            return reader;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }

        private static RomByteReader testRomReader() {
            try {
                return RomByteReader.fromRom(TestEnvironment.currentRom());
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
