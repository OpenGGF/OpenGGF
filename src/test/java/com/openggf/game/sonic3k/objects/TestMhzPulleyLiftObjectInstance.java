package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.GameStateManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestMhzPulleyLiftObjectInstance {
    private static final int MHZ_PULLEY_LIFT = 0x06;

    @Test
    void registryRoutesSklSlot06ToMhzPulleyLiftInsteadOfAizRideVine() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);

        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));

        assertEquals("MHZPulleyLift", pulley.getName(),
                "SKL slot $06 is Obj_MHZPulleyLift; MHZ must not use the S3KL AIZ ride-vine object");
        assertEquals(5, pulley.getPriorityBucket(),
                "Obj_MHZPulleyLift initializes parent and handle priority=$280");
    }

    @Test
    void fallingPlayerInLeftHandleGrabWindowIsCarriedAtRomOffset() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);
        player.setRenderFlips(false, true);

        assertEquals("MHZPulleyLift", pulley.getName(),
                "SKL slot $06 must construct the MHZ pulley before behavior can be validated");
        pulley.update(0, player);

        assertTrue(player.isObjectControlled(),
                "sub_3E508 writes object_control=3 when a falling player grabs a pulley handle");
        assertTrue(player.isObjectControlAllowsCpu(),
                "object_control=3 is a bits 0-6 control state, not ROM bit-7 full-control");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "object_control=3 suppresses normal player movement while the pulley owns positioning");
        assertEquals(0x17CE, player.getCentreX() & 0xFFFF,
                "left child handle spawns at parent x_pos-$32");
        assertEquals(0x0670, player.getCentreY() & 0xFFFF,
                "grabbed player is snapped to child handle y_pos+$42");
        assertEquals(0x92, player.getMappingFrame(),
                "loc_3E658 writes mapping_frame=$92 when the grabbed handle offset is at least $20");
        assertTrue(player.isObjectMappingFrameControl(),
                "Perform_Player_DPLC uses the pulley-owned mapping frame while object_control=3 holds the player");
        assertFalse(player.getRenderVFlip(),
                "loc_3E690 clears render_flags bit 1 on grab so pulley-owned player frames are never V-flipped");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void nativeP2CanGrabPulleyWhenPlayerOneIsOutsideGrabWindow() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        AbstractObjectInstance pulley = (AbstractObjectInstance) registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite sidekick = fallingPlayerAt(0x17CE, 0x0660);
        pulley.setServices(new TestObjectServices()
                .withGameState(mock(GameStateManager.class))
                .withSidekicks(List.of(sidekick)));
        TestablePlayableSprite sonic = fallingPlayerAt(0x1900, 0x0660);

        pulley.update(0, sonic);

        assertFalse(sonic.isObjectControlled());
        assertTrue(sidekick.isObjectControlled(),
                "sub_3E4EC checks Player_2 before Player_1, so native P2 can grab a pulley handle independently");
        assertEquals(0x17CE, sidekick.getCentreX() & 0xFFFF);
        assertEquals(0x0670, sidekick.getCentreY() & 0xFFFF);
    }

    @Test
    void hurtPlayerInsidePulleyGrabWindowIsNotCaptured() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);
        player.setHurt(true);

        pulley.update(0, player);

        assertFalse(player.isObjectControlled(),
                "loc_3E682 rejects routine(a1) >= 4 before the pulley grab path writes object_control=3");
        assertFalse(player.isObjectMappingFrameControl(),
                "a hurt player rejected by the ROM routine gate must not receive pulley-owned mapping frames");
        assertEquals((short) 0x200, player.getYSpeed(),
                "rejected pulley grab must leave the player's falling y_vel untouched");
    }

    @Test
    void jumpReleasesPulleyHandleWithRomVelocityAndDirectionalXSpeed() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        assertEquals("MHZPulleyLift", pulley.getName(),
                "SKL slot $06 must construct the MHZ pulley before behavior can be validated");
        pulley.update(0, player);
        player.setDirectionalInputPressed(false, false, true, false);
        player.setJumpInputPressed(true, true);
        pulley.update(1, player);

        assertFalse(player.isObjectControlled(),
                "button A/B/C release clears object_control in sub_3E508");
        assertFalse(player.isObjectMappingFrameControl(),
                "pulley release returns mapping-frame ownership to the player animation system");
        assertEquals((short) -0x200, player.getXSpeed(),
                "holding left during pulley release writes x_vel=-$200");
        assertEquals((short) -0x380, player.getYSpeed(),
                "pulley release writes y_vel=-$380");
        assertTrue(player.getAir(), "release sets Status_InAir");
        assertTrue(player.isJumping(), "sub_3E508 writes jumping=1 on pulley release");
        assertTrue(player.getRolling(), "release sets Status_Roll");
    }

    @Test
    void heldJumpWithoutFreshPressDoesNotReleasePulleyHandle() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        pulley.update(0, player);
        player.setJumpInputPressed(true, false);
        pulley.update(1, player);

        assertTrue(player.isObjectControlled(),
                "sub_3E508 masks only the low Ctrl_1_logical A/B/C bits; held jump alone does not release");
        assertTrue(player.isObjectMappingFrameControl(),
                "the pulley still owns the player's mapping frame while held jump is ignored");
        assertEquals(0, player.getYSpeed(),
                "held jump must not apply the pulley release y_vel=-$380");
    }

    @Test
    void hurtPlayerIsReleasedFromHeldPulleyWithoutLaunchVelocity() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        pulley.update(0, player);
        player.setHurt(true);
        pulley.update(1, player);

        assertFalse(player.isObjectControlled(),
                "loc_3E58A releases the handle when routine(a1) >= 4, including the hurt routine");
        assertFalse(player.isObjectMappingFrameControl(),
                "the pulley must return mapping-frame ownership when the forced release path runs");
        assertEquals(0, player.getXSpeed(),
                "the non-jump forced release path does not write x_vel");
        assertEquals(0, player.getYSpeed(),
                "the non-jump forced release path does not write y_vel=-$380");
    }

    @Test
    void heldDirectionalInputUpdatesFacingAndRenderFlip() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        player.setDirection(Direction.RIGHT);
        player.setRenderFlips(false, false);
        pulley.update(0, player);

        player.setDirectionalInputPressed(false, false, true, false);
        pulley.update(1, player);

        assertEquals(Direction.LEFT, player.getDirection(),
                "loc_3E5F2 sets Status_Facing when holding left on a pulley handle");
        assertTrue(player.getRenderHFlip(),
                "loc_3E60C mirrors Status_Facing into render_flags bit 0 while held");

        player.setDirectionalInputPressed(false, false, false, true);
        pulley.update(2, player);

        assertEquals(Direction.RIGHT, player.getDirection(),
                "loc_3E600 clears Status_Facing when holding right on a pulley handle");
        assertFalse(player.getRenderHFlip(),
                "loc_3E60C clears render_flags bit 0 when Status_Facing is clear");
    }

    @Test
    void idleHandleRetractionMovesParentYBeforeSnappingPlayer() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        ObjectInstance pulley = registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        pulley.update(0, player);
        pulley.update(1, player);
        pulley.update(2, player);

        assertEquals(0x0666, player.getCentreY() & 0xFFFF,
                "loc_3E37E subtracts 2 from parent y_pos at this alignment before loc_3E646 snaps the held player");
    }

    @Test
    void downInputOnlyPlaysPulleyMoveSfxForNonZeroSubtype() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        RecordingServices services = new RecordingServices();
        AbstractObjectInstance pulley = (AbstractObjectInstance) registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 0, 0, false, 0));
        pulley.setServices(services);
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        pulley.update(0, player);
        services.clear();

        player.setDirectionalInputPressed(false, true, false, false);
        pulley.update(1, player);

        assertFalse(services.playedSfx(Sonic3kSfx.PULLEY_MOVE.id),
                "loc_3E632 skips sfx_PulleyMove when the parent subtype is zero");

        RecordingServices enabledServices = new RecordingServices();
        AbstractObjectInstance enabledPulley = (AbstractObjectInstance) registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        enabledPulley.setServices(enabledServices);
        TestablePlayableSprite enabledPlayer = fallingPlayerAt(0x17CE, 0x0660);

        enabledPulley.update(0, enabledPlayer);
        enabledServices.clear();

        enabledPlayer.setDirectionalInputPressed(false, true, false, false);
        enabledPulley.update(1, enabledPlayer);

        assertTrue(enabledServices.playedSfx(Sonic3kSfx.PULLEY_MOVE.id),
                "loc_3E632 plays sfx_PulleyMove when down is pressed and the parent subtype is nonzero");
    }

    @Test
    void heldDownInputDoesNotRetriggerPulleyMoveSfxEveryFrame() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        RecordingServices services = new RecordingServices();
        AbstractObjectInstance pulley = (AbstractObjectInstance) registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        pulley.setServices(services);
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        pulley.update(0, player);
        services.clear();

        player.setDirectionalInputPressed(false, true, false, false);
        pulley.update(1, player);
        pulley.update(2, player);

        assertEquals(1, services.sfxCount(Sonic3kSfx.PULLEY_MOVE.id),
                "loc_3E632 tests Ctrl_1_logical low-byte down press, not the held down bit");
    }

    @Test
    void downHeldBeforeGrabDoesNotPlayPulleyMoveSfxAfterGrab() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_MHZ);
        RecordingServices services = new RecordingServices();
        AbstractObjectInstance pulley = (AbstractObjectInstance) registry.create(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        pulley.setServices(services);
        TestablePlayableSprite player = fallingPlayerAt(0x17CE, 0x0660);

        player.setDirectionalInputPressed(false, true, false, false);
        pulley.update(0, player);
        services.clear();
        pulley.update(1, player);

        assertFalse(services.playedSfx(Sonic3kSfx.PULLEY_MOVE.id),
                "a held down bit that was already active before grab is not a fresh Ctrl_1_logical down press");
    }

    @Test
    void pulleyLiftRendersRomParentRopePulleysAndHandles() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MHZ_PULLEY_LIFT)).thenReturn(renderer);
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        MhzPulleyLiftObjectInstance pulley = new MhzPulleyLiftObjectInstance(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        pulley.setServices(new TestObjectServices().withLevelManager(levelManager));

        pulley.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(4, 0x1800, 0x0600, false, false);
        verify(renderer).drawFrameIndex(0, 0x1800, 0x0640, false, false);
        verify(renderer).drawFrameIndex(5, 0x17F0, 0x0678, false, false);
        verify(renderer).drawFrameIndex(6, 0x1810, 0x0678, false, false);
        verify(renderer).drawFrameIndex(7, 0x17CE, 0x062E, false, false);
        verify(renderer).drawFrameIndex(7, 0x1832, 0x0630, true, false);
    }

    @Test
    void idleSpawnUsesParentCopyOffsetsBeforeChildHandleOffsets() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MHZ_PULLEY_LIFT)).thenReturn(renderer);
        LevelManager levelManager = mock(LevelManager.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        MhzPulleyLiftObjectInstance pulley = new MhzPulleyLiftObjectInstance(new ObjectSpawn(
                0x1800, 0x0600, MHZ_PULLEY_LIFT, 1, 0, false, 0));
        pulley.setServices(new TestObjectServices().withLevelManager(levelManager));
        TestablePlayableSprite player = fallingPlayerAt(0x1900, 0x0660);

        pulley.update(0, player);
        pulley.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(4, 0x1800, 0x0600, false, false);
        verify(renderer).drawFrameIndex(0, 0x1800, 0x0640, false, false);
        verify(renderer).drawFrameIndex(5, 0x17F0, 0x0678, false, false);
        verify(renderer).drawFrameIndex(6, 0x1810, 0x0678, false, false);
        verify(renderer).drawFrameIndex(7, 0x17CE, 0x062A, false, false);
        verify(renderer).drawFrameIndex(7, 0x1832, 0x062C, true, false);
    }

    private static TestablePlayableSprite fallingPlayerAt(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setYSpeed((short) 0x200);
        player.setXSpeed((short) 0x100);
        player.setGSpeed((short) 0x100);
        player.setAir(true);
        return player;
    }

    private static final class RecordingServices extends TestObjectServices {
        private final List<Integer> sfx = new ArrayList<>();

        @Override
        public void playSfx(int soundId) {
            sfx.add(soundId);
        }

        private boolean playedSfx(int soundId) {
            return sfx.contains(soundId);
        }

        private int sfxCount(int soundId) {
            return (int) sfx.stream()
                    .filter(id -> id == soundId)
                    .count();
        }

        private void clear() {
            sfx.clear();
        }
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
