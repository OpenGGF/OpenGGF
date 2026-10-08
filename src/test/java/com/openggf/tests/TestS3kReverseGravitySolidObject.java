package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kInvisibleBlockObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.ObjectTerrainUtils;
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
 * Group H: a solid object caught from its other face under {@code Reverse_gravity_flag}.
 *
 * <p>Four ROM rows, one mirrored geometry:
 * <ul>
 *   <li>{@code SolidObject_cont} (sonic3k.asm:41403-41424) is {@code loc_1DFD6}
 *       (:41426-41440) plus one {@code neg.w d3} on the player-minus-object Y delta;
 *       everything else, the {@code default_y_radius}/{@code y_radius} pair included, is
 *       identical.</li>
 *   <li>{@code loc_1E0FC} (:41569-41574) negates the vertical separation before
 *       {@code sub.w d3,y_pos(a1)}.</li>
 *   <li>{@code loc_1E154} (:41606-41632) inserts {@code neg.w d3} and
 *       {@code addq.w #2,y_pos(a1)}, turning the landing write from {@code y - d3 + 3}
 *       into {@code y + d3 - 3}.</li>
 *   <li>{@code MvSonicOnPtfm} (:41648-41653) branches to {@code loc_1E1AA}/{@code loc_1E1F4}
 *       and carries the rider at {@code y_pos(a0) + d3 + y_radius(a1)}.</li>
 * </ul>
 *
 * <p><strong>Every inverted case is asserted against its own upright control, measured on
 * the same block in the same test.</strong> Both are expressed as an offset from the
 * block's centre, so the assertion is that the two are exact negations — it does not
 * restate the {@code d2}/{@code d3} constants the block itself chooses, and it would fail
 * for any partial mirror.
 *
 * <p>The block is placed in a clear column the test finds and then guards, because the
 * measured reverse-gravity corridor at x=$1ACC is a 64 px gap: a 38 px player plus a 16 px
 * block does not fit inside it with clearance on both faces.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravitySolidObject {

    /** Subtype 0: {@code halfWidth = 8}, {@code halfHeight = 8} — a 16x16 solid. */
    private static final int BLOCK_SUBTYPE = 0x00;
    private static final int BLOCK_HALF_HEIGHT = 8;
    private static final int PROBE_Y_RADIUS = 19;
    /** Player half-height plus block half-height plus room to fall onto either face. */
    private static final int REQUIRED_CLEAR_HALF_SPAN = 96;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void aSolidBlockIsCaughtFromItsOtherFaceUnderReverseGravity() {
        int[] site = findClearColumn();
        int blockX = site[0];
        int blockY = site[1];

        int uprightRest = landOnBlock(blockX, blockY, false);
        int invertedRest = landOnBlock(blockX, blockY, true);

        int uprightOffset = uprightRest - blockY;
        int invertedOffset = invertedRest - blockY;

        assertTrue(uprightOffset < 0,
                "control: upright, the player comes to rest above the block's centre "
                        + "(offset " + uprightOffset + ")");
        assertEquals(-uprightOffset, invertedOffset,
                "loc_1E154's neg.w d3 / addq.w #2 and MvSonicOnPtfm's loc_1E1F4 put the "
                        + "inverted player exactly as far below the block as the upright "
                        + "player rests above it");
    }

    /**
     * Drops the player onto the block and returns the centre Y it settles at. Inverted, the
     * player is started on the far side with the same positive {@code y_vel}, which
     * {@code MoveSprite_TestGravity} integrates upward (sonic3k.asm:36073-36078).
     */
    private int landOnBlock(int blockX, int blockY, boolean reverseGravity) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            ObjectManager objectManager = GameServices.level().getObjectManager();
            Sonic3kInvisibleBlockObjectInstance block = new Sonic3kInvisibleBlockObjectInstance(
                    new ObjectSpawn(blockX, blockY, 0x28, BLOCK_SUBTYPE, 0, false, 0));
            objectManager.addDynamicObject(block);

            AbstractPlayableSprite sprite = fixture.sprite();
            int approach = BLOCK_HALF_HEIGHT + PROBE_Y_RADIUS + 12;
            int startY = reverseGravity ? blockY + approach : blockY - approach;
            NativePositionOps.writeXPosResetSubpixel(sprite, blockX);
            NativePositionOps.writeYPosResetSubpixel(sprite, startY);
            sprite.setAir(true);
            sprite.setOnObject(false);
            sprite.setXSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            sprite.setYSpeed((short) 0x0400);
            fixture.camera().updatePosition(true);

            for (int frame = 0; frame < 8 && sprite.getAir(); frame++) {
                fixture.stepIdleFrames(1);
            }
            assertFalse(sprite.getAir(),
                    "the block must catch the player (reverseGravity=" + reverseGravity + ")");
            // One more frame so the rider is carried by MvSonicOnPtfm rather than only
            // snapped by loc_1E154: both rows must agree on the same resting place.
            fixture.stepIdleFrames(1);
            assertFalse(sprite.getAir(), "and keep holding it on the next frame");
            return sprite.getCentreY();
        } finally {
            GameServices.gameState().setReverseGravityActive(false);
            SessionManager.clear();
        }
    }

    /**
     * Break-the-fixture guard: find a column of Death Egg act 1 with no terrain within
     * {@link #REQUIRED_CLEAR_HALF_SPAN} either way, so the only solid the player can meet
     * is the block. Fails loudly rather than silently testing against a wall.
     */
    private int[] findClearColumn() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            for (int x = 0x0200; x <= 0x1800; x += 0x20) {
                for (int y = 0x0200; y <= 0x0600; y += 0x20) {
                    if (isClear(x, y)) {
                        return new int[] { x, y };
                    }
                }
            }
            throw new AssertionError(
                    "no clear column found in Death Egg act 1 for the solid-object fixture");
        } finally {
            SessionManager.clear();
        }
    }

    private boolean isClear(int x, int y) {
        return !ObjectTerrainUtils.checkFloorDist(x, y, PROBE_Y_RADIUS).hasCollision()
                && !ObjectTerrainUtils.checkCeilingDist(x, y, PROBE_Y_RADIUS).hasCollision()
                && ObjectTerrainUtils.checkFloorDist(
                        x, y + REQUIRED_CLEAR_HALF_SPAN - PROBE_Y_RADIUS, PROBE_Y_RADIUS)
                        .distance() > 0
                && ObjectTerrainUtils.checkCeilingDist(
                        x, y - REQUIRED_CLEAR_HALF_SPAN + PROBE_Y_RADIUS, PROBE_Y_RADIUS)
                        .distance() > 0;
    }
}
