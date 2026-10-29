package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.objects.CdRingSpillGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only authored actual-World witnesses for the post-dynamic native prefix.
 * J2f30be83581c88ad69a22a8ce5aaea238542f586; native
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Main223,405-432,513-549; Past Scroll183-331,340-579; Events7-52,86-123;
 * Object Functions251-365,382-559; Palette Cycle Script15-47; Past Palette Cycle86-171.
 * The existing independent scanner, object,
 * reserved-body and ordinary plane models are inputs. No production scroll,
 * queue-drain, Frame/Body/animator or effect-replay operation computes expected
 * values. All route work runs only through the actual unchanged World step.
 */
@Isolated
class TestSonicCdPastLevelFlowWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualCompletedDynamicPassRunsNativeCameraDrawAndFullFirstMainTail() throws Exception {
        var p = ready();
        assertTerminal(p, before(p), 45L, 8, 0, "PAST_LEVEL_FLOW");
        // The new completed CPU bytes/count/provenance must be a legal healthy full/course cut.
        var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var complete = captureNative(h);
        contrast(p.placement()); registry.restore(full); assertNativeCapture(complete, h);
        contrast(p.placement()); registry.restoreCourse(course); assertNativeCapture(complete, h);
    }

    @Test void fullHealthy44RestoreReinstatesEveryPartitionBeforeOneActualCameraAndDraw() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        assertTerminal(p, before, 45L, 8, 0, "PAST_LEVEL_FLOW_FULL44_FIRST_RETURN");
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNoReservedPass(p);
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 45L, 8, 0, "PAST_LEVEL_FLOW_FULL44");
    }

    @Test void courseRestoreBeforeRealUnpause47RunsNativeCameraAndDrawExactlyOnce() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var actor = frame.captureActor(); var initialReserved = reserved(p.visit());
        step(h, start(true));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        step(h, start(false));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        var registry = h.mode().getRewindRegistry(); var course = registry.captureCourse();
        var nativeBefore = captureNative(h); var before = before(p);
        assertTerminal(p, before, 47L, 0x80, 0x80, "PAST_LEVEL_FLOW_COURSE47_FIRST_RETURN");
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNoReservedPass(p);
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 47L, 0x80, 0x80, "PAST_LEVEL_FLOW_COURSE47");
    }

    record FlowExpected(CdPlayerBody.State primary, CdR11aForegroundScroll.State foreground,
                        CdR11aBackgroundScroll.State background, CdPlayerMovement.Bounds bounds,
                        int cameraYCentreWord, DrawExpected draw, TailExpected tail) { }
    record AnimationExpected(int logTimer, int logFrame, int unusedTimer, int unusedFrame, CdRingSpillGlobals spill) { }
    record TailExpected(byte[] palette, byte[] steps, byte[] timers, CdRingGlobals ring, AnimationExpected animations) {
        TailExpected { palette = palette.clone(); steps = steps.clone(); timers = timers.clone(); }
        @Override public byte[] palette() { return palette.clone(); }
        @Override public byte[] steps() { return steps.clone(); }
        @Override public byte[] timers() { return timers.clone(); }
    }
    record DrawExpected(byte[] cpuSat, int spriteCount, List<CdObjectDrawQueue.Provenance> provenance,
                        byte[][] images, byte[] reservedImages) {
        DrawExpected {
            cpuSat = cpuSat.clone(); provenance = List.copyOf(provenance);
            images = copy(images); reservedImages = reservedImages.clone();
        }
        @Override public byte[] cpuSat() { return cpuSat.clone(); }
        @Override public byte[][] images() { return copy(images); }
        @Override public byte[] reservedImages() { return reservedImages.clone(); }
    }
    private record Foreground(CdR11aForegroundScroll.State state, CdPlayerMovement.Bounds bounds,
                              int yCentre) { }
    private record Mapping(byte[] bytes, int tableOffset, int frames) { }

    /** Read actual healthy inputs, require this owned native branch, then compute detached values. */
    static FlowExpected expected(Prepared p, Before before) throws Exception {
        assertEquals(-1, before.prefix().firstUnownedWhole(), "A full actual dynamic walk is required before Main405");
        assertEquals(SonicCdGameModule.PastLoadBoundary.LEVEL_FLOW, before.prefix().boundary(),
                "The inherited body oracle still ends precisely before Main405");
        assertEquals(CdReleaseVariant.USA, p.assets().identity().variant());
        var primary = expectedPastInitState(before.actor().state());
        var env = expectedPastInitEnvironment(before.shared().environment());
        assertEquals(0, before.observed().level().levelRestart(), "Actual native Main407 must not select LevelStart");
        assertEquals(0, env.globals().debugMode(), "This actual fixture selects the native routine guard");
        assertTrue(primary.motion().routine() < 6, "The actual Init result selects DoScroll, not dead-camera freeze");
        assertEquals(0, env.movement().gates().zoneAct(), "Only actual PPZ1 is modeled here");
        assertEquals(0, env.movement().gates().timeZone(), "PPZ1 raw Past0 selects LevEvents_PPZ2");
        assertEquals(0, primary.motion().playerControl() & 2,
                "This first Init fixture must not require an unowned ramp branch");
        int hudTime = before.observed().level().updateHudTime();
        assertTrue((hudTime & ~255) == 0, "Read the actual HUD time byte, never elapsed recipe time");
        var fg = foreground(before.scroll().state(), primary, env,
                p.assets().loadPlan().bounds().top(), hudTime);
        var bg = background(before.scroll().background(), fg.state());
        var cpu = assertInstanceOf(CdAssetBytes.class, actualField(before.values(), "cpuSat")).copy();
        var draw = draw(p.assets(), before.prefix(), expectedPastReservedHud(), cpu, fg.state(), bg);
        var section = before.observed().section();
        byte[] ranges = p.assets().view(CdR11bAssets.Asset.SECTION_DATA).bytes().copy();
        int index = 0;
        while (fg.state().cameraX() >= word(ranges, index * 2)) {
            assertTrue(++index < 4, "Actual camera must select a complete native section range");
        }
        assertEquals(index * 2, section.sectionWord() >>> 8,
                "This first return selects UpdateSectionArt.FoundRange RTS; a new PLC list needs its own oracle");
        assertEquals(0, section.queue().headSource(), "Actual ProcessPLCs must select its empty-head RTS");
        assertEquals(0, section.remainingTilesWord());
        return new FlowExpected(primary, fg.state(), bg, fg.bounds(), fg.yCentre(), draw, tail(p.assets(), before));
    }

    /** Genuine Main223/ClearFlowers/ClearCamera observations precede the first ordinary service. */
    static void assertInitialLoop(Prepared p) throws Exception {
        var calls = p.harness().module().pastLoadState().calls();
        for (var call : List.of(SonicCdGameModule.PastLoadCall.CLEAR_FLOWERS,
                SonicCdGameModule.PastLoadCall.CLEAR_CAMERA, SonicCdGameModule.PastLoadCall.CLEAR_DEMO_INDEX))
            assertEquals(1, java.util.Collections.frequency(calls, call), "One actual native initialization " + call);
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.CLEAR_CAMERA)
                < calls.indexOf(SonicCdGameModule.PastLoadCall.CLEAR_DEMO_INDEX));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.CLEAR_DEMO_INDEX)
                < calls.indexOf(SonicCdGameModule.PastLoadCall.CLEAR_SCREEN));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.CLEAR_DEMO_INDEX)
                < calls.indexOf(SonicCdGameModule.PastLoadCall.LOAD_PLAYER));
        var loop = actualField(actualField(p.visit(), "values"), "loop");
        assertEquals(OptionalInt.of(0), actualField(loop, "demoDataIndexWord"),
                "Only actual Main223 establishes nativeF78E; tail.demoS1IndexWord is distinct nativeF790");
        assertArrayEquals(new byte[7], assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteSteps7")).copy());
        assertArrayEquals(new byte[7], assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteTimers7")).copy());
        var animations = assertInstanceOf(Optional.class, actualField(loop, "animations")).orElseThrow();
        assertAnimations(new AnimationExpected(0, 0, 0, 0, new CdRingSpillGlobals(0, 0, 0)), animations);
        assertEquals(Optional.empty(), actualField(loop, "completedDraw"));
        assertEquals(0, p.harness().module().pastLoadState().ring().ringAnimationTimer());
        assertEquals(0, p.harness().module().pastLoadState().ring().ringAnimationFrame());
    }

    /** Literal Palette Cycle Script15-47 and Main513-549; no production palette/animation calls. */
    private static TailExpected tail(CdR11bAssets assets, Before before) throws Exception {
        var loop = actualField(before.values(), "loop");
        assertEquals(OptionalInt.of(0), actualField(loop, "demoDataIndexWord"),
                "Main405 reads the actual previously observed store, not a route index or demoS1 alias");
        byte[] palette = before.observed().cpuPalette().copy();
        byte[] steps = assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteSteps7")).copy();
        byte[] timers = assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteTimers7")).copy();
        assertEquals(7, steps.length); assertEquals(7, timers.length);
        var r = before.observed().ring();
        if (r.timeStopTimer() == 0) {
            byte[] data = assets.view(CdR11bAssets.Asset.PALETTE_CYCLE_DATA).bytes().copy();
            assertEquals(84, data.length);
            for (int cycle = 0; cycle < 6; cycle++) {
                int base = cycle * 14;
                timers[cycle]--;
                if (timers[cycle] >= 0) continue;
                int frame = (Byte.toUnsignedInt(steps[cycle]) + 1) & 255;
                if (frame >= octet(data, base + 1)) frame = 0;
                steps[cycle] = (byte) frame;
                timers[cycle] = (byte) octet(data, base + 2 + frame * 2);
                int colorIndex = (byte) octet(data, base + 3 + frame * 2);
                int color = word(data, base + 8 + colorIndex * 2);
                putWord(palette, octet(data, base) * 2, color);
            }
        }
        var a = assertInstanceOf(Optional.class, actualField(loop, "animations")).orElseThrow();
        int logTimer = (int) actualField(a, "logSpikeTimerByte"), logFrame = (int) actualField(a, "logSpikeFrameByte");
        int unusedTimer = (int) actualField(a, "unusedTimerByte"), unusedFrame = (int) actualField(a, "unusedFrameByte");
        var spill = assertInstanceOf(CdRingSpillGlobals.class, actualField(a, "spill"));
        logTimer = (logTimer - 1) & 255;
        if ((byte) logTimer < 0) { logTimer = 11; logFrame = (logFrame - 1) & 7; }
        int ringTimer = (r.ringAnimationTimer() - 1) & 255, ringFrame = r.ringAnimationFrame();
        if ((byte) ringTimer < 0) { ringTimer = 7; ringFrame = (ringFrame + 1) & 3; }
        unusedTimer = (unusedTimer - 1) & 255;
        if ((byte) unusedTimer < 0) { unusedTimer = 7; unusedFrame = (unusedFrame + 1) & 255; if (unusedFrame >= 6) unusedFrame = 0; }
        if (spill.timerByte() != 0) {
            int accum = u16(spill.accumWord() + spill.timerByte());
            int rotated = u16((accum << 7) | (accum >>> 9));
            spill = new CdRingSpillGlobals((spill.timerByte() - 1) & 255, accum, rotated & 3);
        }
        return new TailExpected(palette, steps, timers,
                new CdRingGlobals(Math.min(r.rings(), 999), r.lives(), r.livesFlags(), 0, 0, r.timeStopTimer(), ringTimer, ringFrame),
                new AnimationExpected(logTimer, logFrame, unusedTimer, unusedFrame, spill));
    }
    static void assertAnimations(AnimationExpected expected, Object actual) throws Exception {
        assertEquals(expected.logTimer(), actualField(actual, "logSpikeTimerByte"));
        assertEquals(expected.logFrame(), actualField(actual, "logSpikeFrameByte"));
        assertEquals(expected.unusedTimer(), actualField(actual, "unusedTimerByte"));
        assertEquals(expected.unusedFrame(), actualField(actual, "unusedFrameByte"));
        assertEquals(expected.spill(), actualField(actual, "spill"));
    }
    static void assertLoop(Prepared p, Before before, FlowExpected expected) throws Exception {
        var loop = actualField(actualField(p.visit(), "values"), "loop");
        var old = actualField(before.values(), "loop");
        assertEquals(actualField(old, "demoDataIndexWord"), actualField(loop, "demoDataIndexWord"));
        assertEquals(OptionalInt.of(0), actualField(loop, "demoDataIndexWord"));
        assertArrayEquals(expected.tail().steps(), assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteSteps7")).copy());
        assertArrayEquals(expected.tail().timers(), assertInstanceOf(CdAssetBytes.class, actualField(loop, "paletteTimers7")).copy());
        assertAnimations(expected.tail().animations(), assertInstanceOf(Optional.class, actualField(loop, "animations")).orElseThrow());
        var cpu = assertInstanceOf(CdObjectDrawQueue.CpuSat.class,
                assertInstanceOf(Optional.class, actualField(loop, "completedDraw")).orElseThrow());
        assertArrayEquals(expected.draw().cpuSat(), cpu.bytes());
        assertEquals(expected.draw().spriteCount(), cpu.spriteCount()); assertEquals(expected.draw().provenance(), cpu.provenance());
    }
    static List<SonicCdGameModule.PastLoadCall> suffix(Before before) {
        var calls = new ArrayList<SonicCdGameModule.PastLoadCall>();
        calls.add(SonicCdGameModule.PastLoadCall.POST_OBJECT_GUARDS);
        calls.add(SonicCdGameModule.PastLoadCall.LEVEL_SCROLL);
        calls.add(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS);
        if (before.observed().ring().timeStopTimer() == 0) calls.add(SonicCdGameModule.PastLoadCall.PALETTE_CYCLE);
        calls.add(SonicCdGameModule.PastLoadCall.UPDATE_SECTION);
        calls.add(SonicCdGameModule.PastLoadCall.PREPARE_PLCS);
        calls.add(SonicCdGameModule.PastLoadCall.UPDATE_GLOBAL_ANIMATIONS);
        calls.add(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN);
        return List.copyOf(calls);
    }

    /** Literal Main/event/scroll feedback changes only existing bounds and camera-centre fields. */
    static CdPlayerBody.Environment feedback(CdPlayerBody.Environment before, FlowExpected flow) {
        var m = before.movement(); var g = m.globals();
        var next = new CdPlayerMovement.Globals(g.lookMode(), flow.cameraYCentreWord(), g.primaryAngle(),
                g.secondaryAngle(), g.boredTimer(), g.scoreChain(), g.debugAngle(), g.debugAngleShift(),
                g.debugQuadrant(), g.debugFloorDistance());
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), flow.bounds(),
                m.gates(), next), before.controls(), before.globals(), before.dynamicSlots());
    }

    /** Past Scroll183-204, Events14-52/121-123, ScrollCamX340-405/ScrollCamY428-579. */
    private static Foreground foreground(CdR11aForegroundScroll.State old, CdPlayerBody.State primary,
                                        CdPlayerBody.Environment env, int top, int hudTime) {
        var b = env.movement().bounds(); var actor = primary.motion();
        // The actual HUD byte is supplied. Raw Past0's PPZ2 branch does not read it;
        // the Present-only left-bound gate is intentionally outside this fixture.
        assertTrue((hudTime & ~255) == 0);
        int centre = env.movement().globals().cameraYCentre();
        // TST.B reads the high byte at the WORD label, before flag clears/events.
        if ((old.scrollLock() >>> 8) != 0) return new Foreground(old, b, centre);
        int x = old.cameraX(), y = old.cameraY16_16(), bottom = b.bottom();
        int destBottom = 0x310, shift = old.bottomBoundShift();
        if (actor.animation() == 0x2B) bottom = destBottom = y >>> 16;
        if (destBottom != bottom) {
            if (destBottom < bottom) {
                if ((y >>> 16) > destBottom) bottom = (y >>> 16) & 0xFFFE;
                bottom = u16(bottom - 4);
            } else bottom = u16(bottom + (u16((y >>> 16) + 8) >= bottom && (actor.flags() & 2) != 0 ? 16 : 4));
            shift = 0x100 | (shift & 255);
        }
        int oldX = x, hCross = old.horizontalCrossed(), vCross = old.verticalCrossed(), flags = 0;
        int relative = u16((actor.centreX16_16() >>> 16) - x);
        boolean borrow = relative < env.globals().cameraXCentre();
        int distanceX = u16(relative - env.globals().cameraXCentre());
        int dx = 0;
        if (distanceX != 0) {
            int distance = (short) distanceX;
            if (borrow) {
                x = u16(x + Math.max(distance, -16));
                if ((short) x <= (short) b.left()) x = b.left();
            } else {
                x = u16(x + Math.min(distance, 16));
                if ((short) x >= (short) b.right()) x = b.right();
            }
            dx = u16((x - oldX) << 8);
        }
        if (((x & 16) ^ hCross) == 0) {
            hCross ^= 16; flags |= (short) (x - oldX) < 0 ? 0x400 : 0x800;
        }
        int distanceY = u16((actor.centreY16_16() >>> 16) - (y >>> 16));
        if ((actor.flags() & 4) != 0) distanceY = u16(distanceY - 5);
        boolean idle = false; int cap;
        if ((actor.flags() & 2) != 0) {
            int adjusted = u16(distanceY + 32);
            boolean above = adjusted < centre;
            distanceY = u16(adjusted - centre);
            if (!above) { boolean inside = distanceY < 64; distanceY = u16(distanceY - 64); idle = inside; }
            cap = 16;
        } else {
            distanceY = u16(distanceY - centre); idle = distanceY == 0;
            int magnitude = actor.groundVelocity() < 0 ? u16(-actor.groundVelocity()) : actor.groundVelocity();
            cap = centre != 0x60 ? 2 : magnitude >= 0x800 ? 16 : 6;
        }
        int dy = 0;
        if (!idle || (shift >>> 8) != 0) {
            int distance = idle ? 0 : (short) distanceY;
            if (idle) { shift &= 255; cap = 0; }
            int candidate = distance > cap ? y + (cap << 16)
                    : distance < -cap ? y - (cap << 16) : u16((y >>> 16) + distance) << 16;
            int word = candidate >>> 16;
            if (distance < 0 && (short) word <= (short) top) {
                assertTrue((short) word > -0x100, "Actual top-wrap requires a separate actor/background contract");
                candidate = (top << 16) | (candidate & 65535);
            } else if (distance >= 0 && (short) word >= (short) bottom) {
                assertTrue(word < 0x800, "Actual bottom-wrap requires a separate actor/background contract");
                candidate = (bottom << 16) | (candidate & 65535);
            }
            dy = Integer.rotateRight(candidate - y, 8) & 65535;
            if ((((candidate >>> 16) & 16) ^ vCross) == 0) {
                vCross ^= 16; flags |= (short) ((candidate >>> 16) - (y >>> 16)) < 0 ? 0x100 : 0x200;
            }
            y = candidate;
        }
        return new Foreground(new CdR11aForegroundScroll.State(x, y, hCross, vCross, dx, dy, flags,
                destBottom, old.destLeftBound(), shift, old.scrollLock()),
                new CdPlayerMovement.Bounds(b.left(), b.right(), bottom, b.bossFight()), centre);
    }

    /** Past206-331: long additions, exact flag bytes, full supplied RAM tails and thirty blocks. */
    private static CdR11aBackgroundScroll.State background(CdR11aBackgroundScroll.State old,
                                                          CdR11aForegroundScroll.State f) {
        if ((f.scrollLock() >>> 8) != 0) return old;
        int dx = (short) f.scrollXDiff(), dy = (short) f.scrollYDiff();
        int x3 = old.cameraBg3X() + (dx << 5), x2 = old.cameraBg2X() + ((dx << 4) * 3);
        int x = old.cameraBgX() + (dx << 7), y = old.cameraBgY() + (dy << 5), yw = y >>> 16;
        byte[] cross = old.crossingRam().copy();
        int flags = crossing(cross, 4, old.cameraBg3X(), x3, 6)
                | crossing(cross, 2, old.cameraBg2X(), x2, 4)
                | crossing(cross, 0, old.cameraBgX(), x, 2)
                | crossing(cross, 1, old.cameraBgY(), y, 0);
        byte[] deformation = old.deformationRam().copy();
        int[] speeds = { 0x10000, 0xC000, 0x8000, 0x4000 };
        int at = 16;
        for (int i = 0; i < 4; i++) {
            putLong(deformation, i * 4, longword(deformation, i * 4) + speeds[i]);
            for (int n = 0; n < 2; n++, at += 2) putWord(deformation, at, -(word(deformation, i * 4) + (x3 >>> 16)));
        }
        for (int n = 0; n < 10; n++, at += 2) putWord(deformation, at, -(x3 >>> 16));
        for (int n = 0; n < 24; n++, at += 2) putWord(deformation, at, -(x2 >>> 16));
        assertEquals(100, at, "Native last deformation store; preserve the remaining412 bytes");
        byte[] hscroll = old.hscrollRam().copy();
        int from = 16 + ((yw & 0x1F8) >>> 2), to = 0;
        for (int block = 0; block < 30; block++, from += 2) {
            int lines = block == 0 ? 8 - (yw & 7) : 8;
            int value = (u16(-f.cameraX()) << 16) | word(deformation, from);
            for (int line = 0; line < lines; line++, to += 4) putLong(hscroll, to, value);
        }
        return new CdR11aBackgroundScroll.State(x, y, x2,
                wordStore(old.cameraBg2Y(), yw), x3, wordStore(old.cameraBg3Y(), yw), CdAssetBytes.copyOf(cross),
                flags, 0, 0, old.cloudRam(), CdAssetBytes.copyOf(deformation), CdAssetBytes.copyOf(hscroll),
                (f.cameraYWord() << 16) | yw);
    }
    private static int crossing(byte[] image, int at, int old, int next, int lowBit) {
        if ((((next >>> 16) & 16) ^ Byte.toUnsignedInt(image[at])) != 0) return 0;
        image[at] ^= 16;
        return 1 << (8 + lowBit + (next - old < 0 ? 0 : 1));
    }

    /** Native DrawObjects consumes recorded physical pointers; no recull or re-enqueue follows camera. */
    private static DrawExpected draw(CdR11bAssets assets, RingPrefix prefix, byte[] reserved, byte[] previous,
                                     CdR11aForegroundScroll.State fg, CdR11aBackgroundScroll.State bg) {
        assertEquals(640, previous.length); assertEquals(1984, reserved.length);
        byte[] sat = previous.clone(); byte[][] images = prefix.images(); var provenance = new ArrayList<CdObjectDrawQueue.Provenance>();
        int count = 0; int[] bytes = prefix.pointerByteCounts(); int[][] pointers = prefix.pointerWholes();
        for (int bucket = 0; bucket < 8; bucket++) {
            assertEquals(0, bytes[bucket] & 1); assertTrue(bytes[bucket] >= 0 && bytes[bucket] <= 126);
            for (int ordinal = 0; ordinal < bytes[bucket] / 2; ordinal++) {
                int whole = pointers[bucket][ordinal]; assertTrue(whole > 0 && whole < 128, "No primary DrawObject during literal Init");
                byte[] image = whole < 32 ? Arrays.copyOfRange(reserved, (whole - 1) * 64, whole * 64) : images[whole - 32];
                if (image[0] == 0) continue; // Native loaded-ID test, before every map read.
                int flags = Byte.toUnsignedInt(image[1]), x = word(image, 8), y = word(image, 10);
                int cx = 0, cy = 0;
                switch (flags & 12) {
                    case 4 -> { cx = fg.cameraX(); cy = fg.cameraYWord(); }
                    case 8 -> { cx = bg.cameraBgXWord(); cy = bg.cameraBgYWord(); }
                    case 12 -> { cx = bg.cameraBg3XWord(); cy = bg.cameraBg3YWord(); }
                    default -> { }
                }
                if ((flags & 12) != 0) {
                    y = word(image, 12);
                    if (cy < 0x100 && y >= 0x800) y = u16(y - 0x800);
                    else if (cy >= 0x700 && y < 0x100) y = u16(y + 0x800);
                    x = u16(x - cx + 128); y = u16(y - cy + 128);
                }
                assertEquals(0, flags & 0x20, "Actual pending static mapping needs its own authenticated source contract");
                // ObjSonic_Init135-155 returns directly, so the first player owns no pending pointer.
                var mapping = mapping(assets, longword(image, 4));
                int frame = Byte.toUnsignedInt(image[0x1A]); assertTrue(frame < mapping.frames());
                int start = mapping.tableOffset() + (short) word(mapping.bytes(), mapping.tableOffset() + frame * 2);
                int pieces = octet(mapping.bytes(), start++);
                assertTrue(pieces <= 128, "Native SUBQ.B/BMI high-count shape is outside this bounded actual fixture");
                assertTrue(start + pieces * 5 <= mapping.bytes().length, "Complete actual mapping body must remain in its source view");
                for (int piece = 0; piece < pieces && count < 80; piece++) {
                    int p = start + piece * 5, ry = (byte) octet(mapping.bytes(), p), size = octet(mapping.bytes(), p + 1);
                    int rx = (byte) octet(mapping.bytes(), p + 4);
                    if ((flags & 1) != 0) rx = -rx - (((size >>> 2) & 3) + 1) * 8;
                    if ((flags & 2) != 0) ry = -ry - ((size & 3) + 1) * 8;
                    int at = count * 8, sx = (x + rx) & 0x1FF;
                    putWord(sat, at, y + ry); sat[at + 2] = (byte) size; sat[at + 3] = (byte) (count + 1);
                    putWord(sat, at + 4, u16(word(mapping.bytes(), p + 2) + word(image, 2)) ^ ((flags & 3) << 11));
                    putWord(sat, at + 6, sx == 0 ? 1 : sx);
                    provenance.add(new CdObjectDrawQueue.Provenance(bucket, ordinal, new CdObjectDrawQueue.CdSlotRef(whole), piece));
                    count++;
                }
                image[1] |= (byte) 0x80; // Even empty/full-SAT mappings execute DrawDone.
                if (whole < 32) System.arraycopy(image, 0, reserved, (whole - 1) * 64, 64);
            }
        }
        if (count == 80) sat[79 * 8 + 3] = 0;
        else Arrays.fill(sat, count * 8, count * 8 + 4, (byte) 0);
        return new DrawExpected(sat, count, provenance, images, reserved);
    }
    /** Actual raw B map roots, including Spring's distinct roots inside one complete include. */
    private static Mapping mapping(CdR11bAssets assets, int address) {
        for (var asset : List.of(CdR11bAssets.Asset.MAP_SPR_HUD, CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME,
                CdR11bAssets.Asset.MAP_SPR_RING, CdR11bAssets.Asset.MAP_SPRING,
                CdR11bAssets.Asset.PLATFORM_MAPPINGS, CdR11bAssets.Asset.MAP_SPR_TUNNEL_DOOR)) {
            var view = assets.view(asset); int root = address - view.range().nativeAddress();
            int frames = switch (asset) {
                case MAP_SPR_HUD -> root == 0 ? 4 : -1;
                case MAP_SPR_MONITOR_TIME -> root == 0 ? 20 : -1;
                case MAP_SPR_RING -> root == 0 ? 9 : -1;
                case MAP_SPRING -> root == 0 || root == 6 || root == 80 ? 3 : -1;
                case PLATFORM_MAPPINGS -> root == 0 ? 3 : -1;
                case MAP_SPR_TUNNEL_DOOR -> root == 0 ? 2 : -1;
                default -> -1;
            };
            if (frames > 0) return new Mapping(view.bytes().copy(), root, frames);
        }
        fail("Actual pending oMap has no authenticated bounded B source: " + Integer.toHexString(address));
        throw new AssertionError();
    }
    static void assertBackground(CdR11aBackgroundScroll.State expected, Object value) {
        var actual = assertInstanceOf(CdR11aBackgroundScroll.State.class, value);
        assertEquals(expected.cameraBgX(), actual.cameraBgX()); assertEquals(expected.cameraBgY(), actual.cameraBgY());
        assertEquals(expected.cameraBg2X(), actual.cameraBg2X()); assertEquals(expected.cameraBg2Y(), actual.cameraBg2Y());
        assertEquals(expected.cameraBg3X(), actual.cameraBg3X()); assertEquals(expected.cameraBg3Y(), actual.cameraBg3Y());
        assertEquals(expected.scrollFlagsBg(), actual.scrollFlagsBg()); assertEquals(expected.scrollFlagsBg2(), actual.scrollFlagsBg2());
        assertEquals(expected.scrollFlagsBg3(), actual.scrollFlagsBg3()); assertEquals(expected.vscroll(), actual.vscroll());
        assertArrayEquals(expected.crossingRam().copy(), actual.crossingRam().copy());
        assertArrayEquals(expected.cloudRam().copy(), actual.cloudRam().copy());
        assertArrayEquals(expected.deformationRam().copy(), actual.deformationRam().copy());
        assertArrayEquals(expected.hscrollRam().copy(), actual.hscrollRam().copy());
    }
    private static byte[][] copy(byte[][] images) {
        byte[][] result = new byte[images.length][];
        for (int i = 0; i < images.length; i++) result[i] = images[i].clone();
        return result;
    }
    private static int wordStore(int before, int word) { return (word << 16) | (before & 65535); }
    private static int u16(int value) { return value & 65535; }
    private static int octet(byte[] bytes, int at) { assertTrue(at >= 0 && at < bytes.length); return Byte.toUnsignedInt(bytes[at]); }
    private static int word(byte[] bytes, int at) { return (octet(bytes, at) << 8) | octet(bytes, at + 1); }
    private static int longword(byte[] bytes, int at) { return (word(bytes, at) << 16) | word(bytes, at + 2); }
    private static void putWord(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int at, int value) { putWord(bytes, at, value >>> 16); putWord(bytes, at + 2, value); }
}
