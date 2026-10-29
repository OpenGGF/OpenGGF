package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.session.*;
import com.openggf.game.soniccd.level.SonicCdPastLevel;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-disc World/load path. Source components are not substituted for admission. */
@Isolated
class TestSonicCdPastLoadingWorld {
    private static CdGameDataSource source;
    @BeforeEach void setup() {
        TestEnvironment.resetAll(); var c = GameServices.configuration();
        c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 320); c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 224);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic"); c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false); c.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        c.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        c.setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        c.resolveDisplayAspect(); GameServices.graphics().initHeadless(); GameServices.audio().setBackend(new NullAudioBackend());
    }
    @AfterEach void cleanup() { SessionManager.clear(); TestEnvironment.resetAll(); }

    @Test void actualFiveJumpEntryLoadsPastInTheSameWorldAndReplaysItsFirstLoadingService() throws Exception {
        var h = opening(); var m = h.module(); var mode = h.mode(); var lm = mode.getLevelManager();
        var world = mode.getWorldSession(); var oldFrame = m.requirePlayerFrame();
        var oldRuntime = assertInstanceOf(CdOpeningObjectRuntime.class, m.getGameService(NativeDynamicObjectPass.class));
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, m.levelProgramState().phase());
        var saved = oldRuntime.timeWarpSavedState(); var services = m.programServicesState();
        var driver = services.loads().installed().orElseThrow().driver();
        var oldOwner = oldFrame.captureActor().owner(); var carriedClock = oldFrame.vIntClock(); var generation = lm.getCompletedProductionLoadGeneration();
        step(h, route(7729));
        assertSame(world, mode.getWorldSession()); assertSame(source(), world.getDataSource());
        assertEquals(generation + 1, lm.getCompletedProductionLoadGeneration());
        var level = assertInstanceOf(SonicCdPastLevel.class, lm.getCurrentLevel());
        var frame = m.requirePlayerFrame(); var start = m.pastLoadState();
        assertNotSame(oldFrame, frame); assertNotSame(oldOwner, frame.captureActor().owner());
        assertTrue(oldFrame.closed()); assertTrue(oldRuntime.closed());
        assertNull(lm.getLevelGamestate(), "Loading native ring/time have one Module owner, no generic ticking HUD partition");
        assertSame(level.assets(), start.assets()); assertTrue(frame.stageSource().ownsPast(level.assets()));
        assertEquals(SonicCdGameModule.PastLoadPhase.FIRST_PLC, start.phase());
        assertEquals(carriedClock, frame.vIntClock(), "FF190A survives RunMMD and precedes ClearFlowers"); assertEquals(0, lm.getFrameCounter());
        assertEquals(0, frame.state().header().slotId()); assertEquals(0, frame.state().motion().routine());
        assertEquals(0x80, frame.environment().movement().gates().timeZone());
        assertTrue(mode.getSpriteManager().getAllSprites().isEmpty(), "Native LoadPlayer has not been reached");
        assertSame(driver, m.programServicesState().loads().installed().orElseThrow().driver());
        assertEquals(SonicCdProgramLoadServices.LoadPrefix.MATCH_REUSED, m.programServicesState().loads().lastLoad().prefix());
        assertEquals(services.initialization(), m.programServicesState().initialization());
        assertArrayEquals(new byte[128], start.displayedPalette().copy(), "InitVDP output precedes first C publication");
        for (int at = 0; at < 128; at += 2) assertEquals(0xEEE, start.cpuPalette().unsignedWord(at));
        var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var beforeNative = captureNative(h);
        step(h, route(7730));
        var after = m.pastLoadState(); var actor = frame.captureActor(); var shared = frame.captureShared();
        var afterNative = captureNative(h);
        assertEquals(SonicCdGameModule.PastLoadPhase.SECTION_PLC, after.phase());
        assertEquals(0, frame.vIntClock().absoluteValue().orElseThrow(), "The first C pulse precedes the actual ClearFlowers counter reset"); assertEquals(0, lm.getFrameCounter());
        assertEquals(0x80, frame.environment().movement().gates().timeZone());
        assertEquals(saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_X), frame.state().motion().centreX16_16() >>> 16);
        assertEquals(saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_Y), frame.state().motion().centreY16_16() >>> 16);
        assertEquals(saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS), frame.state().motion().flags());
        assertEquals((short) saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_GVEL), frame.state().motion().groundVelocity());
        assertEquals(saved.read(CdTimeWarpSaveData.SavedField.RINGS), after.ring().rings());
        assertEquals(0, frame.state().header().slotId(), "WaitPLC2 precedes LoadPlayer");
        for (int at = 0; at < 128; at += 2) assertEquals(0xEEE, after.displayedPalette().unsignedWord(at));
        assertTrue(after.calls().contains(SonicCdGameModule.PastLoadCall.LOAD_SAVED));
        assertTrue(after.calls().contains(SonicCdGameModule.PastLoadCall.INITIAL_FOREGROUND));
        assertTrue(after.calls().indexOf(SonicCdGameModule.PastLoadCall.INITIAL_FOREGROUND)
                < after.calls().indexOf(SonicCdGameModule.PastLoadCall.INITIAL_BACKGROUND));
        registry.restore(full); assertEquals(start, m.pastLoadState()); assertNativeCapture(beforeNative, h);
        step(h, route(7730)); assertEquals(after, m.pastLoadState()); assertEquals(actor, frame.captureActor()); assertEquals(shared, frame.captureShared()); assertNativeCapture(afterNative, h);
        registry.restoreCourse(course); assertEquals(start, m.pastLoadState()); assertNativeCapture(beforeNative, h);
        step(h, route(7730)); assertEquals(after, m.pastLoadState()); assertEquals(actor, frame.captureActor()); assertEquals(shared, frame.captureShared()); assertNativeCapture(afterNative, h);
        assertEquals(generation + 1, lm.getCompletedProductionLoadGeneration(), "Rewind invokes no load or programme setup");
        assertSame(driver, m.programServicesState().loads().installed().orElseThrow().driver());
    }

    @Test void publicLevelOverrideCannotSelectPastWithoutTheActualStagedSource() throws Exception {
        var h = opening(); var before = h.module().programServicesState();
        assertNull(h.module().loadLevelOverride(0)); assertSame(before, h.module().programServicesState());
        assertInstanceOf(com.openggf.game.soniccd.level.SonicCdLevel.class, h.mode().getLevelManager().getCurrentLevel());
    }

    @Test void descriptiveTransitionContextDoesNotMintThePrivatePastAdmission() throws Exception {
        var h = opening(); var old = h.module().requirePlayerFrame(); var generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var c = SonicCdPlayerInitProfile.openingObjectsContext(); c.setLoadCause(LevelLoadCause.STAGE_RETURN_FULL_ASSEMBLY);
        assertThrows(RuntimeException.class, () -> h.module().getLevelInitProfile().levelLoadSteps(c));
        assertSame(old, h.module().requirePlayerFrame()); assertFalse(old.closed());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }

    @Test void pastLoadingRunsRealSectionServicesToItsExactNativeNextOwner() throws Exception {
        var h = opening(); var module = h.module();
        for (int call = 1; call <= 7730; call++) step(h, route(call));
        var frame = module.requirePlayerFrame();
        var visit = actualField(module, "pastLoading");
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry();
        boolean replayed = false; int returned = 7730, attempted = -1; long finalBeforeCounter = -1;
        for (int call = 7731; call <= 7800; call++) {
            var before = module.pastLoadState();
            assertEquals(SonicCdGameModule.PastLoadPhase.SECTION_PLC, before.phase());
            // Native fast PLC budget is18 tiles; >18 guarantees this is a healthy
            // retained-decoder service rather than the terminal CPU load tail.
            boolean cut = !replayed && before.section().remainingTilesWord() > 18
                    && before.section().remainingTilesWord() <= 36;
            var full = cut ? registry.capture() : null;
            var course = cut ? registry.captureCourse() : null;
            var nativeBefore = cut ? captureNative(h) : null;
            finalBeforeCounter = frame.vIntClock().absoluteValue().orElseThrow();
            try { step(h, route(call)); }
            catch (SonicCdGameModule.UnsupportedPastLoading stopped) {
                assertEquals(SonicCdGameModule.PastLoadBoundary.ANIMATED_TILES, stopped.boundary());
                attempted = call; break;
            }
            returned = call;
            if (cut) {
                var nativeAfter = captureNative(h);
                registry.restore(full); assertNativeCapture(nativeBefore, h);
                step(h, route(call)); assertNativeCapture(nativeAfter, h);
                registry.restoreCourse(course); assertNativeCapture(nativeBefore, h);
                step(h, route(call)); assertNativeCapture(nativeAfter, h);
                replayed = true;
            }
        }
        assertTrue(replayed, "Bounded route must include a real retained-decoder full/course edge");
        assertEquals(returned + 1, attempted, "Bounded continuation must reach its genuine next native owner");
        assertTrue(frame.closed());
        assertEquals((finalBeforeCounter + 1) & 0xFFFFFFFFL, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(1, frame.state().header().slotId()); assertEquals(0, frame.state().motion().routine());
        assertEquals(0x78, frame.state().timers().hurt()); assertEquals(0, frame.environment().globals().levelFrames());
        assertEquals(0x80, frame.environment().movement().gates().timeZone(), "Main315 marker clear is after unowned Main312");
        assertEquals(new com.openggf.game.soniccd.player.CdPlayerBody.Controls(0,0,0,0,0), frame.environment().controls());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        // Retained immutable values are inspected after failure; no guarded capture,
        // owner accessor, retry, Body/effect replay or post-failure service is called.
        var values = actualField(visit, "values");
        assertEquals(SonicCdGameModule.PastLoadPhase.ANIMATED_TILES, actualField(values, "phase"));
        var section = assertInstanceOf(com.openggf.game.soniccd.resources.CdR11bSectionArt.State.class, actualField(values, "section"));
        assertEquals(0, section.queue().headSource()); assertEquals(0, section.remainingTilesWord());
        var globals = actualField(values, "globals"); assertEquals(OptionalInt.of(30), actualField(globals, "drownTimerWord"));
        var hud = assertInstanceOf(com.openggf.game.soniccd.level.CdAssetBytes.class, actualField(values, "hudObjects"));
        byte[] expectedHud = new byte[31 * 64];
        for (int whole : new int[]{2,3,5}) expectedHud[(whole-1)*64] = 0x1C;
        expectedHud[2*64+0x28] = 1; expectedHud[4*64+0x29] = 1;
        expectedHud[30*64] = 0x19; expectedHud[30*64+0x28] = 0x0A;
        assertArrayEquals(expectedHud, hud.copy(), "Every reserved non-primary byte after native clear/stores, before object execution");
        assertSourceLifeIcon(h, section);
        System.out.println("PAST_LOAD_BOUNDARY returned=" + returned + " attempted=" + attempted
                + " clock=" + frame.vIntClock().absoluteValue().orElseThrow()
                + " phase=ANIMATED_TILES full/course=retained-decoder");
    }
    private static void assertSourceLifeIcon(Harness h, com.openggf.game.soniccd.resources.CdR11bSectionArt.State section) {
        var level = assertInstanceOf(SonicCdPastLevel.class, h.mode().getLevelManager().getCurrentLevel());
        var source = level.assets().view(com.openggf.game.soniccd.level.CdR11bAssets.Asset.ART_LIFE_ICON).bytes();
        for (int tile = 0; tile < 4; tile++) assertArrayEquals(source.slice(tile*32,32).copy(),
                com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.readTile(section.residency(),0xB420/32+tile).copy());
    }
    private static Object actualField(Object value, String name) throws ReflectiveOperationException {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }

    /** Read-only comparison of ALL fields in the two actual CD capture partitions. */
    private record NativeCapture(Object loading, Object scroll) { }
    private static NativeCapture captureNative(Harness h) {
        var all = h.mode().getRewindRegistry().capture();
        var loading = all.get("soniccd-opening-player-shared");
        assertNotNull(loading);
        return new NativeCapture(loading, h.module().getScrollHandlerProvider().getHandler(0).captureRewindState());
    }
    private static void assertNativeCapture(NativeCapture expected, Harness h) throws ReflectiveOperationException {
        var actual = captureNative(h);
        assertNativeValue(expected.loading(), actual.loading(), "loading");
        assertNativeValue(expected.scroll(), actual.scroll(), "scroll");
    }
    private static void assertNativeValue(Object expected, Object actual, String field) throws ReflectiveOperationException {
        if (expected == actual) return;
        assertNotNull(expected, field); assertNotNull(actual, field);
        if (expected instanceof com.openggf.game.soniccd.level.CdAssetBytes bytes) {
            assertArrayEquals(bytes.copy(), assertInstanceOf(com.openggf.game.soniccd.level.CdAssetBytes.class, actual).copy(), field);
        } else if (expected instanceof List<?> list) {
            var other = assertInstanceOf(List.class, actual); assertEquals(list.size(), other.size(), field);
            for (int i = 0; i < list.size(); i++) assertNativeValue(list.get(i), other.get(i), field + "[" + i + "]");
        } else if (expected.getClass().isArray()) {
            assertEquals(expected.getClass(), actual.getClass(), field);
            int size = java.lang.reflect.Array.getLength(expected); assertEquals(size, java.lang.reflect.Array.getLength(actual), field);
            for (int i = 0; i < size; i++) assertNativeValue(java.lang.reflect.Array.get(expected, i), java.lang.reflect.Array.get(actual, i), field + "[" + i + "]");
        } else if (expected.getClass().isRecord()) {
            assertEquals(expected.getClass(), actual.getClass(), field);
            for (var component : expected.getClass().getRecordComponents()) {
                var accessor = component.getAccessor(); accessor.setAccessible(true);
                assertNativeValue(accessor.invoke(expected), accessor.invoke(actual), field + "." + component.getName());
            }
        } else if (expected.getClass().getName().equals("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot")) {
            assertEquals(expected.getClass(), actual.getClass(), field);
            for (var component : expected.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(component.getModifiers())) continue;
                component.setAccessible(true);
                assertNativeValue(component.get(expected), component.get(actual), field + "." + component.getName());
            }
        } else {
            // Existing SectionArt/current/live-aux values implement full scoped equality;
            // opaque source/World/Frame/visit owners must retain their exact identities.
            assertEquals(expected, actual, field);
        }
    }

    private record Harness(SonicCdGameModule module, GameplayModeContext mode, GameLoop loop) { }
    private static Harness opening() throws Exception {
        var m = SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(new CdProgramInitialization.ExecutionEnvironment(
                Optional.of(new CdProgramInitialization.ExecutionRegion(0x80))));
        var mode = SessionManager.openGameplaySession(m, m, source(), null); GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        GameModuleRegistry.setCurrent(m); var loop = new GameLoop(EngineServices.current()); loop.setGameplayMode(mode); loop.setInputHandler(new InputHandler()); loop.setGameMode(GameMode.LEVEL);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext()); return new Harness(m,mode,loop);
    }
    private static LogicalInputSnapshot route(int call) {
        int actions = call >= 1201 && call <= 1232 || call >= 1770 && call <= 1801 || call >= 3002 && call <= 3033
                || call >= 5201 && call <= 5232 || call >= 7001 && call <= 7032 ? 2 : 0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,actions,0,false,false),PlayerInputState.neutral());
    }
    private static void step(Harness h, LogicalInputSnapshot input) { h.loop().getInputHandler().setLogicalOverride(input); h.loop().step(); }
    private static CdGameDataSource source() throws Exception {
        if(source!=null)return source; var root=System.getProperty("soniccd.media.root");assertNotNull(root);
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1,cues.size());
            source=CdStockLaunch.prepare(cues.getFirst(),CdStockLaunch.Request.r11a());return source;}
    }
}
