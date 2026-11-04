package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdTamabboh;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original-USA five-jump route through the first actual Past ID22 visit.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Tamabboh/Main.asm:7-72,162-282;
 * Object Functions.asm:137-196,240-363. Component tests own the native algorithm;
 * this test checks genuine World publication, actual owner lifetime and replay.
 * Fire, cleared-missile and bad-selector ingress below are fixed native object
 * fixtures at a healthy cut. They change no Frame, terrain, camera, spawner or art.
 * No Body/helper invocation, returned-effect replay, current-pixel or parity oracle.
 */
@Isolated
class TestSonicCdPastTamabbohWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void natural7851AndFixedNativeCallbacksUseActualWorldOwnersAndFullCourseReplay() throws Exception {
        var p = ready(); var h = p.harness(); var pool = p.placement().pool();
        var world = h.mode().getWorldSession(); var frame = p.frame(); var source = frame.stageSource();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var cachedBody = assertInstanceOf(CdTamabboh.class, actualField(p.visit(), "tamabboh"));
        for (int call = 7775; call <= 7850; call++) {
            step(h, route(call)); healthy(p, cachedBody, world, generation, source);
        }
        assertArrayEquals(new byte[64], pool.state(37).copyBytes(), "Actual healthy7850 first-free source37");

        replayEdge(p, route(7851), cachedBody, world, generation, source, "natural7850-7851");
        var natural = pool.state(37);
        assertEquals(0x22, natural.id()); assertEquals(0xA7, natural.savedFlagsId());
        assertEquals(0, natural.subtype()); assertEquals(0, natural.subtype2());
        assertTrue(natural.routine() == 2 || natural.routine() == 4,
                "The real floor determines Position/Main; no fitted terrain or floor result");
        assertEquals(0x1D60, natural.centreX()); assertEquals(0x0151, natural.centreY());
        assertEquals(0x2403, natural.word(TILE));
        assertEquals(0x20B7C0, natural.longBits(CdObjectState.LongField.MAPPINGS));
        assertEquals(0x20B79A, natural.longBits(CdObjectState.LongField.TAMABBOH_ANIMATION_SCRIPT));
        // Complete first-call image. Only the genuine floor-selected routine and native
        // visibility bit are observations rather than detached scalar expectations.
        byte[] expected = new byte[64]; var bytes = ByteBuffer.wrap(expected);
        expected[0] = 0x22; expected[1] = (byte) natural.spriteFlags();
        bytes.putShort(2, (short) 0x2403); bytes.putInt(4, 0x20B7C0);
        bytes.putShort(8, (short) 0x1D60); bytes.putShort(12, (short) 0x0151);
        expected[0x16] = 15; expected[0x17] = 16; expected[0x18] = 4; expected[0x19] = 16;
        expected[0x20] = 0x2C; expected[0x23] = (byte) 0xA7; expected[0x24] = (byte) natural.routine();
        bytes.putShort(0x2A, (short) 0x1D60); bytes.putInt(0x2C, -0xA000); bytes.putInt(0x30, 0x20B79A);
        assertEquals(4, natural.spriteFlags() & 0x7F); assertArrayEquals(expected, natural.copyBytes());
        assertEquals(121L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(77, frame.environment().globals().levelFrames());
        assertEquals(8, frame.environment().controls().rawHeld());
        assertEquals(0, frame.environment().movement().gates().timeZone());
        assertEquals(0, frame.environment().movement().gates().timeWarpDirection());
        assertEquals(1, tamabbohCalls(p)); assertEquals(0, tamabbohSounds(p));

        var registry = h.mode().getRewindRegistry(); var naturalCut = registry.capture();
        var naturalImage = captureNative(h);
        requireSameSpawnChunk(p);
        for (int whole = 32; whole < 128; whole++) if (pool.state(whole).id() == 0)
            assertArrayEquals(new byte[64], pool.state(whole).copyBytes(), "Fixed scene requires genuinely cleared free images");
        // ONE fixed Fire ingress. The actual increasing physical pass, real first-free
        // scan and real child visits determine allocation and same-pass Init naturally.
        seed(p, natural.moveByte(ROUTINE, 10).moveByte(SPRITE_FLAGS, natural.spriteFlags() | 0x80));
        var neutral = LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        replayEdge(p, neutral, cachedBody, world, generation, source, "constructed-Fire");
        assertEquals(1, tamabbohSounds(p));
        assertEquals(4, pool.state(37).routine()); assertEquals(0x78, pool.state(37).word(TAMABBOH_COOLDOWN));
        int children = 0, left = 0, right = 0, samePass = 0;
        for (int whole = 32; whole < 128; whole++) {
            var child = pool.state(whole);
            if (child.id() != 0x22 || child.subtype() != 1) continue;
            children++;
            assertEquals(natural.centreX(), child.centreX()); assertEquals(natural.centreY() - 10, child.centreY());
            assertEquals(natural.word(TILE), child.word(TILE)); assertEquals(5, child.unsignedByte(PRIORITY));
            int direction = child.unsignedByte(TAMABBOH_MISSILE_DIRECTION);
            if (direction == 0) right++; else { assertEquals(0xFF, direction); left++; }
            assertEquals(whole > 37 ? 2 : 0, child.routine(), "Later physical children execute Init in this pass");
            if (whole > 37) samePass++;
        }
        assertEquals(2, children); assertEquals(1, left); assertEquals(1, right);
        assertEquals(1 + samePass, tamabbohCalls(p));

        registry.restore(naturalCut); complete(naturalImage, captureNative(h), "natural.before-cleared-missile");
        healthy(p, cachedBody, world, generation, source); requireSameSpawnChunk(p);
        // Native nonnegative-visibility missile Main clears all64, then MUST still
        // call real DrawObject. DrawObjects consumes the ID0 pointer without Map0.
        seed(p, natural.moveByte(SUBTYPE, 1).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4));
        replayEdge(p, neutral, cachedBody, world, generation, source, "constructed-cleared-ID0-missile");
        assertArrayEquals(new byte[64], pool.state(37).copyBytes()); assertEquals(1, tamabbohCalls(p));
        assertEquals(0, tamabbohSounds(p));
        var draw = completedDraw(p);
        assertTrue(draw.provenance().stream().noneMatch(piece -> piece.slotRef().wholeIndex() == 37),
                "The actual ID0 drain emits no piece and resolves no fabricated mapping");

