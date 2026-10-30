package com.openggf.game.soniccd;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdPataBata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE-ONLY independent B World witnesses, authored before the private Pata binding.
 * J58c38a6296a3194fa4a8c0791085c446d35c70ae; N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Pata-Bata/Main.asm:7-80; Object Animate.asm:14-61; Functions (Misc).asm:69-101;
 * Object Spawner.asm:176-254,267-314,388-446; Object Functions.asm:137-196,240-363.
 * Expected values never call Body/Frame/placement/animation/drawing production helpers.
 * Constructed scenes are explicitly one closed-cut native RAM fixture, not a natural route.
 * The one natural7808 attempt admits a returned Main OR its typed later dispatcher fence.
 * No failure capture, restore, retry or requirePlayerFrame call follows a failed attempt.
 * CPU SAT is checked; no renderer/current-art pixel or whole-stage parity claim is made.
 */
@Isolated
class TestSonicCdPastPataBataWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void natural7808OnceReturnsPata64BeforeEitherMainReturnOrTheActualLaterFence() throws Throwable {
        var w = healthy7776(); var p = w.p();
        for (int call = 7777; call <= 7807; call++) { step(p.harness(), route(call)); healthy(w, call); }
        var pool = p.placement().pool(); var pre = p.placement().capture();
        assertArrayEquals(new byte[64], pool.state(64).copyBytes(), "Actual healthy7807 whole64 must be fully zero");
        var scroll = scroll(p); int cameraX = scroll.state().cameraX(), cameraY = scroll.state().cameraYWord();
        var spawned = forwardSpawnTo361(p, pre, cameraX);
        assertEquals(64, spawned.whole(), "Source-ordered ID-only allocation must independently select whole64");
        byte[] initial = spawned.image();
        assertEquals(0x3E, u(initial[0])); assertEquals(0, u(initial[0x28])); assertEquals(0, u(initial[0x29]));
        var expected = nativeBody(initial, p.assets());
        boolean queued = visible(expected, cameraX, cameraY);
        assertTrue(anchorInside(expected, cameraX), "Natural source anchor remains inside native CheckObjDespawn2");
        var oldCpu = completedDraw(p); var values = actualField(p.visit(), "values");
        var oldCpuBytes = actualField(values, "cpuSat"); var saved = actualField(p.visit(), "saved");
        var source = p.frame().stageSource(); var art = actualField(p.frame(), "art");
        var frameOwner = actualField(p.frame(), "captureOwner"); var poolOwner = actualField(pool, "owner");
        var world = p.harness().mode().getWorldSession();
        long generation = p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration();
        assertBinding(w);
        Throwable failure = null;
        try { step(p.harness(), route(7808)); }
        catch (Throwable original) {
            // Original failures outside the ONE explicitly accepted later fence escape unchanged.
            try { diagnose(p, original); }
            catch (Throwable diagnostic) { if (diagnostic != original) original.addSuppressed(diagnostic); }
            if (!(original instanceof SonicCdGameModule.UnsupportedPastLoading bounded)
                    || bounded.boundary() != SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS
                    || !calls(p).contains("PATA_BATA_BODY")) throw original;
            failure = original;
        }
        // Retained references/raw getters only on the failure branch.
        var names = calls(p);
        assertEquals(1L, count(names, "PATA_BATA_BODY"));
        assertEquals(4L, count(names.subList(0, names.indexOf("PATA_BATA_BODY")), "FLOAT_BLOCK_BODY"));
        assertEquals(8L, count(names.subList(0, names.indexOf("PATA_BATA_BODY")), "FLOAT_BLOCK_SOLID"));
        ordered(names, "ORDINARY_SERVICE", "LEVEL_FRAME_INCREMENT", "SPAWN_OBJECTS", "PRIMARY_MAIN",
                "RESERVED_OBJECTS", "RING_BODY", "FLOAT_BLOCK_BODY", "ANIMAL_PRESENCE", "PATA_BATA_BODY");
        assertTrue(names.lastIndexOf("ANIMAL_PRESENCE") < names.lastIndexOf("SPRING_BODY"));
        assertTrue(names.lastIndexOf("SPRING_BODY") < names.indexOf("PATA_BATA_BODY"));
        assertEquals(0x24, pool.state(62).id(), "Earlier real Animal62 remains its native physical identity");
        assertEquals(0x0A, pool.state(63).id(), "Earlier real Spring63 returns before Pata64");
        assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, actualField(p.frame(), "pastSource")); assertSame(art, actualField(p.frame(), "art"));
        assertSame(frameOwner, actualField(p.frame(), "captureOwner")); assertSame(poolOwner, actualField(pool, "owner"));
        assertSame(saved, actualField(p.visit(), "saved")); assertDev(w);
        assertEquals(78L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertEquals(34, p.frame().environment().globals().levelFrames());
        assertEquals(8, p.frame().environment().controls().rawHeld());
        assertEquals(0, p.frame().environment().movement().gates().timeZone());
        assertEquals(0, p.frame().environment().movement().gates().timeWarpDirection());
        if (failure == null) {
            if (queued) expected[1] |= (byte) 0x80;
            assertTrue(names.contains("DYNAMIC_OBJECTS")); assertTrue(names.contains("DRAW_OBJECTS"));
            assertTrue(names.contains("ORDINARY_MAIN_RETURN")); assertFalse(p.frame().closed());
            assertPataPieces(p, 64, expected, completedDraw(p));
            System.out.println("PAST_PATA_NATURAL7808 disposition=RETURNED_FULL_MAIN");
        } else {
            assertFalse(names.contains("DYNAMIC_OBJECTS")); assertFalse(names.contains("DRAW_OBJECTS"));
            assertFalse(names.contains("ORDINARY_MAIN_RETURN")); assertTrue(p.frame().closed()); assertFalse(p.frame().paused());
            assertSame(oldCpu, completedDraw(p)); assertSame(oldCpuBytes, actualField(actualField(p.visit(), "values"), "cpuSat"));
            assertArrayEquals(oldCpu.bytes(), bytes(actualField(actualField(p.visit(), "values"), "displayedSat")));
            var queue = assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue"));
            assertEquals(CdObjectDrawQueue.Phase.CLOSED, queue.phase());
            var pointers = (CdObjectDrawQueue.CdSlotRef[][]) actualField(queue, "pointers");
            var counts = (int[]) actualField(queue, "pointerByteCounts"); int occurrences = 0;
            for (int bucket = 0; bucket < 8; bucket++) {
                assertTrue(counts[bucket] >= 0 && counts[bucket] <= 0x7E && (counts[bucket] & 1) == 0);
                for (int n = 0; n < counts[bucket] / 2; n++) {
                    assertNotNull(pointers[bucket][n]);
                    if (pointers[bucket][n].wholeIndex() == 64) { occurrences++; assertEquals(3, bucket); }
                }
            }
            assertEquals(queued ? 1 : 0, occurrences, "Genuine DrawObject admission precedes retained later fence");
            assertSame(scroll.state(), actualField(actualField(p.harness().module(), "playerPresentation"), "scrollState"));
            assertSame(scroll.background(), actualField(actualField(p.harness().module(), "playerPresentation"), "backgroundState"));
            System.out.println("PAST_PATA_NATURAL7808 disposition=RETAINED_LATER_DYNAMIC_OBJECTS_INCOMPLETE");
        }
        assertArrayEquals(expected, pool.state(64).copyBytes(), "Native row361/zero64 -> Init/Main/Animate/Draw/tail, every byte");
        assertRetired(p);
        System.out.println("PAST_PATA_NATURAL7808 sourceRow=361 sourceWhole=" + spawned.whole()
                + " savedId=" + u(initial[0x23]) + " source=" + HexFormat.of().formatHex(initial)
                + " cameraX=" + cameraX + " cameraY=" + cameraY + " queued=" + queued
                + " retained64=" + HexFormat.of().formatHex(pool.state(64).copyBytes()));
    }

    @Test void constructedEightFixedNativeScenesReplayBothEdgesThroughFullRestoration() throws Exception {
        constructedScenes(false);
    }

    @Test void constructedEightFixedNativeScenesReplayBothEdgesThroughCourseRestoration() throws Exception {
        constructedScenes(true);
    }

    private record World(Prepared p, CdOpeningObjectRuntime.DevGlobals dev, Object world, long generation,
                         Object source, Object art, Object poolOwner) { }
    private record Control(Object actor, Object shared, Object values, Object scroll, Object sounds,
                           CdObjectDrawQueue.CpuSat cpu) { }
    private record Spawned(int whole, byte[] image) { }

    private static World healthy7776() throws Exception {
        var h = opening();
        var opening = assertInstanceOf(CdOpeningObjectRuntime.class, h.module().getGameService(NativeDynamicObjectPass.class));
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, h.module().levelProgramState().phase());
        var dev = opening.devGlobals(); var bank = opening.placement().capture().savedFlags();
        step(h, route(7729));
        var frame = h.module().requirePlayerFrame(); var visit = actualField(h.module(), "pastLoading");
        var placement = assertInstanceOf(CdObjectPlacementRuntime.class, actualField(visit, "placement"));
        int returned = 7729;
        while (h.module().pastLoadState().phase() != SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL) {
            assertTrue(returned < 7800); step(h, route(++returned));
        }
        assertEquals(7774, returned);
        var p = new Prepared(h, visit, frame, placement, h.module().pastLoadState().assets(), bank, returned);
        var w = new World(p, dev, h.mode().getWorldSession(),
                h.mode().getLevelManager().getCompletedProductionLoadGeneration(), frame.stageSource(),
                actualField(frame, "art"), actualField(placement.pool(), "owner"));
        assertDev(w); assertUnscanned(p);
        assertTerminal(p, before(p), 45L, 8, 0, "PAST_PATA_HEALTHY7775");
        healthy(w, 7775); step(h, route(7776)); healthy(w, 7776); assertBinding(w);
        return w;
    }

    private static void healthy(World w, int call) throws Exception {
        var p = w.p(); var frame = p.frame();
        assertFalse(frame.closed()); assertFalse(frame.paused());
        assertSame(frame, actualField(p.harness().module(), "playerFrame"));
        assertSame(p.visit(), actualField(p.harness().module(), "pastLoading"));
        assertSame(p.assets(), actualField(p.visit(), "assets"));
        assertSame(p.placement(), actualField(p.visit(), "placement"));
        assertSame(w.world(), p.harness().mode().getWorldSession());
        assertEquals(w.generation(), p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(w.source(), frame.stageSource()); assertSame(w.art(), actualField(frame, "art"));
        assertSame(w.poolOwner(), actualField(p.placement().pool(), "owner"));
        assertEquals(0, frame.environment().movement().gates().timeZone());
        assertEquals(0, frame.environment().movement().gates().timeWarpDirection());
        assertEquals(call - 7730L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(call - 7774, frame.environment().globals().levelFrames());
        assertEquals(8, frame.environment().controls().rawHeld());
        assertTrue(calls(p).contains("ORDINARY_MAIN_RETURN")); assertRetired(p); assertDev(w);
    }

    private static void assertDev(World w) throws Exception {
        var globals = actualField(actualField(w.p().visit(), "values"), "globals");
        assertSame(w.dev(), actualField(globals, "retainedDev"),
                "Both original explicit-DEV values keep their exact carried owner through A retirement and B replacement/restore");
        assertEquals(0, w.dev().goodFuture()); assertEquals(0, w.dev().projDestroyed());
    }

    private static void assertBinding(World w) throws Exception {
        var p = w.p(); var field = p.visit().getClass().getDeclaredField("pataBata");
        assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
        var body = assertInstanceOf(CdPataBata.class, actualField(p.visit(), "pataBata"));
        assertSame(p.placement(), actualField(body, "runtime")); var a = body.assets();
        assertEquals(0x3E, CdPataBata.OBJECT_ID);
        assertEquals(0x20B386, a.normalMappingsAddress()); assertEquals(0x20B3A8, a.damagedMappingsAddress());
        assertArrayEquals(p.assets().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes().copy(), a.objectTileIds().copy());
        assertArrayEquals(ANIMATION, a.animations().bytes().copy()); assertEquals(0, a.animations().tableOffset());
        assertEquals(2, a.animations().scriptCount());
        assertEquals(3, a.normal().mappingFrames().size()); assertEquals(3, a.damaged().mappingFrames().size());
        assertArrayEquals(NORMAL, a.normal().mappings().copy()); assertArrayEquals(DAMAGED, a.damaged().mappings().copy());
        assertArrayEquals(ANIMATION, a.normal().animationTable().copy()); assertArrayEquals(ANIMATION, a.damaged().animationTable().copy());
        assertArrayEquals(NORMAL, p.assets().view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA1).bytes().copy());
        assertArrayEquals(DAMAGED, p.assets().view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA2).bytes().copy());
        assertArrayEquals(ANIMATION, p.assets().view(CdR11bAssets.Asset.ANI_PATA_BATA).bytes().copy());
        assertEquals(0x20B386, p.assets().view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA1).range().nativeAddress());
        assertEquals(0x20B3A8, p.assets().view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA2).range().nativeAddress());
        assertEquals(36L, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals("bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(p.assets().view(CdR11bAssets.Asset.SINE_TABLE).bytes().copy())));
    }

    private static void assertRetired(Prepared p) throws Exception {
        for (String field : List.of("activePata", "activePrimary", "activeReserved", "activeRing", "activeSpring",
                "activePlatform", "activeFloat", "activeTunnelDoor", "activeMonitor", "activeDrain"))
            assertNull(actualField(p.visit(), field), field);
        var active = p.visit().getClass().getDeclaredField("activePata");
        assertTrue(Modifier.isPrivate(active.getModifiers())); assertTrue(Modifier.isTransient(active.getModifiers()));
        assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(p.frame(), "bodyEntitled")); assertNull(actualField(p.frame(), "pending"));
    }

    private static void constructedScenes(boolean course) throws Exception {
        var w = healthy7776(); var p = w.p(); var registry = p.harness().mode().getRewindRegistry();
        var pristine = registry.capture(); var nativePristine = captureNative(p.harness()); var camera = scroll(p);
        int x = u16(camera.state().cameraX() + 32), y = u16(camera.state().cameraYWord() + 80);
        assertTrue(y <= 0xFFF, "Single fixed native Y must fit the placement-format coordinate; no fitting/search");
        // Native SpawnObjects_Main equality at this actual cut is mandatory.
        sameChunk(p); seedOnlyNative(p, -1, 0, x, y);
        Control[] controls = new Control[2];
        for (int edge = 0; edge < 2; edge++) {
            sameChunk(p); step(p.harness(), route(7777 + edge)); healthy(w, 7777 + edge);
            controls[edge] = control(p);
            for (int whole = 32; whole < 128; whole++) assertArrayEquals(new byte[64], p.placement().pool().state(whole).copyBytes());
        }
        int scenes = 0;
        for (int subtype : new int[]{0, 1}) for (int flips = 0; flips < 4; flips++) {
            registry.restore(pristine); complete(nativePristine, captureNative(p.harness()), "pristine7776");
            healthy(w, 7776); sameChunk(p);
            seedOnlyNative(p, subtype, flips, x, y); scenes++;
            for (int edge = 0; edge < 2; edge++) replayEdge(w, 7777 + edge, course, controls[edge]);
        }
        assertEquals(8, scenes);
        System.out.println("PAST_PATA_CONSTRUCTED scenes=8 edges=2 restore=" + (course ? "COURSE" : "FULL")
                + " fixedX=" + x + " fixedY=" + y + " CPU_SAT_ONLY currentPataPixels=UNMEASURED");
    }

    /** The only constructed writes: clear96 and native ID/X/Y/subtype/subtype2/both flip bytes at whole40. */
    private static void seedOnlyNative(Prepared p, int subtype, int flips, int x, int y) throws Exception {
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared(); var values = actualField(p.visit(), "values");
        var camera = scroll(p); var status = p.placement().status(); var saved = p.placement().capture().savedFlags();
        var pool = p.placement().pool(); var owner = actualField(pool, "owner");
        pool.clearDynamicSlots();
        if (subtype >= 0) {
            pool.write(pool.state(40).moveByte(ID, 0x3E).moveWord(X, x).moveWord(Y, y)
                    .moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, 0).moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips));
            byte[] expected = new byte[64]; expected[0] = 0x3E; expected[1] = expected[0x22] = (byte) flips;
            putWord(expected, 8, x); putWord(expected, 12, y); expected[0x28] = (byte) subtype;
            assertArrayEquals(expected, pool.state(40).copyBytes());
        }
        assertEquals(actor, p.frame().captureActor()); assertEquals(shared, p.frame().captureShared());
        assertSame(values, actualField(p.visit(), "values")); complete(camera, scroll(p), "seed.scroll");
        assertEquals(status, p.placement().status()); assertArrayEquals(saved.copy(), p.placement().capture().savedFlags().copy());
        assertSame(owner, actualField(pool, "owner"));
        assertNull(actualField(p.visit(), "drawQueue")); assertRetired(p);
    }

    private static void replayEdge(World w, int call, boolean course, Control control) throws Exception {
        var p = w.p(); var h = p.harness(); var registry = h.mode().getRewindRegistry(); sameChunk(p);
        var before = captureNative(h); var fullCut = registry.capture();
        var courseCut = course ? registry.captureCourse() : null;
        byte[][] all = images(p.placement().pool()); byte[] bank = p.placement().capture().savedFlags().copy();
        var status = p.placement().status(); var oldCpu = completedDraw(p);
        byte[] expected = nativeBody(all[8], p.assets());
        var preCamera = scroll(p);
        assertTrue(visible(expected, preCamera.state().cameraX(), preCamera.state().cameraYWord()),
                "Fixed +32/+80 scene must actually reach DrawObject's admitted visible path");
        assertTrue(anchorInside(expected, preCamera.state().cameraX()));
        expected[1] |= (byte) 0x80; // Native DrawDone, never the body/callback.
        var input = route(call);
        step(h, input); healthy(w, call); sceneOutput(w, control, all, bank, status, oldCpu, expected);
        var after = captureNative(h);
        if (course) registry.restoreCourse(courseCut); else registry.restore(fullCut);
        complete(before, captureNative(h), (course ? "course" : "full") + ".before" + call);
        healthy(w, call - 1); sameChunk(p); assertArrayEquals(all[8], p.placement().pool().state(40).copyBytes());
        step(h, input); healthy(w, call); sceneOutput(w, control, all, bank, status, oldCpu, expected);
        complete(after, captureNative(h), (course ? "course" : "full") + ".after" + call);
    }

    private static void sceneOutput(World w, Control control, byte[][] before, byte[] bank,
            CdObjectPlacementRuntime.Status status, CdObjectDrawQueue.CpuSat prior, byte[] expected) throws Exception {
        var p = w.p(); var pool = p.placement().pool();
        assertArrayEquals(expected, pool.state(40).copyBytes(), "Full64 arithmetic/phase/sine/script/flags/DrawDone including nonwrites");
        for (int whole = 32; whole < 128; whole++) if (whole != 40)
            assertArrayEquals(before[whole - 32], pool.state(whole).copyBytes(), "All95 unrelated dynamic images whole" + whole);
        assertArrayEquals(bank, p.placement().capture().savedFlags().copy()); assertEquals(status, p.placement().status());
        // Same-owner no-Pata forward control isolates all actual upstream-owned work.
        complete(control.actor(), p.frame().captureActor(), "upstream.actor");
        complete(control.shared(), p.frame().captureShared(), "upstream.shared");
        complete(control.scroll(), scroll(p), "upstream.scroll");
        complete(control.sounds(), actualField(p.visit(), "sounds"), "upstream.sounds");
        var actualValues = actualField(p.visit(), "values");
        for (var component : actualValues.getClass().getRecordComponents()) {
            if (Set.of("cpuSat", "displayedSat", "calls", "loop").contains(component.getName())) continue;
            var accessor = component.getAccessor(); accessor.setAccessible(true);
            complete(accessor.invoke(control.values()), accessor.invoke(actualValues), "upstream.Values." + component.getName());
        }
        assertArrayEquals(prior.bytes(), bytes(actualField(actualValues, "displayedSat")),
                "Real VInt publishes the prior scene CPU SAT, including its earlier Pata pieces");
        var oldLoop = actualField(control.values(), "loop"); var loop = actualField(actualValues, "loop");
        for (String name : List.of("demoDataIndexWord", "animations", "paletteSteps7", "paletteTimers7"))
            complete(actualField(oldLoop, name), actualField(loop, name), "upstream.loop." + name);
        var names = calls(p); var controlNames = callNames(control.values());
        assertEquals(1L, count(names, "PATA_BATA_BODY"));
        assertEquals(controlNames, names.stream().filter(n -> !n.equals("PATA_BATA_BODY")).toList());
        ordered(names, "PRIMARY_MAIN", "RESERVED_OBJECTS", "PATA_BATA_BODY", "DYNAMIC_OBJECTS", "DRAW_OBJECTS", "ORDINARY_MAIN_RETURN");
        assertWholeCpu(p, control.cpu(), prior, expected);
        assertBinding(w); assertRetired(p);
    }

    private static Control control(Prepared p) throws Exception {
        return new Control(p.frame().captureActor(), p.frame().captureShared(), actualField(p.visit(), "values"),
                scroll(p), actualField(p.visit(), "sounds"), completedDraw(p));
    }
    private static void sameChunk(Prepared p) throws Exception {
        assertEquals(2, p.placement().status().routine());
        assertEquals(scroll(p).state().cameraX() & 0xFF80, p.placement().status().previousChunkWord(),
                "Hard native pre-spawn equality; never mute spawner or move camera/cursors to admit fixture");
    }

    /** Native forward scanner through source row361 only; no production scanner/body is executed. */
    private static Spawned forwardSpawnTo361(Prepared p, CdObjectPlacementRuntime.Snapshot pre, int cameraX) {
        var rows = p.assets().placements().records(); assertEquals(497, rows.size()); var row = rows.get(361);
        assertEquals(0x1C47, row.centreX()); assertEquals(0x0221, row.packedY()); assertEquals(0xBE, row.encodedId());
        assertEquals(0, row.subtype()); assertEquals(0x20, row.periodFlags()); assertEquals(0, row.subtype2());
        var status = pre.status(); assertEquals(2, status.routine());
        int chunk = cameraX & 0xFF80; assertTrue((short) chunk > (short) status.previousChunkWord());
        int bound = u16(chunk + 0x280), at = status.rightRecordIndex();
        assertTrue(at <= 361 && bound > row.centreX(), "Actual next native forward scan must reach row361");
        byte[] bank = pre.savedFlags().copy(); byte[][] images = images(p.placement().pool());
        int saved = 0;
        for (int n = 0; n < at; n++) if (rows.get(n).hasSavedFlag()) saved++;
        assertEquals((1 + saved) & 255, u(bank[0]), "Saved entry header independently follows complete source-prefix flags");
        int savedId = 0;
        for (; at <= 361; at++) {
            var r = rows.get(at); assertTrue(bound > r.centreX());
            if (r.hasSavedFlag()) { savedId = u(bank[0]); bank[0]++; }
            if ((r.periodFlags() & 0x20) == 0) continue;
            if (r.hasSavedFlag()) {
                int offset = 2 + 3 * savedId;
                assertTrue(offset >= 2 && offset < bank.length);
                boolean loaded = (bank[offset] & 0x80) != 0; bank[offset] |= (byte) 0x80;
                if (loaded) { assertNotEquals(361, at, "Source target must be genuinely newly admitted"); continue; }
            }
            int free = 0; while (free < 96 && images[free][0] != 0) free++;
            assertTrue(free < 96, "Actual native forward allocation must not fail before source row361");
            byte[] image = images[free]; putWord(image, 8, r.centreX()); putWord(image, 12, r.packedY() & 0xFFF);
            image[1] = image[0x22] = (byte) ((r.packedY() >>> 14) & 3);
            if (r.hasSavedFlag()) image[0x23] = (byte) savedId;
            image[0] = (byte) (r.encodedId() & 0x7F); image[0x28] = (byte) r.subtype(); image[0x29] = (byte) r.subtype2();
            if (at == 361) return new Spawned(32 + free, image.clone());
        }
        throw new AssertionError("Source row361 did not allocate");
    }

    /** Literal native semantics on expected bytes only. No object/animation/math production call. */
    private static byte[] nativeBody(byte[] initial, CdR11bAssets assets) {
        byte[] b = initial.clone(); assertEquals(0x3E, u(b[0]));
        if (u(b[0x24]) == 0) {
            b[0x24] += 2; b[1] |= 4; b[0x18] = 3; b[0x20] = 0x2A;
            b[0x17] = b[0x19] = b[0x16] = 0x10;
            putWord(b, 0x2A, word(b, 8)); putLong(b, 0x2C, (word(b, 12) << 16) | 0x8000);
            byte[] tileIds = assets.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes().copy();
            assertEquals(72, tileIds.length); assertEquals(0x26, word(tileIds, 2));
            int selected = (short) word(tileIds, 2) + u(b[0x29]) * 2;
            putWord(b, 2, word(tileIds, selected)); assertEquals(0x243F, word(b, 2));
            boolean damaged = b[0x28] != 0;
            putLong(b, 0x30, damaged ? -0x4000 : -0x8000); putWord(b, 0x36, damaged ? -0x100 : -0x200);
            putWord(b, 0x38, damaged ? 4 : 3); b[0x1C] = (byte) (damaged ? 1 : 0);
            putLong(b, 4, damaged ? 0x20B3A8 : 0x20B386);
        }
        int delta = longBits(b, 0x30); putLong(b, 8, longBits(b, 8) + delta);
        int distance = (short) u16(word(b, 8) - word(b, 0x2A));
        if (distance < 0) distance = (short) -distance;
        if (distance >= 0x80) {
            delta = -delta; putLong(b, 0x30, delta); putLong(b, 8, longBits(b, 8) + delta);
            b[1] ^= 1; b[0x22] ^= 1; putWord(b, 0x34, 0);
        }
        putWord(b, 0x34, word(b, 0x34) + (short) word(b, 0x36));
        byte[] sineTable = assets.view(CdR11bAssets.Asset.SINE_TABLE).bytes().copy(); assertEquals(640, sineTable.length);
        int sine = (short) word(sineTable, u(b[0x34]) * 2);
        int swapped = ((sine & 0xFFFF) << 16) | (delta >>> 16), shift = word(b, 0x38) & 63;
        putLong(b, 12, longBits(b, 0x2C) + (shift >= 32 ? swapped >> 31 : swapped >> shift));
        int animation = u(b[0x1C]);
        if (animation != u(b[0x1D])) { b[0x1D] = b[0x1C]; b[0x1B] = b[0x1E] = 0; }
        b[0x1E]--;
        if (b[0x1E] < 0) {
            int script = (short) word(ANIMATION, animation * 2); b[0x1E] = ANIMATION[script];
            int cursor = u(b[0x1B]), frame = u(ANIMATION[script + 1 + cursor]);
            if (frame == 255) { cursor = 0; b[0x1B] = 0; frame = u(ANIMATION[script + 1]); }
            assertTrue(frame < 128, "Only original Pata scripts' actual FF branch is in this bounded oracle");
            b[0x1A] = (byte) (frame & 31);
            int rotated = ((frame << 3) | (frame >>> 5)) & 255;
            b[1] = (byte) ((u(b[1]) & 0xFC) | ((rotated ^ u(b[0x22])) & 3));
            b[0x1B] = (byte) (cursor + 1);
        }
        b[1] &= 0x7F; // Genuine native DrawObject entry clear; tail sees the live anchor.
        return b;
    }

    /** Complete SAT expected from upstream control pieces plus literal Pata pieces; stale tail stays prior CPU. */
    private static void assertWholeCpu(Prepared p, CdObjectDrawQueue.CpuSat control,
                                        CdObjectDrawQueue.CpuSat prior, byte[] image) throws Exception {
        var actual = completedDraw(p); byte[] expected = prior.bytes(), source = control.bytes();
        var proof = new ArrayList<CdObjectDrawQueue.Provenance>(); int insertion = control.provenance().size(), ordinal = 0;
        for (int n = 0; n < control.provenance().size(); n++) {
            var item = control.provenance().get(n);
            if (item.queueBucket() > 3 && insertion == control.provenance().size()) insertion = n;
            if (item.queueBucket() == 3) ordinal = Math.max(ordinal, item.queueOrdinal() + 1);
        }
        byte[] raw = image[0x28] == 0 ? NORMAL : DAMAGED;
        int root = (short) word(raw, u(image[0x1A]) * 2), pieces = u(raw[root]);
        assertEquals(2, pieces, "These two short edges actually use native frame0 in both roots");
        int out = 0;
        for (int n = 0; n <= control.provenance().size(); n++) {
            if (n == insertion) for (int piece = 0; piece < pieces; piece++) {
                writeExpectedPiece(expected, out, raw, root + 1 + piece * 5, image, scroll(p));
                proof.add(new CdObjectDrawQueue.Provenance(3, ordinal, new CdObjectDrawQueue.CdSlotRef(40), piece)); out++;
            }
            if (n < control.provenance().size()) {
                System.arraycopy(source, n * 8, expected, out * 8, 8);
                proof.add(control.provenance().get(n)); out++;
            }
        }
        assertTrue(out < 80);
        for (int n = 0; n < out; n++) expected[n * 8 + 3] = (byte) (n + 1);
        Arrays.fill(expected, out * 8, out * 8 + 4, (byte) 0);
        assertEquals(out, actual.spriteCount()); assertEquals(proof, actual.provenance()); assertArrayEquals(expected, actual.bytes());
        assertArrayEquals(expected, bytes(actualField(actualField(p.visit(), "values"), "cpuSat")));
        assertPataPieces(p, 40, image, actual);
    }

    private static void assertPataPieces(Prepared p, int whole, byte[] image, CdObjectDrawQueue.CpuSat cpu) throws Exception {
        byte[] raw = image[0x28] == 0 ? NORMAL : DAMAGED;
        assertEquals(image[0x28] == 0 ? 0x20B386 : 0x20B3A8, longBits(image, 4));
        int root = (short) word(raw, u(image[0x1A]) * 2), pieces = u(raw[root]), matched = 0;
        for (int n = 0; n < cpu.provenance().size(); n++) {
            var item = cpu.provenance().get(n); if (item.slotRef().wholeIndex() != whole) continue;
            assertEquals(3, item.queueBucket()); assertEquals(matched, item.mappingPieceOrdinal());
            byte[] expected = new byte[640]; writeExpectedPiece(expected, n, raw, root + 1 + matched * 5, image, scroll(p));
            byte[] actual = cpu.bytes();
            for (int at : new int[]{0, 1, 2, 4, 5, 6, 7}) assertEquals(expected[n * 8 + at], actual[n * 8 + at], "Native SAT byte" + at);
            assertEquals(n == 79 ? 0 : n + 1, u(actual[n * 8 + 3]));
            matched++;
        }
        assertEquals((image[1] & 0x80) == 0 ? 0 : pieces, matched,
                "Actual native queue/drain, flips, palette1 and priority3 are CPU witnesses, not current-art pixels");
    }
    private static void writeExpectedPiece(byte[] sat, int index, byte[] raw, int at, byte[] image,
                                            SonicCdPlayerPresentation.ScrollCapture scroll) {
        int ry = raw[at], size = u(raw[at + 1]), rx = raw[at + 4], flags = u(image[1]);
        if ((flags & 1) != 0) rx = -rx - (((size >>> 2) & 3) + 1) * 8;
        if ((flags & 2) != 0) ry = -ry - ((size & 3) + 1) * 8;
        int y = wrappedY(word(image, 12), scroll.state().cameraYWord());
        int originX = u16(word(image, 8) - scroll.state().cameraX() + 128);
        int originY = u16(y - scroll.state().cameraYWord() + 128), out = index * 8;
        putWord(sat, out, originY + ry); sat[out + 2] = (byte) size; sat[out + 3] = (byte) (index == 79 ? 0 : index + 1);
        putWord(sat, out + 4, u16(word(raw, at + 2) + word(image, 2)) ^ ((flags & 3) << 11));
        int x = (originX + rx) & 0x1FF; putWord(sat, out + 6, x == 0 ? 1 : x);
    }

    private static boolean visible(byte[] image, int cameraX, int cameraY) {
        int dx = u16(word(image, 8) - cameraX), dy = u16(wrappedY(word(image, 12), cameraY) - cameraY);
        return (short) u16(dx + u(image[0x19])) >= 0 && (short) u16(dx - u(image[0x19])) < 320
                && (short) u16(dy + u(image[0x16])) >= 0 && (short) u16(dy - u(image[0x16])) < 224;
    }
    private static boolean anchorInside(byte[] image, int cameraX) {
        return u16((word(image, 0x2A) & 0xFF80) - (u16(cameraX - 0x80) & 0xFF80)) <= 0x280;
    }
    private static int wrappedY(int y, int cameraY) {
        if (cameraY < 0x100 && y >= 0x800) return u16(y - 0x800);
        if (cameraY >= 0x700 && y < 0x100) return u16(y + 0x800);
        return y;
    }
    private static SonicCdPlayerPresentation.ScrollCapture scroll(Prepared p) {
        return (SonicCdPlayerPresentation.ScrollCapture)
                p.harness().module().getScrollHandlerProvider().getHandler(0).captureRewindState();
    }
    private static List<String> calls(Prepared p) throws Exception { return callNames(actualField(p.visit(), "values")); }
    private static List<String> callNames(Object values) throws Exception {
        return ((List<?>) actualField(values, "calls")).stream().map(x -> ((Enum<?>) x).name()).toList();
    }
    private static long count(List<String> values, String name) { return values.stream().filter(name::equals).count(); }
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        return (CdObjectDrawQueue.CpuSat) ((Optional<?>) actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw")).orElseThrow();
    }
    private static byte[] bytes(Object value) { return assertInstanceOf(CdAssetBytes.class, value).copy(); }
    private static byte[][] images(CdObjectPool pool) {
        byte[][] out = new byte[96][]; for (int n = 0; n < 96; n++) out[n] = pool.state(32 + n).copyBytes(); return out;
    }
    private static void diagnose(Prepared p, Throwable first) throws Exception {
        System.out.println("PAST_PATA_RETAINED_PARTIAL7808 class=" + first.getClass().getName()
                + " boundary=" + (first instanceof SonicCdGameModule.UnsupportedPastLoading x ? x.boundary() : "OTHER")
                + " calls=" + calls(p));
        for (int whole : new int[]{62, 63, 64, 65})
            System.out.println("PAST_PATA_RETAINED_PARTIAL whole=" + whole
                    + " image=" + HexFormat.of().formatHex(p.placement().pool().state(whole).copyBytes()));
    }

    /** Includes checkpoint knowledge, complete SAT, all96/bank, captures, and opaque ownership identities. */
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
            assertArrayEquals(b.knownMask().copy(), ((CdCheckpointSaveData.State) actual).knownMask().copy(), name); assertEquals(b, actual, name);
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
                var read = component.getAccessor(); read.setAccessible(true);
                complete(read.invoke(expected), read.invoke(actual), name + '.' + component.getName());
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
    private static void ordered(List<String> calls, String... names) {
        int previous = -1;
        for (String name : names) { int at = calls.indexOf(name); assertTrue(at > previous, "Native order " + name + " in " + calls); previous = at; }
    }
    private static int u(byte value) { return Byte.toUnsignedInt(value); }
    private static int u16(int value) { return value & 0xFFFF; }
    private static int word(byte[] b, int at) { return (u(b[at]) << 8) | u(b[at + 1]); }
    private static int longBits(byte[] b, int at) { return (word(b, at) << 16) | word(b, at + 2); }
    private static void putWord(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void putLong(byte[] b, int at, int value) { putWord(b, at, value >>> 16); putWord(b, at + 2, value); }

    // Complete native includes, preserving the damaged root's real final EVEN byte.
    private static final byte[] ANIMATION = HexFormat.of().parseHex("0004000b070000010201ff030000010201ff");
    private static final byte[] NORMAL = HexFormat.of().parseHex("00060011001c02f0070000f0f00700080002f80d0010f0080000180001f00f0019f0");
    private static final byte[] DAMAGED = HexFormat.of().parseHex("00060011001c02f0070000f0f00700290002f80d0010f00800001800020000003100f00f0019f000");
}
