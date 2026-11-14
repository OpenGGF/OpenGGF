package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dynamic-slot claims at level start against the native stage fixtures (comparison only). Level initialization
 * fills its fixed slots first, including Tidal Tempest's water surface written straight into the first dynamic slot
 * ({@code move.b #4,object_spawn_pool+obj.id}, r4/main.asm:249); the layout's first window is spawned afterwards by the
 * first main-loop iteration's {@code SpawnObjects} ({@code SpawnObjects_Init} falling into the forward walk, Object
 * Spawner.asm:26-107; r4/main.asm:382), and only then does {@code RunObjects} let the title card and ring groups place
 * their pieces. Slots decide same-frame order and Random draws, so they are compared exactly.
 */
class TestCdLevelStartSlots {
    private static final Pattern SLOT_DUMP = Pattern.compile("\"vfc\":(\\d+),\"event\":\"slot_dump\",\"slots\":\\[(.*)\\]\\}");
    private static final Pattern SLOT_ENTRY = Pattern.compile("\\[(\\d+),\"0x([0-9A-F]+)\"\\]");
    private static final Pattern APPEARED = Pattern.compile(
            "\"vfc\":(\\d+),\"event\":\"object_appeared\",\"slot\":(\\d+),\"object_type\":\"0x([0-9A-F]+)\"");

    @TempDir
    Path tempDir;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    /** Metallic Madness act 3: every dynamic slot after the load (levelFrames 1) holds native's object ID. */
    @Test
    void r83cLevelStartSlotsMatchNative() throws Exception {
        TreeSet<String> expected = nativeSlotDump(CdStagePayload.R83C, 1);
        TreeSet<String> actual = new TreeSet<>();
        CdFixtureReplay.replay(tempDir, CdStagePayload.R83C, 0, row -> {
            if (row == 0) actual.addAll(engineSlots());
        });
        assertFalse(expected.isEmpty(), "no native slot dump at levelFrames 1");
        assertEquals(expected, actual, "R83C dynamic slots (slot:ID) at levelFrames 1");
    }

    /**
     * Tidal Tempest act 1 bad future: the water surface holds slot 32 and the layout starts at 33 (native levelFrames 1
     * dump, slots 32-37 before the first Yago), and the bubble spawner's first bubble at levelFrames 347 takes free
     * slot 33 below the spawner, as natively (its slot decides when it draws its Random angle).
     */
    @Test
    void r41dWaterSurfaceSlotAndFirstBubbleMatchNative() throws Exception {
        TreeSet<String> nativeStart = new TreeSet<>();
        for (String s : nativeSlotDump(CdStagePayload.R41D, 1)) if (slotOf(s) <= 37) nativeStart.add(s);
        TreeSet<String> nativeBubbles = nativeAppearances(CdStagePayload.R41D, 347, 0x20);
        TreeSet<String> engineStart = new TreeSet<>();
        TreeSet<String> engineBubbles = new TreeSet<>();
        Map<ObjectInstance, Boolean> seen = new IdentityHashMap<>();
        CdFixtureReplay.replay(tempDir, CdStagePayload.R41D, 380, row -> {
            int levelFrame = GameServices.level().getFrameCounter();
            if (row == 0) {
                for (String s : engineSlots()) if (slotOf(s) <= 37) engineStart.add(s);
            }
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                if (!(o instanceof AbstractObjectInstance a) || a.isDestroyed() || a.getSlotIndex() < 32) continue;
                if (seen.put(o, Boolean.TRUE) == null && levelFrame == 347 && o.getSpawn().objectId() == 0x20) {
                    engineBubbles.add(String.format("%d:%02X", a.getSlotIndex(), 0x20));
                }
            }
        });
        assertEquals(nativeStart, engineStart, "R41D dynamic slots 32-37 (slot:ID) at levelFrames 1");
        assertFalse(nativeBubbles.isEmpty(), "no native bubble appearance at levelFrames 347");
        // The spawner ($20, a layout entry entering the window on the same frame) also appears at 347 in a later slot
        // (native 62); the engine's is three slots later because it still holds three Yago ($2F) that native has
        // deleted (TTZ object lane, see the SLOTS report). The bubble is the lowest $20 slot: FindObjSlot (Object
        // Spawner.asm:329-340) gives it the first free slot, below the spawner.
        assertEquals(nativeBubbles.first(), engineBubbles.isEmpty() ? "none" : engineBubbles.first(),
                "R41D first bubble at levelFrames 347 (slot:ID)");
    }

    /**
     * Tidal Tempest act 1 good future: the vent's bubbles ($20 below $80) take free slots below the spawner (native
     * slots 41 and 58 from levelFrames 641 and 656), so they first run a frame after they are made. The init's
     * {@code move.b #$84,obj.sprite_flags} keeps them until their own {@code DrawObject} has seen them off screen
     * (bubble_spawner.asm:26, :102-109): each native slot dump in the window holds as many $20 objects as the engine.
     */
    @Test
    void r41cVentBubblesLiveUntilTheirOwnDraw() throws Exception {
        Map<Integer, Integer> nativeCounts = new java.util.TreeMap<>();
        for (String line : Files.readAllLines(aux(CdStagePayload.R41C))) {
            Matcher m = SLOT_DUMP.matcher(line);
            if (!m.find()) continue;
            int levelFrame = Integer.parseInt(m.group(1));
            // To levelFrames 740: at row 750 native's player breathes the slot-58 bubble, a call the engine holds back
            // (the large bubble's breath, objects/r4/bubble_spawner.asm:75-96; see ObjBubble).
            if (levelFrame < 600 || levelFrame > 740 || nativeCounts.containsKey(levelFrame)) continue;
            int count = 0;
            Matcher e = SLOT_ENTRY.matcher(m.group(2));
            while (e.find()) {
                if (Integer.parseInt(e.group(1)) >= 32 && Integer.parseInt(e.group(2), 16) == 0x20) count++;
            }
            nativeCounts.put(levelFrame, count);
        }
        Map<Integer, Integer> engineCounts = new java.util.TreeMap<>();
        CdFixtureReplay.replay(tempDir, CdStagePayload.R41C, 822, row -> {
            int levelFrame = GameServices.level().getFrameCounter();
            if (!nativeCounts.containsKey(levelFrame) || engineCounts.containsKey(levelFrame)) return;
            int count = 0;
            for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
                if (o instanceof AbstractObjectInstance a && !a.isDestroyed() && a.getSlotIndex() >= 32
                        && o.getSpawn().objectId() == 0x20) count++;
            }
            engineCounts.put(levelFrame, count);
        });
        assertTrue(nativeCounts.values().stream().anyMatch(c -> c >= 3), "the native window holds two vent bubbles");
        assertEquals(nativeCounts, engineCounts, "R41C $20 objects (spawner and bubbles) per native slot dump");
    }

    private static int slotOf(String slotAndId) {
        return Integer.parseInt(slotAndId.substring(0, slotAndId.indexOf(':')));
    }

    private static TreeSet<String> engineSlots() {
        TreeSet<String> out = new TreeSet<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof AbstractObjectInstance a && !a.isDestroyed() && a.getSlotIndex() >= 32) {
                out.add(String.format("%d:%02X", a.getSlotIndex(), o.getSpawn().objectId()));
            }
        }
        return out;
    }

    private static Path aux(CdStagePayload payload) {
        return CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/trace/aux_state.jsonl");
    }

    /** The recorder's first {@code slot_dump} at {@code levelFrame}: occupied dynamic slots as "slot:ID". */
    private static TreeSet<String> nativeSlotDump(CdStagePayload payload, int levelFrame) throws Exception {
        TreeSet<String> out = new TreeSet<>();
        for (String line : Files.readAllLines(aux(payload))) {
            Matcher m = SLOT_DUMP.matcher(line);
            if (!m.find() || Integer.parseInt(m.group(1)) != levelFrame) continue;
            Matcher e = SLOT_ENTRY.matcher(m.group(2));
            while (e.find()) {
                int slot = Integer.parseInt(e.group(1));
                if (slot >= 32) out.add(String.format("%d:%02X", slot, Integer.parseInt(e.group(2), 16)));
            }
            break;
        }
        return out;
    }

    private static TreeSet<String> nativeAppearances(CdStagePayload payload, int levelFrame, int id) throws Exception {
        TreeSet<String> out = new TreeSet<>();
        for (String line : Files.readAllLines(aux(payload))) {
            Matcher m = APPEARED.matcher(line);
            if (m.find() && Integer.parseInt(m.group(1)) == levelFrame && Integer.parseInt(m.group(3), 16) == id) {
                out.add(String.format("%d:%02X", Integer.parseInt(m.group(2)), id));
            }
        }
        return out;
    }
}
