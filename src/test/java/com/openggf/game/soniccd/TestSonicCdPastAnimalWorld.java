package com.openggf.game.soniccd;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.SonicCdPastLevel;
import com.openggf.game.soniccd.objects.CdAnimal;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bSectionArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * External independent source author; ALL AUTHOR JAVA EXECUTION UNRUN.
 * J58c38a6296a3194fa4a8c0791085c446d35c70ae;
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff; shipped FixBugs=0.
 * ObjAnimal (Animal/Main.asm:7-19), CheckAnimalPrescence (Object Time Check.asm:55-84),
 * CheckObjDespawn (Object Spawner.asm:385-446), SpawnObjects_Main (:206-218 in the Java translation).
 * The actual outgoing Present DEV pair is carried as its existing value/provenance;
 * this is no claim that native IPX initialization executed. At raw Past0 only the
 * projector byte is reached; these witnesses do not claim goodFuture was prefetched.
 * Actual five-jump/load/7776 and single natural7808 are separate from the expressly
 * CONSTRUCTED closed-cut Animal40 scene. No Body/Frame/Solid/terrain/placement/
 * animation/draw producer replay or Result-effect replay supplies expected bytes.
 */
@Isolated
class TestSonicCdPastAnimalWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualOutgoingDevPairSurvivesTheReal7729RetirementWithoutChangingCaptureShape() throws Exception {
        var e = pendingEntry(); var p = enter(e);
        assertSame(e.retainedDev(), retained(p.visit()));
        assertEquals(e.retainedDev().goodFuture(), retained(p.visit()).goodFuture());
        assertEquals(e.retainedDev().projDestroyed(), retained(p.visit()).projDestroyed());
        assertEquals(e.clock(), p.frame().vIntClock());
        assertTrue(e.oldFrame().closed()); assertTrue(e.oldRuntime().closed());
        assertNotSame(e.oldFrame(), p.frame());
        assertEquals(SonicCdGameModule.PastLoadPhase.FIRST_PLC, p.harness().module().pastLoadState().phase());
        assertEquals(0x80, p.frame().environment().movement().gates().timeZone());
        assertEquals(17, nested(SonicCdGameModule.class, "PastCarry").getRecordComponents().length);
        assertEquals(10, nested(SonicCdPastLoadingVisit.class, "LoadingGlobals").getRecordComponents().length);
        assertEquals(17, nested(SonicCdPastLoadingVisit.class, "Values").getRecordComponents().length);
        assertEquals(7L, Arrays.stream(nested(SonicCdPastLoadingVisit.class, "Snapshot").getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).count());
        assertEquals(11, SonicCdGameModule.PastLoadState.class.getRecordComponents().length);
        assertEquals(1, SonicCdPastLoadingVisit.class.getDeclaredConstructors().length);
        assertEquals(15, SonicCdPastLoadingVisit.class.getDeclaredConstructors()[0].getParameterCount());
        var carryFields = nested(SonicCdGameModule.class, "PastCarry").getRecordComponents();
        assertEquals("retainedDev", carryFields[16].getName());
        assertEquals(CdOpeningObjectRuntime.DevGlobals.class, carryFields[16].getType());
        var calls = SonicCdGameModule.PastLoadCall.values();
        var oldNames = ("LOAD_LEVEL_PCM INIT_VDP INIT_CONTROLLERS NORMAL_LOAD LEVEL_SERVICE PROCESS_PLCS "
                + "PLAY_LEVEL_MUSIC IMMEDIATE_LEVEL IMMEDIATE_STANDARD CLEAR_FLOWERS CLEAR_OBJECTS CLEAR_MISC "
                + "CLEAR_CAMERA CLEAR_SCREEN PALETTE_SONIC LEVEL_SIZE LOAD_SAVED LEVEL_SCROLL BLOCKS_RAM LEVEL_LAYOUT "
                + "PALETTE_LEVEL SECTION_ART INITIAL_FOREGROUND INITIAL_BACKGROUND COLLISION_POINTER LOAD_PLAYER "
                + "RESERVED_HUD LIFE_ICON RESERVED_TIME_ICON BEGIN_MAIN UPDATE_ANIMATED_TILES FROM_WHITE_SERVICE "
                + "FROM_WHITE_COLOURS LEVEL_STARTED ORDINARY_PUBLICATION ORDINARY_DRAW_LEVEL ORDINARY_SLOW_PLC "
                + "UPDATE_HUD ORDINARY_SERVICE LEVEL_FRAME_INCREMENT SPAWN_OBJECTS PRIMARY_INIT PRIMARY_MAIN "
                + "RESERVED_OBJECTS RING_BODY DYNAMIC_OBJECTS SPRING_BODY PLATFORM_BODY TUNNEL_DOOR_PRIMARY_SOLID "
                + "TUNNEL_DOOR_SECONDARY_SOLID TUNNEL_DOOR_BODY MONITOR_SOLID MONITOR_FLOOR_ANGLE MONITOR_FM "
                + "TIME_POST_DIRECTION TIME_POST_SUB_CPU MONITOR_BODY TIME_POST_BODY CLEAR_DEMO_INDEX POST_OBJECT_GUARDS "
                + "DEAD_CAMERA_FREEZE DRAW_OBJECTS PALETTE_CYCLE UPDATE_SECTION PREPARE_PLCS UPDATE_GLOBAL_ANIMATIONS "
                + "ORDINARY_MAIN_RETURN FLOAT_BLOCK_SOLID FLOAT_BLOCK_BODY").split(" ");
        assertEquals(oldNames.length + 1, calls.length);
        for (int i = 0; i < oldNames.length; i++) assertEquals(oldNames[i], calls[i].name(), "Existing ordinal " + i);
        assertEquals("ANIMAL_PRESENCE", calls[oldNames.length].name());
        assertCachedAnimal(p, null);
    }

    @Test void realLoadingFadeAndFirstMainCopiesRetainTheSameOutgoingPairThrough7776() throws Exception {
        var e = pendingEntry(); var p = enter(e); var h = p.harness();
        var phases = EnumSet.of(h.module().pastLoadState().phase());
        var valuesSeen = new IdentityHashMap<Object, Boolean>(); var globalsSeen = new IdentityHashMap<Object, Boolean>();
        valuesSeen.put(actualField(p.visit(), "values"), true);
        globalsSeen.put(actualField(actualField(p.visit(), "values"), "globals"), true);
        for (int call = 7730; call <= 7776; call++) {
            step(h, route(call));
            assertFalse(p.frame().closed(), "Actual returned loading/Main cut " + call);
            assertRetained(p.visit(), e.retainedDev());
            var values = actualField(p.visit(), "values");
            valuesSeen.put(values, true); globalsSeen.put(actualField(values, "globals"), true);
            phases.add(h.module().pastLoadState().phase());
            assertSame(p.frame(), actualField(h.module(), "playerFrame"));
            assertSame(p.visit(), actualField(h.module(), "pastLoading"));
            assertSame(e.world(), h.mode().getWorldSession());
            assertEquals(e.generation() + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        }
        assertTrue(phases.containsAll(Set.of(SonicCdGameModule.PastLoadPhase.FIRST_PLC,
                SonicCdGameModule.PastLoadPhase.SECTION_PLC, SonicCdGameModule.PastLoadPhase.FROM_WHITE,
                SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL)));
        assertTrue(valuesSeen.size() > 1); assertTrue(globalsSeen.size() > 1);
        assertHealthy(p, 7776);
        var globals = actualField(actualField(p.visit(), "values"), "globals");
        assertEquals(OptionalInt.of(30), actualField(globals, "drownTimerWord"));
        assertTrue(assertInstanceOf(Optional.class, actualField(globals, "tail")).isPresent());
        assertTrue(callNames(actualField(p.visit(), "values")).contains("ORDINARY_MAIN_RETURN"));
        assertCachedAnimal(p, null);
    }

    @Test void fullAndCourse7729To7730ReplaysCompareCompleteNativeStateBeforeForward() throws Exception {
        var e = pendingEntry(); var p = enter(e); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        assertRetained(p.visit(), e.retainedDev());
        step(h, route(7730)); assertRetained(p.visit(), e.retainedDev());
        assertEquals(SonicCdGameModule.PastLoadPhase.SECTION_PLC, h.module().pastLoadState().phase());
        var after = captureNative(h);
        registry.restore(full);
        assertComplete(before, captureNative(h), "full.7729.beforeAnyForward");
        assertRetained(p.visit(), e.retainedDev());
        step(h, route(7730)); assertComplete(after, captureNative(h), "full.7730.after");
        assertRetained(p.visit(), e.retainedDev());
        registry.restoreCourse(course);
        assertComplete(before, captureNative(h), "course.7729.beforeAnyForward");
        assertRetained(p.visit(), e.retainedDev());
        step(h, route(7730)); assertComplete(after, captureNative(h), "course.7730.after");
        assertRetained(p.visit(), e.retainedDev());
        assertEquals(e.generation() + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(e.driver(), h.module().programServicesState().loads().installed().orElseThrow().driver());
    }

    @Test void constructedLoadingGlobalsCopiesPreserveRawPairAndRejectNull() throws Exception {
        // Pure immutable data only. NEVER install this record into a Visit/Frame/World.
        var type = nested(SonicCdPastLoadingVisit.class, "LoadingGlobals"); var components = type.getRecordComponents();
        assertEquals(List.of("live", "contact", "score", "drownTimerWord", "tail", "bonus", "layoutRam",
                "debugBlockWord", "pauseMusicBit", "retainedDev"), Arrays.stream(components).map(x -> x.getName()).toList());
        var ctor = type.getDeclaredConstructor(Arrays.stream(components).map(x -> x.getType()).toArray(Class<?>[]::new));
        ctor.setAccessible(true);
        var pair = new CdOpeningObjectRuntime.DevGlobals(0xA5, 0xFF);
        var live = CdCheckpointLiveAux.unknown();
        Object[] arguments = {live, new CdPlayerBody.ContactGlobals(0, 0, 0),
                new CdOpeningObjectRuntime.ScoreGlobals(123, 456, 0xA5), OptionalInt.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), pair};
        var original = ctor.newInstance(arguments);
        var copies = List.of(
                copy(original, "withLive", new Class<?>[]{CdCheckpointLiveAux.State.class}, CdCheckpointLiveAux.unknown()),
                copy(original, "withDrownTimer", new Class<?>[]{int.class}, 0xBEEF),
                copy(original, "clearBonus", new Class<?>[0]),
                copy(original, "clearDebugBlock", new Class<?>[0]),
                copy(original, "withLayout", new Class<?>[]{CdAssetBytes.class}, CdAssetBytes.copyOf(new byte[]{0x33})),
                copy(original, "withPauseMusicBit", new Class<?>[]{int.class}, 0x80),
                copy(original, "finishMainStores", new Class<?>[0]));
        for (var value : copies) {
            assertNotSame(original, value); assertSame(pair, actualField(value, "retainedDev"));
            var raw = assertInstanceOf(CdOpeningObjectRuntime.DevGlobals.class, actualField(value, "retainedDev"));
            assertEquals(0xA5, raw.goodFuture()); assertEquals(0xFF, raw.projDestroyed());
        }
        assertSame(pair, actualField(original, "retainedDev")); assertSame(live, actualField(original, "live"));
        arguments[9] = null;
        var rejected = assertThrowsExactly(InvocationTargetException.class, () -> ctor.newInstance(arguments));
        assertInstanceOf(NullPointerException.class, rejected.getCause());
    }

    @Test void natural7808OnceReturnsAnimalPresenceThenPreservesTheActualLaterTypedStop() throws Exception {
        var p = healthy7776(); var h = p.harness(); var f = p.frame(); var dev = retained(p.visit());
        for (int call = 7777; call <= 7807; call++) { step(h, route(call)); assertHealthy(p, call); assertRetained(p.visit(), dev); }
        var pool = p.placement().pool(); var beforeImages = images(pool);
        var lastClosed = captureNative(h); // Legal healthy7807 capture; never capture a failed owner.
        assertNotNull(lastClosed);
        var beforeValues = actualField(p.visit(), "values"); var beforeCalls = callNames(beforeValues);
        var oldCpu = completedDraw(p); var cpuBytes = actualField(beforeValues, "cpuSat");
        var beforeSection = assertInstanceOf(CdR11bSectionArt.State.class, actualField(beforeValues, "section"));
        var current = beforeSection.residency();
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) h.module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        int cameraX = scroll.state().cameraX();
        assertEquals(0, f.environment().movement().gates().timeZone());
        assertEquals(0, dev.projDestroyed(), "Actual outgoing DEV lineage, not an invented Past default");
        assertTrue((((0x1C2B & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF) <= 0x280,
                "Hard actual pre-scroll keep predicate; no coordinate fitting/retry");
        var row = p.assets().placements().records().get(359);
        assertEquals(0x1C2B, row.centreX()); assertEquals(0x0209, row.packedY());
        assertEquals(0x24, row.objectId()); assertEquals(0, row.subtype()); assertEquals(0, row.subtype2());
        assertEquals(0xA0, row.periodFlags()); assertFalse(row.hasSavedFlag());
        var source = f.stageSource(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var saved = actualField(p.visit(), "saved"); var frameOwner = actualField(f, "captureOwner");
        var poolOwner = actualField(pool, "owner"); var artOwner = actualField(f, "art");
        var presentation = actualField(h.module(), "playerPresentation");
        var cached = assertCachedAnimal(p, null);
        // EXACTLY ONE natural7808 attempt. Keep the first exception even if diagnostics fail.
        var failure = assertThrowsExactly(SonicCdGameModule.UnsupportedPastLoading.class, () -> {
            try { step(h, route(7808)); }
            catch (Throwable first) {
                try { diagnoseNatural(p, beforeImages, current, beforeCalls, 77L, 33, first); }
                catch (Throwable diagnostic) { if (diagnostic != first) first.addSuppressed(diagnostic); }
                throw first;
            }
        });
        // Only retained references/raw data below: no requirePastLoadingVisit,
        // module.pastLoadState, capture, restore, retry, extra service or input.
        assertEquals(SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS, failure.boundary());
        var afterValues = actualField(p.visit(), "values"); var calls = callNames(afterValues);
        int animal = calls.indexOf("ANIMAL_PRESENCE"); assertTrue(animal >= 0, () -> "Missing actual Animal return: " + calls);
        var prefix = calls.subList(0, animal);
        ordered(prefix, "ORDINARY_SERVICE", "LEVEL_FRAME_INCREMENT", "SPAWN_OBJECTS", "PRIMARY_MAIN",
                "RESERVED_OBJECTS", "RING_BODY", "FLOAT_BLOCK_SOLID", "FLOAT_BLOCK_BODY");
        assertEquals(4L, prefix.stream().filter("FLOAT_BLOCK_BODY"::equals).count());
        assertEquals(8L, prefix.stream().filter("FLOAT_BLOCK_SOLID"::equals).count());
        assertFalse(calls.contains("DYNAMIC_OBJECTS")); assertFalse(calls.contains("DRAW_OBJECTS"));
        assertFalse(calls.contains("ORDINARY_MAIN_RETURN"));
        assertTrue(calls.stream().filter(x -> x.startsWith("ANIMAL_")).allMatch("ANIMAL_PRESENCE"::equals));
        assertArrayEquals(authenticAnimalImage(), pool.state(62).copyBytes(), "Authentic row359 full64, no Init/flag/map writes");
        assertTrue(f.closed()); assertFalse(f.paused()); assertEquals(78L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(34, f.environment().globals().levelFrames()); assertEquals(8, f.environment().controls().rawHeld());
        assertRetained(p.visit(), dev); assertSame(cached, actualField(p.visit(), "animal"));
        var queue = assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue"));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, queue.phase()); assertNoPointer(queue, 62);
        assertSame(oldCpu, completedDraw(p)); assertSame(cpuBytes, actualField(afterValues, "cpuSat"));
        assertTrue(oldCpu.provenance().stream().noneMatch(x -> x.slotRef().wholeIndex() == 62));
        assertArrayEquals(oldCpu.bytes(), assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "displayedSat")).copy());
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, actualField(f, "pastSource")); assertSame(artOwner, actualField(f, "art"));
        assertSame(frameOwner, actualField(f, "captureOwner")); assertSame(poolOwner, actualField(pool, "owner"));
        assertSame(saved, actualField(p.visit(), "saved")); assertSame(p.assets(), actualField(p.visit(), "assets"));
        assertSame(scroll.state(), actualField(presentation, "scrollState"));
        assertSame(scroll.background(), actualField(presentation, "backgroundState"));
        assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertNull(actualField(p.visit(), "activeFloat")); assertNull(actualField(p.visit(), "activePrimary"));
        assertNull(actualField(p.visit(), "activeDrain")); assertFalse((boolean) actualField(f, "bodyEntitled"));
        assertNull(actualField(f, "pending"));
    }

    @Test void constructedHealthy7776AnimalPresenceReplays7777WithNoDrawOrAllocation() throws Exception {
        var p = healthy7776(); var h = p.harness(); var f = p.frame(); var placement = p.placement();
        var pool = placement.pool(); var dev = retained(p.visit()); var cached = assertCachedAnimal(p, null);
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) h.module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        var status = placement.status(); var bank = placement.capture().savedFlags();
        assertEquals(2, status.routine());
        assertEquals(scroll.state().cameraX() & 0xFF80, status.previousChunkWord(),
                "Hard fixed7776 predicate: next real prespawn must return without rewriting any pool image");
        assertEquals(0, f.environment().movement().gates().timeZone()); assertEquals(0, dev.projDestroyed());
        var actor = f.captureActor(); var shared = f.captureShared(); var values = actualField(p.visit(), "values");
        var sounds = actualField(p.visit(), "sounds"); var owner = actualField(pool, "owner");
        var source = f.stageSource(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        // CONSTRUCTED SCENE: the sole CLOSED_CUT ingress clears96 exactly once,
        // then writes one same-owner native40 image. No actor/stage/cursor/saved
        // mutation, spawner mute, default global, route search or later reseed.
        pool.clearDynamicSlots();
        pool.write(pool.state(40).moveByte(ID, 0x24).moveWord(X, scroll.state().cameraX())
                .moveWord(Y, f.state().motion().centreY16_16() >>> 16)
                .moveByte(ROUTINE, 0xFE).moveByte(SUBTYPE, 0x7F).moveByte(SPRITE_FLAGS, 0x80));
        byte[] expected = new byte[64]; expected[0] = 0x24; expected[1] = (byte) 0x80;
        putWord(expected, 8, scroll.state().cameraX()); putWord(expected, 12, f.state().motion().centreY16_16() >>> 16);
        expected[0x24] = (byte) 0xFE; expected[0x28] = 0x7F;
        assertConstructedImages(pool, expected);
        assertEquals(actor, f.captureActor()); assertEquals(shared, f.captureShared());
        assertSame(values, actualField(p.visit(), "values")); assertSame(sounds, actualField(p.visit(), "sounds"));
        assertEquals(status, placement.status()); assertArrayEquals(bank.copy(), placement.capture().savedFlags().copy());
        assertSame(owner, actualField(pool, "owner")); assertSame(source, f.stageSource());
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertRetained(p.visit(), dev);
        // Capture AFTER ingress; full/course restoration must work unaided.
        var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var before = captureNative(h);
        step(h, route(7777)); assertConstructedReturn(p, status, bank, expected, cached, dev);
        var after = captureNative(h);
        registry.restore(full); assertComplete(before, captureNative(h), "constructed.full.beforeAnyForward");
        assertHealthy(p, 7776); assertConstructedImages(pool, expected); assertRetained(p.visit(), dev);
        step(h, route(7777)); assertConstructedReturn(p, status, bank, expected, cached, dev);
        assertComplete(after, captureNative(h), "constructed.full.after7777");
        registry.restoreCourse(course); assertComplete(before, captureNative(h), "constructed.course.beforeAnyForward");
        assertHealthy(p, 7776); assertConstructedImages(pool, expected); assertRetained(p.visit(), dev);
        step(h, route(7777)); assertConstructedReturn(p, status, bank, expected, cached, dev);
        assertComplete(after, captureNative(h), "constructed.course.after7777");
        assertSame(owner, actualField(pool, "owner")); assertSame(source, f.stageSource());
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }

    record Entry(Harness harness, CdPlayerFrameRuntime oldFrame, CdOpeningObjectRuntime oldRuntime,
                 CdOpeningObjectRuntime.DevGlobals retainedDev, CdPlayerFrameRuntime.VIntClock clock,
                 CdAssetBytes bank, Object world, long generation, Object driver, Object initialization) { }

    private static Entry pendingEntry() throws Exception {
        var h = opening(); var m = h.module(); var oldFrame = m.requirePlayerFrame();
        var oldRuntime = assertInstanceOf(CdOpeningObjectRuntime.class, m.getGameService(NativeDynamicObjectPass.class));
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, m.levelProgramState().phase());
        assertFalse(oldFrame.closed()); assertFalse(oldRuntime.closed());
        var dev = oldRuntime.devGlobals(); assertSame(dev, actualField(oldRuntime, "devGlobals"));
        var services = m.programServicesState();
        return new Entry(h, oldFrame, oldRuntime, dev, oldFrame.vIntClock(), oldRuntime.placement().capture().savedFlags(),
                h.mode().getWorldSession(), h.mode().getLevelManager().getCompletedProductionLoadGeneration(),
                services.loads().installed().orElseThrow().driver(), services.initialization());
    }
    private static Prepared enter(Entry e) throws Exception {
        var h = e.harness(); step(h, route(7729)); var m = h.module();
        assertTrue(e.oldFrame().closed()); assertTrue(e.oldRuntime().closed());
        assertSame(e.world(), h.mode().getWorldSession());
        assertEquals(e.generation() + 1, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var level = assertInstanceOf(SonicCdPastLevel.class, h.mode().getLevelManager().getCurrentLevel());
        var frame = m.requirePlayerFrame(); var visit = actualField(m, "pastLoading");
        assertNotSame(e.oldFrame(), frame); assertTrue(frame.stageSource().ownsPast(level.assets()));
        assertSame(e.driver(), m.programServicesState().loads().installed().orElseThrow().driver());
        assertEquals(e.initialization(), m.programServicesState().initialization());
        assertEquals(SonicCdProgramLoadServices.LoadPrefix.MATCH_REUSED, m.programServicesState().loads().lastLoad().prefix());
        var placement = assertInstanceOf(CdObjectPlacementRuntime.class, actualField(visit, "placement"));
        assertRetained(visit, e.retainedDev());
        return new Prepared(h, visit, frame, placement, level.assets(), e.bank(), 7729);
    }
    private static Prepared healthy7776() throws Exception {
        var p = ready(); var dev = retained(p.visit());
        assertTerminal(p, before(p), 45L, 8, 0, "PAST_ANIMAL_HEALTHY7775");
        assertRetained(p.visit(), dev); assertHealthy(p, 7775);
        step(p.harness(), route(7776)); assertHealthy(p, 7776); assertRetained(p.visit(), dev); return p;
    }
    private static void assertHealthy(Prepared p, int call) throws Exception {
        var f = p.frame(); assertFalse(f.closed()); assertFalse(f.paused());
        assertSame(f, actualField(p.harness().module(), "playerFrame"));
        assertSame(p.visit(), actualField(p.harness().module(), "pastLoading"));
        assertEquals(call - 7730L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(call - 7774, f.environment().globals().levelFrames());
        assertEquals(8, f.environment().controls().rawHeld()); assertEquals(0, f.environment().movement().gates().timeZone());
        assertSame(p.assets(), actualField(p.visit(), "assets")); assertSame(p.placement(), actualField(p.visit(), "placement"));
        assertTrue(callNames(actualField(p.visit(), "values")).contains("ORDINARY_MAIN_RETURN"));
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeDrain"));
        assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(f, "bodyEntitled")); assertNull(actualField(f, "pending"));
    }
    private static CdOpeningObjectRuntime.DevGlobals retained(Object visit) throws Exception {
        return assertInstanceOf(CdOpeningObjectRuntime.DevGlobals.class,
                actualField(actualField(actualField(visit, "values"), "globals"), "retainedDev"));
    }
    private static void assertRetained(Object visit, CdOpeningObjectRuntime.DevGlobals expected) throws Exception {
        var actual = retained(visit); assertSame(expected, actual);
        assertEquals(expected.goodFuture(), actual.goodFuture()); assertEquals(expected.projDestroyed(), actual.projDestroyed());
    }
    private static Object assertCachedAnimal(Prepared p, Object expected) throws Exception {
        var field = p.visit().getClass().getDeclaredField("animal");
        assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
        var actual = assertInstanceOf(CdAnimal.class, actualField(p.visit(), "animal"));
        assertSame(p.placement(), actualField(actual, "runtime"));
        if (expected != null) assertSame(expected, actual);
        return actual;
    }
    private static void assertConstructedReturn(Prepared p, CdObjectPlacementRuntime.Status status, CdAssetBytes bank,
                                                byte[] expected, Object cached, CdOpeningObjectRuntime.DevGlobals dev) throws Exception {
        assertHealthy(p, 7777); assertRetained(p.visit(), dev); assertCachedAnimal(p, cached);
        assertConstructedImages(p.placement().pool(), expected);
        assertEquals(status, p.placement().status()); assertArrayEquals(bank.copy(), p.placement().capture().savedFlags().copy());
        var calls = callNames(actualField(p.visit(), "values"));
        assertEquals(1L, calls.stream().filter("ANIMAL_PRESENCE"::equals).count());
        ordered(calls, "SPAWN_OBJECTS", "PRIMARY_MAIN", "RESERVED_OBJECTS", "ANIMAL_PRESENCE", "DYNAMIC_OBJECTS",
                "DRAW_OBJECTS", "ORDINARY_MAIN_RETURN");
        assertFalse(calls.contains("RING_BODY")); assertFalse(calls.contains("FLOAT_BLOCK_BODY"));
        assertTrue(calls.stream().filter(x -> x.startsWith("ANIMAL_")).allMatch("ANIMAL_PRESENCE"::equals));
        assertTrue(completedDraw(p).provenance().stream().noneMatch(x -> x.slotRef().wholeIndex() == 40),
                "Presence return supplies no Animal Draw pointer or CPU sprite");
    }
    private static void assertConstructedImages(CdObjectPool pool, byte[] expected) {
        for (int whole = 32; whole < 128; whole++) assertArrayEquals(whole == 40 ? expected : new byte[64],
                pool.state(whole).copyBytes(), "Constructed exact full96 physical" + whole);
    }
    private static byte[] authenticAnimalImage() {
        byte[] image = new byte[64]; image[0] = 0x24; putWord(image, 8, 0x1C2B); putWord(image, 12, 0x0209); return image;
    }
    private static void assertNoPointer(CdObjectDrawQueue queue, int whole) throws Exception {
        var pointers = assertInstanceOf(CdObjectDrawQueue.CdSlotRef[][].class, actualField(queue, "pointers"));
        var counts = assertInstanceOf(int[].class, actualField(queue, "pointerByteCounts"));
        assertEquals(8, pointers.length); assertEquals(8, counts.length);
        for (int bucket = 0; bucket < 8; bucket++) {
            assertTrue(counts[bucket] >= 0 && counts[bucket] <= 0x7E && (counts[bucket] & 1) == 0);
            for (int ordinal = 0; ordinal < counts[bucket] / 2; ordinal++) {
                assertNotNull(pointers[bucket][ordinal]); assertNotEquals(whole, pointers[bucket][ordinal].wholeIndex());
            }
        }
    }
    private static byte[][] images(CdObjectPool pool) {
        byte[][] out = new byte[96][]; for (int i = 0; i < 96; i++) out[i] = pool.state(32 + i).copyBytes(); return out;
    }
    private static void diagnoseNatural(Prepared p, byte[][] before, CdR11bCameraArtResidency.State current,
                                        List<String> lastCalls, long lastClock, int lastMain, Throwable first) throws Exception {
        // Observations, never oracle/certificate. Raw partial state is explicitly
        // distinct from lastClosed7807; a diagnostic fault is suppressed on first.
        System.out.println("PAST_ANIMAL_LAST_CLOSED returned=7807 clock=" + lastClock + " main=" + lastMain + " calls=" + lastCalls);
        var values = actualField(p.visit(), "values"); var calls = callNames(values);
        System.out.println("PAST_ANIMAL_RAW_PARTIAL7808 firstClass=" + first.getClass().getName() + " firstMessage=" + first.getMessage()
                + " boundary=" + (first instanceof SonicCdGameModule.UnsupportedPastLoading x ? x.boundary() : "OTHER")
                + " calls=" + calls + " closed=" + p.frame().closed() + " clock=" + p.frame().vIntClock().absoluteValue()
                + " main=" + p.frame().environment().globals().levelFrames());
        int changed = 0;
        for (int i = 0; i < 96; i++) {
            byte[] after = p.placement().pool().state(32 + i).copyBytes();
            if (!Arrays.equals(before[i], after)) {
                changed++; System.out.println("PAST_ANIMAL_RAW_CHANGED whole=" + (32 + i) + " before=" + HexFormat.of().formatHex(before[i])
                        + " after=" + HexFormat.of().formatHex(after));
            }
        }
        System.out.println("PAST_ANIMAL_RAW_PARTIAL7808 full96Changed=" + changed);
        var afterCurrent = assertInstanceOf(CdR11bSectionArt.State.class, actualField(values, "section")).residency();
        for (var region : CdR11bCameraArtResidency.Region.values()) {
            int count = 0; var examples = new StringBuilder();
            for (int at = 0; at < region.byteLength(); at++) {
                var old = current.region(region).knownByte(at); var next = afterCurrent.region(region).knownByte(at);
                if (!old.equals(next)) { if (count < 32) examples.append(Integer.toHexString(region.destinationWord() + at)).append(':').append(old).append('>').append(next).append(' '); count++; }
            }
            System.out.println("PAST_ANIMAL_RAW_CURRENT region=" + region + " changed=" + count + " firstAtMost32=" + examples);
        }
    }
    private static List<String> callNames(Object values) throws Exception {
        var calls = assertInstanceOf(List.class, actualField(values, "calls"));
        return calls.stream().map(x -> ((Enum<?>) x).name()).toList();
    }
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        var optional = assertInstanceOf(Optional.class, actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw"));
        return assertInstanceOf(CdObjectDrawQueue.CpuSat.class, optional.orElseThrow());
    }
    private static Class<?> nested(Class<?> owner, String name) {
        return Arrays.stream(owner.getDeclaredClasses()).filter(x -> x.getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    private static Object copy(Object value, String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = value.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(value, arguments);
    }
    /** Complete checkpoint-aware comparison of the actual existing capture shapes. */
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
            assertEquals(b, actual, name);
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
                var accessor = component.getAccessor(); accessor.setAccessible(true);
                assertComplete(accessor.invoke(expected), accessor.invoke(actual), name + '.' + component.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); assertComplete(field.get(expected), field.get(actual), name + '.' + field.getName());
            }
        } else assertEquals(expected, actual, name);
    }
    private static void ordered(List<String> calls, String... names) {
        int previous = -1;
        for (String name : names) { int at = calls.indexOf(name); assertTrue(at > previous, "Native order: " + name + " in " + calls); previous = at; }
    }
    private static void putWord(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
}
