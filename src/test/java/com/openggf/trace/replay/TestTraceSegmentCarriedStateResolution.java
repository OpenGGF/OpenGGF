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
    void nativeContinuationWithoutRecordedBankDoesNotImportLiveRings() throws Exception {
        // loc_63C14 saves Act3_ring_count, loc_63B4 clears the live count,
        // and loc_59B1C consumes that separate bank at ScreenEvents
        // (sonic3k.asm:131250-131259, 7826-7833, 119410-119424).
        // The current recorder exports neither the bank nor Act3_flag. Its
        // PreLevel live count cannot distinguish this from an ordinary fresh
        // entry, so the importer must not infer a continuation from the zone.
        for (String run : List.of(SONIC_TAILS, TAILS)) {
            Path segment = TraceFixtureRoot.resolve(RUNS.resolve(run).resolve("hpz22"));
            assumeTrue(Files.isDirectory(segment), "fixture not present: " + segment);
            TraceMetadata meta = TraceMetadata.load(segment.resolve("metadata.json"));
            assertEquals(0, meta.lastStarPostHit());
            assertEquals(0, meta.levelEntryRam().get("special_bonus_entry_flag"));
            assertTrue(meta.levelEntryRam().get("ring_count") > 0, "live count discriminates");
            TraceSegmentCarriedState state = TraceSegmentCarriedStateImport.resolve(segment, meta);
            assertNull(state.entryRestore(), run + ": no Saved/Saved2 restore branch");
            assertNull(state.ringCount(), run + ": live Ring_count is not the missing Act3 bank");
        }
        assertNull(resolve(SONIC_TAILS, "mgz").ringCount(),
                "ordinary fresh entry also discards the previous zone's live rings");
    }

    @Test
    void checkpointReturnToTheSameBossActStillImportsItsRecordedSavedBank() throws Exception {
        // Same destination as hpz22, but a real checkpoint return. Destination
        // identity cannot stand in for the recorder-observed restore branch.
        TraceSegmentCarriedState sonicTails = resolve(SONIC_TAILS, "hpz22_2");
        assertEquals("Saved", sonicTails.entryRestore().block());
        assertEquals(335, sonicTails.ringCount());
        assertEquals(1, sonicTails.entryRestore().checkpoint().index());
        TraceSegmentCarriedState tails = resolve(TAILS, "hpz22_2");
        assertEquals("Saved", tails.entryRestore().block());
        assertEquals(68, tails.ringCount());
        assertEquals(2, tails.entryRestore().checkpoint().index());
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
    void recordedSpecialRingArrayReplacesTheTouchDerivation() throws Exception {
        // S+T aiz_4 follows three giant-ring-ended AIZ segments. The recorded
        // Collected_special_ring_array is imported as is; every touch the
        // whole-segment signature finds (ring and flash in two slots on one
        // frame) sets a bit the recording also holds.
        TraceSegmentCarriedState aiz4 = resolve(SONIC_TAILS, "aiz_4");
        assertTrue(aiz4.consumedGiantRings().isEmpty(), "derivation unused when recorded");
        int recorded = aiz4.collectedSpecialRingArray();
        for (String dir : List.of("aiz", "aiz_2", "aiz_3")) {
            Path segment = TraceFixtureRoot.resolve(RUNS.resolve(SONIC_TAILS).resolve(dir));
            List<TraceSegmentCarriedState.ConsumedGiantRing> touches =
                    TraceSegmentCarriedStateImport.recordedGiantRingTouches(segment, dir);
            assertTrue(touches.stream().anyMatch(t -> (recorded & (1 << (t.subtype() & 0x1F))) != 0),
                    dir + " touch not in recorded array " + Integer.toBinaryString(recorded));
        }
        assertEquals(0, resolve(SONIC_TAILS, "mgz").collectedSpecialRingArray(),
                "a new zone starts with a cleared array");
    }

    @Test
    void starPostBonusReturnCarriesTheRecordedStarPostByte() throws Exception {
        // Recorded at the segment's load (metadata.last_star_post_hit): the
        // return re-enters on post 6, which loc_2D008 initialises as hit.
        assertEquals(6, resolve(TAILS, "lrz_3").lastStarPostHit());
        assertEquals(6, resolve(KNUCKLES, "lbz_2").lastStarPostHit());
        assertEquals(0, resolve(SONIC_TAILS, "mgz").lastStarPostHit(), "start-table entry");
    }
    @Test
    void recordedEntryRamAgreesWithEveryDerivedValue() throws Exception {
        // Each derived value the recorder now samples directly must match it:
        // a disagreement means the derivation or the recording is wrong.
        List<String> all = new java.util.ArrayList<>();
        int checked = 0;
        for (String run : List.of(SONIC_TAILS, TAILS, KNUCKLES,
                "s3k-multibonus", "s3-knux-multibonus-ss")) {
            Path runDir = TraceFixtureRoot.resolve(RUNS.resolve(run));
            if (!Files.isDirectory(runDir)) {
                continue;
            }
            try (var dirs = Files.list(runDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                    Path metadata = dir.resolve("metadata.json");
                    if (!Files.exists(metadata)) {
                        continue;
                    }
                    TraceMetadata meta = TraceMetadata.load(metadata);
                    if (meta.levelEntryRam() == null) {
                        continue;
                    }
                    checked++;
                    TraceSegmentCarriedState state = TraceSegmentCarriedStateImport.resolve(dir, meta);
                    state.disagreements().forEach(d -> all.add(run + "/" + dir.getFileName() + ": " + d));
                }
            }
        }
        assumeTrue(checked > 0, "no fixture records level_entry_ram");
        assertEquals(List.of(), all);
    }

    @Test
    void starPostRestoreUsesTheRecordedSavedBlock() throws Exception {
        TraceSegmentCarriedState lrz3 = resolve(TAILS, "lrz_3");
        assertEquals("Saved", lrz3.entryRestore().block());
        assertEquals(304, lrz3.ringCount());
        assertEquals(6, lrz3.entryRestore().checkpoint().index());
        assertEquals(10814, lrz3.entryRestore().checkpoint().x());
        assertEquals(10644, lrz3.entryRestore().checkpoint().cameraX());
        // Saved_timer $00032815: 3:40, restored with Timer_frame 59 one second back.
        assertEquals((3 * 60 + 40) * 60 - 1, lrz3.entryRestore().timerFrames());
        TraceSegmentCarriedState hcz4 = resolve(SONIC_TAILS, "hcz_4");
        assertEquals("Saved2", hcz4.entryRestore().block(), "special-stage return");
        assertEquals(180, hcz4.ringCount());
        TraceSegmentCarriedState mgz = resolve(SONIC_TAILS, "mgz");
        assertNull(mgz.entryRestore(), "start-table entry restores nothing");
    }
}
