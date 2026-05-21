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
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(Sonic2AnimationIds.FLOAT2.id(), tails.getAnimationId());
        assertEquals(0, tails.getYSpeed());
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
        assertEquals(Sonic2AnimationIds.HANG2.id(), tails.getAnimationId());
        assertEquals(1, intField(vineSwitch, "mappingFrame"));
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
