package com.openggf.tests.trace.s1;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.ObjectOccupancyOracle;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Local comparison-only probe for the S1 LZ2 Obj64 frontier. This class is not
 * picked up by the default Surefire include pattern; run it explicitly while
 * diagnosing the LZ2 complete-run trace.
 */
@RequiresRom(SonicGame.SONIC_1)
class DebugS1Lz2BubblesOccupancyProbe {
    private static final Path TRACE_DIR =
            Path.of("src/test/resources/traces/s1/lz2_completerun");
    private static final int ZONE_LZ = 3;
    private static final int ACT_2 = 1;
    private static final int OBJ_BUBBLES = 0x64;
    private static final int FIRST_DYNAMIC_SLOT =
            ObjectSlotLayout.SONIC_1.firstDynamicSlot();

    @Test
    void measureObj64CountFrontier() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR),
                "Trace directory not found: " + TRACE_DIR);
        Assumptions.assumeTrue(Files.exists(TRACE_DIR.resolve("metadata.json")),
                "metadata.json not found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();
        Assumptions.assumeTrue("s1".equals(meta.game()),
                "Expected S1 metadata, got " + meta.game());

        Path bk2Path = resolveBk2File(TRACE_DIR, meta);
        Assumptions.assumeTrue(bk2Path != null,
                "No BK2 found for " + TRACE_DIR);

        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(SonicGame.SONIC_1, ZONE_LZ, ACT_2);
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                    .withRecording(bk2Path)
                    .withRecordingStartFrame(
                            TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(ZONE_LZ, ACT_2);
            }
            if (TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();
            TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);

            ObjectManager objectManager = GameServices.level() != null
                    ? GameServices.level().getObjectManager()
                    : null;
            Assumptions.assumeTrue(objectManager != null,
                    "ObjectManager unavailable after bootstrap");
            System.out.printf("[s1-lz2-obj64-count] policy objectPreludeFrames=%d startTraceIndex=%d%n",
                    TraceReplayBootstrap.levelObjectTitleCardPreludeFramesForTraceReplay(trace),
                    boot.replayStart().startingTraceIndex());
            System.out.println("[s1-lz2-obj64-count] post-bootstrap engine Obj64: "
                    + summarizeEngineObj64(objectManager));

            int startTraceIndex = boot.replayStart().startingTraceIndex();
            boolean objectSlotDivergenceReported = false;
            boolean slotDivergenceReported = false;
            boolean makerStateDivergenceReported = false;
            for (int i = startTraceIndex; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }
                if (!TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)) {
                    continue;
                }

                if (!objectSlotDivergenceReported) {
                    ObjectSlotDivergence objectSlotDivergence =
                            firstObjectSlotDivergence(trace, objectManager, i);
                    if (objectSlotDivergence != null) {
                        objectSlotDivergenceReported = true;
                        System.out.printf(
                                "[s1-lz2-all-slots] first divergence frame=%d slot=%d expected=%s actual=%s%n",
                                objectSlotDivergence.frame(),
                                objectSlotDivergence.slot(),
                                objectSlotDivergence.expected(),
                                objectSlotDivergence.actual());
                        int first = Math.max(FIRST_DYNAMIC_SLOT, objectSlotDivergence.slot() - 8);
                        int last = objectSlotDivergence.slot() + 12;
                        System.out.println("[s1-lz2-all-slots] expected: "
                                + summarizeExpectedSlots(trace, objectSlotDivergence.frame(), first, last));
                        System.out.println("[s1-lz2-all-slots] engine: "
                                + summarizeEngineSlots(objectManager, first, last));
                    }
                }

                if (!slotDivergenceReported) {
                    Obj64SlotDivergence slotDivergence =
                            firstObj64SlotDivergence(trace, objectManager, i);
                    if (slotDivergence != null) {
                        slotDivergenceReported = true;
                        System.out.printf(
                                "[s1-lz2-obj64-slots] first divergence frame=%d "
                                        + "expected=%s actual=%s%n",
                                slotDivergence.frame(),
                                slotDivergence.expectedSlots(),
                                slotDivergence.actualSlots());
                        System.out.println("[s1-lz2-obj64-slots] expected 70-90: "
                                + summarizeExpectedSlots(trace, slotDivergence.frame(), 70, 90));
                        System.out.println("[s1-lz2-obj64-slots] engine 70-90: "
                                + summarizeEngineSlots(objectManager, 70, 90));
                        System.out.println("[s1-lz2-rings] suspects: "
                                + summarizeRingState(GameServices.level().getRingManager(),
                                List.of(
                                        new RingSpawn(0x0278, 0x0468),
                                        new RingSpawn(0x0290, 0x0468),
                                        new RingSpawn(0x02A8, 0x0468),
                                        new RingSpawn(0x0278, 0x0480),
                                        new RingSpawn(0x0290, 0x0480),
                                        new RingSpawn(0x02A8, 0x0480))));
                        System.out.println("[s1-lz2-ring-spawns] "
                                + summarizeRingObjectSpawns(objectManager));
                        System.out.println("[s1-lz2-ring-live] "
                                + summarizeLiveRingObjects(objectManager));
                    }
                }

                if (!makerStateDivergenceReported) {
                    MakerStateDivergence stateDivergence =
                            firstMakerStateDivergence(trace, objectManager, i);
                    if (stateDivergence != null) {
                        makerStateDivergenceReported = true;
                        System.out.printf(
                                "[s1-lz2-obj64-state] first divergence frame=%d "
                                        + "maker=@%04X,%04X field=%s expected=%s actual=%s%n",
                                stateDivergence.frame(),
                                stateDivergence.x() & 0xFFFF,
                                stateDivergence.y() & 0xFFFF,
                                stateDivergence.field(),
                                stateDivergence.expected(),
                                stateDivergence.actual());
                        System.out.println("[s1-lz2-obj64-state] engine Obj64: "
                                + summarizeEngineObj64(objectManager));
                        System.out.println("[s1-lz2-obj64-state] ROM Obj64 prev: "
                                + summarizeRomObj64(trace, stateDivergence.frame() - 1));
                        System.out.println("[s1-lz2-obj64-state] ROM Obj64 curr: "
                                + summarizeRomObj64(trace, stateDivergence.frame()));
                    }
                }

                ObjectOccupancyOracle.CountDivergence divergence =
                        ObjectOccupancyOracle.firstTransientCountDivergence(
                                trace,
                                objectManager,
                                i,
                                FIRST_DYNAMIC_SLOT,
                                Set.of(OBJ_BUBBLES),
                                false);
                if (divergence != null) {
                    System.out.printf(
                            "[s1-lz2-obj64-count] first divergence frame=%d "
                                    + "id=0x%02X romCount=%d engineCount=%d%n",
                            divergence.frame(),
                            divergence.id(),
                            divergence.romCount(),
                            divergence.engineCount());
                    System.out.println("[s1-lz2-obj64-count] engine Obj64: "
                            + summarizeEngineObj64(objectManager));
                    System.out.println("[s1-lz2-obj64-count] ROM Obj64 prev: "
                            + summarizeRomObj64(trace, divergence.frame() - 1));
                    System.out.println("[s1-lz2-obj64-count] ROM Obj64 curr: "
                            + summarizeRomObj64(trace, divergence.frame()));
                    return;
                }
            }

            System.out.println("[s1-lz2-obj64-count] no Obj64 count divergence");
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    private record Obj64SlotDivergence(int frame, Set<Integer> expectedSlots,
                                       Set<Integer> actualSlots) {
    }

    private record ObjectSlotDivergence(int frame, int slot, String expected, String actual) {
    }

    private record MakerStateDivergence(int frame, int x, int y, String field,
                                        String expected, String actual) {
    }

    private record MakerState(int freq, int time, int prod, int type, int delay, int tableOffset) {
        String fieldValue(String field) {
            return switch (field) {
                case "freq" -> Integer.toString(freq);
                case "time" -> Integer.toString(time);
                case "prod" -> String.format("%02X", prod & 0xFF);
                case "type" -> Integer.toString(type);
                case "delay" -> Integer.toString(delay);
                case "tbl" -> String.format("%02X", tableOffset & 0xFF);
                default -> "";
            };
        }
    }

    private static MakerStateDivergence firstMakerStateDivergence(TraceData trace,
                                                                  ObjectManager objectManager,
                                                                  int frame) {
        if (!trace.metadata().hasPerFrameS1Obj64State()) {
            return null;
        }
        for (TraceEvent.S1Obj64State state : trace.s1Obj64StatesForFrame(frame)) {
            if ((state.routine() & 0xFF) != 0x0A) {
                continue;
            }
            MakerState expected = romMakerState(state);
            MakerState actual = engineMakerStateAt(objectManager, state.x(), state.y());
            if (actual == null) {
                return new MakerStateDivergence(frame, state.x(), state.y(),
                        "present", expected.toString(), "absent");
            }
            for (String field : List.of("freq", "time", "prod", "type", "delay", "tbl")) {
                String want = expected.fieldValue(field);
                String got = actual.fieldValue(field);
                if (!want.equals(got)) {
                    return new MakerStateDivergence(frame, state.x(), state.y(),
                            field, want, got);
                }
            }
        }
        return null;
    }

    private static Obj64SlotDivergence firstObj64SlotDivergence(TraceData trace,
                                                                ObjectManager objectManager,
                                                                int frame) {
        Set<Integer> expected = new java.util.TreeSet<>();
        for (Map.Entry<Integer, Integer> entry : ObjectOccupancyOracle
                .expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT)
                .entrySet()) {
            if ((entry.getValue() & 0xFF) == OBJ_BUBBLES) {
                expected.add(entry.getKey());
            }
        }

        Set<Integer> actual = new java.util.TreeSet<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object
                    && object.getSpawn() != null
                    && (object.getSpawn().objectId() & 0xFF) == OBJ_BUBBLES) {
                actual.add(object.getSlotIndex());
            }
        }

        return expected.equals(actual)
                ? null
                : new Obj64SlotDivergence(frame, expected, actual);
    }

    private static ObjectSlotDivergence firstObjectSlotDivergence(TraceData trace,
                                                                  ObjectManager objectManager,
                                                                  int frame) {
        Map<Integer, Integer> expected = new TreeMap<>(
                ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT));
        Map<Integer, Integer> actual = new TreeMap<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object
                    && object.getSpawn() != null
                    && object.getSlotIndex() >= FIRST_DYNAMIC_SLOT) {
                actual.put(object.getSlotIndex(), object.getSpawn().objectId() & 0xFF);
            }
        }
        int lastSlot = Math.max(
                expected.isEmpty() ? FIRST_DYNAMIC_SLOT : expected.keySet().stream().mapToInt(Integer::intValue).max().orElse(FIRST_DYNAMIC_SLOT),
                actual.isEmpty() ? FIRST_DYNAMIC_SLOT : actual.keySet().stream().mapToInt(Integer::intValue).max().orElse(FIRST_DYNAMIC_SLOT));
        for (int slot = FIRST_DYNAMIC_SLOT; slot <= lastSlot; slot++) {
            Integer want = expected.get(slot);
            Integer got = actual.get(slot);
            if (!java.util.Objects.equals(want, got)) {
                return new ObjectSlotDivergence(
                        frame,
                        slot,
                        want == null ? "--" : String.format("%02X", want & 0xFF),
                        got == null ? "--" : String.format("%02X", got & 0xFF));
            }
        }
        return null;
    }

    private static String summarizeExpectedSlots(TraceData trace, int frame,
                                                 int firstSlot, int lastSlotInclusive) {
        Map<Integer, Integer> expected = new TreeMap<>(
                ObjectOccupancyOracle.expectedOccupancy(trace, frame, FIRST_DYNAMIC_SLOT));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : expected.entrySet()) {
            int slot = entry.getKey();
            if (slot >= firstSlot && slot <= lastSlotInclusive) {
                parts.add(String.format("s%02d=%02X", slot, entry.getValue() & 0xFF));
            }
        }
        return String.join(" ", parts);
    }

    private static String summarizeEngineSlots(ObjectManager objectManager,
                                               int firstSlot, int lastSlotInclusive) {
        Map<Integer, Integer> actual = new TreeMap<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object
                    && object.getSpawn() != null
                    && object.getSlotIndex() >= firstSlot
                    && object.getSlotIndex() <= lastSlotInclusive) {
                actual.put(object.getSlotIndex(), object.getSpawn().objectId() & 0xFF);
            }
        }
        List<String> parts = new ArrayList<>();
        for (int slot : actual.keySet()) {
            AbstractObjectInstance object = objectAtSlot(objectManager, slot);
            parts.add(String.format("s%02d=%02X@%04X,%04X",
                    slot,
                    actual.get(slot) & 0xFF,
                    object != null ? object.getX() & 0xFFFF : 0,
                    object != null ? object.getY() & 0xFFFF : 0));
        }
        return String.join(" ", parts);
    }

    private static String summarizeRingState(RingManager ringManager, List<RingSpawn> rings) {
        if (ringManager == null) {
            return "ringManager=null";
        }
        List<String> parts = new ArrayList<>();
        for (RingSpawn ring : rings) {
            parts.add(String.format("@%04X,%04X collected=%s sparkle=%d",
                    ring.x() & 0xFFFF,
                    ring.y() & 0xFFFF,
                    ringManager.isCollected(ring),
                    ringManager.getSparkleStartFrame(ring)));
        }
        return String.join(" ", parts);
    }

    private static String summarizeRingObjectSpawns(ObjectManager objectManager) {
        Set<ObjectSpawn> activeSpawns = Set.copyOf(objectManager.getActiveSpawns());
        List<String> parts = new ArrayList<>();
        for (ObjectSpawn spawn : objectManager.getAllSpawns()) {
            if ((spawn.objectId() & 0xFF) != 0x25
                    || spawn.x() < 0x0260 || spawn.x() > 0x02B0
                    || spawn.y() < 0x0450 || spawn.y() > 0x0490) {
                continue;
            }
            var instance = objectManager.getActiveObjectForRewind(spawn);
            int slot = instance instanceof AbstractObjectInstance object ? object.getSlotIndex() : -1;
            parts.add(String.format("@%04X,%04X sub=%02X active=%s inst=%s slot=%d dorm=%s rem=%s ctr=%d",
                    spawn.x() & 0xFFFF,
                    spawn.y() & 0xFFFF,
                    spawn.subtype() & 0xFF,
                    activeSpawns.contains(spawn),
                    instance != null,
                    slot,
                    objectManager.isDormant(spawn),
                    objectManager.isRemembered(spawn),
                    objectManager.getSpawnCounter(spawn)));
        }
        return String.join(" | ", parts);
    }

    private static String summarizeLiveRingObjects(ObjectManager objectManager) {
        List<String> parts = new ArrayList<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance object)
                    || object.getSpawn() == null
                    || (object.getSpawn().objectId() & 0xFF) != 0x25
                    || object.getSpawn().x() < 0x0260 || object.getSpawn().x() > 0x02B0
                    || object.getSpawn().y() < 0x0450 || object.getSpawn().y() > 0x0490) {
                continue;
            }
            parts.add(String.format("s%02d @%04X,%04X spawn=@%04X,%04X %s",
                    object.getSlotIndex(),
                    object.getX() & 0xFFFF,
                    object.getY() & 0xFFFF,
                    object.getSpawn().x() & 0xFFFF,
                    object.getSpawn().y() & 0xFFFF,
                    object.traceDebugDetails()));
        }
        return String.join(" | ", parts);
    }

    private static AbstractObjectInstance objectAtSlot(ObjectManager objectManager, int slot) {
        for (var instance : objectManager.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object && object.getSlotIndex() == slot) {
                return object;
            }
        }
        return null;
    }

    private static MakerState romMakerState(TraceEvent.S1Obj64State state) {
        int obj34Byte = (state.objoff34() >>> 8) & 0xFF;
        int type = obj34Byte >= 0x80 ? obj34Byte - 0x100 : obj34Byte;
        int prodWord = state.objoff36() & 0xFFFF;
        int prod = ((prodWord >>> 8) & 0xC0) | (prodWord & 0x3F);
        int tableOffset = (int) ((state.objoff3c() & 0xFFFFFFFFL) - 0x00013020L);
        return new MakerState(
                state.objoff33() & 0xFF,
                state.objoff32() & 0xFF,
                prod,
                type,
                state.objoff38() & 0xFFFF,
                tableOffset);
    }

    private static MakerState engineMakerStateAt(ObjectManager objectManager, int x, int y) {
        for (var instance : objectManager.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance object)
                    || object.getSpawn() == null
                    || (object.getSpawn().objectId() & 0xFF) != OBJ_BUBBLES
                    || (object.getSpawn().x() & 0xFFFF) != (x & 0xFFFF)
                    || (object.getSpawn().y() & 0xFFFF) != (y & 0xFFFF)) {
                continue;
            }
            String details = object.traceDebugDetails();
            if (details == null || !details.startsWith("r=0A ")) {
                continue;
            }
            return new MakerState(
                    parseDetailInt(details, "freq", false),
                    parseDetailInt(details, "time", false),
                    parseDetailInt(details, "prod", true),
                    parseDetailInt(details, "type", false),
                    parseDetailInt(details, "delay", false),
                    parseDetailInt(details, "tbl", true));
        }
        return null;
    }

    private static int parseDetailInt(String details, String key, boolean hex) {
        String prefix = key + "=";
        for (String token : details.split(" ")) {
            if (token.startsWith(prefix)) {
                String value = token.substring(prefix.length());
                return Integer.parseInt(value, hex ? 16 : 10);
            }
        }
        throw new IllegalArgumentException("Missing " + key + " in " + details);
    }

    private static Path resolveBk2File(Path traceDir, TraceMetadata meta) throws Exception {
        if (meta.sourceBk2() != null && !meta.sourceBk2().isBlank()) {
            Path shared = traceDir.getParent().resolve("_movies").resolve(meta.sourceBk2());
            if (Files.exists(shared)) {
                return shared;
            }
        }
        try (var files = Files.list(traceDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static String summarizeEngineObj64(ObjectManager objectManager) {
        List<String> parts = new ArrayList<>();
        for (var instance : objectManager.getActiveObjects()) {
            if (!(instance instanceof AbstractObjectInstance object)
                    || object.getSpawn() == null
                    || (object.getSpawn().objectId() & 0xFF) != OBJ_BUBBLES) {
                continue;
            }
            String details = object.traceDebugDetails();
            parts.add(String.format(
                    "s%02d @%04X,%04X spawn=@%04X,%04X%s",
                    object.getSlotIndex(),
                    object.getX() & 0xFFFF,
                    object.getY() & 0xFFFF,
                    object.getSpawn().x() & 0xFFFF,
                    object.getSpawn().y() & 0xFFFF,
                    details == null || details.isBlank() ? "" : " " + details));
        }
        return String.join(" | ", parts);
    }

    private static String summarizeRomObj64(TraceData trace, int frame) {
        if (frame < 0 || !trace.metadata().hasPerFrameS1Obj64State()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (TraceEvent.S1Obj64State state : trace.s1Obj64StatesForFrame(frame)) {
            parts.add(String.format(
                    "f%d s%02d @%04X,%04X r=%02X status=%02X render=%02X "
                            + "sub=%02X anim=%02X obj32=%02X obj33=%02X "
                            + "obj34=%04X obj36=%04X obj38=%04X obj3c=%08X",
                    frame,
                    state.slot(),
                    state.x() & 0xFFFF,
                    state.y() & 0xFFFF,
                    state.routine() & 0xFF,
                    state.status() & 0xFF,
                    state.renderFlags() & 0xFF,
                    state.subtype() & 0xFF,
                    state.anim() & 0xFF,
                    state.objoff32() & 0xFF,
                    state.objoff33() & 0xFF,
                    state.objoff34() & 0xFFFF,
                    state.objoff36() & 0xFFFF,
                    state.objoff38() & 0xFFFF,
                    state.objoff3c() & 0xFFFFFFFFL));
        }
        return String.join(" | ", parts);
    }
}
