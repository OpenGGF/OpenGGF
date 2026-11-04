package com.openggf.game.soniccd;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine cold-ready/five-jump/Past loading and ordinary cuts.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff IPX.RunMMD1311-1319 clears
 * [FF2000,FFFB00), including current water[FFFA80,FFFB00). Main normal/target
 * loads and PP cycles do not write water; ClearScreen's first4 bytes are already0.
 * No actor/global/art/queue/camera/clock input is seeded. No fade, new programme
 * phase, palClearFlags knowledge, next-act handoff, native parity or pixels are claimed.
 */
@Isolated
class TestSonicCdPastWaterPaletteWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualPastEntryClearOwnsAll128ZeroWaterBytesIndependentlyOfOutgoingWhiteAndNewNormalPalette() throws Exception {
        var e = entered(); var actual = e.harness().module().pastLoadState(); var water = water(e);
        assertEquals(SonicCdGameModule.PastLoadPhase.FIRST_PLC, actual.phase()); assertEquals(128, water.length());
        assertArrayEquals(new byte[128], water.copy());
        for (int at = 0; at < 128; at += 2) {
            assertEquals(0x0EEE, e.oldPalettes().waterPalette().unsignedWord(at), "Real outgoing22-step white fade precedes a new RunMMD clear");
            assertEquals(0x0EEE, actual.cpuPalette().unsignedWord(at), "Marked Past normal palette is white, never a zero-water alias");
        }
        assertNotSame(e.oldPalettes().waterPalette(), water); assertNotSame(e.oldPalettes().cycle().palette(), water);
        assertNotSame(e.oldPalettes().displayedPalette(), water); assertPaletteOwners(e);
        assertTrue(e.oldFrame().closed()); assertTrue(e.oldRuntime().closed()); assertFalse(e.frame().closed());
        assertSame(actual.cpuPalette(), image(e, "cpuPalette")); assertSame(actual.displayedPalette(), image(e, "displayedPalette"));
        var captured = e.visit().capture(); assertSame(e.visit(), actualField(captured, "owner"));
        assertSame(water, actualField(actualField(captured, "values"), "waterPalette"));
    }

    @Test void initialFullAndCourseRestorePrecedeForwardAndRetainWaterDuringTheRealFirstLoadingService() throws Exception {
        var e = entered(); replayFromHere(e, 7730);
        assertEquals(SonicCdGameModule.PastLoadPhase.SECTION_PLC, e.harness().module().pastLoadState().phase());
        assertArrayEquals(new byte[128], water(e).copy()); assertPaletteOwners(e);
    }

    @Test void everyActualLoadingPhaseKeepsTheSameIndependentWaterValueWhileSourceLoadsAndFadeChangeNormalAndTarget() throws Exception {
        var e = entered(); var water = water(e); boolean sawSection = false, sawFade = false;
        for (int call = 7730; call <= 7774; call++) {
            step(e.harness(), route(call)); assertHealthy(e); assertSame(water, water(e)); assertPaletteOwners(e);
            sawSection |= e.harness().module().pastLoadState().phase() == SonicCdGameModule.PastLoadPhase.SECTION_PLC;
            sawFade |= e.harness().module().pastLoadState().phase() == SonicCdGameModule.PastLoadPhase.FROM_WHITE;
        }
        assertTrue(sawSection); assertTrue(sawFade); var current = e.harness().module().pastLoadState();
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, current.phase());
        var expected = current.assets().loadPlan().initialPalette();
        assertArrayEquals(expected.copy(), current.cpuPalette().copy()); assertArrayEquals(expected.copy(), image(e, "fadePalette").copy());
        assertArrayEquals(current.assets().view(CdR11bAssets.Asset.PAL_SONIC).bytes().copy(), current.cpuPalette().slice(0, 32).copy());
        assertSame(water, water(e)); assertArrayEquals(new byte[128], water.copy());
    }

    @Test void ordinaryHeldRouteAndBothReplayPartitionsPreserveWaterAndEveryOtherActualCapturedOwner() throws Exception {
        var e = entered(); load(e); var water = water(e);
        for (int call = 7775; call <= 7808; call++) {
            step(e.harness(), route(call)); assertHealthy(e); assertSame(water, water(e)); assertPaletteOwners(e);
            assertTrue(e.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        }
        replayFromHere(e, 7809); assertSame(water, water(e));
    }

    @Test void immutableWaterExportsCannotChangeCurrentTargetDisplayedOrOutgoingPaletteValues() throws Exception {
        var e = entered(); load(e); var water = water(e); var before = captureNative(e.harness());
        var normal = image(e, "cpuPalette"); var target = image(e, "fadePalette"); var displayed = image(e, "displayedPalette");
        byte[] outgoing = e.oldPalettes().waterPalette().copy(); byte[] exported = water.copy(); Arrays.fill(exported, (byte) 0xFF);
        assertThrows(ReadOnlyBufferException.class, () -> water.buffer().put(0, (byte) 0xFF));
        byte[] targetCopy = target.copy(); Arrays.fill(targetCopy, (byte) 0xFF);
        byte[] normalCopy = normal.copy(); Arrays.fill(normalCopy, (byte) 0xFF);
        assertSame(water, water(e)); assertArrayEquals(new byte[128], water(e).copy()); assertPaletteOwners(e);
        assertSame(normal, image(e, "cpuPalette")); assertSame(target, image(e, "fadePalette")); assertSame(displayed, image(e, "displayedPalette"));
        assertArrayEquals(outgoing, e.oldPalettes().waterPalette().copy()); assertNativeCapture(before, e.harness());
    }

    @Test void existingForeignCaptureSourceAndRetiredFrameGuardsDoNotAlterRetainedPaletteImages() throws Exception {
        var e = entered(); var before = captureNative(e.harness()); var water = water(e);
        assertThrows(IllegalArgumentException.class, () -> e.frame().restoreActor(e.oldActor()));
        assertThrows(IllegalArgumentException.class, () -> e.frame().restoreShared(e.oldShared()));
        assertThrows(IllegalStateException.class, () -> e.harness().module().requirePastLoadingVisit(null));
        assertNativeCapture(before, e.harness()); assertSame(water, water(e)); assertFalse(e.frame().closed());
        var values = actualField(e.visit(), "values"); var normal = image(e, "cpuPalette"); var target = image(e, "fadePalette");
        // Explicitly retire the genuine completed Frame to test existing capture guards; no native failure is fabricated.
        e.frame().close(); assertTrue(e.frame().closed());
        assertThrows(IllegalStateException.class, () -> e.visit().capture());
        assertThrows(IllegalStateException.class, () -> e.harness().mode().getRewindRegistry().capture());
        assertSame(values, actualField(e.visit(), "values")); assertSame(water, water(e));
        assertSame(normal, image(e, "cpuPalette")); assertSame(target, image(e, "fadePalette"));
        // No forward call or restore follows retirement; normal fixture cleanup owns final release.
    }

    private record Entered(Harness harness, SonicCdPastLoadingVisit visit, CdPlayerFrameRuntime frame,
                           CdPlayerFrameRuntime oldFrame, CdOpeningObjectRuntime oldRuntime,
                           CdPlayerFrameRuntime.ActorState oldActor, CdPlayerFrameRuntime.SharedState oldShared,
                           CdOpeningObjectRuntime.PaletteState oldPalettes) { }
    private static Entered entered() throws Exception {
        var h = opening(); var module = h.module(); var oldFrame = module.requirePlayerFrame();
        var old = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, module.levelProgramState().phase());
        var oldActor = oldFrame.captureActor(); var oldShared = oldFrame.captureShared(); var oldPalettes = old.palettes();
        step(h, route(7729)); assertSame(world, h.mode().getWorldSession());
        assertEquals(generation + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var e = new Entered(h, (SonicCdPastLoadingVisit) actualField(module, "pastLoading"), module.requirePlayerFrame(),
                oldFrame, old, oldActor, oldShared, oldPalettes);
        assertHealthy(e); return e;
    }
    private static void load(Entered e) throws Exception {
        var water = water(e); for (int call = 7730; call <= 7774; call++) {
            step(e.harness(), route(call)); assertHealthy(e); assertSame(water, water(e));
        }
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, e.harness().module().pastLoadState().phase());
    }
    private static void replayFromHere(Entered e, int call) throws Exception {
        var h = e.harness(); var registry = h.mode().getRewindRegistry(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration(); var source = e.frame().stageSource();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h); var water = water(e);
        registry.restore(full); assertNativeCapture(before, h); assertSame(water, water(e)); assertHealthy(e);
        registry.restoreCourse(course); assertNativeCapture(before, h); assertSame(water, water(e)); assertHealthy(e);
        step(h, route(call)); assertHealthy(e); var after = captureNative(h); assertSame(water, water(e));
        registry.restore(full); assertNativeCapture(before, h); assertHealthy(e); step(h, route(call)); assertHealthy(e); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); assertHealthy(e); step(h, route(call)); assertHealthy(e); assertNativeCapture(after, h);
        assertSame(water, water(e)); assertSame(source, e.frame().stageSource()); assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void assertHealthy(Entered e) throws Exception {
        assertSame(e.frame(), e.harness().module().requirePlayerFrame()); assertFalse(e.frame().closed());
        assertSame(e.visit(), actualField(e.harness().module(), "pastLoading")); assertArrayEquals(new byte[128], water(e).copy());
    }
    private static CdAssetBytes water(Entered e) throws Exception { return image(e, "waterPalette"); }
    private static CdAssetBytes image(Entered e, String field) throws Exception { return (CdAssetBytes) actualField(actualField(e.visit(), "values"), field); }
    private static void assertPaletteOwners(Entered e) throws Exception {
        var water = water(e); assertEquals(128, water.length()); assertNotSame(water, image(e, "cpuPalette"));
        assertNotSame(water, image(e, "fadePalette")); assertNotSame(water, image(e, "displayedPalette"));
    }
}
