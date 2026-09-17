package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
import com.openggf.game.sonic3k.objects.HPZMasterEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.HpzCollapseBlockFragmentObjectInstance;
import com.openggf.game.sonic3k.objects.HpzKnucklesBossMusicObjectInstance;
import com.openggf.game.sonic3k.objects.HpzKnucklesCeilingExplosionObjectInstance;
import com.openggf.game.sonic3k.objects.HpzRobotnikShipObjectInstance;
import com.openggf.game.sonic3k.objects.HpzShipSparkOrbiterObjectInstance;
import com.openggf.game.sonic3k.objects.HpzTeleporterRouteHelperObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hidden Palace ($1601) Sonic/Tails Knuckles fight, Master Emerald theft, altar collapse and
 * teleporter ending ({@code CutsceneKnux_HPZ}, {@code loc_64C70}, {@code loc_64E88},
 * {@code loc_45BF4}) through the real level loop.
 *
 * <p>Frame intervals marked "ROM" were read from the Sonic + Tails complete-run recording
 * {@code s3k-sonic-tails-complete-emeralds/hpz22_2} (comparison only) and agree with the routine
 * timers in sonic3k.asm.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzKnucklesFightHeadless {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) {
                saved.put(key, config.getConfigValue(key));
            }
        }
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private HeadlessTestFixture boot(String sidekick, int x, int y) {
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
    }

    @Test
    void knucklesMovesIntoSlot45ThenLocksTheCameraBeforeTheFight() {
        HeadlessTestFixture fixture = boot("", 0x1080, 0x42C);
        var sonic = fixture.sprite();

        fixture.stepIdleFrames(1);
        CutsceneKnucklesHpzInstance knuckles = knuckles().orElseThrow();
        // CutsceneKnux_HPZ: Dynamic_object_RAM+object_size*45 is absolute SST slot 48.
        assertEquals(48, knuckles.getSlotIndex());
        assertTrue(knuckles.initializedForTest(), "the camera already sits inside word_63CEC");
        assertTrue(knuckles.mappingFrameForTest() >= 0xD8 && knuckles.mappingFrameForTest() <= 0xDB,
                "byte_66771 idle frames");
        assertEquals(8, knuckles.collisionPropertyForTest());

        // Pal_CutsceneKnux on line 2 with colour 6 forced to $088 (loc_63D5C).
        fixture.stepIdleFrames(1);
        Level level = GameServices.level().getCurrentLevel();
        Palette.Color expected = new Palette.Color();
        expected.fromSegaFormat(0x0088);
        Palette.Color colour6 = level.getPalette(1).getColor(6);
        assertEquals(expected.r, colour6.r);
        assertEquals(expected.g, colour6.g);
        assertEquals(expected.b, colour6.b);

        var music = active(HpzKnucklesBossMusicObjectInstance.class).orElseThrow();
        int frames = 0;
        while (!hpz().knucklesCutsceneFlag(0) && frames < 400) {
            fixture.stepFrame(false, false, false, (sonic.getCentreX() & 0xFFFF) < 0x1180, false);
            frames++;
        }
        assertTrue(hpz().knucklesCutsceneFlag(0), "loc_63DD4 sets _unkFAB8 bit 0");
        assertEquals(7, music.bitsForTest(), "music played and both camera ranges locked");
        assertEquals(0x10E0, GameServices.camera().getMinX() & 0xFFFF);
        assertEquals(0x10E0, GameServices.camera().getMaxX() & 0xFFFF);
        assertEquals(0x380, GameServices.camera().getMinY() & 0xFFFF);
        fixture.stepIdleFrames(1);
        assertFalse(active(HpzKnucklesBossMusicObjectInstance.class).isPresent());
    }

    @Test
    void eightHitsDefeatKnucklesAndStartTheRoomShake() {
        HeadlessTestFixture fixture = boot("", 0x1080, 0x42C);
        var sonic = fixture.sprite();
        int hits = 0;
        int lastHp = 8;
        for (int frame = 0; frame < 1200; frame++) {
            // Status_Invincible makes every loc_660BE contact a loc_6429E hit.
            if (sonic.getInvincibleFrames() < 100) {
                sonic.setInvincibleFrames(1200);
            }
            step(fixture, contactInput(sonic, knuckles().orElse(null), frame));
            CutsceneKnucklesHpzInstance knuckles = knuckles().orElse(null);
            if (knuckles != null && knuckles.collisionPropertyForTest() != lastHp) {
                assertEquals(lastHp - 1, knuckles.collisionPropertyForTest(), "one point per loc_6429E");
                lastHp = knuckles.collisionPropertyForTest();
                hits++;
            }
            if (knuckles != null && knuckles.routineForTest() == 0x1C) {
                break;
            }
        }
        CutsceneKnucklesHpzInstance knuckles = knuckles().orElseThrow();
        assertEquals(8, hits);
        assertEquals(0, knuckles.collisionPropertyForTest());
        assertEquals(0x1C, knuckles.routineForTest(), "loc_643A4 after the byte_6680A landing");
        assertEquals(2, knuckles.mappingSetForTest(), "loc_64310 switches to Map_HPZKnucklesGrab");
        assertTrue(hpz().screenShake().flag() < 0, "st (Screen_shake_flag).w");
        assertEquals(16, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(HpzKnucklesCeilingExplosionObjectInstance.class::isInstance).count());
    }

    @Test
    void rewindAcrossTheFightReplaysIdentically() {
        HeadlessTestFixture fixture = boot("", 0x1080, 0x42C);
        var registry = fixture.gameplayMode().getRewindRegistry();
        FightBot bot = new FightBot();
        CompositeSnapshot midFight = null;
        List<boolean[]> window = new ArrayList<>();
        for (int frame = 0; frame < 1500; frame++) {
            CutsceneKnucklesHpzInstance knuckles = knuckles().orElse(null);
            boolean[] input = bot.input(fixture.sprite(), knuckles, hpz(), frame);
            if (midFight == null && knuckles != null && knuckles.collisionPropertyForTest() == 5
                    && knuckles.routineForTest() == 0x16) {
                midFight = registry.capture();
            }
            if (midFight != null) {
                window.add(input);
            }
            step(fixture, input);
            if (window.size() == 120) {
                break;
            }
        }
        assertTrue(midFight != null && window.size() == 120, "the bot lands Knuckles' third hit");
        CompositeSnapshot expected = registry.capture();
        CutsceneKnucklesHpzInstance live = knuckles().orElseThrow();
        int routine = live.routineForTest();
        int x = live.getX();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(midFight);
            assertEquals(0x16, knuckles().orElseThrow().routineForTest());
            for (boolean[] input : window) {
                step(fixture, input);
            }
            assertSnapshotsEqual(expected, registry.capture(), "fight replay " + cycle);
            assertEquals(routine, knuckles().orElseThrow().routineForTest());
            assertEquals(x, knuckles().orElseThrow().getX());
        }
    }

    @Test
    void theftCollapseAndTeleporterEndingRunToSkySanctuary() {
        HeadlessTestFixture fixture = boot("", 0x1080, 0x42C);
        var registry = fixture.gameplayMode().getRewindRegistry();
        FightBot bot = new FightBot();
        Level level = GameServices.level().getCurrentLevel();
        int cameraReadyFrame = -1;
        int releaseFrame = -1;
        boolean shipTouchable = false;
        boolean orbitersSeen = false;
        boolean collapseWritten = false;
        int lowestFloorY = 0;
        int fragments = 0;
        boolean tiredSeen = false;
        boolean holdSeen = false;
        int beamReadyKnucklesX = -1;
        int beamReadyKnucklesY = -1;
        CompositeSnapshot endingHold = null;
        List<boolean[]> endingInputs = new ArrayList<>();
        CompositeSnapshot endingAfter = null;
        // Rewind spots across the parent-linked graph: crane and ship children, spark
        // chains and the altar beam carrying Knuckles.
        List<RewindWindow> windows = List.of(
                new RewindWindow("crane grab", () -> active(com.openggf.game.sonic3k.objects.HpzCraneEmeraldDebrisObjectInstance.class).isPresent()),
                new RewindWindow("spark chains", () -> active(HpzShipSparkOrbiterObjectInstance.class).map(o -> o.phaseForTest() >= 3).orElse(false)),
                new RewindWindow("altar beam", () -> active(com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance.class).isPresent()
                        && knuckles().isPresent()));
        int exitFrame = -1;
        for (int frame = 0; frame < 5200; frame++) {
            Optional<HpzZoneRuntimeState> state = S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry());
            if (state.isEmpty()) {
                exitFrame = frame;
                break;
            }
            HpzZoneRuntimeState hpz = state.get();
            AbstractPlayableSprite sonic = fixture.sprite();
            assertFalse(sonic.getDead(), "the scripted route must survive, frame " + frame);
            boolean[] input = bot.input(sonic, knuckles().orElse(null), hpz, frame);
            var helper = active(HpzTeleporterRouteHelperObjectInstance.class);
            if (endingHold == null && helper.isPresent() && helper.get().stageForTest(0) == 1) {
                endingHold = registry.capture();
            }
            if (endingHold != null && endingAfter == null) {
                endingInputs.add(input);
            }
            for (RewindWindow window : windows) {
                window.beforeStep(registry, input);
            }
            step(fixture, input);
            for (RewindWindow window : windows) {
                window.afterStep(fixture, registry);
            }
            if (endingHold != null && endingAfter == null && endingInputs.size() == 150) {
                endingAfter = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(endingHold);
                    for (boolean[] replayInput : endingInputs) {
                        step(fixture, replayInput);
                    }
                    assertSnapshotsEqual(endingAfter, registry.capture(), "ending replay " + cycle);
                }
            }

            if (cameraReadyFrame < 0 && hpz.knucklesCutsceneFlag(0) && knuckles().map(
                    k -> k.routineForTest() >= 0x24).orElse(false)) {
                cameraReadyFrame = frame;
                int cameraX = GameServices.camera().getX() & 0xFFFF;
                // addq.w #2 / cmpi.w #$1580 / blo: an odd start stops one pixel past $1580.
                assertTrue(cameraX == 0x1580 || cameraX == 0x1581, "loc_64D1A pans to $1580");
            }
            if (releaseFrame < 0 && hpz.knucklesCutsceneFlag(1)) {
                releaseFrame = frame;
            }
            var ship = active(HpzRobotnikShipObjectInstance.class);
            shipTouchable |= ship.map(s -> s.getX() >= 0x1890 && s.getCollisionProperty() != 0).orElse(false);
            orbitersSeen |= active(HpzShipSparkOrbiterObjectInstance.class).isPresent();
            if (!collapseWritten && Byte.toUnsignedInt(level.getMap().getValue(0, 0x30, 7)) == 0x61
                    && Byte.toUnsignedInt(level.getMap().getValue(0, 0x31, 7)) == 0x61) {
                collapseWritten = true;
                assertTrue(hpz.knucklesCutsceneFlag(5), "Events_fg_4 follows Knuckles' landing");
            }
            lowestFloorY = Math.max(lowestFloorY, sonic.getCentreY() & 0xFFFF);
            fragments = Math.max(fragments, (int) GameServices.level().getObjectManager().getActiveObjects()
                    .stream().filter(HpzCollapseBlockFragmentObjectInstance.class::isInstance).count());
            var k = knuckles();
            if (!tiredSeen && k.map(kk -> kk.routineForTest() == 0x56).orElse(false)) {
                tiredSeen = true;
                assertEquals(0x161C, k.get().getX());
                assertEquals(0x62C, k.get().getY());
                assertEquals(4, k.get().mappingSetForTest(), "Map_SSZKnucklesTired");
            }
            if (!holdSeen && helper.isPresent() && helper.get().stageForTest(0) >= 1) {
                holdSeen = true;
                assertEquals(0x1628, sonic.getCentreX() & 0xFFFF, "sub_45C8E holds Player 1 at $1628");
            }
            if (beamReadyKnucklesX < 0 && hpz.knucklesCutsceneFlag(7) && k.isPresent()) {
                beamReadyKnucklesX = k.get().getX();
                beamReadyKnucklesY = k.get().getY();
            }
        }
        assertTrue(cameraReadyFrame > 0 && releaseFrame > 0);
        // ROM: camera reaches $1580 on $2F2E; Knuckles releases the player on $3054 (294 frames).
        assertEquals(294, releaseFrame - cameraReadyFrame);
        assertTrue(shipTouchable, "loc_64F38 makes the ship touchable at X $1890");
        assertTrue(orbitersSeen, "loc_6531E spark chains");
        assertTrue(collapseWritten, "HPZ_ScreenEvent writes chunk $61 at FG row 7 columns $30/$31");
        assertTrue(lowestFloorY >= 0x64C, "the player falls through the collapse (ROM Y $64C)");
        // ChildObjDat_6664A asks for $20 pieces; CreateChild6_Simple stops at the first
        // AllocateObjectAfterCurrent failure, so the block's slot bounds the count.
        assertTrue(fragments > 0 && fragments <= 32, "ChildObjDat_6664A fragments: " + fragments);
        assertTrue(tiredSeen && holdSeen);
        assertEquals(0x15C0, beamReadyKnucklesX, "loc_64BC6 lands Knuckles on the pad");
        assertEquals(0x600, beamReadyKnucklesY);
        assertTrue(endingAfter != null, "the ending hold was rewound and replayed");
        for (RewindWindow window : windows) {
            assertTrue(window.done(), "rewind window " + window.name + " ran");
        }
        assertTrue(exitFrame > 0, "StartNewLevel ended Hidden Palace");
        assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct(), "StartNewLevel $A00");
        assertFalse(active(HPZMasterEmeraldObjectInstance.class).isPresent());
    }

    @Test
    void sonicAndTailsEndingMatchesTheRecordedTimers() {
        HeadlessTestFixture fixture = boot("tails", 0x16A0, 0x62C);
        var sonic = fixture.sprite();
        int shakeStart = -1;
        int walkFrame = -1;
        int padFrame = -1;
        int requestFrame = -1;
        int exitFrame = -1;
        for (int frame = 0; frame < 1400; frame++) {
            if (S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).isEmpty()) {
                exitFrame = frame;
                break;
            }
            var helper = active(HpzTeleporterRouteHelperObjectInstance.class).orElse(null);
            if (helper == null) {
                step(fixture, new boolean[5]);
                continue;
            }
            boolean left = helper.stageForTest(0) == 0;
            step(fixture, new boolean[]{false, false, left, false, false});
            // Each player's stage 2 rewrites the sequence and the $48 shake; the later one counts.
            if (walkFrame < 0 && helper.shakeForTest() == 0x47) {
                shakeStart = frame;
                assertEquals(0x1628, sonic.getCentreX() & 0xFFFF);
                assertEquals(0x1638, GameServices.sprites().getSidekicks().getFirst().getCentreX() & 0xFFFF,
                        "Player 2 is held $10 further right");
            }
            if (walkFrame < 0 && sonic.getXSpeed() == -0x100 && helper.stageForTest(0) == 4) {
                walkFrame = frame;
            }
            if (requestFrame < 0 && helper.exitRequestedForTest()) {
                requestFrame = frame;
            }
            if (padFrame < 0 && helper.stageForTest(0) == 5) {
                padFrame = frame;
                assertEquals(0x15C0, sonic.getCentreX() & 0xFFFF);
                assertTrue(sonic.isObjectControlled());
            }
        }
        assertTrue(shakeStart > 0 && walkFrame > 0 && padFrame > 0 && requestFrame > 0 && exitFrame > 0);
        // ROM: camera shake from $37A1, x_vel -$100 on $38B8 (279 frames later).
        assertEquals(279, walkFrame - shakeStart);
        // ROM: pad on $38E5 (Level_frame_counter $1ADC), StartNewLevel at $1B54 (120 frames).
        assertEquals(120, requestFrame - padFrame);
        assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
    }

    // ------------------------------------------------------------------ helpers

    private static void step(HeadlessTestFixture fixture, boolean[] input) {
        fixture.stepFrame(input[0], input[1], input[2], input[3], input[4]);
    }

    /** Walk into Knuckles, turning to face him. */
    private static boolean[] contactInput(AbstractPlayableSprite sonic, CutsceneKnucklesHpzInstance knuckles,
                                          int frame) {
        if (knuckles == null || knuckles.routineForTest() >= 0x18 || frame < 60) {
            return new boolean[5];
        }
        boolean right = knuckles.getX() > (sonic.getCentreX() & 0xFFFF);
        return new boolean[]{false, false, !right, right, false};
    }

    /**
     * A reactive player for the whole route without invincibility: jumps onto Knuckles when he
     * is open, dodges his jump, glide and spin dash, then walks through the cutscene.
     */
    private static final class FightBot {
        private boolean jumpHeld;

        boolean[] input(AbstractPlayableSprite sonic, CutsceneKnucklesHpzInstance knuckles,
                        HpzZoneRuntimeState hpz, int frame) {
            boolean right = false;
            boolean left = false;
            boolean jump = false;
            int px = sonic.getCentreX() & 0xFFFF;
            int flags = hpz == null ? 0 : hpz.knucklesCutsceneFlags();
            int r = knuckles == null ? -1 : knuckles.routineForTest();
            boolean grounded = !sonic.getAir();
            if (knuckles != null && r < 0x18 && (flags & 1) == 0) {
                right = px < 0x1120;
            } else if (knuckles != null && r < 0x18) {
                int dx = knuckles.getX() - px;
                int adx = Math.abs(dx);
                if (r == 0x16 || r == 0x08 || r == 0x0A) {
                    int cameraX = GameServices.camera().getX() & 0xFFFF;
                    int target = knuckles.getX() > cameraX + 0xA0 ? cameraX + 0x30 : cameraX + 0x110;
                    if (Math.abs(target - px) > 8) {
                        right = target > px;
                        left = target < px;
                    }
                } else if (r == 0x12) {
                    if (adx < 0x60) {
                        left = dx > 0;
                        right = dx < 0;
                    }
                } else if (r == 0x14) {
                    jump = grounded && adx < 0x48 && adx > 0x20;
                } else if (r == 0x0C) {
                    // stay on the ground under the glide
                } else if (grounded) {
                    if (adx < 0x28) {
                        left = dx > 0;
                        right = dx < 0;
                    } else if (adx < 0x48) {
                        jump = true;
                        right = dx > 0;
                        left = dx < 0;
                    } else {
                        right = dx > 0;
                        left = dx < 0;
                    }
                } else if (adx > 4) {
                    right = dx > 0;
                    left = dx < 0;
                }
                if (!grounded && jumpHeld) {
                    jump = true;
                }
            } else if (knuckles != null && r < 0x24) {
                right = px < 0x1260;
            } else if (knuckles != null && r < 0x32) {
                right = true;
                jump = grounded && (frame % 40) == 0 && px < 0x14C0;
            } else if (knuckles != null && r < 0x42) {
                right = px < 0x1860 && (flags & 2) != 0;
            } else if (knuckles != null && r < 0x56) {
                // the collapse and the punch
            } else if (hpz != null) {
                left = px > 0x1600;
                jump = px > 0x1690 && px < 0x16D8 && (grounded ? (frame % 20) == 0 : jumpHeld);
            }
            jumpHeld = jump;
            return new boolean[]{false, false, left, right, jump};
        }
    }

    /** Capture at the first frame a condition holds, then restore twice and replay 60 frames. */
    private static final class RewindWindow {
        private static final int LENGTH = 60;
        private final String name;
        private final java.util.function.BooleanSupplier trigger;
        private CompositeSnapshot start;
        private final List<boolean[]> inputs = new ArrayList<>();
        private boolean done;

        RewindWindow(String name, java.util.function.BooleanSupplier trigger) {
            this.name = name;
            this.trigger = trigger;
        }

        void beforeStep(com.openggf.game.rewind.RewindRegistry registry, boolean[] input) {
            if (done) {
                return;
            }
            if (start == null && trigger.getAsBoolean()) {
                start = registry.capture();
            }
            if (start != null) {
                inputs.add(input);
            }
        }

        void afterStep(HeadlessTestFixture fixture, com.openggf.game.rewind.RewindRegistry registry) {
            if (done || inputs.size() < LENGTH) {
                return;
            }
            done = true;
            CompositeSnapshot expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(start);
                for (boolean[] input : inputs) {
                    step(fixture, input);
                }
                assertSnapshotsEqual(expected, registry.capture(), name + " replay " + cycle);
            }
        }

        boolean done() {
            return done;
        }
    }

    private static Optional<CutsceneKnucklesHpzInstance> knuckles() {
        return active(CutsceneKnucklesHpzInstance.class);
    }

    private static <T> Optional<T> active(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance)
                .filter(object -> !object.isDestroyed())
                .map(type::cast)
                .findFirst();
    }

    private static HpzZoneRuntimeState hpz() {
        return S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static void assertSnapshotsEqual(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), boundary);
        List<String> differences = new ArrayList<>();
        for (String key : expected.entries().keySet()) {
            var keyDifferences = RewindSnapshotDiff.diffKey(key, canonical(expected.get(key)),
                    canonical(actual.get(key)));
            if (!keyDifferences.isEmpty()) {
                differences.add(key + ": " + keyDifferences);
            }
        }
        assertTrue(differences.isEmpty(), () -> boundary + " " + differences);
    }

    /**
     * The canonicalisation TestFbzSqueezeOrdinaryRoll uses (slot-keyed objects, no render
     * telemetry), plus dynamic objects keyed by slot: restore re-appends player-owned objects
     * such as the insta-shield, and execution follows slots rather than list order.
     */
    private static Object canonical(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof com.openggf.level.Pattern pattern) {
            byte[] pixels = new byte[com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM];
            pattern.copyInto(pixels, 0);
            return canonical(pixels);
        }
        if (value instanceof List<?> list) {
            if (!list.isEmpty() && list.getFirst()
                    instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry) {
                var bySlot = new java.util.TreeMap<String, Object>();
                for (Object item : list) {
                    var entry = (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry) item;
                    bySlot.put(String.format("%05d:%s", entry.slotIndex(), entry.className()), canonical(entry));
                }
                return bySlot;
            }
            if (!list.isEmpty() && list.getFirst()
                    instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) {
                var bySlot = new java.util.TreeMap<Integer, Object>();
                for (Object item : list) {
                    var entry = (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) item;
                    assertNull(bySlot.put(entry.slotIndex(), canonical(entry)), "duplicate captured slot identity");
                }
                return bySlot;
            }
            var result = new ArrayList<Object>(list.size());
            for (Object item : list) {
                result.add(canonical(item));
            }
            return result;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            var result = new java.util.LinkedHashMap<Object, Object>();
            map.forEach((key, item) -> result.put(canonical(key), canonical(item)));
            return result;
        }
        if (value instanceof java.util.Set<?> set) {
            var result = new java.util.LinkedHashSet<Object>();
            for (Object item : set) {
                result.add(canonical(item));
            }
            return result;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            var result = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                result.add(canonical(java.lang.reflect.Array.get(value, i)));
            }
            return result;
        }
        if (type.isRecord()) {
            try {
                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("recordType", type.getName());
                for (var component : type.getRecordComponents()) {
                    if (value instanceof com.openggf.game.rewind.snapshot.LevelSnapshot
                            && component.getName().equals("epochAtCapture")) {
                        continue;
                    }
                    if (value instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot
                            && (component.getName().equals("bucketsDirty")
                            || component.getName().equals("peakSlotCount"))) {
                        continue;
                    }
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    result.put(component.getName(), canonical(accessor.invoke(value)));
                }
                return result;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot compare snapshot record " + type.getName(), failure);
            }
        }
        return value;
    }
}
