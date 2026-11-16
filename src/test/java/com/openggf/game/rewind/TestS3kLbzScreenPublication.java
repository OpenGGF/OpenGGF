package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbzScreenPublication {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @Test void malformedComposedPayloadDoesNotMutateState() {
        var events = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        events.writeScreenShakeFlag(30);
        events.setupScreenShake(0, false); // timed index 29 reads into contiguous table 2
        events.publishScreenShakeForeground();
        byte[] before = events.captureLbzState();
        var restored = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        restored.restoreLbzState(before);
        assertArrayEquals(before, restored.captureLbzState());
        assertEquals(3, restored.screenShakeOffset());
        for (int at : new int[] {0, 4, 8, before.length - 20}) {
            byte[] bad = before.clone();
            java.nio.ByteBuffer.wrap(bad).putInt(at, 0x10000);
            assertFalse(com.openggf.game.sonic3k.events.Sonic3kLBZEvents.hasValidLbzState(bad));
            assertThrows(IllegalArgumentException.class, () -> events.restoreLbzState(bad));
            assertArrayEquals(before, events.captureLbzState());
        }
        byte[] truncated = java.util.Arrays.copyOf(before, before.length - 1);
        assertThrows(IllegalArgumentException.class, () -> events.restoreLbzState(truncated));
        assertArrayEquals(before, events.captureLbzState());
    }

    @Test void flagFromAnotherZoneDoesNotKeepDeathEggRumbleAlive() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        // The ship's native release clears Screen_shake_flag; a retained S2/shared
        // boolean is not the ROM word tested by LBZ2BGE_DeathEgg.
        state.setLaunchActive(true);
        state.setDeathEggRumble(true);
        state.writeScreenShakeFlag(0);
        GameServices.gameState().setScreenShakeActive(true);
        fixture.runner().stepFrame(false, false, false, false, false);
        assertFalse(state.isDeathEggRumble());
        assertEquals(0, events.screenShakeFlag());
    }

    @Test void registryRebindRetainsTheEventOwnedPublication() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var original = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        original.writeScreenShakeFlag(20);
        original.setLaunchYDelta(3);
        byte[] before = manager.getLbzEvents().captureLbzState();
        GameServices.zoneRuntimeRegistry().install(new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(1, original.playerCharacter()));
        manager.ensureZoneRuntimeStateInstalled();
        assertSame(original, GameServices.zoneRuntimeRegistry().current());
        assertArrayEquals(before, manager.getLbzEvents().captureLbzState());
        original.setLaunchRiderDelta(3);
        fixture.sprite().setObjectRoutineOverride(6);
        original.setLaunchActive(true);
        manager.update();
        assertEquals(0, original.getLaunchRiderDelta(), "a skipped BG movement dispatch must publish no repeated ship add");
    }

    @Test void shakeUsesTheLevelClockRatherThanEventUpdateOrdinal() {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        f.stepIdleFrames(7);
        var e = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        e.writeScreenShakeFlag(0xFF00);
        var oracle = new com.openggf.game.sonic3k.runtime.S3kScreenShake();
        oracle.writeFlag(0xFF00);
        oracle.setup(GameServices.level().getFrameCounter(), false);
        e.update(1, 123);
        assertEquals(oracle.offset(), e.screenShakeOffset());
    }

    @Test void waterWavesUseTheLevelClockRatherThanEventUpdateOrdinal() {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        f.stepIdleFrames(7);
        var e = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        var scroll = (com.openggf.game.sonic3k.scroll.SwScrlLbz) GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_LBZ);
        var oracle = scroll.createBackgroundPublication();
        oracle.restorePublication(e.backgroundPublication().capturePublication());
        f.camera().setY((short) 0x5F0);
        f.camera().copyLivePositionToScreenEventWords();
        e.update(1, 123);
        oracle.publish(f.camera().getXCopy(), f.camera().getYCopy(), GameServices.level().getFrameCounter(), 1);
        assertArrayEquals(oracle.capturePublication(), e.backgroundPublication().capturePublication());
    }

    @Test void nullCameraFocusDoesNotReplaceTheNativePrimarySlot() throws Exception {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        var e = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        assertSame(f.sprite(), GameServices.sprites().getMainPlayable());
        e.writeScreenShakeFlag(20);
        var focus = com.openggf.camera.Camera.class.getDeclaredField("focusedSprite");
        focus.setAccessible(true); focus.set(f.camera(), null);
        e.update(1, 123);
        assertEquals(19, e.screenShakeFlag());
    }

    @Test void finalFallRetainsShakeWordsAndReplacesTheShakenForegroundCopy() {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        var e = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        e.writeScreenShakeFlag(20); e.setupScreenShake(0, false);
        state.setLaunchActive(true); state.setFinalFallActive(true);
        int y = f.camera().getY() & 0xFFFF;
        e.update(1, 123);
        assertEquals(19, e.screenShakeFlag(), "Falling returns before setup");
        assertEquals(-5, e.screenShakeOffset(), "the native shake word is retained");
        assertEquals(y, e.foregroundCameraYCopy(0), "Falling overwrites the earlier shaken copy");
        assertEquals((y - 2) & 0xFFFF, f.camera().getY() & 0xFFFF);
    }

    @Test void corruptLbzPayloadCannotMutateManagerBaseFields() {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        var m = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var before = m.capture();
        byte[] extra = before.extra();
        int magic = -1;
        for (int i = 0; i <= extra.length - 8; i++) if (java.nio.ByteBuffer.wrap(extra).getInt(i) == 0x4C425A31) { magic = i; break; }
        assertTrue(magic >= 0);
        java.nio.ByteBuffer.wrap(extra).putInt(magic + 4, 99);
        var incoming = new com.openggf.game.rewind.snapshot.LevelEventSnapshot(0, 0, 77, 88, 999, 55, true,
                new short[] {7}, new byte[] {8}, extra);
        assertDoesNotThrow(() -> m.restore(incoming));
        var after = m.capture();
        assertEquals(before.currentZone(), after.currentZone()); assertEquals(before.currentAct(), after.currentAct());
        assertEquals(before.eventRoutineFg(), after.eventRoutineFg()); assertEquals(before.eventRoutineBg(), after.eventRoutineBg());
        assertEquals(before.frameCounter(), after.frameCounter()); assertEquals(before.timerFrames(), after.timerFrames());
        assertEquals(before.bossActive(), after.bossActive());
        assertArrayEquals(before.eventDataFg(), after.eventDataFg()); assertArrayEquals(before.eventDataBg(), after.eventDataBg());
        assertArrayEquals(before.extra(), after.extra());
    }

    @Test void differentBoundIdentityCannotMutateManagerBaseFields() {
        var f = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        var m = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var before = m.capture();
        byte[] extra = before.extra();
        int magic = -1;
        for (int i = 0; i <= extra.length - 8; i++) if (java.nio.ByteBuffer.wrap(extra).getInt(i) == 0x4C425A31) { magic = i; break; }
        assertTrue(magic >= 4);
        var current = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var other = current.playerCharacter() == com.openggf.game.PlayerCharacter.KNUCKLES
                ? com.openggf.game.PlayerCharacter.SONIC_ALONE : com.openggf.game.PlayerCharacter.KNUCKLES;
        byte[] foreign = new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(1, other).captureBytes();
        int oldLength = java.nio.ByteBuffer.wrap(extra).getInt(magic - 4);
        var changed = java.nio.ByteBuffer.allocate(extra.length - oldLength + foreign.length);
        changed.put(extra, 0, magic - 4).putInt(foreign.length).put(foreign)
                .put(extra, magic + oldLength, extra.length - magic - oldLength);
        m.restore(new com.openggf.game.rewind.snapshot.LevelEventSnapshot(0, 0, 77, 88, 999, 55, true,
                new short[] {7}, new byte[] {8}, changed.array()));
        var after = m.capture();
        assertEquals(before.currentZone(), after.currentZone()); assertEquals(before.currentAct(), after.currentAct());
        assertEquals(before.frameCounter(), after.frameCounter()); assertEquals(before.timerFrames(), after.timerFrames());
        assertArrayEquals(before.extra(), after.extra());
        assertSame(current, GameServices.zoneRuntimeRegistry().current());
    }

    @Test void boundRuntimeIdentityMismatchIsRejectedAtomically() {
        var e = new com.openggf.game.sonic3k.events.Sonic3kLBZEvents();
        var bound = new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(1, com.openggf.game.PlayerCharacter.KNUCKLES, e);
        byte[] before = e.captureLbzState();
        for (var foreign : new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState[] {
                new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(0, com.openggf.game.PlayerCharacter.KNUCKLES),
                new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(1, com.openggf.game.PlayerCharacter.SONIC_ALONE)}) {
            assertThrows(IllegalArgumentException.class, () -> e.restoreLbzState(foreign.captureBytes()));
            assertArrayEquals(before, e.captureLbzState());
            assertSame(bound, e.runtimePublication(1, com.openggf.game.PlayerCharacter.KNUCKLES));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1})
    void directedLoadPreparesInitialScrollBeforeLbzScreenInit(int act) throws Exception {
        com.openggf.tests.SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_LBZ, act);
        var nativeModule = GameServices.module();
        com.openggf.game.session.SessionManager.clear();
        com.openggf.game.GameModuleRegistry.setCurrent(new com.openggf.game.patch.DelegatingGameModule(nativeModule, "lbz-load-witness") {
            @Override public <T> T getGameService(Class<T> type) {
                if (type == com.openggf.level.scroll.DirectedScrollPresentation.class)
                    return type.cast(com.openggf.level.scroll.DirectedScrollPresentation.ENABLED);
                return super.getGameService(type);
            }
        });
        TestEnvironment.activeGameplayMode();
        var fixture = assertDoesNotThrow(() -> HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, act).build());
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        byte[] before = manager.getLbzEvents().captureLbzState();
        assertDoesNotThrow(() -> GameServices.level().recomputeParallaxOnlyForCurrentFrame());
        assertArrayEquals(before, manager.getLbzEvents().captureLbzState(), "initial-scroll reconstruction must not dispatch ScreenInit or BG events");
        var handler = (com.openggf.game.sonic3k.scroll.SwScrlLbz) GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_LBZ);
        int x = fixture.camera().getX(), y = fixture.camera().getY();
        int[] words = new int[224];
        handler.update(words, x, y, GameServices.level().getFrameCounter(), act);
        short foregroundY = handler.getVscrollFactorFG() == 0 ? (short) y : handler.getVscrollFactorFG();
        var output = new com.openggf.level.scroll.ScrollProjection(words, null, null, null, foregroundY,
                handler.getVscrollFactorBG(), handler.getBgCameraX(), handler.getBgPeriodWidth(),
                handler.getShakeOffsetX(), handler.getShakeOffsetY(), handler.getBgTilemapUpdateMode());
        var phase = handler.captureProjection(new com.openggf.level.scroll.ScrollProjectionContext(x, y,
                GameServices.level().getFrameCounter(), act, 320, 224, output));
        assertEquals(output, phase.sample(new com.openggf.level.scroll.ScrollView(x, y, 320, 224)));
        assertEquals(384, phase.sample(new com.openggf.level.scroll.ScrollView(x + 31, y + 17, 577, 384)).horizontal().length);
        assertArrayEquals(before, manager.getLbzEvents().captureLbzState(), "sampling the load phase cannot advance native state");
        fixture.stepIdleFrames(1);
    }

    @Test void validatedRestorePropagatesUnexpectedOwnerFailures() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1).withSkippedZoneIntro().build();
        var manager = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var snapshot = manager.capture();
        var owner = org.mockito.Mockito.spy(manager.getLbzEvents());
        var marker = new IllegalStateException("unexpected LBZ owner failure");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) throw marker;
            return invocation.callRealMethod();
        }).when(owner).restoreLbzState(org.mockito.ArgumentMatchers.any(byte[].class));
        var field = Sonic3kLevelEventManager.class.getDeclaredField("lbzEvents");
        field.setAccessible(true); field.set(manager, owner);
        assertSame(marker, assertThrows(IllegalStateException.class, () -> manager.restore(snapshot)),
                "a validated payload does not authorize swallowing unexpected runtime failures");
    }

    @Test void rendererRebuildDoesNotConsumeTimedShake() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .withSkippedZoneIntro().build();
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
        events.startBigArmTimedShake(20);
        fixture.runner().stepFrame(false, false, false, false, false);
        fixture.runner().stepFrame(false, false, false, false, false);
        var handler = GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_LBZ);
        int shake = handler.getShakeOffsetY();
        assertNotEquals(0, shake, "real LBZ2 screen event published the timed shake");
        byte[] before = events.captureLbzState();
        int baseY = (events.foregroundCameraYCopy(0) - shake) & 0xFFFF;
        int fixed = ((short) (baseY - 0x5F0) << 16) >> 1;
        int step = fixed >> 3;
        int expectedBg = ((fixed - step - (step >> 2)) >> 16) + 0x2C0 + shake;
        assertEquals((short) expectedBg, handler.getVscrollFactorBG(),
                "LBZ2_Deform subtracts shake from the already-shaken FG copy");
        int[] words = new int[224];
        handler.update(words, fixture.camera().getX(), fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        assertEquals(shake, handler.getShakeOffsetY(), "same-frame renderer rebuild must retain publication");
        assertArrayEquals(before, events.captureLbzState(), "rebuild advances neither shake nor cloud/table state");
    }
}
