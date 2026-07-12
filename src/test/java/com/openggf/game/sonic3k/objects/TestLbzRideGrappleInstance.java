package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLbzRideGrappleInstance {

    @Test
    void registryRoutesS3klSlot17ToLbzRideGrapple() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);

        ObjectInstance grapple = registry.create(new ObjectSpawn(
                0x1800, 0x0600, Sonic3kObjectIds.LBZ_RIDE_GRAPPLE, 0, 0, false, 0));

        assertFalse(grapple instanceof PlaceholderObjectInstance,
                "S3KL slot $17 is Obj_LBZRideGrapple and must not remain a placeholder");
        assertEquals("LBZRideGrapple", grapple.getName());
    }

    @Test
    void playerInsideGrabWindowIsHeldAtRomOffset() {
        ObjectInstance grapple = grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1800, 0x0620);

        grapple.update(0, player);

        assertTrue(player.isObjectControlled(),
                "loc_267B2 writes object_control=3 when the player is inside the grapple window");
        assertTrue(player.isObjectControlAllowsCpu(),
                "object_control=3 is a bits 0-6 control state, not ROM bit-7 full-control");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "object_control=3 suppresses normal movement while the grapple owns positioning");
        assertEquals(0x1800, player.getCentreX() & 0xFFFF);
        assertEquals(0x0624, player.getCentreY() & 0xFFFF,
                "loc_2673E snaps player y_pos to handle y_pos+$24");
        assertEquals(Sonic3kAnimationIds.HANG2.id(), player.getAnimationId(),
                "loc_267F8 writes anim=$14 on capture");
        assertEquals(0x91, player.getMappingFrame(),
                "move.b angle(a0),d0 reads the high byte of the word accumulator, so the first held frame is $91");
        assertTrue(player.isObjectMappingFrameControl(),
                "Perform_Player_DPLC consumes the grapple-owned mapping frame while held");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void heldPlayerUsesHighByteAngleForStableHandleMotion() {
        ObjectInstance grapple = grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1800, 0x0620);

        grapple.update(0, player);
        for (int frame = 1; frame < 8; frame++) {
            grapple.update(frame, player);

            int dx = Math.abs((player.getCentreX() & 0xFFFF) - 0x1800);
            int dy = Math.abs((player.getCentreY() & 0xFFFF) - 0x0624);
            assertTrue(dx <= 1,
                    "early idle sway should only move Sonic by a pixel-scale amount, not jerk him sideways");
            assertTrue(dy <= 8,
                    "early chain extension should move Sonic by a small bounded amount, not jerk him vertically");
            assertEquals(0x91, player.getMappingFrame(),
                    "byte_26794 should also use the high angle byte, not the rapidly changing low byte");
        }
    }

    @Test
    void nativeP2IsProcessedBeforePlayerOne() {
        AbstractObjectInstance grapple = (AbstractObjectInstance) grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite sidekick = playerAt(0x1800, 0x0620);
        grapple.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));
        TestablePlayableSprite sonic = playerAt(0x1900, 0x0620);

        grapple.update(0, sonic);

        assertFalse(sonic.isObjectControlled());
        assertTrue(sidekick.isObjectControlled(),
                "sub_26694 checks Player_2 before Player_1, so native P2 can grab independently");
        assertEquals(0x1800, sidekick.getCentreX() & 0xFFFF);
        assertEquals(0x0624, sidekick.getCentreY() & 0xFFFF);
    }

    @Test
    void heldDirectionalInputUpdatesFacingAndHeldFrame() {
        ObjectInstance grapple = grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1800, 0x0620);
        player.setDirection(Direction.RIGHT);

        grapple.update(0, player);

        player.setDirectionalInputPressed(false, false, true, false);
        grapple.update(1, player);

        assertEquals(Direction.LEFT, player.getDirection(),
                "loc_26726 sets Status_Facing when holding left");
        assertTrue(player.getRenderHFlip(),
                "loc_2673E mirrors Status_Facing into render_flags bit 0");

        player.setDirectionalInputPressed(false, false, false, true);
        grapple.update(2, player);

        assertEquals(Direction.RIGHT, player.getDirection(),
                "loc_26732 clears Status_Facing when holding right");
        assertFalse(player.getRenderHFlip(),
                "loc_2673E clears render_flags bit 0 when Status_Facing is clear");
    }

    @Test
    void jumpReleasesHeldPlayerWithRomRollLaunchState() {
        ObjectInstance grapple = grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1800, 0x0620);

        grapple.update(0, player);
        player.setJumpInputPressed(true, true);
        grapple.update(1, player);

        assertFalse(player.isObjectControlled(),
                "A/B/C press in loc_266B0 clears the per-player grab byte and object_control");
        assertFalse(player.isObjectMappingFrameControl(),
                "release returns mapping-frame ownership to the player animation system");
        assertEquals((short) 0, player.getXSpeed(),
                "release copies the current grapple x_vel to the player");
        assertEquals((short) -0x380, player.getYSpeed(),
                "loc_266E6 writes y_vel=-$380");
        assertTrue(player.getAir(), "release sets Status_InAir");
        assertTrue(player.isJumping(), "release writes jumping=1");
        assertTrue(player.getRolling(), "release sets Status_Roll");
        assertEquals(7, player.getXRadius());
        assertEquals(0x0E, player.getYRadius());
        assertEquals(Sonic3kAnimationIds.ROLL.id(), player.getAnimationId());
    }

    @Test
    void jumpReleaseSuppressesStaleJumpPressForShieldAbilities() {
        ObjectInstance grapple = grapple(0x1800, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1800, 0x0620);

        grapple.update(0, player);
        player.setJumpInputPressed(true, true);
        grapple.update(1, player);

        assertTrue(player.consumeSuppressNextJumpPress(),
                "object-controlled release must consume the held release press before airborne shield ability checks");
    }

    @Test
    void publicUpdateCapturesMainAndTwoSidekicksWhileMainRetainsSharedMotionAuthority() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        main.setDirection(Direction.RIGHT);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        nativeP2.setDirection(Direction.LEFT);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        extension.setDirection(Direction.LEFT);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extension));

        grapple.update(0, main);

        assertTrue(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
        assertTrue(extension.isObjectControlled(),
                "third and later configured players must receive independent grapple state");

        for (int frame = 1; frame < 48; frame++) {
            grapple.update(frame, main);
        }
        assertTrue(grapple.getX() > 0x1E00,
                "extension processing must not override the native P2-then-P1 shared direction order");
    }

    @Test
    void publicUpdateCapturesMainAndThreeSidekicks() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extensionOne = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extensionTwo = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extensionOne, extensionTwo));

        grapple.update(0, main);

        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertTrue(player.isObjectControlled(),
                    "every policy participant must receive independent grapple state");
        }
    }

    @Test
    void extensionJumpReleaseDoesNotReleaseOtherHeldPlayers() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extension));
        grapple.update(0, main);

        extension.setJumpInputPressed(true, true);
        grapple.update(1, main);

        assertTrue(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
        assertFalse(extension.isObjectControlled());
        assertEquals((short) -0x380, extension.getYSpeed());
    }

    @Test
    void unloadReleasesEveryHeldPlayerIncludingExtensionSidekicks() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extension));
        grapple.update(0, main);

        grapple.onUnload();

        for (TestablePlayableSprite player : List.of(main, nativeP2, extension)) {
            assertFalse(player.isObjectControlled());
            assertFalse(player.isObjectMappingFrameControl());
        }
    }

    @Test
    void deadExtensionSidekickIsReleasedWithoutDisturbingNativeOwners() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extension));
        grapple.update(0, main);
        extension.setDead(true);

        grapple.update(1, main);

        assertFalse(extension.isObjectControlled());
        assertFalse(extension.isObjectMappingFrameControl());
        assertTrue(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
    }

    @Test
    void pathEndEjectsExtensionSidekicks() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        main.setDirection(Direction.RIGHT);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        nativeP2.setDirection(Direction.RIGHT);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        extension.setDirection(Direction.RIGHT);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, extension));
        grapple.update(0, main);

        for (int frame = 1; frame < 400 && extension.isObjectControlled(); frame++) {
            grapple.update(frame, main);
        }

        assertFalse(extension.isObjectControlled(),
                "an extension sidekick must be ejected when the shared grapple reaches its path end");
        assertFalse(extension.isObjectMappingFrameControl());
        assertTrue(extension.getAir());
        assertEquals(0, extension.getYSpeed());
    }

    @Test
    void temporarilyOmittedExtensionRejoinsWithoutTransferringGrabState() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite nativeP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite omitted = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite retained = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(nativeP2, omitted, retained));
        grapple.update(0, main);

        grapple.setServices(playerServices(main, List.of(nativeP2, retained)));
        grapple.update(1, main);
        assertTrue(omitted.isObjectControlled(),
                "temporary roster omission must not abandon a held player's cleanup ownership");

        grapple.setServices(playerServices(main, List.of(nativeP2, omitted, retained)));
        omitted.setJumpInputPressed(true, true);
        grapple.update(2, main);

        assertFalse(omitted.isObjectControlled(),
                "the returning player must recover its own held state and accept its own release input");
        assertEquals((short) -0x380, omitted.getYSpeed());
        assertTrue(retained.isObjectControlled(),
                "the returning player's state must not be transferred to another extension participant");
    }

    @Test
    void rosterReorderKeepsGrabStateWithPlayerIdentity() {
        TestablePlayableSprite main = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite originalP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite extension = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                main, List.of(originalP2, extension));
        grapple.update(0, main);

        grapple.setServices(playerServices(main, List.of(extension, originalP2)));
        extension.setJumpInputPressed(true, true);
        grapple.update(1, main);

        assertFalse(extension.isObjectControlled(),
                "moving from extension position to native P2 must retain this player's release state");
        assertEquals((short) -0x380, extension.getYSpeed());
        assertTrue(originalP2.isObjectControlled(),
                "the former native P2 state must migrate with its player instead of being overwritten");
        assertTrue(main.isObjectControlled());
    }

    @Test
    void nonEmptyExtensionStateRewindsThroughStablePlayerRefs() {
        TestablePlayableSprite oldMain = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite oldP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite oldExtension = playerAt(0x1E00, 0x0620);
        AbstractObjectInstance grapple = configuredGrapple(0x1E00, 0x0600, 3,
                oldMain, List.of(oldP2, oldExtension));
        grapple.update(0, oldMain);
        RewindObjectStateBlob snapshot = CompactFieldCapturer.capture(
                grapple, rewindContext(oldMain, oldP2, oldExtension));

        TestablePlayableSprite newMain = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite newP2 = playerAt(0x1E00, 0x0620);
        TestablePlayableSprite newExtension = playerAt(0x1E00, 0x0620);
        grapple.setServices(playerServices(newMain, List.of(newP2, newExtension)));
        CompactFieldCapturer.restore(
                grapple, snapshot, rewindContext(newMain, newP2, newExtension));
        newExtension.setJumpInputPressed(true, true);

        grapple.update(1, newMain);

        assertFalse(newExtension.isObjectControlled(),
                "restored extension ownership must resolve through PlayerRefId and accept release input");
        assertEquals((short) -0x380, newExtension.getYSpeed());
    }

    private static ObjectInstance grapple(int x, int y, int subtype) {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);
        return registry.create(new ObjectSpawn(
                x, y, Sonic3kObjectIds.LBZ_RIDE_GRAPPLE, subtype, 0, false, 0));
    }

    private static AbstractObjectInstance configuredGrapple(
            int x,
            int y,
            int subtype,
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        AbstractObjectInstance grapple = (AbstractObjectInstance) grapple(x, y, subtype);
        grapple.setServices(playerServices(main, sidekicks));
        return grapple;
    }

    private static TestObjectServices playerServices(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        return new TestObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }
        };
    }

    private static RewindCaptureContext rewindContext(
            TestablePlayableSprite main,
            TestablePlayableSprite nativeP2,
            TestablePlayableSprite extension) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        table.registerPlayer(nativeP2, PlayerRefId.sidekick(0));
        table.registerPlayer(extension, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(table);
    }

    private static TestablePlayableSprite playerAt(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setYSpeed((short) 0x0200);
        player.setGSpeed((short) 0x0100);
        return player;
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }
}
