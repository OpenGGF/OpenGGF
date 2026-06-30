package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestCnzCannonInstance {

    @Test
    void solidParamsMatchRomTopSolidCall() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());

        SolidObjectParams params = cannon.getSolidParams();

        assertEquals(0x10, params.halfWidth());
        assertEquals(0x29, params.airHalfHeight());
        assertEquals(0x29, params.groundHalfHeight());
    }

    @Test
    void solidProfileNamesTopSolidTraversalPolicy() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());

        SolidRoutineProfile profile = cannon.getSolidRoutineProfile();

        assertEquals(SolidRoutineKind.TOP_SOLID_ONLY, profile.kind());
        assertTrue(profile.topSolidOnly());
        assertTrue(profile.stickyContactBuffer());
        assertFalse(profile.allowsObjectControlledSolidContacts());
    }

    @Test
    void groundedPlayerInsideCannonBodyDoesNotCaptureWithoutStandingContact() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E68);
        player.setCentreY((short) 0x0851);
        player.setXSpeed((short) -0x014A);
        player.setGSpeed((short) -0x014A);
        player.setAir(false);

        cannon.update(3897, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals((short) -0x014A, player.getXSpeed());
        assertEquals((short) -0x014A, player.getGSpeed());
        assertEquals(4, cannon.getRenderFrameForTest());
    }

    @Test
    void standingContactCapturesPlayerLikeRomStandingBitPath() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E68);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);
        player.setXSpeed((short) -0x014A);
        player.setGSpeed((short) -0x014A);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3898);

        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isControlLocked());
        assertTrue(player.getRolling());
        assertTrue(player.getAir());
        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x082C, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(0x5200, player.getYSubpixelRaw());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(7, player.getXRadius());
        assertEquals(14, player.getYRadius());
        assertEquals(2, player.getAnimationId());
        assertEquals(RenderPriority.MAX, player.getPriorityBucket());
        assertFalse(player.isHighPriority());
    }

    @Test
    void cpuSidekickStandingContactDoesNotStartCannonSwing() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite sidekick = new TestPlayableSprite();
        sidekick.setCpuControlled(true);
        sidekick.setCentreX((short) 0x1E68);
        sidekick.setCentreY((short) 0x082C);

        cannon.onSolidContact(sidekick, new SolidContact(true, false, false, true, false), 3898);
        cannon.update(3899, sidekick);

        assertFalse(sidekick.isObjectControlled());
        assertFalse(sidekick.isControlLocked());
        assertFalse(sidekick.getRolling());
        assertEquals(4, cannon.getRenderFrameForTest());
    }

    @Test
    void capturedPlayerIsPulledDownWithRomGravityBeforeLaunchReady() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E78);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3966);
        cannon.update(3967, player);
        cannon.update(3968, player);

        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x082C, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(0x8A00, player.getYSubpixelRaw());
        assertEquals(0x0070, player.getYSpeed() & 0xFFFF);
        assertTrue(player.isObjectControlled());
    }

    @Test
    void launchSnapsPlayerToRomReleasePosition() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E78);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3966);
        for (int frame = 3967; frame < 4020 && (player.getCentreY() & 0xFFFF) != 0x0869; frame++) {
            cannon.update(frame, player);
        }
        assertTrue(player.isObjectControlled());
        assertEquals(0x0869, player.getCentreY() & 0xFFFF);
        int ySubpixelAtRelease = player.getYSubpixelRaw();

        player.setJumpInputPressed(true);
        cannon.update(4020, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x0851, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(ySubpixelAtRelease, player.getYSubpixelRaw());
        assertEquals(player.getXSpeed(), player.getGSpeed());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(RenderPriority.MAX, player.getPriorityBucket());

        for (int frame = 4021; frame <= 4028; frame++) {
            cannon.update(frame, player);
        }

        assertEquals(RenderPriority.PLAYER_DEFAULT, player.getPriorityBucket());
    }

    @Test
    void launchSpawnsRomDissipatePuffsDuringCooldown() {
        ObjectManager objectManager = mock(ObjectManager.class);
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new ObjectManagerServices(objectManager));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E78);
        player.setCentreY((short) 0x082C);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3966);
        cannon.setLaunchDelayFramesForTest(0);
        player.setJumpInputPressed(true);
        cannon.update(4020, player);
        cannon.update(4024, player);

        ArgumentCaptor<AbstractObjectInstance> child = ArgumentCaptor.forClass(AbstractObjectInstance.class);
        verify(objectManager).addDynamicObjectAfterCurrent(child.capture());
        assertEquals("CNZCannonLaunchPuff", child.getValue().getName());
        assertEquals(0x1E68, child.getValue().getX() & 0xFFFF);
        assertEquals(0x0851, child.getValue().getY() & 0xFFFF);
    }

    @Test
    void recapturingAfterLaunchRestartsChamberRotationFromFreshEntry() {
        CnzCannonInstance freshCannon = new CnzCannonInstance(spawn());
        freshCannon.setServices(new TestObjectServices());
        TestPlayableSprite freshPlayer = new TestPlayableSprite();
        freshPlayer.setCentreX((short) 0x1E78);
        freshPlayer.setCentreY((short) 0x082C);
        freshCannon.onSolidContact(freshPlayer, new SolidContact(true, false, false, true, false), 5000);
        freshCannon.setLaunchDelayFramesForTest(0);
        freshCannon.update(5001, freshPlayer);
        int freshEntryFrame = freshCannon.getRenderFrameForTest();

        CnzCannonInstance reusedCannon = new CnzCannonInstance(spawn());
        reusedCannon.setServices(new TestObjectServices());
        TestPlayableSprite reusedPlayer = new TestPlayableSprite();
        reusedPlayer.setCentreX((short) 0x1E78);
        reusedPlayer.setCentreY((short) 0x082C);

        reusedCannon.onSolidContact(reusedPlayer, new SolidContact(true, false, false, true, false), 3966);
        reusedCannon.setLaunchDelayFramesForTest(0);
        reusedPlayer.setJumpInputPressed(true);
        reusedCannon.update(4020, reusedPlayer);
        reusedPlayer.setJumpInputPressed(false);
        for (int frame = 4021; frame <= 4036; frame++) {
            reusedCannon.update(frame, reusedPlayer);
        }

        reusedCannon.onSolidContact(reusedPlayer, new SolidContact(true, false, false, true, false), 4037);
        reusedCannon.setLaunchDelayFramesForTest(0);
        reusedCannon.update(4038, reusedPlayer);

        assertEquals(freshEntryFrame, reusedCannon.getRenderFrameForTest());
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x1E68, 0x0869, 0x42, 0, 0, false, 0);
    }

    private static final class ObjectManagerServices extends TestObjectServices {
        private final ObjectManager objectManager;

        private ObjectManagerServices(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }
}
