package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CharacterKey;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** SSZ2_ScreenInit / Obj_57C1E arrival and encounter scroll restoration; not the final fight. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszKnucklesArrivalHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void nativeArrivalRiseAndPriorityReleaseReplayAtBothWidths(int width) {
        var fixture = boot(width);
        assertEquals(CharacterKey.KNUCKLES, fixture.sprite().characterKey());
        assertEquals(width, fixture.camera().getWidth());
        // The runner retries setup-only admission; consume that boundary explicitly
        // to inspect Knuckles_Init/Obj_57C1E before the first LevelLoop rise.
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        assertEquals(0x649, fixture.camera().getY() & 0xFFFF);
        assertEquals(0x6AE, fixture.sprite().getCentreY() & 0xFFFF);
        assertTrue(fixture.camera().getFrozen());
        assertTrue(fixture.sprite().isObjectControlled());
        assertFalse(fixture.sprite().getAir(),
                "Knuckles_Init returns before physics; the arrival preserves grounded status");
        fixture.stepIdleFrames(17);
        assertFalse(fixture.sprite().getAir(), "the teleporter rise must not trigger the crane");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        fixture.stepIdleFrames(12);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int cameraY = fixture.camera().getY();
        registry.restore(snapshot); fixture.stepIdleFrames(12);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(cameraY, fixture.camera().getY());
        // $44 rise passes; the final decrement skips camera movement, just as in act 1.
        fixture.stepIdleFrames(0x44 - 29);
        assertEquals(0xA0, fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(0x6AE - 8 * 0x44, fixture.sprite().getCentreY() & 0xFFFF);
        assertEquals(0x649 - 8 * 0x43, fixture.camera().getY() & 0xFFFF);
        assertTrue(fixture.camera().getFrozen());
        fixture.stepIdleFrames(1);
        assertFalse(fixture.camera().getFrozen());
        assertTrue(fixture.sprite().isHighPriority(), "loc_57D3C sets art_tile bit 15 for act 2");
        assertEquals(SszArrivalControllerObjectInstance.PHASE_SWING,
                GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszArrivalControllerObjectInstance.class).getFirst().phaseForTest());
        assertFalse(fixture.sprite().getDead());
    }
    @ParameterizedTest @ValueSource(ints = {320, 800})
    void act2ScrollRerenderAndRegistryRestoreDoNotAdvanceDriftTwice(int width) {
        var fixture = boot(width);
        fixture.stepIdleFrames(300);
        var state = com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                .currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var scroll = new com.openggf.game.sonic3k.scroll.SwScrlSsz();
        int x = fixture.camera().getX(), y = fixture.camera().getY();
        int[] first = new int[224], repeated = new int[224], next = new int[224];
        // A registry restore recomposes parallax at LevelManager's captured clock.
        // Keep this first render on that clock: inventing a future render frame
        // makes the post-restore callback legitimately compose a different frame.
        int frame = GameServices.level().getFrameCounter();
        int drift = state.cloudDrift();
        boolean advances = frame != state.backgroundScrollFrame();
        scroll.update(first, x, y, frame, 1);
        assertEquals(drift + (advances ? 0x1000 : 0), state.cloudDrift());
        byte[] rendered = state.captureBytes();
        short[] columns = scroll.getPerColumnVScrollBG();
        assertNotNull(columns);
        assertNull(scroll.getPerColumnVScrollFG(), "act-1 launch columns must not leak into act 2");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        scroll.update(repeated, x, y, frame, 1);
        assertArrayEquals(first, repeated);
        assertArrayEquals(rendered, state.captureBytes());
        scroll.update(next, x, y, frame + 1, 1);
        byte[] advanced = state.captureBytes();
        registry.restore(saved);
        // Recreating the handler must not rely on transient last-frame caches.
        scroll = new com.openggf.game.sonic3k.scroll.SwScrlSsz();
        scroll.update(repeated, x, y, frame, 1);
        assertArrayEquals(first, repeated);
        assertArrayEquals(columns, scroll.getPerColumnVScrollBG());
        assertArrayEquals(rendered, state.captureBytes());
        scroll.update(repeated, x, y, frame + 1, 1);
        assertArrayEquals(next, repeated);
        assertArrayEquals(advanced, state.captureBytes());
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void defeatShakeUsesPriorOffsetAcrossForegroundBackgroundAndRewind(int width) {
        var fixture = boot(width);
        fixture.stepIdleFrames(300);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        var rumble = new com.openggf.game.sonic3k.objects.bosses.SszMechaDefeatRumble(
                new com.openggf.level.objects.ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        rumble.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(rumble);
        var scroll = new com.openggf.game.sonic3k.scroll.SwScrlSsz();
        int[] output = new int[224];
        boolean observedNonzero = false;
        for (int i = 0; i < 15; i++) {
            int previous = state.screenShake().offset();
            fixture.stepIdleFrames(1);
            assertEquals(previous, state.appliedScreenShakeOffset(), "screen event consumes prior background result");
            observedNonzero |= previous != 0;
            int x = fixture.camera().getX(), y = fixture.camera().getY();
            scroll.update(output, x, y, GameServices.level().getFrameCounter(), 1);
            assertEquals((short) (y + previous), scroll.getVscrollFactorFG());
            int extra = state.specialVIntRoutine() == 4 || state.specialVIntRoutine() == 8 ? 0 : 8;
            assertEquals((short) (y + previous - 0x320 - state.cloudOscillator() + extra), scroll.getVscrollFactorBG());
        }
        assertTrue(observedNonzero, "exercise active shaking rather than a zero-only check");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(7);
        scroll.update(output, fixture.camera().getX(), fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        byte[] expected = state.captureBytes();
        short foreground = scroll.getVscrollFactorFG(), background = scroll.getVscrollFactorBG();
        short[] columns = scroll.getPerColumnVScrollBG();
        registry.restore(saved); fixture.stepIdleFrames(7);
        state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        scroll = new com.openggf.game.sonic3k.scroll.SwScrlSsz();
        int[] replayed = new int[224];
        scroll.update(replayed, fixture.camera().getX(), fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        assertArrayEquals(output, replayed); assertArrayEquals(expected, state.captureBytes());
        assertEquals(foreground, scroll.getVscrollFactorFG()); assertEquals(background, scroll.getVscrollFactorBG());
        assertArrayEquals(columns, scroll.getPerColumnVScrollBG());
        scroll.update(replayed, fixture.camera().getX(), fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        assertArrayEquals(expected, state.captureBytes(), "same-frame rerender advances neither shake nor drift");
        state.screenShake().writeFlag(0);
        fixture.stepIdleFrames(1);
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.bosses.SszMechaDefeatRumble.class).isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void defeatSignalPatchesOnlyNativeFloorRowsAndReplaysAfterRestore(int width) {
        var fixture = boot(width);
        fixture.stepIdleFrames(300);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        var map = GameServices.level().getCurrentLevel().getMap();
        assertEquals(4, state.foregroundRoutine());
        assertFalse(state.endingRunning());
        int[][] before = new int[12][map.getWidth()];
        for (int row = 0; row < before.length; row++)
            for (int x = 0; x < before[row].length; x++) before[row][x] = map.getValue(0, x, row) & 255;
        // Declared component boundary: loc_7BCB0 supplies this byte after the fade.
        state.setEventsFg4Low(0xFF);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(saved);
                state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
                map = GameServices.level().getCurrentLevel().getMap();
                assertFalse(state.endingRunning());
                assertEquals(4, state.foregroundRoutine());
                for (int row = 0; row < before.length; row++)
                    for (int x = 0; x < before[row].length; x++) assertEquals(before[row][x], map.getValue(0, x, row) & 255);
            }
            fixture.stepIdleFrames(1);
            assertEquals(8, state.foregroundRoutine());
            assertEquals(0, state.eventsFg4());
            assertTrue(state.endingRunning());
            for (int row = 0; row < before.length; row++) {
                for (int x = 0; x < before[row].length; x++) {
                    int expected = row == 9 && x < 9 ? ((x & 1) == 0 ? 0x17 : 0x18)
                            : row == 10 && x < 9 ? 0x19 : before[row][x];
                    assertEquals(expected, map.getValue(0, x, row) & 255, "row=" + row + " x=" + x);
                }
            }
            fixture.stepIdleFrames(2);
            assertEquals(8, state.foregroundRoutine(), "stage8 waits for a separate positive signal");
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void seededIslandRedrawRetainsRowsAndRegeneratesArtAfterRewind(int width) {
        var fixture = boot(width); fixture.stepIdleFrames(300);
        fixture.sprite().setDebugMode(true);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        // Declared screen-event boundary: the camera controller's positive completion byte.
        state.setForegroundRoutine(8); state.setEventsFg4(0xFF);
        fixture.stepIdleFrames(1);
        assertEquals(0xC, state.foregroundRoutine());
        assertEquals(15, state.endingPlane().remaining(), "initializer does not redraw immediately");
        var manager = GameServices.level().getObjectManager();
        assertEquals(1, manager.activeObjectsOfType(com.openggf.game.sonic3k.objects.SszEndingIslandMask.class).size());
        fixture.stepIdleFrames(3);
        assertEquals(9, state.endingPlane().remaining());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        byte[] mixed = state.captureBytes();
        byte[] finished = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                // Deliberately corrupt derived art; restore must regenerate it from event state.
                manager.getObjectServices().currentLevel().getPattern(0x7F0).clear();
                registry.restore(saved);
                state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
                assertArrayEquals(mixed, state.captureBytes());
                assertEquals(6, manager.getObjectServices().currentLevel().getPattern(0x7F0).getPixel(0, 0));
            }
            for (int i = 0; i < 4; i++) {
                fixture.stepIdleFrames(1); assertEquals(0xC, state.foregroundRoutine());
            }
            fixture.stepIdleFrames(1);
            assertEquals(0x10, state.foregroundRoutine());
            assertEquals(-1, state.endingPlane().remaining());
            assertEquals(0, fixture.camera().getY()); assertEquals(0, fixture.camera().getX());
            for (int y = 0; y < 256; y += 8)
                for (int x = 0; x < 512; x += 8)
                    assertEquals(GameServices.level().getForegroundTileDescriptorAtWorld(x + 0x200, y + 0x100),
                            state.endingPlane().descriptor(x, y));
            if (replay == 0) finished = state.captureBytes();
            else assertArrayEquals(finished, state.captureBytes());
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void seededWaterPaletteUsesRomRowsAcrossWrapAndRestore(int width) throws java.io.IOException {
        var fixture = boot(width); fixture.stepIdleFrames(300);
        fixture.sprite().setDebugMode(true);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        // Declared later presentation stage. It is not reached by the excluded ending owner here.
        state.endingPlane().begin(0, GameServices.level()::getForegroundTileDescriptorAtWorld);
        state.setForegroundRoutine(0x18);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(saved);
            for (int frame = 0; frame < 64; frame++) {
                fixture.stepIdleFrames(1);
                int offset = ((frame / 8 + 1) * 6) % 0x30;
                for (int color = 0; color < 3; color++) {
                    int expected = TestEnvironment.objectServices().rom().read16BitAddr(0x592BE + offset + color * 2);
                    int actual = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                            GameServices.level().getCurrentLevel().getPalette(3).getColor(13 + color));
                    assertEquals(expected, actual, "frame=" + frame + " color=" + color);
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void seededIslandBackgroundPreservesNativeColumnsAndExtendsOnlyTheWideMargin(int width) {
        var fixture = boot(width); fixture.stepIdleFrames(300);
        fixture.sprite().setDebugMode(true);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        var provider = new com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider();
        assertEquals(0, provider.backgroundDescriptorRevision(), "encounter projection remains unchanged");
        state.endingPlane().begin(0, GameServices.level()::getForegroundTileDescriptorAtWorld);
        state.setBackgroundCameraX(0x5E);
        if (width == 320) {
            assertEquals(0, provider.backgroundDescriptorRevision(), "native renderer needs no extension");
            return;
        }
        assertTrue(provider.backgroundDescriptorRevision() != 0);
        boolean discardedDifferentArt = false;
        for (int y = 0; y < 1024; y += 8) {
            for (int x = 0x58; x <= 0x198; x += 8) {
                assertEquals(GameServices.level().getBackgroundTileDescriptorAtWorld(x, y),
                        provider.backgroundDescriptorAt(x, y), "native visible tile unchanged");
            }
            int edge = GameServices.level().getBackgroundTileDescriptorAtWorld(0x198, y);
            for (int x = 0x1A0; x < 0x400; x += 8) {
                assertEquals(edge, provider.backgroundDescriptorAt(x, y), "extended ROM sky edge");
                discardedDifferentArt |= edge != GameServices.level().getBackgroundTileDescriptorAtWorld(x, y);
            }
        }
        assertTrue(discardedDifferentArt, "ROM really contains other art beyond the native island view");
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void seededEmeraldPaletteFollowsRomRowsAndReplays(int width) throws java.io.IOException {
        var fixture = boot(width); fixture.stepIdleFrames(300); fixture.sprite().setDebugMode(true);
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 3), true);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        state.endingPlane().begin(0, GameServices.level()::getForegroundTileDescriptorAtWorld);
        state.setForegroundRoutine(0x10);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) registry.restore(saved);
            for (int frame = 0; frame < 64; frame++) {
                fixture.stepIdleFrames(1);
                int offset = ((frame / 4 + 1) * 4) % 0x38;
                for (int color = 0; color < 2; color++) {
                    int expected = TestEnvironment.objectServices().rom().read16BitAddr(0x5931A + offset + color * 2);
                    assertEquals(expected, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                            GameServices.level().getCurrentLevel().getPalette(3).getColor(11 + color)));
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void seededIncompleteEmeraldRedrawFallsThroughAndReplays(int width) {
        var fixture = boot(width); fixture.stepIdleFrames(300); fixture.sprite().setDebugMode(true);
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 0), false);
        var state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
        state.endingPlane().begin(0x100, GameServices.level()::getForegroundTileDescriptorAtWorld);
        state.setForegroundRoutine(0x10); state.setEventsFg4(0xFF);
        fixture.stepIdleFrames(1);
        assertEquals(0x14, state.foregroundRoutine());
        assertEquals(13, state.endingPlane().remaining(), "loc_58C1A falls straight into the first two rows");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        byte[] expected = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) { registry.restore(saved); state = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState(); }
            for (int i = 0; i < 6; i++) { fixture.stepIdleFrames(1); assertEquals(0x14, state.foregroundRoutine()); }
            fixture.stepIdleFrames(1);
            assertEquals(0x18, state.foregroundRoutine());
            assertEquals(0x720, fixture.camera().getY());
            for (int y = 0; y < 256; y += 8)
                for (int x = 0; x < 512; x += 8)
                    assertEquals(GameServices.level().getForegroundTileDescriptorAtWorld(x, y + 0x700), state.endingPlane().descriptor(x, y));
            if (replay == 0) expected = state.captureBytes(); else assertArrayEquals(expected, state.captureBytes());
        }
    }

    private static HeadlessTestFixture boot(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(10, 1)
                .withFreshLevelStartLifecycle().build();
    }

}
