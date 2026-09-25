package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravityTubeObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_DEZGravityTube} ({@code $5A}) driven through the <em>production loop</em> rather
 * than by calling {@code update()} directly.
 *
 * <p>Every assertion in {@link TestS3kDezGravityTubeHeadless} drives the object's
 * {@code update()} itself, so none of them sees the engine's own per-frame bookkeeping between
 * object updates — the solid-contact controller's end-of-frame support sweep, the player's
 * movement tail, and the next frame's object pass. A capture beside the co-located
 * {@code $5B}/{@code $5A} pair at {@code $1A40,$08C0} showed the rider's {@code air}
 * alternating 1/0 with {@code y} pinned, and it was invisible to all 41 of those focused
 * tests. This class is the regression that sees it, on the act's own placement rather than a
 * test-constructed spawn.
 *
 * <p><b>The ROM invariant it asserts.</b> {@code Player_AnglePos} (sonic3k.asm:18735-18741)
 * begins {@code btst #Status_OnObj,status(a0) / beq.s loc_EC5A}, and on the set branch writes
 * {@code 0} to both shared angle outputs and returns: <em>a grounded player standing on an
 * object runs no terrain probe at all</em>, so terrain can never hand them
 * {@code Status_InAir}. The only code that clears {@code Status_OnObj} for a tube rider is the
 * tube's own exit at {@code loc_48FBA} (:95273-95274), reached only when the rider is already
 * airborne ({@code loc_48FA4}, :95262) or has left the X span (:95264-95268). A rider held
 * inside the span therefore keeps {@code Status_OnObj} set and {@code Status_InAir} clear for
 * the whole ride, and the object's angle byte advances by {@code moveq #8,d3} (:95300) every
 * frame, with {@code flip_angle(a1)} (:95314) following it.
 *
 * <p>A rider that leaves and re-mounts every few frames produces the opposite signature: the
 * mount table (:95238-95253) re-seeds the same angle from the same {@code dy}, so the ride
 * angle stalls, and {@code Status_InAir} is set by the exit and cleared again by the next
 * mount — the 1/0 alternation with {@code y} pinned that the capture showed.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityTubeRouteHeadless {

    /** {@code DEZ2_Sprites} record 264: the tube beside the unflipped {@code $5B} at the same x,y. */
    private static final int TUBE_X = 0x1A40;
    private static final int TUBE_Y = 0x08C0;
    /** Two settle frames before the rider is placed, so the level start is out of the way. */
    private static final int WARM_UP_FRAMES = 2;
    private static final int RIDE_FRAMES = 16;

    @AfterEach
    void tearDown() {
        GameServices.gameState().setReverseGravityActive(false);
        SessionManager.clear();
    }

    /** Upright gravity: the ride is continuous through the engine's own frame. */
    @Test
    void theRiderStaysGroundedForTheWholeRideThroughTheProductionLoop() {
        assertContinuousRide(false);
    }

    /**
     * And under {@code Reverse_gravity_flag}, which is the state the co-located {@code $5B}
     * leaves the act in and the state the capture was filmed in.
     */
    @Test
    void theRiderStaysGroundedUnderReverseGravityThroughTheProductionLoop() {
        assertContinuousRide(true);
    }

    @Test
    void placedVerticalTubeKeepsPlayerPhysicsMovingThroughItsSpan() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .startPosition((short) 3136, (short) 1040).startPositionIsCentre().build();
        var player = fixture.sprite();
        player.setAir(true);
        player.setYSpeed((short) 0x600);
        S3kDezGravityTubeObjectInstance tube = null;
        for (int n = 0; n < 8; n++) {
            fixture.stepIdleFrames(1);
            tube = GameServices.level().getObjectManager()
                    .activeObjectsOfType(S3kDezGravityTubeObjectInstance.class).stream()
                    .filter(t -> t.getX() == 3136 && t.getY() == 1248).findFirst().orElse(null);
            if (tube != null && tube.isRidingForTest(true)) break;
        }
        assertNotNull(tube, "actual DEZ2 vertical tube placement");
        assertTrue(tube.isRidingForTest(true), "ordinary falling entry must mount");
        int mountedY = player.getCentreY();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        fixture.stepIdleFrames(10);
        var forward = registry.capture();
        registry.restore(saved);
        fixture.stepIdleFrames(10);
        var replay = registry.capture();
        assertEquals(forward.entries().keySet(), replay.entries().keySet());
        for (String key : forward.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, forward.get(key), replay.get(key));
            assertTrue(differences.isEmpty(), key + ": " + differences);
        }
        registry.restore(saved);
        fixture.stepIdleFrames(20);
        assertTrue(player.getCentreY() > mountedY + 60,
                "loc_49120 leaves bit0 clear: Sonic_Control must advance through the vertical tube");
        fixture.stepIdleFrames(100);
        org.junit.jupiter.api.Assertions.assertFalse(tube.isRidingForTest(true),
                "ordinary physics must carry the rider beyond the tube span");
    }

    private void assertContinuousRide(boolean reverseGravity) {
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .startPosition((short) TUBE_X, (short) TUBE_Y)
                .startPositionIsCentre()
                .build();
        AbstractPlayableSprite sprite = fixture.sprite();
        GameServices.gameState().setReverseGravityActive(reverseGravity);
        for (int frame = 0; frame < WARM_UP_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        S3kDezGravityTubeObjectInstance tube = levelTube();
        assertNotNull(tube, "break-the-fixture: DEZ2_Sprites still places a $5A at "
                + String.format("$%04X,$%04X", TUBE_X, TUBE_Y));

        mount(fixture, sprite, tube);

        List<String> perFrame = new ArrayList<>();
        List<Integer> rideAngles = new ArrayList<>();
        for (int frame = 1; frame <= RIDE_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            perFrame.add(String.format("f%d air=%d onObj=%d x=%04X y=%04X angle=%02X riding=%b",
                    frame, sprite.getAir() ? 1 : 0, sprite.isOnObject() ? 1 : 0,
                    sprite.getCentreX() & 0xFFFF, sprite.getCentreY() & 0xFFFF,
                    tube.angleForTest(true), tube.isRidingForTest(true)));
            rideAngles.add(tube.angleForTest(true));
        }

        String trace = String.join("\n", perFrame);
        for (String row : perFrame) {
            assertTrue(row.contains("air=0"),
                    "Player_AnglePos (:18736) never probes terrain while Status_OnObj is set, "
                            + "so a rider inside the span cannot be handed Status_InAir:\n" + trace);
            assertTrue(row.contains("onObj=1"),
                    "only loc_48FBA (:95273) clears Status_OnObj for a rider, and the rider "
                            + "never left the span:\n" + trace);
            assertTrue(row.contains("riding=true"),
                    "the object's own standing bit stays set for the whole ride:\n" + trace);
        }
        for (int frame = 1; frame < RIDE_FRAMES; frame++) {
            assertEquals((rideAngles.get(frame - 1) + angleStep(tube)) & 0xFF,
                    rideAngles.get(frame).intValue(),
                    "the ride angle advances once per frame (:95300, :95306); a stalled angle "
                            + "is a re-mount, not a ride:\n" + trace);
        }
    }

    /**
     * Put the rider on the tube the way the mount does, then let the engine own every frame
     * after it. The mount itself is {@link TestS3kDezGravityTubeHeadless}'s subject; what this
     * class is about is the frames that follow.
     */
    private void mount(HeadlessTestFixture fixture, AbstractPlayableSprite sprite,
                       S3kDezGravityTubeObjectInstance tube) {
        NativePositionOps.writeXPosResetSubpixel(sprite, TUBE_X);
        NativePositionOps.writeYPosResetSubpixel(sprite, TUBE_Y);
        sprite.setAir(false);
        tube.update(0, sprite);
        assertTrue(tube.isRidingForTest(true), "precondition: sub_48F12 mounted the rider");
    }

    /** {@code moveq #8,d3} (:95300), or {@code moveq #4,d3} for a {@code subtype} bit 6 tube. */
    private int angleStep(S3kDezGravityTubeObjectInstance tube) {
        return tube.isWideForTest() ? 4 : 8;
    }

    private S3kDezGravityTubeObjectInstance levelTube() {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravityTubeObjectInstance tube
                    && (tube.getX() & 0xFFFF) == TUBE_X && (tube.getY() & 0xFFFF) == TUBE_Y
                    && !tube.isVerticalForTest()) {
                return tube;
            }
        }
        return null;
    }
}
