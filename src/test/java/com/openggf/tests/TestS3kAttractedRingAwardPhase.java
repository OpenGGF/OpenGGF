package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent component boundary, not a reproduction or attribution of Pachinko row 122.
 * The fixture's ROM placement enters real attraction and its real SST allocator.
 * Only the RingManager touch and owner-update phases are dispatched; no full-frame claim.
 * Additional controls restore the real pending world, without snapshot hydration,
 * private ring writes, assigned slot, timer or RNG input.
 *
 * Native locked-on SHA1 CFBF98C36C776677290A872547AC47C53D2761D6:
 * Touch_ChkValue $10012: 137C00040005 (move.b #4,routine(a1));
 * loc_1A88C $1A88C: 4A280005666E (tst.b routine(a0); bne.s $1A900);
 * AttractedRing_GiveRing $1A910: 6100FC84 (bsr.w GiveRing at $1A596),
 * then $1A914: 20BC0001A920 (move.l #loc_1A920,(a0)).
 * Those exact canonical-ROM bytes were read statically during source authoring.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAttractedRingAwardPhase {
    @AfterEach
    void clearSession() {
        SessionManager.clear();
    }

    @Test
    void acceptedControlOneTouchAwardsOnlyWhenRingOwnerRuns() {
        var component = component();
        var rings = component.rings();
        var player = component.player();
        var allocated = allocateThroughAttraction(component);
        int before = player.getRingCount();

        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(player, 1);
        int afterTouch = player.getRingCount();
        // The positive post-owner result is the acceptance control. No absent
        // pending/routine-4 implementation field is required as a prerequisite.
        rings.update(component.cameraX(), player, 1, false);
        int afterOwner = player.getRingCount();
        var after = rings.capture().attractedRings();

        assertAll("native touch acceptance then attracted-ring owner award",
                () -> assertEquals(before, afterTouch,
                        "Touch_ChkValue writes routine=4; it does not call GiveRing"),
                () -> assertEquals(before + 1, afterOwner,
                        "the ordinary ring owner must accept and award this overlap"),
                () -> assertEquals(1, after.length),
                () -> assertTrue(after[0].collected(), "accepted ring enters sparkle"),
                () -> assertEquals(allocated.objectSlotIndex(), after[0].objectSlotIndex(),
                        "sparkle retains the same naturally allocated SST"),
                () -> assertEquals(1, after[0].sparkleStartFrame()),
                () -> assertControlOne(player));
    }

    @Test
    void publishedAttractedRingOutsideTouchBoxDoesNotAward() {
        var component = component();
        allocateThroughAttraction(component);
        var player = component.player();
        // Public geometry only: attraction was genuine; now the player is
        // strictly beyond the 6px ring + 8px player horizontal touch bounds.
        player.setCentreX((short) (component.placement().x() - 0x100));
        int before = player.getRingCount();
        component.rings().prepareAttractedRingTouchSnapshot();
        component.rings().collectAttractedRing(player, 1);
        int afterTouch = player.getRingCount();
        component.rings().update(component.cameraX(), player, 1, false);
        var after = component.rings().capture().attractedRings();
        assertAll("non-overlap control",
                () -> assertEquals(before, afterTouch),
                () -> assertEquals(before, player.getRingCount()),
                () -> assertEquals(1, after.length),
                () -> assertFalse(after[0].collected()),
                () -> assertControlOne(player));
    }

    @Test
    void ordinaryPlacedRingStillAwardsAtPlayerTouchWithControlOne() {
        var component = component();
        var player = component.player();
        player.giveShield(ShieldType.FIRE);
        int before = player.getRingCount();
        component.rings().collectStageRings(player, 1);
        assertAll("ordinary placed-ring control",
                () -> assertTrue(player.getRingCount() > before,
                        "the normal sweep may also accept adjacent ROM placements"),
                () -> assertTrue(component.rings().isCollected(component.placement())),
                () -> assertEquals(0, component.rings().capture().attractedRings().length),
                () -> assertControlOne(player));
    }

    @Test
    void acceptedPendingWorldRestoresAndAwardsExactlyOnceAcrossTwoForwardReplays() {
        var component = component();
        var rings = component.rings();
        var player = component.player();
        var allocated = allocateThroughAttraction(component);
        int before = player.getRingCount();
        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(player, 1);
        var accepted = rings.capture().attractedRings()[0];
        assertTrue(accepted.collected());
        assertEquals(-1, accepted.sparkleStartFrame(), "accepted, not yet awarded/sparkling");
        assertEquals(allocated.mappingFrame(), accepted.mappingFrame(), "pending retains prior spin");
        assertEquals(allocated.animationFrameTimer(), accepted.animationFrameTimer());
        assertEquals(allocated.x(), accepted.x());
        assertEquals(allocated.y(), accepted.y());
        assertEquals(allocated.xSub(), accepted.xSub());
        assertEquals(allocated.ySub(), accepted.ySub());
        assertEquals(before, player.getRingCount());
        var pending = component.registry().capture();

        rings.update(component.cameraX(), player, 1, false);
        assertEquals(before + 1, player.getRingCount());
        var once = component.registry().capture();
        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(player, 2);
        rings.update(component.cameraX(), player, 2, false);
        assertEquals(before + 1, player.getRingCount(), "sparkle cannot award again");
        var twice = component.registry().capture();

        for (int replay = 0; replay < 2; replay++) {
            component.registry().restore(pending);
            assertWorld(pending, component.registry().capture());
            assertEquals(before, player.getRingCount());
            // The restored accepted record must not re-enter touch eligibility.
            rings.prepareAttractedRingTouchSnapshot();
            rings.collectAttractedRing(player, 1);
            assertEquals(before, player.getRingCount());
            rings.update(component.cameraX(), player, 1, false);
            assertEquals(before + 1, player.getRingCount());
            assertWorld(once, component.registry().capture());
            assertEquals(allocated.objectSlotIndex(),
                    rings.capture().attractedRings()[0].objectSlotIndex());
            rings.prepareAttractedRingTouchSnapshot();
            rings.collectAttractedRing(player, 2);
            rings.update(component.cameraX(), player, 2, false);
            assertEquals(before + 1, player.getRingCount());
            assertWorld(twice, component.registry().capture());
        }
    }

    @Test
    void actualNativeP2AcceptanceAwardsSharedLedgerOnlyOnceInMainOwnerPass() {
        var component = component();
        var rings = component.rings();
        var allocated = allocateThroughAttraction(component);
        var nativeP2 = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, nativeP2.size(), "prerequisite: real registered native P2");
        var p2 = nativeP2.getFirst();
        assertTrue(p2.isCpuControlled());
        assertFalse(p2.getDead());
        assertFalse(p2.isObjectControlled());
        p2.setCentreX((short) allocated.x());
        p2.setCentreY((short) allocated.y());
        component.player().setCentreX((short) (component.placement().x() - 0x100));
        int before = component.player().getRingCount();
        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(component.player(), 1); // main is outside the touch box
        rings.collectAttractedRing(p2, 1);
        int afterTouch = component.player().getRingCount();
        rings.update(component.cameraX(), component.player(), 1, false);
        int afterOwner = component.player().getRingCount();
        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(p2, 2);
        rings.update(component.cameraX(), component.player(), 2, false);
        assertAll("P2 touch, P1/global ledger owner, no duplicate award",
                () -> assertEquals(before, afterTouch),
                () -> assertEquals(before + 1, afterOwner),
                () -> assertEquals(before + 1, component.player().getRingCount()),
                () -> assertEquals(before + 1, p2.getRingCount()),
                () -> assertEquals(allocated.objectSlotIndex(),
                        rings.capture().attractedRings()[0].objectSlotIndex()));
        // No replaceable recording policy-port fixture exists. Actual collector
        // notification identity/once-only placement is source-audited separately.
    }

    @Test
    void acceptedPickupStillAwardsWhenMainIsDeadAtOwnerPass() {
        var component = component();
        var rings = component.rings();
        allocateThroughAttraction(component);
        var player = component.player();
        int before = player.getRingCount();
        rings.prepareAttractedRingTouchSnapshot();
        rings.collectAttractedRing(player, 1);
        int afterTouch = player.getRingCount();
        // Declared public component state: this is not a native death-route test.
        player.setDead(true);
        rings.update(component.cameraX(), player, 1, false);
        int afterOwner = player.getRingCount();
        rings.update(component.cameraX(), player, 2, false);
        assertAll("accepted ring owner is independent of main's dead dispatcher",
                () -> assertEquals(before, afterTouch),
                () -> assertEquals(before + 1, afterOwner),
                () -> assertEquals(before + 1, player.getRingCount()),
                () -> assertTrue(rings.capture().attractedRings()[0].collected()),
                () -> assertEquals(1, rings.capture().attractedRings()[0].sparkleStartFrame()));
    }

    private static Component component() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0)
                .withSkippedZoneIntro().build();
        var player = fixture.sprite();
        var rings = GameServices.level().getRingManager();
        assertNotNull(rings);
        // Declare one actual loaded ROM record, rather than assuming that the
        // post-boot camera window contains a ring. No placement is created.
        var declared = GameServices.level().getCurrentLevel().getRings().stream()
                .filter(ring -> !rings.isCollected(ring)).findFirst()
                .orElseThrow(() -> new AssertionError("prerequisite: loaded ROM ring record"));
        assertSame(declared, rings.resolveCanonicalSpawn(declared.x(), declared.y()));
        fixture.camera().setX((short) declared.x());
        player.setCentreX((short) declared.x());
        player.setCentreY((short) declared.y());
        // This public owner call updates placement first. false suppresses
        // legacy touch collection/attraction; no frame, timer or clock is set.
        rings.update(fixture.camera().getX(), player,
                GameServices.level().getFrameCounter(), false);
        var placement = rings.getActiveSpawns().stream()
                .filter(ring -> ring == declared && !rings.isCollected(ring)).findFirst()
                .orElseThrow(() -> new AssertionError("prerequisite: active ROM ring placement"));
        player.setCentreX((short) placement.x());
        player.setCentreY((short) placement.y());
        player.giveShield(ShieldType.LIGHTNING);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        assertFalse(player.getDead());
        assertFalse(player.isCpuControlled());
        assertTrue(player.getYRadius() > 3, "ordinary nonempty native touch box");
        assertTrue(player.getInvulnerableFrames() < 90);
        assertControlOne(player);
        // Actual ROM-backed ring owner, including its sparkle assets. This test
        // drives local phases. The dedicated pending test also restores the
        // existing whole registry; it does not claim full-frame replay coverage.
        assertFalse(rings.isCollected(placement));
        assertEquals(0, rings.capture().attractedRings().length);
        return new Component(player, rings, placement, fixture.camera().getX(),
                fixture.gameplayMode().getRewindRegistry());
    }

    private static com.openggf.game.rewind.snapshot.RingSnapshot.AttractedRingEntry
            allocateThroughAttraction(Component component) {
        int allocatedBefore = GameServices.level().getObjectManager().getAllocatedSlotCount();
        int ringsBefore = component.player().getRingCount();
        component.rings().attractStageRings(component.player());
        // S3K's player-before-object-load component inlet defers its SST
        // reservation. Dispatch the existing production assignment owner,
        // which chooses a free slot itself; never assign a slot in this test.
        component.rings().assignPendingAttractedRingSlots();
        // A newly attracted ring must run its real movement/publication owner
        // before a later retained touch pass can consider it. Equal declared
        // centres keep this first $30-subpixel acceleration within the pixel.
        component.rings().update(component.cameraX(), component.player(), 0, false);
        var entries = component.rings().capture().attractedRings();
        assertEquals(1, entries.length, "ordinary lightning attraction creates a pool record");
        var entry = entries[0];
        assertTrue(entry.active());
        assertFalse(entry.collected());
        assertTrue(entry.objectSlotIndex() >= 0, "real allocator reserved the SST");
        assertEquals(allocatedBefore + 1,
                GameServices.level().getObjectManager().getAllocatedSlotCount());
        assertTrue(component.rings().isCollected(component.placement()),
                "attraction consumed the original placement, without awarding it");
        assertEquals(ringsBefore, component.player().getRingCount());
        assertEquals(component.player().getCentreX(), entry.x());
        assertEquals(component.player().getCentreY(), entry.y());
        return entry;
    }

    private static void assertControlOne(AbstractPlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private record Component(AbstractPlayableSprite player, RingManager rings, RingSpawn placement,
                             int cameraX, RewindRegistry registry) { }
}
