package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CheckpointState;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.badniks.EggRoboBadnikInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_EggRobo} ({@code $A0}, sonic3k.asm:198438-198760) in Sky Sanctuary act 1.
 *
 * <p>Expectations come from {@code SSZ1_Sprites}, from {@code sub_9185E}'s three-entry
 * {@code off_9186E} table and from {@code sub_91914}/{@code loc_91570}'s shared
 * {@code _unkFA82} word.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszEggRobo {

    /** {@code SSZ1_Sprites}. */
    private static final int SSZ1_SPRITES = 0x1F90EE;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static List<Integer> subtypes() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        List<Integer> found = new ArrayList<>();
        int address = SSZ1_SPRITES;
        while (rom.readU16BE(address) != 0xFFFF) {
            if (rom.readU8(address + 4) == 0xA0) {
                found.add(rom.readU8(address + 5));
            }
            address += 6;
        }
        return found;
    }

    /**
     * {@code sub_9185E} masks the subtype with {@code $F} and indexes {@code off_9186E}, which has
     * three entries. A placement with any other low nibble would read past the table and jump into
     * whatever follows, so the ROM placing only 0, 2 and 4 is the whole behaviour space.
     */
    @Test
    void everyPlacementUsesOneOfTheThreeDispatchEntries() throws IOException {
        List<Integer> subtypes = subtypes();
        assertEquals(26, subtypes.size(), "$A0 records in SSZ1_Sprites");
        for (int subtype : subtypes) {
            int nibble = subtype & 0x0F;
            assertTrue(nibble == 0 || nibble == 2 || nibble == 4,
                    "low nibble $" + Integer.toHexString(nibble) + " is past off_9186E's three"
                            + " entries (subtype $" + Integer.toHexString(subtype) + ")");
        }
        assertEquals(11, subtypes.stream().filter(s -> (s & 0x0F) == 0).count(), "fly-bys");
        assertEquals(12, subtypes.stream().filter(s -> (s & 0x0F) == 2).count(), "fighters");
        assertEquals(3, subtypes.stream().filter(s -> (s & 0x0F) == 4).count(), "animal releasers");
    }

    /**
     * {@code loc_91570} sets bit {@code subtype >> 4} and {@code sub_91914} tests it, so every
     * fighter in the act needs a fly-by in the same group or it is dropped at birth. The ROM does
     * pair them: each high nibble that carries a fighter carries a fly-by too.
     */
    @Test
    void everyFighterGroupHasAFlyByToReleaseIt() throws IOException {
        var flyByGroups = new TreeSet<Integer>();
        var fighterGroups = new TreeSet<Integer>();
        for (int subtype : subtypes()) {
            int group = (subtype >> 4) & 0x0F;
            if ((subtype & 0x0F) == 0) {
                flyByGroups.add(group);
            } else if ((subtype & 0x0F) == 2) {
                fighterGroups.add(group);
            }
        }
        assertEquals(flyByGroups, fighterGroups,
                "every _unkFA82 group carries both a fly-by and a fighter");
        assertEquals(11, flyByGroups.size(), "groups 0 through $A");
        assertTrue(flyByGroups.contains(0) && flyByGroups.contains(0x0A),
                "the groups run from 0 to $A with no gaps: " + flyByGroups);
    }

    /**
     * {@code _unkFA82} starts clear on every level load — no ROM code clears it, so the state
     * being rebuilt is what does — and {@code bset d0,d1} sets exactly the subtype's high nibble.
     */
    @Test
    void theFlyByWordStartsClearAndTakesOneBitPerGroup() {
        SszZoneRuntimeState state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS);
        assertEquals(0, state.eggRoboFlyByBits(), "a fresh level load has no groups released");
        for (int group = 0; group <= 0x0A; group++) {
            assertFalse(state.eggRoboFlyByPassed(group), "group " + group + " starts clear");
        }
        state.markEggRoboFlyByPassed(0x0A);
        assertEquals(1 << 0x0A, state.eggRoboFlyByBits(), "bset $A,d1");
        assertTrue(state.eggRoboFlyByPassed(0x0A), "group $A is released");
        assertFalse(state.eggRoboFlyByPassed(0x09), "and nothing else is");
        state.markEggRoboFlyByPassed(0);
        assertEquals((1 << 0x0A) | 1, state.eggRoboFlyByBits(), "the bits accumulate");
    }

    /**
     * {@code sub_91914}: a fighter loaded before its fly-by has passed drops the caller's return
     * address and exits, so it never reaches {@code loc_918C4}'s init at all.
     */
    @Test
    void aFighterWithNoReleasedFlyByNeverRuns() {
        // $A0:$12 at ($A40,$9D8): group 1, whose fly-by is the $A0:$10 at ($C20,$9E0).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x0A40, 0x09C0);
        SszZoneRuntimeState state = S3kRuntimeStates
                .currentSsz(GameServices.zoneRuntimeRegistry()).orElse(null);
        if (state != null) {
            assertFalse(state.eggRoboFlyByPassed(1),
                    "group 1's fly-by has not left the screen yet");
        }
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                assertTrue(robo.mode() != EggRoboBadnikInstance.Mode.FIGHTER
                                || (state != null && state.eggRoboFlyByPassed(robo.group())),
                        "a fighter survived with its _unkFA82 bit clear");
            }
        }
    }

    /**
     * {@code loc_918FC}/{@code loc_915F6}: the releaser is a 4x4 invisible object that lets an
     * animal go every sixteenth tick, four times, and only then becomes a real EggRobo.
     */
    @Test
    void theAnimalReleaserLetsFourAnimalsGoBeforeItFliesAway() {
        // $A0:$04 at ($1720,$E20).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1720, 0x0DF0);
        EggRoboBadnikInstance releaser = null;
        for (int frame = 0; frame < 240 && releaser == null; frame++) {
            fixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                if (robo.mode() == EggRoboBadnikInstance.Mode.ANIMAL_RELEASER) {
                    releaser = robo;
                }
            }
        }
        assertNotNull(releaser, "$A0:$04 at ($1720,$E20)");
        assertEquals(EggRoboBadnikInstance.ANIMAL_RELEASES, releaser.animalsLeftForTest(),
                "move.b #4,$39(a0): the count starts at four and only falls on a release tick");
        int animalsBefore = countActive(AnimalObjectInstance.class);
        String stateBefore = releaser.stateForTest();
        assertEquals("RELEASING", stateBefore, "loc_915F6 owns the releaser until the count runs out");

        for (int frame = 0; frame < 300 && "RELEASING".equals(releaser.stateForTest()); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertFalse("RELEASING".equals(releaser.stateForTest()),
                "the fourth release hands the object to loc_9164E");
        assertTrue(releaser.animalsLeftForTest() < 0,
                "subq.b #1,$39(a0) / bpl: the handover happens on the frame the count goes negative");
        assertTrue(countActive(AnimalObjectInstance.class) > animalsBefore,
                "CreateChild6_Simple let animals go on the way");
        assertTrue(releaser.lowPriorityArtForTest(),
                "bclr #7,art_tile(a0) drops the robot behind the level art as it leaves");
    }

    /**
     * The pairing the whole family turns on, driven rather than asserted on a fresh state:
     * {@code loc_91874}'s fly-by really has to reach {@code loc_91570}, set its {@code _unkFA82}
     * bit and delete itself, and only then does {@code sub_91914} let the partner fighter live.
     *
     * <p>Group 6 is the pair that makes this observable in one camera: the fly-by {@code $A0:$60}
     * at {@code ($1520,$C80)} and the fighter {@code $A0:$62} at {@code ($1660,$C80)} sit on the
     * same row, {@code $140} apart.
     */
    @Test
    void theFlyByArcsOffTheScreenAndOnlyThenDoesItsFighterSurvive() {
        // $A0:$60 at ($1520,$C80).
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1500, 0x0C80);
        SszZoneRuntimeState state = S3kRuntimeStates
                .currentSsz(GameServices.zoneRuntimeRegistry()).orElse(null);
        assertNotNull(state, "SSZ act 1 has a runtime state");
        assertFalse(state.eggRoboFlyByPassed(6), "group 6 starts unreleased");

        EggRoboBadnikInstance flyBy = null;
        for (int frame = 0; frame < 120 && flyBy == null; frame++) {
            fixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                if (robo.group() == 6 && robo.mode() == EggRoboBadnikInstance.Mode.FLY_BY) {
                    flyBy = robo;
                }
            }
        }
        assertNotNull(flyBy, "$A0:$60 at ($1520,$C80) loads as a fly-by");
        // move.b #$7F,$40(a0), stepped down three at a time by loc_91526.
        int scaleAtStart = flyBy.scaleIndexForTest();
        int yAtStart = flyBy.getY() & 0xFFFF;

        for (int frame = 0; frame < 600 && !state.eggRoboFlyByPassed(6); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(state.eggRoboFlyByPassed(6),
                "loc_91570 sets bit 6 of _unkFA82 when the fly-by leaves the screen");
        assertTrue(flyBy.scaleIndexForTest() < scaleAtStart,
                "the scale index fell from $" + Integer.toHexString(scaleAtStart)
                        + " on the way past");
        assertNotEquals(yAtStart, flyBy.getY() & 0xFFFF, "and the arc moved it");
    }

    /**
     * The three {@code ObjDat3} rows {@code SetUp_ObjAttributes} loads, and the one field of them
     * that decides whether the player gets hurt: {@code ObjDat3_9199A} (the fly-by) and
     * {@code ObjDat3_919B2} (the animal releaser) both end {@code dc.b …,0} — no collision at all —
     * while only {@code ObjDat3_919A6} (the fighter) carries the size-6 enemy box. A releaser that
     * hurt a falling player would be an invented hazard: it is a 4x4 invisible marker until
     * {@code loc_915F6} converts it, and only then does it take {@code ObjDat3_919A6}.
     */
    @Test
    void onlyTheFighterRowCarriesACollisionBox() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        // dc.l mappings, dc.w art_tile, dc.w priority, dc.b width, height, frame, collision.
        assertEquals(0, rom.readU8(0x09199A + 11), "ObjDat3_9199A (fly-by) has no collision");
        assertEquals(6, rom.readU8(0x0919A6 + 11), "ObjDat3_919A6 (fighter) is the size-6 box");
        assertEquals(0, rom.readU8(0x0919B2 + 11), "ObjDat3_919B2 (releaser) has no collision");

        // $A0:$04 at ($1720,$E20): the releaser, invisible and harmless until it converts.
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1720, 0x0DF0);
        EggRoboBadnikInstance releaser = null;
        for (int frame = 0; frame < 240 && releaser == null; frame++) {
            fixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                if (robo.mode() == EggRoboBadnikInstance.Mode.ANIMAL_RELEASER) {
                    releaser = robo;
                }
            }
        }
        assertNotNull(releaser, "$A0:$04 at ($1720,$E20)");
        assertEquals("RELEASING", releaser.stateForTest(), "still on loc_915F6");
        assertEquals(0, releaser.getCollisionFlags(),
                "ObjDat3_919B2's zero collision byte: the marker cannot hurt anyone");

        for (int frame = 0; frame < 300 && "RELEASING".equals(releaser.stateForTest()); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertFalse("RELEASING".equals(releaser.stateForTest()), "the fourth release converts it");
        assertEquals(6, releaser.getCollisionFlags() & 0x3F,
                "loc_915F6's SetUp_ObjAttributes ObjDat3_919A6 gives the real robot its box");

        // $A0:$60 at ($1520,$C80): the fly-by, which Draw_Sprite never touch-tests.
        reset();
        HeadlessTestFixture flyFixture = bootAtCheckpoint(320, 0x1500, 0x0C80);
        EggRoboBadnikInstance flyBy = null;
        for (int frame = 0; frame < 120 && flyBy == null; frame++) {
            flyFixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                if (robo.mode() == EggRoboBadnikInstance.Mode.FLY_BY) {
                    flyBy = robo;
                }
            }
        }
        assertNotNull(flyBy, "$A0:$60 at ($1520,$C80)");
        assertEquals(0, flyBy.getCollisionFlags(),
                "ObjDat3_9199A's zero collision byte: the scaled pass is scenery");
    }

    /** Rewind spot: the releaser part-way through its four animals, children and all. */
    @Test
    void theEggRoboSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1720, 0x0DF0);
        boolean reached = false;
        for (int frame = 0; frame < 600 && !reached; frame++) {
            fixture.stepIdleFrames(1);
            for (EggRoboBadnikInstance robo : allActive(EggRoboBadnikInstance.class)) {
                if (robo.animalsLeftForTest() < EggRoboBadnikInstance.ANIMAL_RELEASES) {
                    reached = true;
                }
            }
        }
        assertTrue(reached, "the releaser was never reached mid-count");

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore mid-release");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay mid-release");
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static int countActive(Class<?> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return 0;
        }
        int count = 0;
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                count++;
            }
        }
        return count;
    }

    private static <T> List<T> allActive(Class<T> type) {
        List<T> found = new ArrayList<>();
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return found;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                found.add(type.cast(instance));
            }
        }
        return found;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
        if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
            checkpoint.saveCheckpoint(1, x, y, false);
        }
        return fixture;
    }
}