        registry.restore(naturalCut); complete(naturalImage, captureNative(h), "natural.before-selector-stop");
        healthy(p, cachedBody, world, generation, source); requireSameSpawnChunk(p);
        var bad = natural.moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 1); seed(p, bad);
        var later = pool.state(38); // Retained refs only after the deliberately failed step.
        var failure = assertThrows(CdTamabboh.UnsupportedTamabboh.class, () -> step(h, neutral));
        assertEquals(CdTamabboh.UnsupportedBoundary.TILE_ENTRY, failure.boundary());
        assertEquals(37, failure.wholeIndex());
        var prefix = bad.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, bad.spriteFlags() | 4)
                .moveByte(PRIORITY, 4).moveByte(COLLISION_TYPE, 0x2C).moveByte(X_RADIUS, 16)
                .moveByte(WIDTH, 16).moveByte(Y_RADIUS, 15).moveWord(TAMABBOH_ANCHOR_X, bad.centreX());
        assertEquals(prefix, pool.state(37), "Actual completed Init prefix survives its reached selector stop");
        assertEquals(later, pool.state(38), "Later physical source is unvisited after the original stop");
        assertTrue(frame.closed()); assertRetired(p);
        assertEquals(CdObjectDrawQueue.Phase.CLOSED,
                assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue")).phase());
        var retained = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(retained.contains(SonicCdGameModule.PastLoadCall.TAMABBOH_BODY));
        assertFalse(retained.contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));
        assertFalse(retained.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }

    private static void replayEdge(Prepared p, LogicalInputSnapshot input, CdTamabboh body,
                                   Object world, long generation, Object source, String name) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, input); healthy(p, body, world, generation, source); var after = captureNative(h);
        registry.restore(full); complete(before, captureNative(h), name + ".full.before");
        healthy(p, body, world, generation, source); step(h, input); healthy(p, body, world, generation, source);
        complete(after, captureNative(h), name + ".full.after");
        registry.restoreCourse(course); complete(before, captureNative(h), name + ".course.before");
        healthy(p, body, world, generation, source); step(h, input); healthy(p, body, world, generation, source);
        complete(after, captureNative(h), name + ".course.after");
    }
    private static void healthy(Prepared p, CdTamabboh body, Object world, long generation, Object source) throws Exception {
        assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertSame(source, p.frame().stageSource());
        assertSame(body, actualField(p.visit(), "tamabboh")); assertFalse(p.frame().closed());
        var state = p.harness().module().pastLoadState();
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, state.phase());
        assertTrue(state.calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertRetired(p);
    }
    private static void assertRetired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeTamabboh"));
        assertFalse((boolean) actualField(p.visit(), "tamabbohPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingTamabboh"));
    }
    private static long tamabbohCalls(Prepared p) {
        return p.harness().module().pastLoadState().calls().stream()
                .filter(call -> call == SonicCdGameModule.PastLoadCall.TAMABBOH_BODY).count();
    }
    private static long tamabbohSounds(Prepared p) {
        var sounds = p.harness().module().pastLoadState().sounds();
        for (var sound : sounds) if (sound instanceof SonicCdGameModule.PastTamabbohFm fm)
            assertEquals(CdTamabboh.FmSound.FM_A0, fm.sound());
        return sounds.stream().filter(SonicCdGameModule.PastTamabbohFm.class::isInstance).count();
    }
    private static void requireSameSpawnChunk(Prepared p) throws Exception {
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class,
                actualField(p.harness().module(), "playerPresentation"));
        assertEquals(presentation.pastForegroundImage().cameraX() & 0xFF80,
                p.placement().status().previousChunkWord(), "Fixed fixture must not cross a new prespawn chunk");
    }
    private static void seed(Prepared p, CdObjectState image) throws Exception {
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared();
        var before = p.placement().capture(); var values = actualField(p.visit(), "values");
        p.placement().pool().write(image);
        assertEquals(actor, p.frame().captureActor()); assertEquals(shared, p.frame().captureShared());
        assertSame(values, actualField(p.visit(), "values"));
        var after = p.placement().capture(); assertEquals(before.status(), after.status());
        assertArrayEquals(before.savedFlags().copy(), after.savedFlags().copy());
        for (int whole = 32; whole < 128; whole++) if (whole != image.identity().wholeIndex())
            assertEquals(before.pool().slots().get(whole - 32), after.pool().slots().get(whole - 32));
    }
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        var completed = (Optional<?>) actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw");
        return assertInstanceOf(CdObjectDrawQueue.CpuSat.class, completed.orElseThrow());
    }

    /** Complete CD partitions, including all64/96, checkpoint knowledge and all640 SAT bytes. */
    private static void complete(Object expected, Object actual, String name) throws Exception {
        if (expected == actual) return;
        assertNotNull(expected, name); assertNotNull(actual, name); assertEquals(expected.getClass(), actual.getClass(), name);
        if (expected instanceof CdAssetBytes b) assertArrayEquals(b.copy(), ((CdAssetBytes) actual).copy(), name);
        else if (expected instanceof CdObjectDrawQueue.CpuSat b) {
            var a = (CdObjectDrawQueue.CpuSat) actual; assertArrayEquals(b.bytes(), a.bytes(), name);
            assertEquals(b.spriteCount(), a.spriteCount(), name); assertEquals(b.provenance(), a.provenance(), name);
        } else if (expected instanceof CdCheckpointLiveAux.State b) {
            var a = (CdCheckpointLiveAux.State) actual; assertEquals(b.knownMask(), a.knownMask(), name);
            for (int address : new int[]{0xFF1514, 0xFF1515, 0xFF1516, 0xFF1517, 0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E})
                assertEquals(b.knownByte(address), a.knownByte(address), name + ".known" + address);
        } else if (expected instanceof CdCheckpointSaveData.State b) {
            assertArrayEquals(b.knownMask().copy(), ((CdCheckpointSaveData.State) actual).knownMask().copy(), name);
            assertEquals(b, actual, name);
        } else if (expected instanceof Optional<?> b) {
            var a = (Optional<?>) actual; assertEquals(b.isPresent(), a.isPresent(), name);
            if (b.isPresent()) complete(b.orElseThrow(), a.orElseThrow(), name + ".value");
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), name);
            for (int n = 0; n < b.size(); n++) complete(b.get(n), a.get(n), name + '[' + n + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), name);
            for (int n = 0; n < Array.getLength(expected); n++) complete(Array.get(expected, n), Array.get(actual, n), name + '[' + n + ']');
        } else if (expected.getClass().isRecord()) {
            for (var component : expected.getClass().getRecordComponents()) {
                var accessor = component.getAccessor(); accessor.setAccessible(true);
                complete(accessor.invoke(expected), accessor.invoke(actual), name + '.' + component.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); complete(field.get(expected), field.get(actual), name + '.' + field.getName());
            }
        } else assertEquals(expected, actual, name);
    }
}
