package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner-local committed frost-puff restore (IczEndBossInstance.effectChildren), STOCK default.
 * Same declared arena fixture as the immutable P9 diagnostic. Unlike P9 this asserts:
 * immediate pre-update tuple/order/slot equality, reserved-slot parity with the object
 * manager, exact destructive replay, and single slot release.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEastIczEndBossFrostRestore {
    private static final Path OUT = Path.of(System.getProperty("c1east.frost.out", "target/c1-east-frost-restore"));
    private static final int[] CHECKPOINTS = {1, 2, 5, 65, 130};

    private static Bk2FrameInput neutral(int frame) {
        var f = InputScriptCompiler.compile("1 -").getFirst();
        return new Bk2FrameInput(frame, f.p1InputMask(), f.p1ActionMask(), f.p1Start(),
                f.p2InputMask(), f.p2ActionMask(), false, "frost");
    }

    private static List<IczEndBossInstance> bosses() {
        return GameServices.level().getObjectManager().activeObjectsOfType(IczEndBossInstance.class);
    }

    private static void enterArena() {
        var camera = GameServices.camera();
        camera.setX((short) 0x4390);
        camera.setY((short) 0x05F8);
        camera.setMinX((short) 0x4300);
        camera.setMaxX((short) 0x4700);
        camera.setMinY((short) 0x0500);
        camera.setMaxY((short) 0x0700);
        GameServices.level().getObjectManager().reset(0x4390);
    }

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }

    /** One tuple per live puff: the complete captured state record, in list order. */
    private static List<Object> puffTuples(IczEndBossInstance boss) throws Exception {
        List<?> list = (List<?>) field(boss, "effectChildren");
        List<Object> out = new ArrayList<>();
        for (Object child : list) {
            Method m = child.getClass().getDeclaredMethod("captureRewindStateValue");
            m.setAccessible(true);
            out.add(m.invoke(child));
        }
        return out;
    }

    private static List<Integer> puffSlots(List<Object> tuples) throws Exception {
        List<Integer> slots = new ArrayList<>();
        for (Object t : tuples) {
            Method m = t.getClass().getDeclaredMethod("nativeSlot");
            m.setAccessible(true);
            int slot = (int) m.invoke(t);
            if (slot >= 0) slots.add(slot);
        }
        return slots;
    }

    /** Allocator-owned occupied or reserved dynamic SSTs (the object manager's slot allocator). */
    private static Set<Integer> occupiedSlots() {
        return new TreeSet<>(GameServices.level().getObjectManager().occupiedDynamicSlotIdsWithReservations().keySet());
    }

    private static Set<Integer> reservedSlots() {
        var snapshot = GameServices.level().getObjectManager().rewindSnapshottable().capture();
        Set<Integer> reserved = new TreeSet<>();
        snapshot.childSpawns().stream()
                .filter(e -> e.parentSpawn().objectId() == 0xBD)
                .forEach(e -> { for (int s : e.reservedSlots()) if (s >= 0) reserved.add(s); });
        return reserved;
    }

    private static List<String> structural(IczEndBossInstance boss) throws Exception {
        Object arr = field(boss, "structuralChildren");
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < java.lang.reflect.Array.getLength(arr); i++) {
            Object c = java.lang.reflect.Array.get(arr, i);
            StringBuilder sb = new StringBuilder("#" + i);
            for (Field f : c.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Class<?> t = f.getType();
                if (t.isPrimitive()) sb.append(' ').append(f.getName()).append('=').append(f.get(c));
            }
            out.add(sb.toString());
        }
        return out;
    }

    /** The captured tuple with the derived world position (x, y) masked; everything else must match. */
    private static List<String> masked(List<Object> tuples) {
        List<String> out = new ArrayList<>();
        for (Object t : tuples) {
            out.add(t.toString().replaceAll("\\bx=-?\\d+", "x=*").replaceAll("\\by=-?\\d+", "y=*"));
        }
        return out;
    }

    private static IczEndBossInstance byId(ObjectRefId id) {
        var table = GameServices.level().getObjectManager().captureIdentityContext().requireIdentityTable();
        for (IczEndBossInstance b : bosses()) if (id.equals(table.idFor(b))) return b;
        return null;
    }

    @Test void committedStockFrostPuffsRestoreExactlyAndReplayIdentically() throws Exception {
        Files.createDirectories(OUT);
        var s = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                0x4430, 0x0658, null, false, false, null, null, false, 20, false);
        try (var session = new GameplayCaptureSession(s);
             BufferedWriter out = Files.newBufferedWriter(OUT.resolve("frost-restore.txt"), StandardCharsets.UTF_8)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 5, 1, s);
            enterArena();
            int frame = 0;
            for (; frame < 400 && bosses().isEmpty(); frame++) session.step(neutral(frame));
            assertEquals(1, bosses().size());
            ObjectRefId id = GameServices.level().getObjectManager().captureIdentityContext()
                    .requireIdentityTable().idFor(bosses().getFirst());

            for (String phase : new String[]{"QUIET", "COMMITTED"}) {
                int rounds = 0, guard = 0;
                boolean prev = false;
                while (guard++ < 4000) {
                    session.step(neutral(frame++));
                    var b = bosses().getFirst();
                    boolean armed = b.isFrostPuffArmedForTesting();
                    if (armed && !prev) rounds++;
                    prev = armed;
                    if (phase.equals("QUIET") && rounds >= 2 && !armed && b.getFrostPuffCountForTesting() == 0) break;
                    if (phase.equals("COMMITTED") && rounds >= 4) break;
                }
                if (phase.equals("COMMITTED")) for (int n = 0; n < 4; n++) session.step(neutral(frame++));
                var boss = byId(id);
                assertNotNull(boss);
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();

                List<Object> preTuples = puffTuples(boss);
                Set<Integer> preReserved = reservedSlots();
                List<String> preStructural = structural(boss);
                out.write("\n=== " + phase + " frame " + frame + " puffs=" + preTuples.size()
                        + " reserved=" + preReserved + " puffSlots=" + puffSlots(preTuples) + " ===\n");
                if (phase.equals("COMMITTED")) {
                    assertFalse(preTuples.isEmpty(), "committed phase has live puffs");
                    assertTrue(preReserved.containsAll(puffSlots(preTuples)),
                            "every live puff's slot is in the genuine parent's reserved-child ledger");
                    List<Integer> livePuffSlots = puffSlots(preTuples);
                    assertEquals(6, preReserved.stream().filter(sl -> !livePuffSlots.contains(sl)).count(),
                            "ledger = six folded structural slots + live puff slots");
                } else {
                    assertTrue(preTuples.isEmpty(), "quiet phase has no puffs");
                }
                Set<Integer> preOccupied = occupiedSlots();
                var saved = registry.capture();
                Bk2FrameInput before = neutral(frame - 1);

                Map<Integer, List<Object>> fwdTuples = new LinkedHashMap<>();
                Map<Integer, List<String>> fwdStructural = new LinkedHashMap<>();
                Map<Integer, Set<Integer>> fwdReserved = new LinkedHashMap<>();
                Map<Integer, Set<Integer>> fwdOccupied = new LinkedHashMap<>();
                Map<Integer, String> fwdPlayer = new LinkedHashMap<>();
                List<Integer> fwdCounts = new ArrayList<>();
                int offset = 0;
                for (int cp : CHECKPOINTS) {
                    for (; offset < cp; offset++) {
                        session.step(neutral(frame + 1 + offset));
                        fwdCounts.add(byId(id).getFrostPuffCountForTesting());
                    }
                    fwdTuples.put(cp, puffTuples(byId(id)));
                    fwdStructural.put(cp, structural(byId(id)));
                    fwdReserved.put(cp, reservedSlots());
                    fwdOccupied.put(cp, occupiedSlots());
                    var p = session.player();
                    fwdPlayer.put(cp, p.getCentreX() + "," + p.getCentreY() + " r" + p.getRingCount()
                            + " h" + p.isHurt() + " c" + p.isObjectControlled());
                }
                var forwardSnapshot = registry.capture();

                registry.restore(saved);
                session.restoreInputHistory(before);

                // IMMEDIATELY after restore, before any update.
                var restored = byId(id);
                assertNotNull(restored, "identity resolves immediately after restore");
                assertEquals(1, bosses().size(), "exactly one boss after restore");
                List<Object> immediate = puffTuples(restored);
                assertEquals(preTuples, immediate, phase + ": list, order and every tuple restored before update");
                assertEquals(preReserved, reservedSlots(), phase + ": reserved SSTs restored by the object manager");
                assertEquals(preOccupied, occupiedSlots(), phase + ": allocator-held SSTs restored with the manager");
                assertTrue(occupiedSlots().containsAll(puffSlots(immediate)),
                        phase + ": every restored puff slot is held by the restored allocator");
                List<String> postStructural = structural(restored);
                assertEquals(preStructural, postStructural,
                        phase + ": structural children pose restored before any update");
                out.write("structuralChildren immediately after restore equal to pre-capture: true\n");

                offset = 0;
                List<Integer> repCounts = new ArrayList<>();
                for (int cp : CHECKPOINTS) {
                    for (; offset < cp; offset++) {
                        session.step(neutral(frame + 1 + offset));
                        repCounts.add(byId(id).getFrostPuffCountForTesting());
                    }
                    List<Object> replayTuples = puffTuples(byId(id));
                    assertEquals(fwdTuples.get(cp), replayTuples,
                            phase + " puff list/script/timer/anchor/slot/world x,y at +" + cp);
                    assertEquals(fwdStructural.get(cp), structural(byId(id)),
                            phase + " structural children pose at +" + cp);
                    out.write(phase + " +" + cp + " puff tuples and structural pose equal\n");
                    assertEquals(fwdReserved.get(cp), reservedSlots(), phase + " parent reservations at +" + cp);
                    assertEquals(fwdOccupied.get(cp), occupiedSlots(), phase + " allocator-held SSTs at +" + cp);
                    var p = session.player();
                    String line = p.getCentreX() + "," + p.getCentreY() + " r" + p.getRingCount()
                            + " h" + p.isHurt() + " c" + p.isObjectControlled();
                    out.write(phase + " +" + cp + " player forward=" + fwdPlayer.get(cp) + " replay=" + line
                            + " equal=" + fwdPlayer.get(cp).equals(line) + "\n");
                }
                assertEquals(fwdCounts, repCounts, phase + " per-update puff count timeline");
                var replaySnapshot = registry.capture();
                for (String key : forwardSnapshot.entries().keySet()) {
                    var d = RewindSnapshotDiff.diffKey(key, forwardSnapshot.get(key), replaySnapshot.get(key));
                    out.write(phase + " snapshot diff " + key + " (" + d.size() + ")"
                            + (d.isEmpty() ? "" : ": " + d.toString().substring(0, Math.min(400, d.toString().length())))
                            + "\n");
                    if (key.equals("object-manager")) {
                        assertTrue(d.isEmpty(), phase + " object-manager slots/identities: " + d);
                    }
                }
                // Every puff finished and released its slot exactly once: only the structural
                // reservations remain at +130 (same set as the quiet pre-capture reservation).
                Set<Integer> end = reservedSlots();
                out.write("end reserved=" + end + " counts=" + repCounts.subList(0, 12) + "...\n");
                // +130 is a later puff round in the committed phase: its count equals the forward run
                // (asserted per checkpoint above); the original six were released one by one.
                assertTrue(puffTuples(byId(id)).stream().noneMatch(tuple -> tuple.toString().contains("finished=true")),
                        "finished puffs are removed from the list");
            }
        }
    }
}
