package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezTeleporterObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $59}, {@code Obj_DEZTeleporter} (sonic3k.asm:94913-95168).
 *
 * <p>Twenty-one of these in Death Egg act 2, in vertically paired columns whose two subtypes
 * carry opposite bit 7s. Each assertion below pins one ROM mechanism, and each was shown to
 * fail when that mechanism is removed.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezTeleporterHeadless {

    /** A real act 2 placement: the low half of the $1150 column, subtype $C5. */
    private static final int OBJECT_X = 0x1150;
    private static final int OBJECT_Y = 0x072C;

    /** ROM {@code move.w #$300,...} target reached in {@code $300 / 8} updates (:94993-94995). */
    private static final int SPIN_UPDATES = 0x300 / 8;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code loc_48C44} :94944-94952. {@code addq.w #3,d0}, then {@code cmpi.w #$10,d0 / bhs}.
     * Unflipped the window is {@code -3 <= dx <= $C}; it is {@code $10} px wide, and the
     * earlier note that {@code status} bit 0 "widens" it by {@code $A} is wrong — the extra
     * bias <em>moves</em> the same window to the object's other side.
     */
    @Test
    void theUnflippedCaptureWindowRunsFromMinusThreeToPlusTwelve() {
        assertTrue(capturesAt(0, -3), "dx = -3 is the inclusive left edge");
        assertTrue(capturesAt(0, 0x0C), "dx = $C is the inclusive right edge");
        assertFalse(capturesAt(0, -4), "dx = -4 is one past the left edge");
        assertFalse(capturesAt(0, 0x0D), "dx = $D is one past the right edge");
    }

    /** The same {@code $10} px window, mirrored: {@code -$D <= dx <= 2} (:94950-94951). */
    @Test
    void theXFlippedCaptureWindowIsTheSameWidthOnTheOtherSide() {
        assertTrue(capturesAt(1, -0x0D), "dx = -$D is the flipped left edge");
        assertTrue(capturesAt(1, 2), "dx = 2 is the flipped right edge");
        assertFalse(capturesAt(1, -0x0E), "one past the flipped left edge");
        assertFalse(capturesAt(1, 3), "one past the flipped right edge");
    }

    /** {@code addi.w #$20,d1 / cmpi.w #$40,d1} (:94955-94957): top inclusive, bottom exclusive. */
    @Test
    void theCaptureBandIsFortyPixelsTallCentredOnTheObject() {
        assertTrue(capturesAt(0, 0, -0x20), "dy = -$20 is inside");
        assertTrue(capturesAt(0, 0, 0x1F), "dy = $1F is the last row inside");
        assertFalse(capturesAt(0, 0, -0x21), "dy = -$21 is above the band");
        assertFalse(capturesAt(0, 0, 0x20), "dy = $20 is the exclusive bottom edge");
    }

    /** {@code btst #Status_InAir,status(a1) / bne} (:94962-94963). */
    @Test
    void anAirbornePlayerIsNotCaptured() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezTeleporterObjectInstance pad = place(0, 0xC5);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(true);
            pad.update(0, sprite);
            assertFalse(pad.isRidingForTest(true),
                    "the airborne refusal sits before the capture");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code movea.w interact(a1),a3 / cmpi.l #Obj_DEZTeleporter,(a3) / tst.b (a3,d0.w)}
     * (:94968-94973): the refusal is keyed on the <em>same</em> state-block slot of the other
     * teleporter.
     *
     * <p>The first version of this test could not fail. It captured the player on one
     * teleporter and immediately offered them to a second, but a captured player already has
     * {@code object_control} set, so the earlier refusal at :94958 answered first and removing
     * the interact check left the test green. The interact check only does work in the window
     * the ROM built it for: after {@code loc_48E2C} has cleared {@code object_control}
     * (:95105) and the player has landed, but before {@code loc_48E94} has seen them leave and
     * cleared the block. This version puts the player in exactly that state.
     */
    @Test
    void aPlayerWhoseRideJustEndedIsNotCapturedAgainByItsNeighbour() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            // Subtype $01: bit 7 clear and a one-frame budget, so the ride ends beside the
            // object instead of hundreds of pixels away.
            S3kDezTeleporterObjectInstance first = place(0, 0x01);
            S3kDezTeleporterObjectInstance second = place(0, 0x41);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            first.update(0, sprite);
            assertTrue(first.isRidingForTest(true), "precondition: the first one captured");
            for (int update = 0; update < SPIN_UPDATES + 8 && first.isRidingForTest(true);
                    update++) {
                first.update(0, sprite);
            }
            assertFalse(first.isRidingForTest(true), "precondition: the ride finished");
            assertFalse(sprite.isObjectControlled(),
                    "precondition: loc_48E2C cleared object_control, so :94958 no longer refuses");
            // And the player is back on their feet inside the neighbour's window.
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            sprite.setAir(false);
            assertFalse(sprite.getAir(), "precondition: the airborne refusal no longer applies");

            second.update(0, sprite);
            assertFalse(second.isRidingForTest(true),
                    "the neighbour must see the first one's still-live state block");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_48D2C} :94993-95006. {@code 4(a4)} climbs by 8 and the launch happens on the
     * update it reaches exactly {@code $300}: 96 updates, not the capture frame.
     */
    @Test
    void theLaunchWaitsForTheSpinRampToReachThreeHundred() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezTeleporterObjectInstance pad = place(0, 0xC5);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            pad.update(0, sprite);

            for (int update = 1; update < SPIN_UPDATES; update++) {
                pad.update(0, sprite);
                assertEquals(0, sprite.getYSpeed(),
                        "no launch velocity before the ramp reaches $300 (update " + update + ")");
            }
            pad.update(0, sprite);
            assertEquals(-0x1000, sprite.getYSpeed(),
                    "status bit 1 clear negates the $1000 (:95001-95005)");
            assertEquals(0xC5 & 0x7F, pad.remainingBudgetForTest(true),
                    "the subtype's low seven bits become the frame budget (:94997-94999)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_48DCA} :95065-95080. The write needs {@code 6(a4) == 8(a4)} — the midpoint of
     * the ride — and nothing before it moves the flag.
     */
    @Test
    void theFlagIsWrittenAtTheMidpointAndNotBefore() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezTeleporterObjectInstance pad = place(0, 0xC5);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            pad.update(0, sprite);
            for (int update = 0; update < SPIN_UPDATES; update++) {
                pad.update(0, sprite);
            }
            int budget = pad.remainingBudgetForTest(true);
            int half = budget >> 1;
            while (pad.remainingBudgetForTest(true) > half) {
                assertFalse(GameServices.gameState().isReverseGravityActive(),
                        "nothing before the midpoint writes the flag");
                pad.update(0, sprite);
            }
            assertEquals(half, pad.remainingBudgetForTest(true),
                    "the loop stopped on the update whose entry budget is the half budget");
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "and that update has not run yet");
            pad.update(0, sprite);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "subtype $C5 has bit 7 set, so the midpoint writes 1 (:95072-95080)");
        } finally {
            SessionManager.clear();
        }
    }

    /** The paired placement, subtype {@code $45}: bit 7 clear, so it writes 0. */
    @Test
    void thePairedSubtypeWritesZeroAtItsOwnMidpoint() {
        assertFalse(rideToMidpoint(0x45, true), "subtype $45 clears the flag");
        assertTrue(rideToMidpoint(0xC5, false), "subtype $C5 sets it");
    }

    /**
     * {@code cmpa.w #Player_1,a1 / bne} (:95069-95070). Player 2 rides the teleporter with the
     * same four routines and the same pose table, and never touches the flag.
     */
    @Test
    void playerTwoRidesButNeverWritesTheFlag() {
        HeadlessTestFixture fixture = fixtureWithSidekick();
        try {
            AbstractPlayableSprite sidekick = sidekick();
            org.junit.jupiter.api.Assumptions.assumeTrue(sidekick != null,
                    "this assertion needs a second player");
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezTeleporterObjectInstance pad = place(0, 0xC5);
            moveTo(sidekick, OBJECT_X, OBJECT_Y);
            sidekick.setAir(false);

            for (int update = 0; update <= SPIN_UPDATES + 0x45 + 4; update++) {
                pad.update(0, null);
            }
            assertTrue(pad.isRidingForTest(false) || pad.remainingBudgetForTest(false) < 0,
                    "precondition: the sidekick's own block ran");
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "only Player 1 reaches loc_48DF2");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The rewind spot: capture mid-ride, run past the midpoint write, restore and replay. A
     * restore that returned the global flag but not the rider's own budget would write the
     * flag on a different frame of the replay than the first run did.
     */
    @Test
    void theRideSurvivesACaptureAndRestore() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezTeleporterObjectInstance pad = place(0, 0xC5);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            pad.update(0, sprite);
            for (int update = 0; update < SPIN_UPDATES; update++) {
                pad.update(0, sprite);
            }
            int half = pad.remainingBudgetForTest(true) >> 1;
            while (pad.remainingBudgetForTest(true) > half + 3) {
                pad.update(0, sprite);
            }
            CompositeSnapshot midRide =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();
            int budgetAtCapture = pad.remainingBudgetForTest(true);

            for (int update = 0; update < 6; update++) {
                pad.update(0, sprite);
            }
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "the forward run must cross the midpoint before the restore");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(midRide);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "restore must return the pre-midpoint flag");
            S3kDezTeleporterObjectInstance restored = restored(pad);
            assertEquals(budgetAtCapture, restored.remainingBudgetForTest(true),
                    "and the rider's own remaining budget");

            for (int update = 0; update < 3; update++) {
                assertFalse(GameServices.gameState().isReverseGravityActive(),
                        "the replay still needs the same number of updates");
                restored.update(0, sprite);
            }
            restored.update(0, sprite);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "and writes on the same update of the replay as of the first run");
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void bossDefeatBlocksNewRidersForBothPlayersButLetsAnExistingRideFinish() {
        var fixture = fixtureWithSidekick();
        var player = fixture.sprite();
        var partner = sidekick();
        org.junit.jupiter.api.Assertions.assertNotNull(partner);
        var runtime = (com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState)
                GameServices.zoneRuntimeState();
        var pad = place(0, 0x01);
        moveTo(player, OBJECT_X, OBJECT_Y);
        moveTo(partner, OBJECT_X, OBJECT_Y);
        player.setAir(false);
        partner.setAir(false);
        runtime.setBossSignals(1);
        pad.update(0, player);
        assertFalse(pad.isRidingForTest(true));
        assertFalse(pad.isRidingForTest(false));
        runtime.setBossSignals(0);
        pad.update(1, player);
        assertTrue(pad.isRidingForTest(true));
        assertTrue(pad.isRidingForTest(false));
        runtime.setBossSignals(1);
        for (int i = 0; i < SPIN_UPDATES + 8; i++) pad.update(i + 2, player);
        assertFalse(pad.isRidingForTest(true));
        assertFalse(pad.isRidingForTest(false));
        assertFalse(player.isObjectControlled());
        assertFalse(partner.isObjectControlled());
    }

    // --- helpers ---

    private boolean rideToMidpoint(int subtype, boolean initialFlag) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(initialFlag);
            S3kDezTeleporterObjectInstance pad = place(0, subtype);
            moveTo(sprite, OBJECT_X, OBJECT_Y);
            pad.update(0, sprite);
            for (int update = 0; update < SPIN_UPDATES + (subtype & 0x7F) + 2; update++) {
                pad.update(0, sprite);
            }
            return GameServices.gameState().isReverseGravityActive();
        } finally {
            SessionManager.clear();
        }
    }

    private boolean capturesAt(int renderFlags, int dx) {
        return capturesAt(renderFlags, dx, 0);
    }

    private boolean capturesAt(int renderFlags, int dx, int dy) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezTeleporterObjectInstance pad = place(renderFlags, 0xC5);
            moveTo(sprite, OBJECT_X + dx, OBJECT_Y + dy);
            sprite.setAir(false);
            pad.update(0, sprite);
            return pad.isRidingForTest(true);
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezTeleporterObjectInstance place(int renderFlags, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x59, subtype, renderFlags,
                false, OBJECT_Y, -1);
        S3kDezTeleporterObjectInstance pad = new S3kDezTeleporterObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(pad);
        return pad;
    }

    private S3kDezTeleporterObjectInstance restored(S3kDezTeleporterObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezTeleporterObjectInstance pad) {
                return pad;
            }
        }
        return original;
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private AbstractPlayableSprite sidekick() {
        var sidekicks = GameServices.sprites().getSidekicks();
        return sidekicks.isEmpty() ? null : sidekicks.getFirst();
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }

    private HeadlessTestFixture fixtureWithSidekick() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }
}
