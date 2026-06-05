package com.openggf.game.sonic2.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.badniks.SlicerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SlicerPincerInstance;
import com.openggf.game.sonic2.objects.badniks.SpinyBadnikInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2ARZBossInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic2TriggerParticipation {

    @Test
    void arrowShooterDetectsQueryOnlySidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1010, 0x1000);
        ArrowShooterObjectInstance shooter = new ArrowShooterObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x22, 0, 0, false, 0),
                "ArrowShooter");
        shooter.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        shooter.update(0, main);

        assertEquals(1, intField(shooter, "currentAnim"),
                "Arrow Shooter should use ObjectPlayerQuery participants for detection");
    }

    @Test
    void barrierRisesForQueryOnlySidekickInDetectionZone() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0F00, 0x1000);
        BarrierObjectInstance barrier = new BarrierObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x2D, 0, 0, false, 0),
                "Barrier");
        barrier.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        barrier.update(0, main);

        assertEquals(0x0FF8, barrier.getY(),
                "Barrier should rise when a query participant enters its detection zone");
    }

    @Test
    void barrierSolidBottomBoundsUseLiveRollingRadius() {
        BarrierObjectInstance barrier = new BarrierObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.BARRIER, 1, 0, false, 0),
                "Barrier");

        assertTrue(barrier.fullSolidBottomOverlapUsesCurrentYRadiusOnly(null),
                "Obj2D must use S2 SolidObject's live y_radius lower bound");
    }

    @Test
    void nutNativeXSnapsPreservePlayerSubpixel() {
        TestablePlayableSprite main = player("sonic", 0x1000, 0x1000);
        main.setSubpixelRaw(0xCF00, 0x1C00);
        NutObjectInstance nut = new NutObjectInstance(
                new ObjectSpawn(0x1000, 0x1020, Sonic2ObjectIds.NUT, 0, 0, false, 0),
                "Nut");
        nut.setServices(new QueryOnlyPlayerServices(main, List.of()));

        nut.onSolidContact(main, new SolidContact(true, false, false, true, false), 0);
        nut.update(0, main);

        assertEquals(0x1000, main.getCentreX());
        assertEquals(0xCF00, main.getXSubpixelRaw(),
                "Obj69 align writes only x_pos(a1), preserving x_sub");

        main.setCentreXPreserveSubpixel((short) 0x1002);
        nut.onSolidContact(main, new SolidContact(true, false, false, true, false), 1);
        nut.update(1, main);

        assertEquals(0x1000, main.getCentreX());
        assertEquals(0xCF00, main.getXSubpixelRaw(),
                "Obj69 screw movement writes only x_pos(a1), preserving x_sub");
    }

    @Test
    void nutSolidBottomBoundsUseLiveRollingRadius() {
        NutObjectInstance nut = new NutObjectInstance(
                new ObjectSpawn(0x13C0, 0x064C, Sonic2ObjectIds.NUT, 0x15, 0, false, 0),
                "Nut");

        assertTrue(nut.fullSolidBottomOverlapUsesCurrentYRadiusOnly(null),
                "Obj69 SolidObject tail doubles live y_radius(a1), so rolling lower-half contact "
                        + "must not use stand radius");
    }

    @Test
    void wfzPaletteSwitcherUsesQueryOnlySidekickCrossing() {
        TestablePlayableSprite main = player("sonic", 0x0800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0FF0, 0x1000);
        GameStateManager gameState = new GameStateManager();
        WFZPalSwitcherObjectInstance switcher = new WFZPalSwitcherObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x8B, 0, 0, false, 0),
                "WFZPalSwitcher");
        switcher.setServices(new QueryOnlyPlayerServices(main, List.of(tails)).withGameState(gameState));

        switcher.update(0, main);
        tails.setCentreX((short) 0x1010);
        switcher.update(1, main);

        assertTrue(gameState.isWfzFireToggle(),
                "WFZ palette switcher should route sidekick crossing through ObjectPlayerQuery");
    }

    @Test
    void springAppliesCheckpointContactToQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        SpringObjectInstance spring = new SpringObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x41, 0x10, 0, false, 0),
                "Spring");
        spring.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCheckpointBatch(new SolidCheckpointBatch(
                        spring,
                        Map.of(tails, pushingContact()))));

        spring.update(0, main);

        assertEquals(0x1000, tails.getXSpeed() & 0xFFFF,
                "Spring should consume ObjectPlayerQuery participants for manual checkpoint contact");
        assertEquals(0x1000, tails.getGSpeed() & 0xFFFF);
    }

    @Test
    void speedLauncherStartsForQueryOnlyStandingSidekick() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        tails.setOnObject(true);
        SpeedLauncherObjectInstance launcher = new SpeedLauncherObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xC0, 0x01, 0, false, 0),
                "SpeedLauncher");
        launcher.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));
        launcher.onSolidContact(tails, new SolidContact(true, false, false, true, false), 0);

        launcher.update(0, main);

        assertEquals(0x0FF4, launcher.getX(),
                "Speed Launcher should use ObjectPlayerQuery participants when selecting standing riders");
        assertEquals(0x0FF4, tails.getCentreX() & 0xFFFF);
    }

    @Test
    void speedLauncherUsesLatchedStandingMaskOnDestinationFrame() {
        TestablePlayableSprite main = player("sonic", 0x1000, 0x1000);
        main.setOnObject(true);
        SpeedLauncherObjectInstance launcher = new SpeedLauncherObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xC0, 0x01, 0, false, 0),
                "SpeedLauncher");
        launcher.setServices(new QueryOnlyPlayerServices(main, List.of()));
        launcher.onSolidContact(main, new SolidContact(true, false, false, true, false), 0);

        launcher.update(0, main);
        main.setOnObject(false);
        launcher.onSolidContactCleared(main, 1);

        launcher.update(1, main);

        assertTrue(main.getAir(),
                "ObjC0 must launch from its latched standing bit before PlatformObject refreshes it");
        assertEquals(0xF380, main.getXSpeed() & 0xFFFF);
        assertEquals(0xFC00, main.getYSpeed() & 0xFFFF);
    }

    @Test
    void hPropellerPushesQueryOnlySidekick() {
        OscillationManager.reset();
        TestablePlayableSprite main = player("sonic", 0x1800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x0FB0);
        HPropellerObjectInstance propeller = new HPropellerObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xB5, 0x66, 0, false, 0));
        propeller.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        propeller.update(0, main);

        assertTrue(tails.getAir(),
                "Horizontal propeller should use ObjectPlayerQuery participants for push checks");
        assertEquals(0x0FAF, tails.getCentreY() & 0xFFFF,
                "ObjB5 adds the computed push to native y_pos, not sprite top-left bounds");
        assertEquals(Sonic2AnimationIds.FLOAT2.id(), tails.getAnimationId());
        assertEquals(0, tails.getYSpeed());
    }

    @Test
    void wallTurretShotUsesRomObj98SubtypeIdentity() throws Exception {
        ObjectSpawn parentSpawn = new ObjectSpawn(
                0x0100, 0x0080, Sonic2ObjectIds.WALL_TURRET, 0x74, 0, false, 0);
        WallTurretShotInstance shot = new WallTurretShotInstance(
                parentSpawn, 0x0100, 0x0098, 0, 0x0100);

        assertEquals(Sonic2ObjectIds.PROJECTILE, shot.getSpawn().objectId(),
                "ObjB8 should allocate Obj98 for its wall-turret shot child");
        assertEquals(0x8E, shot.getSpawn().subtype(),
                "ObjB8 writes subtype $8E so Obj98 loads ObjB8_SubObjData2");
        assertEquals(4, shot.getOnScreenHalfWidth(),
                "ObjB8_SubObjData2 sets the projectile width_pixels to 4");
        assertEquals(3, intField(shot, "mappingFrame"),
                "ObjB8 parent initializes the projectile mapping_frame to 3");

        shot.update(0, null);
        assertEquals(3, intField(shot, "mappingFrame"),
                "ROM AnimateSprite reads script frame 3 on the first Obj98_WallTurretShotMove update");
        shot.update(1, null);
        shot.update(2, null);
        assertEquals(3, intField(shot, "mappingFrame"),
                "Ani_WallTurretShot duration 2 keeps frame 3 for two wait ticks");
        shot.update(3, null);
        assertEquals(4, intField(shot, "mappingFrame"),
                "Ani_WallTurretShot advances to frame 4 after the duration expires");
    }

    @Test
    void wallTurretFireSpawnsObj98ProjectileChild() {
        TestablePlayableSprite main = player("sonic", 0x0100, 0x00C0);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of());
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, services);
        services.withObjectManager(objectManager);
        WallTurretObjectInstance turret = objectManager.createDynamicObject(
                () -> new WallTurretObjectInstance(
                        new ObjectSpawn(0x0100, 0x0080, Sonic2ObjectIds.WALL_TURRET, 0x74, 0, false, 0),
                        "WallTurret"));

        turret.update(0, main);
        turret.update(1, main);
        turret.update(2, main);

        WallTurretShotInstance shot = objectManager.getActiveObjects().stream()
                .filter(WallTurretShotInstance.class::isInstance)
                .map(WallTurretShotInstance.class::cast)
                .findFirst()
                .orElseThrow();

        assertInstanceOf(WallTurretShotInstance.class, shot);
        assertEquals(Sonic2ObjectIds.PROJECTILE, shot.getSpawn().objectId());
        assertEquals(0x8E, shot.getSpawn().subtype());
        assertEquals(0x0100, shot.getX(), "Centered turret shot should spawn at ObjB8 byte_3BA2A X offset 0");
        assertEquals(0x0098, shot.getY(), "Centered turret shot should spawn at ObjB8 byte_3BA2A Y offset $18");
    }

    @Test
    void wallTurretDetectsClosestQueryOnlySidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x0200, 0x00C0);
        TestablePlayableSprite tails = player("tails", 0x0130, 0x00C0);
        WallTurretObjectInstance turret = new WallTurretObjectInstance(
                new ObjectSpawn(0x0100, 0x0080, Sonic2ObjectIds.WALL_TURRET, 0x74, 0, false, 0),
                "WallTurret");
        turret.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        turret.update(0, main);

        assertEquals(4, intField(turret, "routine"),
                "ObjB8 uses Obj_GetOrientationToPlayer, which chooses the nearest MainCharacter/Sidekick by X");
        assertEquals(2, intField(turret, "fireTimer"));
    }

    @Test
    void wallTurretAimsAndFiresAtClosestQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x0200, 0x00C0);
        TestablePlayableSprite tails = player("tails", 0x0130, 0x00C0);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(tails));
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, services);
        services.withObjectManager(objectManager);
        WallTurretObjectInstance turret = objectManager.createDynamicObject(
                () -> new WallTurretObjectInstance(
                        new ObjectSpawn(0x0100, 0x0080, Sonic2ObjectIds.WALL_TURRET, 0x74, 0, false, 0),
                        "WallTurret"));

        turret.update(0, main);
        turret.update(1, main);
        turret.update(2, main);

        WallTurretShotInstance shot = objectManager.getActiveObjects().stream()
                .filter(WallTurretShotInstance.class::isInstance)
                .map(WallTurretShotInstance.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(0x0111, shot.getX(),
                "ObjB8 should aim right when the closest Obj_GetOrientationToPlayer participant is right of the turret");
        assertEquals(0x0090, shot.getY());
    }

    @Test
    void slidingSpikesTriggerForQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0F60, 0x1000);
        SlidingSpikesObjectInstance spikes = new SlidingSpikesObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x76, 0, 0, false, 0),
                "SlidingSpikes");
        spikes.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        spikes.update(0, main);
        spikes.update(1, main);

        assertEquals(0x0FFF, spikes.getX(),
                "Sliding Spikes should use ObjectPlayerQuery participants for approach detection");
    }

    @Test
    void vineSwitchGrabsQueryOnlySidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1028);
        VineSwitchObjectInstance vineSwitch = new VineSwitchObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x7F, 0, 0, false, 0),
                "VineSwitch");
        vineSwitch.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        vineSwitch.update(0, main);

        assertTrue(tails.isObjectControlled(),
                "Vine Switch should use ObjectPlayerQuery participants for grab checks");
        assertRomObjControlBitOneState(tails, "Vine Switch obj_control=1");
        assertEquals(Sonic2AnimationIds.HANG2.id(), tails.getAnimationId());
        assertEquals(1, intField(vineSwitch, "mappingFrame"));
    }

    @Test
    void movingVineGrabsQueryOnlySidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1088);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(tails));
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, services);
        services.withObjectManager(objectManager);
        MovingVineObjectInstance vine = objectManager.createDynamicObject(
                () -> new MovingVineObjectInstance(
                        new ObjectSpawn(0x1000, 0x1000, 0x80, 0, 0, false, 0),
                        "MovingVine"));

        vine.update(0, main);

        assertTrue(tails.isObjectControlled(),
                "Moving Vine should use ObjectPlayerQuery participants for grab checks");
        assertRomObjControlBitOneState(tails, "Moving Vine obj_control=1");
        assertEquals(Sonic2AnimationIds.HANG2.id(), tails.getAnimationId());
        assertTrue((boolean) field(vine, "player2Grabbed"),
                "Obj80 native P2 grab byte must be driven by sidekick participants");
    }

    @Test
    void wfzGrabObjectGrabsQueryOnlySidekickAsNativeP2() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1008);
        GrabObjectInstance grab = new GrabObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.GRAB, 0, 0, false, 0),
                "Grab");
        grab.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        grab.update(0, main);

        assertFalse(main.isObjectControlled());
        assertRomObjControlBitOneState(tails, "ObjD9 native P2 grab");
        assertEquals(0x1000, tails.getCentreY() & 0xFFFF,
                "ObjD9 writes object y_pos directly to player y_pos");
        assertEquals(Sonic2AnimationIds.HANG2.id(), tails.getAnimationId());
        assertTrue((boolean) field(grab, "player2Grabbed"),
                "ObjD9 objoff_31 must be driven by the sidekick participant");
    }

    @Test
    void wfzGrabObjectUsesRomRenderWidth() {
        GrabObjectInstance grab = new GrabObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.GRAB, 0, 0, false, 0),
                "Grab");

        assertEquals(0x18, grab.getOnScreenHalfWidth(),
                "ObjD9_Init sets width_pixels=$18 for MarkObjGone3/render bounds");
    }

    @Test
    void wfzGrabObjectReleasesQueryOnlySidekickWithDirectionCooldown() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1008);
        GrabObjectInstance grab = new GrabObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.GRAB, 0, 0, false, 0),
                "Grab");
        grab.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        grab.update(0, main);
        tails.setJumpInputPressed(true, true);
        tails.setDirectionalInputPressed(false, false, true, false);
        grab.update(1, main);

        assertFalse(tails.isObjectControlled());
        assertEquals(0xFD00, tails.getYSpeed() & 0xFFFF);
        assertFalse((boolean) field(grab, "player2Grabbed"));
        assertEquals(60, intField(grab, "player2ReleaseDelay"));
    }

    @Test
    void breakablePlatingGrabUsesRomObjControlBitOneState() throws Exception {
        TestablePlayableSprite player = player("sonic", 0x1000, 0x1000);
        BreakablePlatingObjectInstance plating = new BreakablePlatingObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xC1, 0, 0, false, 0),
                "BreakablePlating");

        Method grabPlayer = BreakablePlatingObjectInstance.class
                .getDeclaredMethod("grabPlayer", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        grabPlayer.setAccessible(true);
        grabPlayer.invoke(plating, player);

        assertRomObjControlBitOneState(player, "Breakable Plating obj_control=1");
    }

    @Test
    void wfzBreakablePlatingTouchSignalIsNativeMainCharacterOnly() {
        TestablePlayableSprite main = player("sonic", 0x1000, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        BreakablePlatingObjectInstance plating = new BreakablePlatingObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.BREAKABLE_PLATING, 0, 0, false, 0),
                "BreakablePlating");
        plating.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        plating.onTouchResponse(tails, null, 0);
        plating.update(0, main);

        assertFalse(main.isObjectControlled(),
                "ObjC1 reads MainCharacter after collision_property; Sidekick touch must not grab P1");
        assertFalse(tails.isObjectControlled(),
                "ObjC1 has no Sidekick grab branch");
    }

    @Test
    void wfzBreakablePlatingNativeMainTouchStillGrabs() {
        TestablePlayableSprite main = player("sonic", 0x1000, 0x1000);
        BreakablePlatingObjectInstance plating = new BreakablePlatingObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.BREAKABLE_PLATING, 0, 0, false, 0),
                "BreakablePlating");
        plating.setServices(new QueryOnlyPlayerServices(main, List.of()));

        plating.onTouchResponse(main, null, 0);
        plating.update(0, main);

        assertRomObjControlBitOneState(main, "Breakable Plating native P1 grab");
        assertEquals(0x0FEC, main.getCentreX() & 0xFFFF);
        assertEquals(Sonic2AnimationIds.HANG.id(), main.getAnimationId());
    }

    @Test
    void oozPoppingPlatformLockUsesRomObjControlBitOneState() throws Exception {
        TestablePlayableSprite player = player("sonic", 0x1000, 0x1000);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(player, List.of());
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, services);
        services.withObjectManager(objectManager);
        OOZPoppingPlatformObjectInstance platform = objectManager.createDynamicObject(
                () -> new OOZPoppingPlatformObjectInstance(
                        new ObjectSpawn(0x1000, 0x1000, 0x33, 1, 0, false, 0),
                        "OOZPoppingPlatform"));

        Method lockPlayer = OOZPoppingPlatformObjectInstance.class
                .getDeclaredMethod("lockPlayer", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        lockPlayer.setAccessible(true);
        lockPlayer.invoke(platform, player);

        assertRomObjControlBitOneState(player, "OOZ Popping Platform obj_control=1");
    }

    @Test
    void flipperAppliesCheckpointContactToQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1010, 0x1000);
        FlipperObjectInstance flipper = new FlipperObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x86, 0x01, 0, false, 0),
                "Flipper");
        flipper.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCheckpointBatch(new SolidCheckpointBatch(
                        flipper,
                        Map.of(tails, pushingContact()))));

        flipper.update(0, main);

        assertEquals(0x1000, tails.getXSpeed() & 0xFFFF,
                "Flipper should consume ObjectPlayerQuery participants for manual checkpoint contact");
        assertEquals(0x1000, tails.getGSpeed() & 0xFFFF);
    }

    @Test
    void verticalFlipperMovementSuppressionPreservesExistingObjectControlBits() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        tails.applyObjectControlState(ObjectControlState.nativeBits0To6CpuAllowedMovementActive());
        FlipperObjectInstance flipper = new FlipperObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x86, 0x00, 0, false, 0),
                "Flipper");
        flipper.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCheckpointBatch(new SolidCheckpointBatch(
                        flipper,
                        Map.of(tails, standingContact()))));

        flipper.update(0, main);

        assertTrue(tails.isObjectControlled(),
                "Obj86's local movement suppression must not clear unrelated object-control ownership");
        assertTrue(tails.isObjectControlAllowsCpu(),
                "Obj86 must preserve bit-0-to-6 CPU allowance from the owning object");
        assertTrue(tails.isObjectControlSuppressesMovement(),
                "Obj86 still suppresses movement while standing on the vertical flipper");
        assertFalse(tails.isTouchResponseSuppressedByObjectControl(),
                "Preserved bit-0-to-6 control must keep touch response active");
    }

    @Test
    void springboardLaunchesQueryOnlySidekick() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        SpringboardObjectInstance springboard = new SpringboardObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x40, 0, 0, false, 0),
                "Springboard");
        springboard.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCheckpointBatch(new SolidCheckpointBatch(
                        springboard,
                        Map.of(tails, standingContact()))));

        springboard.update(0, main);
        springboard.update(1, main);

        assertTrue(tails.getAir(),
                "Springboard should launch sidekick participants from ObjectPlayerQuery");
        assertEquals(0xFB00, tails.getYSpeed() & 0xFFFF);
    }

    @Test
    void lateralCannonDropsQueryOnlyRidingSidekickOnRetract() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, new TestObjectServices());
        LateralCannonObjectInstance cannon = new LateralCannonObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xBE, 0, 0, false, 0),
                "LateralCannon");
        cannon.setServices(new QueryOnlyPlayerServices(main, List.of(tails)).withObjectManager(objectManager));
        objectManager.forceRidingObjectForBootstrap(tails, cannon);
        tails.setOnObject(true);
        tails.setAir(false);

        for (int frame = 0; frame < 190; frame++) {
            cannon.update(frame, main);
        }

        assertFalse(objectManager.isRidingObject(tails, cannon),
                "Lateral cannon should drop sidekick riders from ObjectPlayerQuery on retract");
        assertFalse(tails.isOnObject());
        assertTrue(tails.getAir());
    }

    @Test
    void lateralCannonUsesRomRenderWidth() {
        LateralCannonObjectInstance cannon = new LateralCannonObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.LATERAL_CANNON, 0, 0, false, 0),
                "LateralCannon");

        assertEquals(0x18, cannon.getOnScreenHalfWidth(),
                "ObjBE_SubObjData sets width_pixels=$18 for MarkObjGone/render bounds");
    }

    @Test
    void wfzRivetBustIsNativeMainCharacterOnly() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        tails.setRolling(true);
        RivetObjectInstance rivet = new RivetObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.RIVET, 0, 0, false, 0),
                "Rivet");
        rivet.setServices(new QueryOnlyPlayerServices(main, List.of(tails)).withCamera(focusedCamera(main)));

        rivet.update(0, main);
        rivet.onSolidContact(tails, new SolidContact(true, false, false, true, false), 0);

        assertFalse(rivet.isDestroyed(),
                "ObjC2 reads MainCharacter+anim and has no Sidekick bust path");
    }

    @Test
    void wfzRivetStillBustsForRollingNativeMainCharacter() {
        TestablePlayableSprite main = player("sonic", 0x1000, 0x1000);
        main.setRolling(true);
        RivetObjectInstance rivet = new RivetObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.RIVET, 0, 0, false, 0),
                "Rivet");
        rivet.setServices(new QueryOnlyPlayerServices(main, List.of()).withCamera(focusedCamera(main)));

        rivet.update(0, main);
        rivet.onSolidContact(main, new SolidContact(true, false, false, true, false), 0);

        assertTrue(rivet.isDestroyed(),
                "ObjC2 should still bust when native P1 is rolling on the rivet");
        assertTrue(main.getAir());
        assertFalse(main.isOnObject());
    }

    @Test
    void cnzConveyorWidthUsesRomByteShiftWrap() {
        TestablePlayableSprite player = player("sonic", 0x1D9F, 0x061F);
        player.setAir(false);
        CNZConveyorBeltObjectInstance conveyor = new CNZConveyorBeltObjectInstance(
                new ObjectSpawn(0x1DFA, 0x0620, 0x72, 0x90, 0, false, 0),
                "CNZConveyorBelt");

        conveyor.update(0, player);

        assertEquals(0x1D9F, player.getCentreX() & 0xFFFF,
                "Obj72 stores (subtype & $7F) << 4 into a byte; WFZ subtype $90 wraps width to zero");
    }

    @Test
    void cnzConveyorShiftsNativePositionWhenInsideWrappedBounds() {
        TestablePlayableSprite player = player("sonic", 0x100F, 0x0FF0);
        player.setAir(false);
        CNZConveyorBeltObjectInstance conveyor = new CNZConveyorBeltObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x72, 0x01, 0, false, 0),
                "CNZConveyorBelt");

        conveyor.update(0, player);

        assertEquals(0x1011, player.getCentreX() & 0xFFFF);
    }

    @Test
    void seesawAssignsQueryOnlySidekickToNativeP2StandingSlot() {
        TestablePlayableSprite main = player("sonic", 0x1400, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        SeesawObjectInstance seesaw = new SeesawObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x14, 0xFF, 0, false, 0),
                "Seesaw");
        seesaw.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCheckpointBatch(new SolidCheckpointBatch(
                        seesaw,
                        Map.of(tails, standingContact()))));

        seesaw.update(0, main);

        assertSame(tails, seesaw.getStandingPlayer2(),
                "Seesaw has native P1/P2 standing bits, so P2 must come from ObjectPlayerQuery NATIVE_P1_P2");
    }

    @Test
    void spinyTargetsQueryOnlyClosestSidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1800, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1008, 0x1000);
        SpinyBadnikInstance spiny = new SpinyBadnikInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xA5, 0, 0, false, 0));
        spiny.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        // ROM ObjA5_Init `move.w #$80,objoff_2A` also seeds the adjacent detect
        // lockout byte objoff_2B=$80 (objoff_2A=$2A, objoff_2B=$2B; s2.constants.asm
        // line 133), so loc_38B10 skips detection for the first 128 frames
        // (s2.asm:76362, 76367-76370). Advance past that initial lockout, then the
        // spiny's first detection must resolve the closest sidekick (Tails) and attack.
        for (int i = 0; i <= 0x80; i++) {
            spiny.update(i, main);
        }

        assertEquals("ATTACKING", field(spiny, "state").toString(),
                "Spiny should resolve closest P2 candidates through ObjectPlayerQuery");
    }

    @Test
    void mtzSlicerThrowUsesClosestNativePlayerByRomX() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0x0F00, 0, 0x1100, 0x0200, 0);
        TestablePlayableSprite main = player("sonic", 0x0F90, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1010, 0x1000);
        SlicerBadnikInstance slicer = new SlicerBadnikInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.SLICER, 0, 0, false, 0));
        slicer.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        try {
            // ROM ObjA1_Init (routine 0) consumes the first object frame without
            // running the throw-detection logic (s2.asm:75777-75788); ObjA1_Main
            // (routine 2) first runs on the next frame. Step the INIT frame, then
            // the WALKING frame that exercises Obj_GetOrientationToPlayer.
            slicer.update(0, main);
            slicer.update(0, main);

            assertFalse("THROW_WINDUP".equals(field(slicer, "state").toString()),
                    "ObjA1 must run Obj_GetOrientationToPlayer against nearest native P1/P2, not always P1");
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    @Test
    void mtzSlicerPincerHomesTowardClosestNativePlayerByRomX() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0x0F00, 0, 0x1100, 0x0200, 0);
        TestablePlayableSprite main = player("sonic", 0x0F00, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1010, 0x1000);
        SlicerPincerInstance pincer = new SlicerPincerInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.SLICER_PINCERS, 0, 0, false, 0),
                null,
                0x1000,
                0x1000,
                0,
                false,
                0x78);
        pincer.setServices(new QueryOnlyPlayerServices(main, List.of(tails)));

        try {
            pincer.update(0, main);

            assertEquals(0x10, intField(pincer, "xVelocity"),
                    "ObjA2 homing should accelerate toward nearest native P1/P2 by ROM X");
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    @Test
    void arzBossInitWaitsForQueryOnlyExtendedSidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x2A80, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x2AA0, 0x1000);
        TestablePlayableSprite extraSidekick = player("knuckles", 0x2B80, 0x1000);
        Sonic2ARZBossInstance boss = new Sonic2ARZBossInstance(
                new ObjectSpawn(0x2AE0, 0x0388, 0x89, 0, 0, false, 0));
        boss.setServices(new QueryOnlyPlayerServices(main, List.of(tails, extraSidekick)));

        Method checkInitConditions = Sonic2ARZBossInstance.class
                .getDeclaredMethod("checkInitConditions", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        checkInitConditions.setAccessible(true);

        assertFalse((boolean) checkInitConditions.invoke(boss, main),
                "ARZ boss intro should wait for every engine sidekick exposed through ObjectPlayerQuery");
    }

    @Test
    void tiltingPlatformOrientationUsesQueryPlayersWhenRawSidekickListIsEmpty() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1200, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x0FF0, 0x1000);
        TiltingPlatformObjectInstance platform = new TiltingPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xB6, 0x04, 0, false, 0));
        platform.setServices(new QueryOnlyPlayerServices(main, List.of(tails))
                .withCamera(focusedCamera(main)));

        Method isPlayerToLeft = TiltingPlatformObjectInstance.class
                .getDeclaredMethod("isPlayerToLeft", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        isPlayerToLeft.setAccessible(true);

        assertTrue((boolean) isPlayerToLeft.invoke(platform, main),
                "ObjB6 orientation already uses every engine sidekick, but participants must come from ObjectPlayerQuery");
    }

    @Test
    void tiltingPlatformDropsQueryOnlyRidingSidekick() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x1200, 0x1000);
        TestablePlayableSprite tails = player("tails", 0x1000, 0x1000);
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, new TestObjectServices());
        TiltingPlatformObjectInstance platform = new TiltingPlatformObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0xB6, 0, 0, false, 0));
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(tails))
                .withObjectManager(objectManager);
        services.withCamera(focusedCamera(main));
        platform.setServices(services);
        objectManager.forceRidingObjectForBootstrap(tails, platform);
        tails.setOnObject(true);
        tails.setAir(false);

        Method dropRidingPlayers = TiltingPlatformObjectInstance.class.getDeclaredMethod("dropRidingPlayers");
        dropRidingPlayers.setAccessible(true);
        dropRidingPlayers.invoke(platform);

        assertFalse(objectManager.isRidingObject(tails, platform),
                "ObjB6 drop already applies to engine sidekicks, but participants must come from ObjectPlayerQuery");
        assertFalse(tails.isOnObject());
        assertTrue(tails.getAir());
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) x, (short) y);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        return player;
    }

    private static Camera focusedCamera(TestablePlayableSprite player) {
        Camera camera = mock(Camera.class);
        when(camera.getFocusedSprite()).thenReturn(player);
        return camera;
    }

    private static int intField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void assertRomObjControlBitOneState(TestablePlayableSprite player, String context) {
        assertTrue(player.isObjectControlled(), context + " should set object control");
        assertTrue(player.isObjectControlAllowsCpu(), context + " should allow sidekick CPU dispatch");
        assertTrue(player.isObjectControlSuppressesMovement(), context + " should suppress normal movement");
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                context + " should not suppress touch responses");
    }

    private static PlayerSolidContactResult pushingContact() {
        return new PlayerSolidContactResult(
                ContactKind.SIDE,
                false,
                false,
                true,
                false,
                PreContactState.ZERO,
                new PostContactState((short) 0, (short) 0, false, false, true),
                0);
    }

    private static PlayerSolidContactResult standingContact() {
        return new PlayerSolidContactResult(
                ContactKind.TOP,
                true,
                false,
                false,
                false,
                PreContactState.ZERO,
                new PostContactState((short) 0, (short) 0, false, true, false),
                0);
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;
        private SolidExecutionRegistry solidExecution = SolidExecutionRegistry.inert();
        private ObjectManager objectManager;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        QueryOnlyPlayerServices withObjectManager(ObjectManager objectManager) {
            this.objectManager = objectManager;
            return this;
        }

        QueryOnlyPlayerServices withCheckpointBatch(SolidCheckpointBatch batch) {
            this.solidExecution = new FixedSolidExecutionRegistry(batch);
            return this;
        }

        @Override
        public SolidExecutionRegistry solidExecutionRegistry() {
            return solidExecution;
        }
    }

    private static final class FixedSolidExecutionRegistry implements SolidExecutionRegistry {
        private final SolidCheckpointBatch batch;

        private FixedSolidExecutionRegistry(SolidCheckpointBatch batch) {
            this.batch = batch;
        }

        @Override
        public void beginFrame(int frameCounter, List<? extends PlayableEntity> players) {
        }

        @Override
        public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {
        }

        @Override
        public ObjectSolidExecutionContext currentObject() {
            return new ObjectSolidExecutionContext(this, batch.object(), () -> batch);
        }

        @Override
        public PlayerStandingState previousStanding(ObjectInstance object, PlayableEntity player) {
            return PlayerStandingState.NONE;
        }

        @Override
        public void publishCheckpoint(SolidCheckpointBatch batch) {
        }

        @Override
        public void endObject(ObjectInstance object) {
        }

        @Override
        public void finishFrame() {
        }

        @Override
        public void clearTransientState() {
        }
    }
}
