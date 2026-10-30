package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdFloatBlock;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bSectionArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent bounded World witnesses: J52dbfe130002714fedf50455517d61cb5ede5d89,
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Floating Block/Main.asm:7-78,156-165; Object Solid.asm:174-198,473-479;
 * Object Functions.asm:137-196; Object Spawner.asm:267-314.
 * The existing detached first-pass oracle establishes healthy7775. Later calls
 * use only the unchanged World controller. Expected outputs never execute
 * Body/Frame/Solid/terrain/placement/animation/draw production helpers.
 * Restoration compares complete represented native state. The single7808 now
 * returns through the ordinary Main continuation; its Float33 oracle preserves
 * the actual pre-scroll cull and prior-selected versus newly completed SAT. Intervening supported bodies
 * have no independent full96 oracle here; changed images are observations only.
 */
@Isolated
class TestSonicCdPastFloatBlockWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void unchanged7776Then7777NaturallyReachesPrimaryWholeZeroDrawDone() throws Exception {
        var p = healthy7775(); step(p.harness(), route(7776)); assertHealthy(p, 7776);
        assertEquals(26, completedDraw(p).spriteCount()); assertEquals(0, primaryPieces(p));
        assertFalse(p.frame().display().orElseThrow().queued());
        assertEquals(0, p.frame().state().motion().spriteFlags() & 0x80);
        step(p.harness(), route(7777)); assertHealthy(p, 7777);
        assertEquals(30, completedDraw(p).spriteCount()); assertEquals(2, primaryPieces(p));
        assertTrue(p.frame().display().orElseThrow().queued());
        assertEquals(0x80, p.frame().state().motion().spriteFlags() & 0x80);
        assertPrimarySatFromRawBMapping(p);
    }
    @Test void fullHealthy7776And7806CutsRestoreForwardTheirCompleteNativeOutputs() throws Exception {
        replayHealthyEdges(false);
    }
    @Test void courseHealthy7776And7806CutsRestoreForwardTheirCompleteNativeOutputs() throws Exception {
        replayHealthyEdges(true);
    }
    @Test void actual7808OnceCompletesFloat33AndTheOrdinaryMainContinuation() throws Exception {
        var p = healthy7775(); advance(p, 7776, 7807); var h = p.harness(); var f = p.frame();
        var pool = p.placement().pool(); var poolOwner = actualField(pool, "owner");
        byte[][] beforeImages = images(pool);
        assertArrayEquals(new byte[64], beforeImages[1], "Physical33 is free at healthy7807");
        var values = actualField(p.visit(), "values"); var oldCpu = completedDraw(p);
        var cpuBytes = assertInstanceOf(CdAssetBytes.class, actualField(values, "cpuSat"));
        var section = assertInstanceOf(CdR11bSectionArt.State.class, actualField(values, "section"));
        var current = section.residency(); byte[][] tiles = floatTiles(current);
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(h.module(), "playerPresentation"));
        // Legal healthy cut: actual pre-scroll camera words, never actorX.
        var scroll = (SonicCdPlayerPresentation.ScrollCapture)
                h.module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        int cameraX = scroll.state().cameraX(), cameraY = scroll.state().cameraYWord();
        // Ordinary VInt precedes every object. Retain its actual pending PLC
        // rather than inventing an empty queue. Expected emitted bytes use the
        // independent finite Java asset decoder, never SectionArt/Body replay.
        int plcBudget = (scroll.state().scrollLock() & 0xFF00) == 0 ? 3 : 18;
        int plcDestination = section.queue().headDestination();
        int plcCount = Math.min(section.remainingTilesWord(), plcBudget);
        byte[] plcBytes = new byte[plcCount * 32];
        if (plcCount != 0) {
            var decoder = section.decoder().orElseThrow();
            var asset = Arrays.stream(CdR11bAssets.Asset.values())
                    .filter(a -> Integer.toUnsignedLong(a.range().nativeAddress()) == decoder.nativeAddress())
                    .findFirst().orElseThrow();
            assertArrayEquals(p.assets().readerView(asset, CdR11bAssets.ReaderKind.NATIVE).bytes().copy(),
                    decoder.sourceBytes().copy(), "Actual active source is the authenticated original Past asset");
            int total = decoder.headerWord() & 0x7FFF;
            assertEquals(section.remainingTilesWord(), decoder.remainingTiles());
            assertTrue(total >= decoder.remainingTiles());
            var decoded = com.openggf.game.soniccd.level.CdNemesisAsset.decode(
                    p.assets().readerView(asset, CdR11bAssets.ReaderKind.JAVA).bytes().copy(), total * 32, total * 32);
            plcBytes = decoded.bytes().slice((total - decoder.remainingTiles()) * 32, plcCount * 32).copy();
        }
        for (int i = 0; i < plcBytes.length; i++) {
            int address = (plcDestination + i) & 0xFFFF;
            assertFalse(address >= 0x6680 && address < 0x6860, "Earlier real PLC must not overwrite Float tiles");
        }
        System.out.println("PAST_FLOAT_PRE7808 plcHead=" + Long.toHexString(section.queue().headSource())
                + " plcDestination=" + Integer.toHexString(plcDestination) + " remaining=" + section.remainingTilesWord()
                + " budget=" + plcBudget + " emitted=" + plcCount);
        var saved = actualField(p.visit(), "saved"); var source = f.stageSource();
        var artOwner = actualField(f, "art"); var frameOwner = actualField(f, "captureOwner");
        var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var level = actualField(values, "level");
        // Exactly ONE ordinary7808. Every unexpected original fault is rethrown.
        try { step(h, route(7808)); }
        catch (Throwable first) {
            try { diagnose(p, beforeImages, first); }
            catch (Throwable diagnostic) { if (diagnostic != first) first.addSuppressed(diagnostic); }
            throw first;
        }
        // The actual native pass returned; healthy observations are legal below.
        var afterValues = actualField(p.visit(), "values"); var calls = callNames(afterValues);
        int body = calls.indexOf("FLOAT_BLOCK_BODY");
        assertTrue(body >= 0, () -> "Completed7808 must contain the first Float33 return: " + calls);
        var firstFloat = calls.subList(0, body + 1).stream().filter(x -> x.startsWith("FLOAT_BLOCK_")).toList();
        assertEquals(List.of("FLOAT_BLOCK_SOLID", "FLOAT_BLOCK_SOLID", "FLOAT_BLOCK_BODY"), firstFloat,
                "Two separately published actual Top outcomes precede first normal body marker");
        ordered(calls, "ORDINARY_SERVICE", "LEVEL_FRAME_INCREMENT", "SPAWN_OBJECTS", "PRIMARY_MAIN",
                "RESERVED_OBJECTS", "RING_BODY", "FLOAT_BLOCK_SOLID", "FLOAT_BLOCK_BODY",
                "PATA_BATA_BODY", "DYNAMIC_OBJECTS", "POST_OBJECT_GUARDS", "LEVEL_SCROLL", "DRAW_OBJECTS",
                "UPDATE_SECTION", "PREPARE_PLCS", "UPDATE_GLOBAL_ANIMATIONS", "ORDINARY_MAIN_RETURN");
        assertEquals("ORDINARY_MAIN_RETURN", calls.getLast());
        assertFalse(f.closed()); assertFalse(f.paused());
        assertEquals(78L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(34, f.environment().globals().levelFrames());
        assertEquals(8, f.environment().controls().rawHeld());
        assertEquals(0, f.environment().movement().gates().timeZone());
        // Native cleared64 + row355 -> Init -> raw Past Main. Both Top calls
        // see own bit7 clear, store25=1, take GetOff, clear25=0. Flags0 makes
        // GetOffObject inert; no player geometry/terrain oracle is executed.
        assertArrayEquals(float33AfterBody(cameraX), pool.state(33).copyBytes(),
                "Every Float33 byte including integer/fraction aliases and nonwrites");
        boolean culled = float33Culled(cameraX, cameraY), deleted = float33OutsideWindow(cameraX);
        assertTrue(culled, "Fixed native Float33 lies outside the actual pre-scroll viewport");
        assertFalse(deleted, "Native masked chunk equality280 retains Float33");
        assertNull(actualField(p.visit(), "drawQueue"), "Completed native DrawObjects retires its actual queue");
        var completed = completedDraw(p);
        assertEquals(640, completed.bytes().length);
        assertEquals(completed.spriteCount(), completed.provenance().size());
        assertFalse(completed.provenance().stream().anyMatch(x -> x.slotRef().wholeIndex() == 33),
                "Culled Float33 contributes no mapping pieces to the new CPU table");
        System.out.println("PAST_FLOAT_RETURNED7808 cameraX=" + cameraX + " cameraY=" + cameraY
                + " float33Culled=" + culled + " float33WindowDeleted=" + deleted
                + " bodyMarkers=" + calls.stream().filter("FLOAT_BLOCK_BODY"::equals).count());
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, actualField(f, "pastSource")); assertSame(artOwner, actualField(f, "art"));
        assertSame(frameOwner, actualField(f, "captureOwner")); assertSame(poolOwner, actualField(pool, "owner"));
        assertSame(saved, actualField(p.visit(), "saved")); assertEquals(level, actualField(afterValues, "level"));
        assertSame(presentation, actualField(h.module(), "playerPresentation"));
        assertNotSame(oldCpu, completed, "Successful DrawObjects owns a fresh completed CPU table");
        assertArrayEquals(completed.bytes(), assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "cpuSat")).copy());
        assertArrayEquals(oldCpu.bytes(), cpuBytes.copy(), "The captured healthy7807 CPU image remains immutable");
        assertArrayEquals(oldCpu.bytes(), assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "displayedSat")).copy(),
                "Actual earlier VInt selects the prior CPU image before this Main builds its new table");
        var afterSection = assertInstanceOf(CdR11bSectionArt.State.class, actualField(afterValues, "section"));
        assertSlowPlc(section, afterSection, plcBudget);
        var afterCurrent = afterSection.residency();
        var afterTiles = floatTiles(afterCurrent);
        for (int tile = 0; tile < 15; tile++) assertArrayEquals(tiles[tile], afterTiles[tile], "Current Float tile" + tile);
        assertUnrelatedCurrent(current, afterCurrent, plcDestination, plcBytes);
        var cached = p.visit().getClass().getDeclaredField("floatBlock");
        assertTrue(Modifier.isPrivate(cached.getModifiers())); assertTrue(Modifier.isFinal(cached.getModifiers()));
        assertInstanceOf(CdFloatBlock.class, actualField(p.visit(), "floatBlock"));
        var active = p.visit().getClass().getDeclaredField("activeFloat");
        assertTrue(Modifier.isPrivate(active.getModifiers())); assertTrue(Modifier.isTransient(active.getModifiers()));
        assertNull(actualField(p.visit(), "activeFloat")); assertNull(actualField(p.visit(), "activePrimary"));
        assertNull(actualField(p.visit(), "activeDrain")); assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(f, "bodyEntitled")); assertNull(actualField(f, "pending"));
    }

    private static Prepared healthy7775() throws Exception {
        var p = ready(); assertTerminal(p, before(p), 45L, 8, 0, "PAST_FLOAT_HEALTHY7775");
        assertHealthy(p, 7775); return p;
    }
    private static void advance(Prepared p, int first, int last) throws Exception {
        var world = p.harness().mode().getWorldSession(); var source = p.frame().stageSource();
        long generation = p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration();
        for (int call = first; call <= last; call++) {
            step(p.harness(), route(call)); assertHealthy(p, call);
            assertSame(world, p.harness().mode().getWorldSession()); assertSame(source, p.frame().stageSource());
            assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        }
    }
    private static void assertHealthy(Prepared p, int call) throws Exception {
        var f = p.frame(); assertFalse(f.closed()); assertFalse(f.paused());
        assertSame(f, actualField(p.harness().module(), "playerFrame"));
        assertSame(p.visit(), actualField(p.harness().module(), "pastLoading"));
        assertEquals(call - 7730L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(call - 7774, f.environment().globals().levelFrames());
        assertEquals(8, f.environment().controls().rawHeld()); assertEquals(0, f.environment().movement().gates().timeZone());
        assertSame(p.assets(), actualField(p.visit(), "assets")); assertSame(p.placement(), actualField(p.visit(), "placement"));
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeDrain"));
        assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(f, "bodyEntitled")); assertNull(actualField(f, "pending"));
        var calls = callNames(actualField(p.visit(), "values"));
        assertTrue(calls.contains("ORDINARY_MAIN_RETURN")); assertFalse(calls.contains("FLOAT_BLOCK_BODY"));
        var cpu = completedDraw(p); assertEquals(640, cpu.bytes().length); assertEquals(cpu.spriteCount(), cpu.provenance().size());
        if (call == 7806 || call == 7807) { assertEquals(22, cpu.spriteCount()); assertEquals(0, primaryPieces(p)); }
    }
    private static void replayHealthyEdges(boolean course) throws Exception {
        var p = healthy7775(); advance(p, 7776, 7776); replayEdge(p, 7777, course);
        advance(p, 7778, 7806); replayEdge(p, 7807, course);
    }
    private static void replayEdge(Prepared p, int forward, boolean course) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry(); var before = captureNative(h);
        if (course) {
            var cut = registry.captureCourse(); step(h, route(forward)); assertHealthy(p, forward); var after = captureNative(h);
            registry.restoreCourse(cut); assertComplete(before, captureNative(h), "course.before" + forward);
            assertHealthy(p, forward - 1); step(h, route(forward)); assertHealthy(p, forward);
            assertComplete(after, captureNative(h), "course.after" + forward);
        } else {
            var cut = registry.capture(); step(h, route(forward)); assertHealthy(p, forward); var after = captureNative(h);
            registry.restore(cut); assertComplete(before, captureNative(h), "full.before" + forward);
            assertHealthy(p, forward - 1); step(h, route(forward)); assertHealthy(p, forward);
            assertComplete(after, captureNative(h), "full.after" + forward);
        }
        if (forward == 7777) { assertEquals(2, primaryPieces(p)); assertPrimarySatFromRawBMapping(p); }
    }
    private static byte[] float33AfterBody(int cameraX) {
        // Exact full64 native cleared slot plus row355 stores; no production image builder.
        byte[] image = new byte[64]; image[0] = 0x12;
        putWord(image, 8, 0x1C10); putWord(image, 12, 0xA0);
        image[0x24] = 2; image[1] = 4; putLong(image, 4, 0x20A252); putWord(image, 2, 0x4334);
        image[0x18] = 1; image[0x19] = image[0x16] = 12; image[0x1A] = 5;
        // Both completed Top calls leave25=0. Raw Past draws without Animate.
        if (float33OutsideWindow(cameraX)) Arrays.fill(image, (byte) 0);
        return image;
    }
    private static boolean float33OutsideWindow(int cameraX) {
        return (((0x1C10 & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
    }
    private static boolean float33Culled(int cameraX, int cameraY) {
        int dx = u16(0x1C10 - cameraX);
        if ((short) u16(dx + 12) < 0 || (short) u16(dx - 12) >= 320) return true;
        int y = 0xA0;
        if (cameraY < 0x100 && y >= 0x800) y = u16(y - 0x800);
        else if (cameraY >= 0x700 && y < 0x100) y = u16(y + 0x800);
        int dy = u16(y - cameraY);
        return (short) u16(dy + 12) < 0 || (short) u16(dy - 12) >= 224;
    }
    private static byte[][] floatTiles(CdR11bCameraArtResidency.State state) {
        byte[][] out = new byte[15][]; var region = state.region(CdR11bCameraArtResidency.Region.AT_6400);
        for (int tile = 0; tile < 15; tile++) {
            int destination = 0x6680 + tile * 32;
            for (int at = 0; at < 32; at++) assertTrue(region.knownByte(destination - 0x6400 + at).isPresent(),
                    "Actual current Float byte must be known at" + (destination + at));
            out[tile] = CdR11bCameraArtResidency.readTile(state, destination >>> 5).copy(); assertEquals(32, out[tile].length);
        }
        return out;
    }
    private static void assertSlowPlc(CdR11bSectionArt.State before, CdR11bSectionArt.State after, int budget) {
        int remaining = before.remainingTilesWord();
        int emitted = Math.min(remaining, budget);
        assertEquals(remaining - emitted, after.remainingTilesWord());
        assertEquals(before.sectionWord(), after.sectionWord());
        assertArrayEquals(before.codeTable().copy(), after.codeTable().copy());
        byte[] queue = before.queue().bytes().copy();
        if (remaining != 0) {
            putWord(queue, 4, (before.queue().headDestination() + budget * 32) & 0xFFFF);
            var old = before.decoder().orElseThrow(); var next = after.decoder().orElseThrow();
            assertEquals(old.nativeAddress(), next.nativeAddress());
            assertArrayEquals(old.sourceBytes().copy(), next.sourceBytes().copy());
            assertEquals(old.headerWord(), next.headerWord());
            assertEquals(remaining - emitted, next.remainingTiles());
            assertTrue(next.sourcePointer() >= old.sourcePointer());
            assertTrue(next.sourcePointer() <= next.nativeAddress() + next.sourceBytes().length());
            if (remaining == emitted) {
                // Native final tile:88-byte forward POP, last8 retained, no final saved-decoder store.
                for (int at = 0; at < 88; at++) queue[at] = queue[at + 6];
                assertEquals(before.savedDecoder(), after.savedDecoder());
                assertEquals(budget - emitted + 1, after.processBudgetWord());
            } else {
                putLong(queue, 0, (int) next.sourcePointer());
                assertEquals(0, after.processBudgetWord());
            }
        } else {
            assertEquals(before.decoder(), after.decoder());
            assertEquals(before.savedDecoder(), after.savedDecoder());
            assertEquals(before.processBudgetWord(), after.processBudgetWord());
        }
        assertArrayEquals(queue, after.queue().bytes().copy(), "Only genuine earlier slow service changes descriptor bytes");
    }
    private static void assertUnrelatedCurrent(CdR11bCameraArtResidency.State before, CdR11bCameraArtResidency.State after,
                                               int plcDestination, byte[] plcBytes) {
        for (var region : CdR11bCameraArtResidency.Region.values()) {
            var b = before.region(region); var a = after.region(region);
            for (int offset = 0; offset < region.byteLength(); offset++) {
                int address = region.destinationWord() + offset;
                int emittedOffset = (address - plcDestination) & 0xFFFF;
                if (emittedOffset < plcBytes.length) {
                    assertEquals(java.util.OptionalInt.of(Byte.toUnsignedInt(plcBytes[emittedOffset])), a.knownByte(offset),
                            "Actual earlier PLC byte" + address);
                } else {
                    assertEquals(b.knownByte(offset).isPresent(), a.knownByte(offset).isPresent(), "Unrelated knowledge" + address);
                    // Earlier VInt HUD redraws five time digits in this exact window.
                    if (address < 0xB220 || address >= 0xB360)
                        assertEquals(b.knownByte(offset), a.knownByte(offset), "Unrelated current byte" + address);
                }
            }
        }
    }
    private static byte[][] images(CdObjectPool pool) {
        byte[][] out = new byte[96][]; for (int i = 0; i < 96; i++) out[i] = pool.state(32 + i).copyBytes(); return out;
    }
    private static void diagnose(Prepared p, byte[][] before, Throwable first) throws Exception {
        var calls = callNames(actualField(p.visit(), "values"));
        System.out.println("PAST_FLOAT_INCOMPLETE7808 firstClass=" + first.getClass().getName()
                + " firstMessage=" + first.getMessage() + " boundary="
                + (first instanceof SonicCdGameModule.UnsupportedPastLoading x ? x.boundary() : "OTHER") + " calls=" + calls);
        int changed = 0;
        for (int i = 0; i < 96; i++) {
            byte[] after = p.placement().pool().state(32 + i).copyBytes();
            if (!Arrays.equals(before[i], after)) {
                changed++; System.out.println("PAST_FLOAT_RETAINED_CHANGED whole=" + (32 + i)
                        + " before=" + HexFormat.of().formatHex(before[i]) + " after=" + HexFormat.of().formatHex(after));
            }
        }
        System.out.println("PAST_FLOAT_INCOMPLETE7808 changedImages=" + changed + " closed=" + p.frame().closed()
                + " vint=" + p.frame().vIntClock().absoluteValue() + " main=" + p.frame().environment().globals().levelFrames());
    }
    private static List<String> callNames(Object values) throws Exception {
        return assertInstanceOf(List.class, actualField(values, "calls")).stream().map(x -> ((Enum<?>) x).name()).toList();
    }
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        return assertInstanceOf(CdObjectDrawQueue.CpuSat.class,
                assertInstanceOf(Optional.class, actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw")).orElseThrow());
    }
    private static long primaryPieces(Prepared p) throws Exception {
        return completedDraw(p).provenance().stream().filter(x -> x.slotRef().wholeIndex() == 0).count();
    }
    /** Actual authenticated B map bytes and literal native DrawSprite arithmetic. */
    private static void assertPrimarySatFromRawBMapping(Prepared p) throws Exception {
        var raw = p.assets().view(CdR11bAssets.Asset.MAP_SPR_SONIC).bytes(); assertEquals(3004, raw.length());
        var s = p.frame().state(); var m = s.motion(); assertEquals(4, m.spriteFlags() & 12);
        var scroll = (SonicCdPlayerPresentation.ScrollCapture)
                p.harness().module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        int x = m.centreX16_16() >>> 16, y = m.centreY16_16() >>> 16, cy = scroll.state().cameraYWord();
        if (cy < 0x100 && y >= 0x800) y = u16(y - 0x800);
        else if (cy >= 0x700 && y < 0x100) y = u16(y + 0x800);
        x = u16(x - scroll.state().cameraX() + 128); y = u16(y - cy + 128);
        int start = (short) raw.unsignedWord(s.animation().mapFrame() * 2), pieces = raw.unsignedByte(start);
        assertTrue(pieces > 0 && pieces <= 128); var cpu = completedDraw(p); byte[] sat = cpu.bytes(); int matched = 0;
        for (int index = 0; index < cpu.provenance().size(); index++) {
            var proof = cpu.provenance().get(index); if (proof.slotRef().wholeIndex() != 0) continue;
            assertEquals(2, proof.queueBucket()); assertEquals(0, proof.queueOrdinal()); assertEquals(matched, proof.mappingPieceOrdinal());
            int at = start + 1 + matched * 5, ry = raw.signedByte(at), size = raw.unsignedByte(at + 1), rx = raw.signedByte(at + 4);
            if ((m.spriteFlags() & 1) != 0) rx = -rx - (((size >>> 2) & 3) + 1) * 8;
            if ((m.spriteFlags() & 2) != 0) ry = -ry - ((size & 3) + 1) * 8;
            int out = index * 8, sx = (x + rx) & 0x1FF;
            assertEquals(u16(y + ry), word(sat, out)); assertEquals(size, Byte.toUnsignedInt(sat[out + 2]));
            assertEquals(index == 79 ? 0 : index + 1, Byte.toUnsignedInt(sat[out + 3]));
            assertEquals(u16(raw.unsignedWord(at + 2) + s.header().tile()) ^ ((m.spriteFlags() & 3) << 11), word(sat, out + 4));
            assertEquals(sx == 0 ? 1 : sx, word(sat, out + 6)); matched++;
        }
        assertEquals(pieces, matched);
    }

    /** Full data comparison, including CpuSat's intentionally identity-only Java class. */
    private static void assertComplete(Object expected, Object actual, String name) throws Exception {
        if (expected == actual) return;
        assertNotNull(expected, name); assertNotNull(actual, name); assertEquals(expected.getClass(), actual.getClass(), name);
        if (expected instanceof CdAssetBytes b) {
            assertArrayEquals(b.copy(), ((CdAssetBytes) actual).copy(), name);
        } else if (expected instanceof CdObjectDrawQueue.CpuSat b) {
            var a = (CdObjectDrawQueue.CpuSat) actual;
            assertArrayEquals(b.bytes(), a.bytes(), name); assertEquals(b.spriteCount(), a.spriteCount(), name); assertEquals(b.provenance(), a.provenance(), name);
        } else if (expected instanceof CdCheckpointLiveAux.State b) {
            var a = (CdCheckpointLiveAux.State) actual; assertEquals(b.knownMask(), a.knownMask(), name);
            for (int address : new int[]{0xFF1514, 0xFF1515, 0xFF1516, 0xFF1517, 0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E})
                assertEquals(b.knownByte(address), a.knownByte(address), name + ".known" + address);
        } else if (expected instanceof CdCheckpointSaveData.State b) {
            assertArrayEquals(b.knownMask().copy(), ((CdCheckpointSaveData.State) actual).knownMask().copy(), name);
            assertEquals(b, actual, name); // Native saved equality covers all bytes and all per-byte knowledge.
        } else if (expected instanceof Optional<?> b) {
            var a = (Optional<?>) actual; assertEquals(b.isPresent(), a.isPresent(), name);
            if (b.isPresent()) assertComplete(b.orElseThrow(), a.orElseThrow(), name + ".value");
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), name);
            for (int i = 0; i < b.size(); i++) assertComplete(b.get(i), a.get(i), name + '[' + i + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), name);
            for (int i = 0; i < Array.getLength(expected); i++) assertComplete(Array.get(expected, i), Array.get(actual, i), name + '[' + i + ']');
        } else if (expected.getClass().isRecord()) {
            for (var component : expected.getClass().getRecordComponents()) {
                var read = component.getAccessor(); read.setAccessible(true);
                assertComplete(read.invoke(expected), read.invoke(actual), name + '.' + component.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); assertComplete(field.get(expected), field.get(actual), name + '.' + field.getName());
            }
        } else assertEquals(expected, actual, name); // Semantic values or exact opaque ownership identity.
    }

    private static void ordered(List<String> calls, String... names) {
        int previous = -1;
        for (String name : names) { int at = calls.indexOf(name); assertTrue(at > previous, "Native order: " + name + " in " + calls); previous = at; }
    }
    private static int u16(int value) { return value & 65535; }
    private static int word(byte[] bytes, int at) { return (Byte.toUnsignedInt(bytes[at]) << 8) | Byte.toUnsignedInt(bytes[at + 1]); }
    private static void putWord(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int at, int value) { putWord(bytes, at, value >>> 16); putWord(bytes, at + 2, value); }
}
