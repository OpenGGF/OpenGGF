package com.openggf.tests;

import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.LevelArtEntry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZTriggerPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3kMgzTriggerPlatformObject {

    @Test
    void registryCreatesMgzTriggerPlatformInstance() {
        Sonic3kLevelTriggerManager.reset();
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM, 0x00, 0x00, false, 0));
        assertInstanceOf(MGZTriggerPlatformObjectInstance.class, instance);
    }

    @Test
    void horizontalSubtypeUsesWideFullSolidHitbox() {
        Sonic3kLevelTriggerManager.reset();
        MGZTriggerPlatformObjectInstance instance = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM, 0x00, 0x00, false, 0));

        SolidObjectParams params = instance.getSolidParams();
        assertEquals(0x4B, params.halfWidth());
        assertEquals(0x1E, params.airHalfHeight());
        assertEquals(0x1F, params.groundHalfHeight());
        assertTrue(instance.usesInclusiveRightEdge(),
                "SolidObjectFull retains the exact d1*2 right edge");
        assertTrue(instance.airborneStaleStandingBitReturnsNoContact(null),
                "SolidObjectFull must return before new contact for an airborne stale rider");
        assertTrue(instance.suppressesGroundingRecoveryFromAirborneStaleRide(null),
                "The earlier player slot must remain airborne until the later SolidObjectFull pass");
        assertEquals(0x0003, instance.romObjectCodePointerHighWord());
        assertEquals(5, instance.getPriorityBucket());
    }

    @Test
    void horizontalSubtypeSlidesRightAndDeletesAfterSixtyFourFrames() {
        Sonic3kLevelTriggerManager.reset();
        MGZTriggerPlatformObjectInstance instance = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM, 0x03, 0x00, false, 0));

        for (int frame = 0; frame < 8; frame++) {
            instance.update(frame, null);
        }
        assertEquals(0x1200, instance.getX(), "Object must wait for its trigger");

        Sonic3kLevelTriggerManager.setAll(3);
        for (int frame = 0; frame < 65; frame++) {
            instance.update(frame, null);
        }

        assertEquals(0x1200 + (64 * 2), instance.getX());
        assertTrue(instance.isDestroyed(), "Horizontal variant should delete when its timer expires");
    }

    @Test
    void verticalSubtypeWaitsForTriggerThenMovesOnePixelPerFrame() {
        Sonic3kLevelTriggerManager.reset();
        MGZTriggerPlatformObjectInstance instance = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM, 0x10, 0x00, false, 0));

        instance.update(0, null);
        assertEquals(0x0600, instance.getY());

        Sonic3kLevelTriggerManager.setAll(0);
        for (int frame = 0; frame < 65; frame++) {
            instance.update(frame, null);
        }

        assertEquals(0x0600 + 0x40, instance.getY());
        assertFalse(instance.isDestroyed(), "Vertical variant stays in place after moving");
    }

    @Test
    void triggeredVerticalSubtypeStartsAtFinalTravelPosition() {
        Sonic3kLevelTriggerManager.reset();
        Sonic3kLevelTriggerManager.setAll(5);

        MGZTriggerPlatformObjectInstance instance = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM, 0x25, 0x01, false, 0));

        assertEquals(0x0600 - 0x80, instance.getY());
    }

    @Test
    void subtypeOneLandingRechecksReversedEarlierSlotSubtypeTwoSibling() {
        MGZTriggerPlatformObjectInstance landing = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1D20, 0x0354, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM,
                        0x14, 0x00, false, 133));
        MGZTriggerPlatformObjectInstance laterNativeSibling = new MGZTriggerPlatformObjectInstance(
                new ObjectSpawn(0x1CE0, 0x0314, Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM,
                        0x24, 0x00, false, 132));
        landing.setSlotIndex(6);
        laterNativeSibling.setSlotIndex(4);
        ObjectServices services = mock(ObjectServices.class);
        ObjectManager objectManager = mock(ObjectManager.class);
        com.openggf.game.PlayableEntity player = mock(com.openggf.game.PlayableEntity.class);
        when(services.objectManager()).thenReturn(objectManager);
        when(objectManager.activeObjectsOfType(MGZTriggerPlatformObjectInstance.class))
                .thenReturn(List.of(laterNativeSibling, landing));
        landing.setServices(services);

        landing.onSolidContact(player,
                new SolidContact(true, false, false, true, false), 0);

        verify(objectManager).processImmediateInlineSolidCheckpoint(laterNativeSibling, player, List.of());
    }

    @Test
    void artRegistryProvidesMgzTriggerPlatformSheet() {
        LevelArtEntry entry = findLevelEntry(Sonic3kPlcArtRegistry.getPlan(0x02, 0).levelArt(),
                Sonic3kObjectArtKeys.MGZ_TRIGGER_PLATFORM);

        assertEquals(Sonic3kConstants.MAP_MGZ_TRIGGER_PLATFORM_ADDR, entry.mappingAddr());
        assertEquals(1, entry.artTileBase());
        assertEquals(2, entry.palette());
    }

    @Test
    void profileMarksMgzTriggerPlatformImplemented() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM));
    }

    private static LevelArtEntry findLevelEntry(List<LevelArtEntry> entries, String key) {
        return entries.stream()
                .filter(entry -> key.equals(entry.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing art entry: " + key));
    }
}
