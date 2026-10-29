package com.openggf.game.soniccd;

import com.openggf.game.session.EngineTiming;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original-disc same-World FromWhite continuation. The fixed controller recipe selects the
 * actual loading owner; it supplies no actor values, palette, art, service or replay result.
 * Native N1ecc5f3a Level/Main.asm:312-335 and Palette Fade.asm:178-224.
 * These are logical selected/CPU value checks, not GPU, emulator, audio or regional parity.
 */
@Isolated
class TestSonicCdPastLoadingFade {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();

    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualPastFromWhiteRunsAllTwentyTwoServicesBeforeTheFirstOrdinaryPlacementBoundary() throws Exception {
        var cut = fadeEntry(); var h = cut.harness(); var module = h.module();
        var frame = module.requirePlayerFrame(); var visit = actualField(module, "pastLoading");
        var entry = module.pastLoadState(); var entryValues = values(h);
        var entryActor = frame.captureActor(); var entryShared = frame.captureShared();
        var entryEnvironment = frame.environment(); var entryProgram = module.levelProgramState();
        var entryScroll = module.getScrollHandlerProvider().getHandler(0).captureRewindState();
        var generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var world = h.mode().getWorldSession();
        assertEquals(SonicCdGameModule.PastLoadPhase.FROM_WHITE, entry.phase());
        assertFadeProgress(entryValues, 0, false);
        assertEquals(22L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(new CdPlayerBody.Controls(0, 0, 0, 0, 0), entryEnvironment.controls());
        assertEquals(0, entryEnvironment.movement().gates().timeZone(), "Main315 clears only the native marker");
        assertEquals(0, entryEnvironment.globals().levelFrames());
        assertEquals(1, entryActor.state().header().slotId()); assertEquals(0, entryActor.state().motion().routine());
        assertEquals(0x78, entryActor.state().timers().hurt()); assertFalse(entryShared.paused());
        assertEquals(0, entry.level().powerup(), "The actual BCLR gate returned without an art request");
        assertEquals(1, entry.level().fadeEnableDisplay());
        assertEquals(0, entry.section().queue().headSource()); assertEquals(0, entry.section().remainingTilesWord());
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_LOADING, entryProgram.phase());
        assertNotEquals(0, entryProgram.gameMode() & 0x80); assertEquals(0, entryProgram.levelStarted());
        var globals = actualField(entryValues, "globals");
        var tail = assertInstanceOf(Optional.class, actualField(globals, "tail")).orElseThrow();
        assertEquals(0x003F, actualField(tail, "paletteFadeInfoWord"), "FadeFromWhite replaces the old202F extent");
        byte[] targetBytes = new byte[128];
        System.arraycopy(entry.assets().view(CdR11bAssets.Asset.PAL_SONIC).bytes().copy(), 0, targetBytes, 0, 32);
        System.arraycopy(entry.assets().view(CdR11bAssets.Asset.PAL_LEVEL).bytes().copy(), 0, targetBytes, 32, 96);
        var target = CdAssetBytes.copyOf(targetBytes);
        assertArrayEquals(targetBytes, assertInstanceOf(CdAssetBytes.class, actualField(entryValues, "fadePalette")).copy());
        assertArrayEquals(literalPalette(target, 0), entry.cpuPalette().copy(), "All64 colours start white, including Sonic line0");
        for (int pass = 1; pass <= 22; pass++) {
            var before = module.pastLoadState();
            assertEquals(SonicCdGameModule.PastLoadPhase.FROM_WHITE, before.phase());
            assertFadeProgress(values(h), pass - 1, pass > 1);
            step(h, route(cut.returned() + pass));
            var after = module.pastLoadState(); var afterValues = values(h); var shared = frame.captureShared();
            assertEquals(pass == 22 ? SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL
                    : SonicCdGameModule.PastLoadPhase.FROM_WHITE, after.phase());
            assertFadeProgress(afterValues, pass, true);
            assertArrayEquals(before.cpuPalette().copy(), after.displayedPalette().copy(), "Selected palette precedes colour pass" + pass);
            assertArrayEquals(literalPalette(target, pass - 1), after.displayedPalette().copy(), "Literal selected pass" + pass);
            assertArrayEquals(literalPalette(target, pass), after.cpuPalette().copy(), "Literal CPU pass" + pass);
            if (pass >= 21) assertArrayEquals(targetBytes, after.cpuPalette().copy(), "Canonical target is reached by pass21");
            assertEquals(22L + pass, frame.vIntClock().absoluteValue().orElseThrow());
            assertEquals(entryActor, frame.captureActor(), "No Sonic Body, history, pose, hurt countdown or Display runs");
            assertSame(entryShared.owner(), shared.owner()); assertSame(entryShared.artState(), shared.artState());
            assertFalse(shared.paused());
            var controls = new CdPlayerBody.Controls(8, pass == 1 ? 8 : 0, 0, 0, 0);
            assertEquals(new CdPlayerBody.Environment(entryEnvironment.movement(), controls,
                    entryEnvironment.globals(), entryEnvironment.dynamicSlots()), frame.environment(), "Only actual raw latches change");
            var l = entry.level();
            assertEquals(new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(),
                    l.plcLoadFlags(), l.flowerCount(), 2, l.powerup(), l.timeOver()), after.level());
            assertUntouchedValues(entryValues, afterValues);
            assertEquals(entryScroll, module.getScrollHandlerProvider().getHandler(0).captureRewindState(), "Selector12 has no DrawLevel/camera copy");
            assertEquals(entry.sounds(), after.sounds(), "The fade adds no Body or art sound request");
            assertEquals(0, h.mode().getLevelManager().getFrameCounter());
            assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty());
            assertNull(h.mode().getLevelManager().getLevelGamestate());
            assertSame(world, h.mode().getWorldSession());
            assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
            var p = entryProgram;
            assertEquals(new SonicCdGameModule.LevelProgramState(p.demoMode(), p.timeStones(), p.specialStage(),
                    p.usePlayer2(), p.palFadeFlags(), pass == 22 ? p.gameMode() & 0x7F : p.gameMode(),
                    pass == 22 ? 1 : p.levelStarted(), p.whiteFadeSteps(), p.phase()), module.levelProgramState());
        }
        var finalValues = values(h); var finalActor = frame.state(); var finalEnvironment = frame.environment();
        var finalClock = frame.vIntClock();
        var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,
                () -> step(h, route(cut.returned() + 23)));
        assertEquals(SonicCdGameModule.PastLoadBoundary.PLACEMENT, stopped.boundary());
        assertTrue(frame.closed());
        assertEquals((finalClock.absoluteValue().orElseThrow() + 1) & 0xFFFFFFFFL,
                frame.vIntClock().absoluteValue().orElseThrow(), "Actual first ordinary8 completed before Main placement");
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(finalActor, frame.state());
        var g = finalEnvironment.globals();
        assertEquals(new CdPlayerBody.Environment(finalEnvironment.movement(), finalEnvironment.controls(),
                new CdPlayerBody.Globals(1, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(), g.debugCheat(),
                        g.timeWarpTimer(), g.invincible(), g.bossMusic()), finalEnvironment.dynamicSlots()), frame.environment());
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, actualField(actualField(visit, "values"), "phase"));
        System.out.println("PAST_FADE_BOUNDARY returned=" + (cut.returned() + 22) + " attempted=" + (cut.returned() + 23)
                + " clock=" + frame.vIntClock().absoluteValue().orElseThrow() + " phase=BEGIN_LEVEL colourPasses=22");
    }

    @Test void actualPastFadeRestoresFullAndCourseAtZeroOneSevenAndTwentyOneBeforeForwardingAllValues() throws Exception {
        var cut = fadeEntry(); var h = cut.harness(); var module = h.module();
        var registry = h.mode().getRewindRegistry(); var entryFull = registry.capture(); var entryNative = captureNative(h);
        var frame = module.requirePlayerFrame(); var owner = frame.captureActor().owner();
        var world = h.mode().getWorldSession(); var generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        for (int at : new int[]{0, 1, 7, 21}) {
            registry.restore(entryFull); assertNativeCapture(entryNative, h);
            for (int pass = 1; pass <= at; pass++) step(h, route(cut.returned() + pass));
            assertFadeProgress(values(h), at, at != 0);
            var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
            for (int pass = at + 1; pass <= 22; pass++) step(h, route(cut.returned() + pass));
            var after = captureNative(h);
            assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, module.pastLoadState().phase());
            assertEquals(44L, frame.vIntClock().absoluteValue().orElseThrow());
            registry.restore(full); assertNativeCapture(before, h);
            for (int pass = at + 1; pass <= 22; pass++) step(h, route(cut.returned() + pass));
            assertNativeCapture(after, h);
            registry.restoreCourse(course); assertNativeCapture(before, h);
            for (int pass = at + 1; pass <= 22; pass++) step(h, route(cut.returned() + pass));
            assertNativeCapture(after, h);
            assertSame(frame, module.requirePlayerFrame()); assertSame(owner, frame.captureActor().owner());
            assertSame(world, h.mode().getWorldSession());
            assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration(), "Restore invokes no load/programme setup");
        }
    }

    private record FadeCut(TestSonicCdPastLoadingWorld.Harness harness, int returned) { }

    private static FadeCut fadeEntry() throws Exception {
        var h = opening();
        for (int call = 1; call < 7800; call++) {
            step(h, route(call));
            if (h.module().pastLoadingSelected() && h.module().pastLoadState().phase() == SonicCdGameModule.PastLoadPhase.FROM_WHITE) {
                assertFadeProgress(values(h), 0, false);
                return new FadeCut(h, call);
            }
        }
        fail("The unchanged bounded five-jump controller recipe must reach the actual FromWhite caller");
        throw new AssertionError();
    }

    private static Object values(TestSonicCdPastLoadingWorld.Harness h) throws ReflectiveOperationException {
        return actualField(actualField(h.module(), "pastLoading"), "values");
    }

    private static void assertFadeProgress(Object values, int completed, boolean enabled) throws ReflectiveOperationException {
        var progress = actualField(values, "fadeProgress");
        assertEquals(completed, actualField(progress, "completedSteps"));
        assertEquals(enabled, actualField(progress, "displayEnabled"));
    }

    /** Independent canonical-colour model: consume blue, green, red channel distances from white. */
    private static int literalColour(int target, int passes) {
        assertEquals(0, target & ~0x0EEE, "Authenticated palette colours must be canonical");
        int colour = 0;
        for (int shift : new int[]{8, 4, 0}) {
            int goal = (target >>> shift) & 14;
            int used = Math.min(passes, (14 - goal) / 2);
            colour |= (14 - 2 * used) << shift;
            passes -= used;
        }
        return colour;
    }

    private static byte[] literalPalette(CdAssetBytes target, int passes) {
        byte[] expected = new byte[128];
        for (int at = 0; at < 128; at += 2) {
            int colour = literalColour(target.unsignedWord(at), passes);
            expected[at] = (byte) (colour >>> 8); expected[at + 1] = (byte) colour;
        }
        return expected;
    }

    /** All Values components not changed by a selector12 wait or its colour/progress continuation. */
    private static void assertUntouchedValues(Object expected, Object actual) throws ReflectiveOperationException {
        for (String field : new String[]{"section", "fadePalette", "cpuSat", "displayedSat", "displayedHScroll",
                "displayedVScrollLongBits", "foregroundPlane", "ring", "globals", "hudObjects"}) {
            var before = actualField(expected, field); var after = actualField(actual, field);
            if (before instanceof CdAssetBytes bytes) assertArrayEquals(bytes.copy(), assertInstanceOf(CdAssetBytes.class, after).copy(), field);
            else assertEquals(before, after, field);
        }
    }
}
