package com.openggf.trace.replay;

import com.openggf.game.sonic3k.specialstage.S3kSpecialStageSelection;
import com.openggf.tests.trace.TraceFixtureRoot;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Carried run state resolved from the committed S3K run manifests: every value
 * is a recorder observation or the ROM rule applied to one. ROM-free.
 */
class TestTraceSegmentCarriedStateResolution {

    private static final Path RUNS = Path.of("src/test/resources/traces/s3k/runs");
    private static final String SONIC_TAILS = "s3k-sonic-tails-complete-emeralds";
    private static final String TAILS = "s3k-tails-full-chain-all-emeralds";
    private static final String KNUCKLES = "s3k-knuckles-complete-superemeralds";

    private static TraceSegmentCarriedState resolve(String run, String dir) throws Exception {
        Path segment = TraceFixtureRoot.resolve(RUNS.resolve(run).resolve(dir));
        assumeTrue(Files.isDirectory(segment), "fixture not present: " + segment);
        TraceMetadata meta = TraceMetadata.load(segment.resolve("metadata.json"));
        return TraceSegmentCarriedStateImport.resolve(segment, meta);
    }

    private static int select(TraceSegmentCarriedState s) {
        return S3kSpecialStageSelection.selectStage(s.specialStageCursor(),
                s.superEmeraldStageSet(), s.emeraldStates(),
                s.chaosEmeraldCount(), s.superEmeraldCount());
    }

    @Test
    void returnSegmentCarriesTheManifestRingsAndChaosEmeralds() throws Exception {
        TraceSegmentCarriedState hcz4 = resolve(SONIC_TAILS, "hcz_4");
        assertEquals(180, hcz4.ringCount(), "ss_7 -> hcz_4 rings_after");
        assertEquals(7, hcz4.chaosEmeraldCount());
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1), hcz4.emeraldStates());
        assertEquals(0, hcz4.superEmeraldCount());
    }

    @Test
    void freshZoneSegmentCarriesEmeraldsButNoRings() throws Exception {
        TraceSegmentCarriedState mgz = resolve(SONIC_TAILS, "mgz");
        assertNull(mgz.ringCount(), "a fresh zone has no inbound ring record");
        assertEquals(7, mgz.chaosEmeraldCount(), "S+T MGZ 17383 giant ring sees seven");
    }

    @Test
    void bonusStageCarriesTheRingsItWasEnteredWith() throws Exception {
        assertEquals(146, resolve(SONIC_TAILS, "gumball").ringCount(), "icz -> gumball rings_before");
    }

    @Test
    void failedChaosStageIsSkippedByTheNextSearch() throws Exception {
        // Tails ss_5 (cursor 4) failed; ss_6 and ss_7 cleared 5 and 6. The
        // ss_8 arm samples Current_special_stage 0, and loc_861A's search from
        // 0 finds stage 4, the one still missing.
        TraceSegmentCarriedState ss8 = resolve(TAILS, "ss_8");
        assertEquals(0, ss8.specialStageCursor());
        assertFalse(ss8.superEmeraldStageSet());
        assertEquals(List.of(1, 1, 1, 1, 0, 1, 1), ss8.emeraldStates());
        assertEquals(4, select(ss8));
    }

    @Test
    void pedestalStagesAreTheSuperSetAtThePedestalSubtype() throws Exception {
        TraceSegmentCarriedState first = resolve(SONIC_TAILS, "ss_8");
        assertTrue(first.superEmeraldStageSet());
        assertEquals(List.of(2, 2, 2, 2, 2, 2, 2), first.emeraldStates(),
                "the first shrine visit placed every Chaos Emerald");
        assertEquals(0, select(first));

        // S+T ss_13 is armed on pedestal 6 after Super stages 0-4 cleared.
        TraceSegmentCarriedState ss13 = resolve(SONIC_TAILS, "ss_13");
        assertEquals(List.of(3, 3, 3, 3, 3, 2, 2), ss13.emeraldStates());
        assertEquals(5, ss13.superEmeraldCount());
        assertEquals(6, select(ss13));

        TraceSegmentCarriedState tailsSs9 = resolve(TAILS, "ss_9");
        assertTrue(tailsSs9.superEmeraldStageSet());
        assertEquals(6, select(tailsSs9));

        TraceSegmentCarriedState knucklesSs14 = resolve(KNUCKLES, "ss_14");
        assertEquals(6, select(knucklesSs14));
        assertEquals(6, knucklesSs14.superEmeraldCount());
    }

    @Test
    void shrineEntryBeforeAnySuperStageHoldsUnplacedChaosEmeralds() throws Exception {
        TraceSegmentCarriedState dez23 = resolve(TAILS, "dez23");
        assertEquals(7, dez23.chaosEmeraldCount());
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1), dez23.emeraldStates());
        TraceSegmentCarriedState dez232 = resolve(TAILS, "dez23_2");
        assertEquals(List.of(2, 2, 2, 2, 2, 2, 3), dez232.emeraldStates(),
                "pedestal 6 (ss_9) cleared");
    }

    @Test
    void everyGiantRingTouchOfTheZoneVisitIsACandidate() throws Exception {
        // Whole-segment scan: the ring and its flash share a position in two
        // SST slots on one frame after a touch. Other objects can share a
        // position too; importAtSegmentEntry keeps only candidates standing on
        // a giant-ring placement with the same subtype.
        TraceSegmentCarriedState aiz4 = resolve(SONIC_TAILS, "aiz_4");
        List<String> touches = aiz4.consumedGiantRings().stream()
                .map(r -> r.segmentDir() + ":" + r.x() + ":" + r.y() + ":" + r.subtype())
                .toList();
        assertTrue(touches.containsAll(List.of(
                "aiz_3:6584:1104:4", "aiz_2:576:976:3", "aiz:7112:1216:1")), touches.toString());
        // MHZ3 follows a shrine excursion: the MHZ2 ring that led there is carried.
        TraceSegmentCarriedState mhz3 = resolve(SONIC_TAILS, "mhz_3");
        assertTrue(mhz3.consumedGiantRings().stream()
                .anyMatch(r -> r.segmentDir().equals("mhz_2")
                        && r.x() == 0x2240 && r.y() == 0x0440 && r.subtype() == 3));
        TraceSegmentCarriedState mgz = resolve(SONIC_TAILS, "mgz");
        assertTrue(mgz.consumedGiantRings().isEmpty(), "a new zone starts with a cleared array");
    }
}
