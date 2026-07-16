package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzFinalEggCapsule {
    @BeforeEach void initGraphics() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x1000, 0);
    }
    @AfterEach void resetGraphics() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void finalResultsCapsuleOwnsADedicatedRealSstGraph() {
        assertFalse(AbstractS3kUprightEggCapsuleInstance.class.isAssignableFrom(FbzEndEggCapsuleInstance.class),
                "FBZ must not widen the collapsed shared upright capsule");
        assertTrue(SolidObjectProvider.class.isAssignableFrom(FbzEndEggCapsuleInstance.class));
        assertTrue(RewindRecreatable.class.isAssignableFrom(FbzEndEggCapsuleButtonInstance.class));
        assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(FbzEndEggCapsuleFragmentInstance.class));
        assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(FbzEndEggCapsuleAnimalInstance.class));
        assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(FbzEndEggCapsuleExplosionController.class));
    }

    @Test void buttonIsClassifiedByItsExactGraphRewindCoverage() {
        var result = com.openggf.game.rewind.RewindRoundTripHarness.probeClass(
                FbzEndEggCapsuleButtonInstance.class.getName());
        var graph = assertInstanceOf(
                com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult.GraphCovered.class,
                result, () -> "button graph coverage: " + result);
        assertEquals("com.openggf.game.sonic3k.objects.TestFbzFinalEggCapsule",graph.evidence());
    }

    @Test void placedPrisonAnimalUsesItsSpawnOnlyRecreatePath() {
        assertTrue(SpawnRewindRecreatable.class.isAssignableFrom(
                FbzEggPrisonAnimalInstance.class));
        var result = com.openggf.game.rewind.RewindRoundTripHarness.probeClass(
                FbzEggPrisonAnimalInstance.class.getName());
        assertInstanceOf(
                com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult.Passed.class,
                result, () -> "placed prison animal rewind result: " + result);
    }

    @Test void nativeTablesAndSamePassInitializationAreExact() {
        assertArrayEquals(new int[]{0, -16, 16, -24, 24}, FbzEndEggCapsuleInstance.fragmentOffsets());
        assertArrayEquals(new int[]{0, -8, 8, 16, -16, -24, 24, -4, 4},
                FbzEndEggCapsuleInstance.animalOffsets());
        Harness h = harness();
        FbzEndEggCapsuleInstance capsule = h.addCapsule(40);

        h.manager.update(0x2F80, h.player, null, 1);
        FbzEndEggCapsuleButtonInstance button = h.manager
                .activeObjectsOfType(FbzEndEggCapsuleButtonInstance.class).getFirst();
        assertEquals(41, button.getSlotIndex(), "CreateChild1_Normal uses after-current SST order");
        assertTrue(button.initializedForTest());
        assertEquals(0, button.activeUpdatesForTest(), "setup+draw does not run the button solid routine");

        capsule.signalButtonPressedForTest();
        h.manager.update(0x2F80, h.player, null, 2);
        assertTrue(capsule.isOpenedForTest(), () -> "active=" + h.manager.getActiveObjects().stream()
                .map(ObjectInstance::getName).toList());
        assertEquals(5, capsule.fragmentAttemptsForTest());
        assertEquals(42, capsule.firstFragmentSlotForTest());
        assertEquals(List.of(42, 43, 44, 45, 46), h.manager
                .activeObjectsOfType(FbzEndEggCapsuleFragmentInstance.class).stream()
                .map(AbstractObjectInstance::getSlotIndex).toList(), () -> "active="
                + h.manager.getActiveObjects().stream().map(object -> object.getName() + "@"
                + (object instanceof AbstractObjectInstance a ? a.getSlotIndex() : -1)).toList());
        assertEquals(List.of(47, 48, 49, 50, 51, 52, 53, 54, 55), h.manager
                .activeObjectsOfType(FbzEndEggCapsuleAnimalInstance.class).stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(56, h.manager.activeObjectsOfType(FbzEndEggCapsuleExplosionController.class)
                .getFirst().getSlotIndex());
        assertEquals(1, h.manager.activeObjectsOfType(
                FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion.class).size(),
                "Obj_NormalExpControl performs its first allocation attempt on its creation SST entry");
        assertTrue(h.manager.activeObjectsOfType(FbzEndEggCapsuleFragmentInstance.class).stream()
                .allMatch(fragment -> fragment.activeUpdatesForTest() == 0));
        assertTrue(h.manager.activeObjectsOfType(FbzEndEggCapsuleAnimalInstance.class).stream()
                .allMatch(animal -> animal.activeUpdatesForTest() == 0));
    }

    @Test void timer40UnderflowAttemptsResultsOnTheSixtyFifthRoutineEntryInLowestFreeSlot() {
        Harness h = harness();
        FbzEndEggCapsuleInstance capsule = h.addCapsule(40);
        h.manager.update(0x2F80, h.player, null, 1);
        capsule.signalButtonPressedForTest();
        h.manager.update(0x2F80, h.player, null, 2);

        for (int i = 0; i < 64; i++) h.manager.update(0x2F80, h.player, null, 3 + i);
        assertFalse(capsule.resultsAllocationAttemptedForTest());
        h.manager.update(0x2F80, h.player, null, 67);

        assertTrue(capsule.resultsAllocationAttemptedForTest());
        S3kResultsScreenObjectInstance results = h.manager
                .activeObjectsOfType(S3kResultsScreenObjectInstance.class).getFirst();
        assertEquals(4, results.getSlotIndex(), "AllocateObject must select the lowest free S3K SST slot");
    }

    @Test void fullGraphRoundTripsWithExactSlotsAndForwardReplay() {
        Harness h = harness();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzEndEggCapsuleInstance capsule = h.addCapsule(40);
        h.manager.update(0x2F80, h.player, null, 1);
        capsule.signalButtonPressedForTest();
        h.manager.update(0x2F80, h.player, null, 2);
        List<String> before = graphSignature(h.manager);

        RewindRegistry registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();
        h.manager.update(0x2F80, h.player, null, 3);
        registry.restore(snapshot);

        assertEquals(before, graphSignature(h.manager));
        FbzEndEggCapsuleInstance restoredParent = h.manager
                .activeObjectsOfType(FbzEndEggCapsuleInstance.class).getFirst();
        FbzEndEggCapsuleButtonInstance restoredButton = h.manager
                .activeObjectsOfType(FbzEndEggCapsuleButtonInstance.class).getFirst();
        assertSame(restoredParent,restoredButton.parentForTest(),
                "the real-SST button must restore its exact parent identity");
        h.manager.update(0x2F80, h.player, null, 3);
        assertTrue(h.manager.activeObjectsOfType(FbzEndEggCapsuleInstance.class).getFirst().isOpenedForTest());
    }

    @Test void normalExplosionAllocationFailureConsumesNeitherRngNorBreakSfx() {
        Harness h = harness();
        long seed = h.rng.getSeed();
        FbzEndEggCapsuleExplosionController controller = new FbzEndEggCapsuleExplosionController(
                new ObjectSpawn(0x307C,0x660,0x81,8,0,false,0));
        h.manager.addDynamicObjectAtSlot(controller,127);
        h.manager.update(0x2F80,h.player,null,1);
        assertEquals(seed,h.rng.getSeed(),"Random_Number follows successful CreateChild6_Simple only");
        assertEquals(0,h.services.breakSfxCount);
        assertEquals(0,h.manager.activeObjectsOfType(
                FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion.class).size());
    }

    @Test void fragmentsDrawOnCreationThenApplyMoveSpriteGravityAcrossFlickerMoves() {
        Harness h = harness();
        FbzEndEggCapsuleFragmentInstance finalFragment = new FbzEndEggCapsuleFragmentInstance(
                new ObjectSpawn(0x307C,0x660,0x81,0,0,false,0));
        FbzEggPrisonFragmentInstance placedFragment = new FbzEggPrisonFragmentInstance(
                new ObjectSpawn(0x3090,0x660,0xCF,0,0,false,0));
        h.manager.addDynamicObjectAtSlot(finalFragment,40);
        h.manager.addDynamicObjectAtSlot(placedFragment,41);

        h.manager.update(0x2F80,h.player,null,1);
        assertAll(
                () -> assertTrue(finalFragment.drawVisibleForTest()),
                () -> assertTrue(placedFragment.drawVisibleForTest()),
                () -> assertEquals(0x660,finalFragment.getY()),
                () -> assertEquals(0x660,placedFragment.getY()));

        h.manager.update(0x2F80,h.player,null,2);
        assertAll(
                () -> assertFalse(finalFragment.drawVisibleForTest()),
                () -> assertFalse(placedFragment.drawVisibleForTest()),
                () -> assertEquals(0x307D,finalFragment.getX()),
                () -> assertEquals(0x65F,finalFragment.getY()),
                () -> assertEquals(0x3091,placedFragment.getX()),
                () -> assertEquals(0x65F,placedFragment.getY()),
                () -> assertEquals(0x100,finalFragment.xVelocityForTest()),
                () -> assertEquals(-0xC8,finalFragment.yVelocityForTest()),
                () -> assertEquals(0x100,placedFragment.xVelocityForTest()),
                () -> assertEquals(-0xC8,placedFragment.yVelocityForTest()));

        h.manager.update(0x2F80,h.player,null,3);
        assertAll(
                () -> assertTrue(finalFragment.drawVisibleForTest()),
                () -> assertTrue(placedFragment.drawVisibleForTest()),
                () -> assertEquals(0x307E,finalFragment.getX()),
                () -> assertEquals(0x65E,finalFragment.getY()),
                () -> assertEquals(0x3092,placedFragment.getX()),
                () -> assertEquals(0x65E,placedFragment.getY()),
                () -> assertEquals(-0x90,finalFragment.yVelocityForTest()),
                () -> assertEquals(-0x90,placedFragment.yVelocityForTest()));

        h.manager.update(0x2F80,h.player,null,4);
        h.manager.update(0x2F80,h.player,null,5);
        assertAll(
                () -> assertEquals(0x3080,finalFragment.getX()),
                () -> assertEquals(0x65D,finalFragment.getY()),
                () -> assertEquals(0x3094,placedFragment.getX()),
                () -> assertEquals(0x65D,placedFragment.getY()),
                () -> assertEquals(-0x20,finalFragment.yVelocityForTest()),
                () -> assertEquals(-0x20,placedFragment.yVelocityForTest()));
    }

    @Test void finalFragmentUsesTheNativeUnsignedVerticalCull() {
        Harness h = harness();
        FbzEndEggCapsuleFragmentInstance fragment = new FbzEndEggCapsuleFragmentInstance(
                new ObjectSpawn(0x307C,0x580,0x81,0,0,false,0));
        h.manager.addDynamicObjectAtSlot(fragment,40);
        h.manager.update(0x2F80,h.player,null,1);
        assertFalse(fragment.isDestroyed());
        h.manager.update(0x2F80,h.player,null,2);
        assertTrue(fragment.isDestroyed(),"(y-Camera_Y+$80) below zero is unsigned-higher than $200");
    }

    @Test void bothAnimalRoutinesDeferLightGravityUntilTheUpdateAfterTimerUnderflow() {
        Harness h = harness();
        FbzEndEggCapsuleAnimalInstance finalAnimal = new FbzEndEggCapsuleAnimalInstance(
                new ObjectSpawn(0x307C,0x660,0x81,0,0,false,0));
        FbzEggPrisonAnimalInstance placedAnimal = new FbzEggPrisonAnimalInstance(
                new ObjectSpawn(0x3090,0x660,0xCF,0,0,false,0));
        finalAnimal.setServices(h.services);
        placedAnimal.setServices(h.services);

        finalAnimal.update(0,h.player);
        placedAnimal.update(0,h.player);
        assertAll(
                () -> assertEquals(5,finalAnimal.getPriorityBucket()),
                () -> assertEquals(5,placedAnimal.getPriorityBucket()),
                () -> assertEquals(0x660,finalAnimal.getY()),
                () -> assertEquals(0x660,placedAnimal.getY()));

        finalAnimal.update(1,h.player); // loc_8683E / loc_89D02: install next code only
        placedAnimal.update(1,h.player);
        assertAll(
                () -> assertEquals(1,finalAnimal.getPriorityBucket()),
                () -> assertEquals(1,placedAnimal.getPriorityBucket()),
                () -> assertEquals(0x307C,finalAnimal.getX()),
                () -> assertEquals(0x660,finalAnimal.getY()),
                () -> assertEquals(0x3090,placedAnimal.getX()),
                () -> assertEquals(0x660,placedAnimal.getY()),
                () -> assertEquals(-0x380,finalAnimal.yVelocityForTest()),
                () -> assertEquals(-0x380,placedAnimal.yVelocityForTest()));

        finalAnimal.update(2,h.player); // loc_86854 / loc_89D18
        placedAnimal.update(2,h.player);
        assertAll(
                () -> assertEquals(0x307E,finalAnimal.getX()),
                () -> assertEquals(0x65C,finalAnimal.getY()),
                () -> assertEquals(0x3092,placedAnimal.getX()),
                () -> assertEquals(0x65C,placedAnimal.getY()),
                () -> assertEquals(-0x360,finalAnimal.yVelocityForTest()),
                () -> assertEquals(-0x360,placedAnimal.yVelocityForTest()));
    }

    @Test void bothAnimalWaitRoutinesStillRunSpriteCheckDelete() {
        Harness h = harness();
        FbzEndEggCapsuleAnimalInstance finalAnimal = new FbzEndEggCapsuleAnimalInstance(
                new ObjectSpawn(0x4000,0x660,0x81,2,0,false,0));
        FbzEggPrisonAnimalInstance placedAnimal = new FbzEggPrisonAnimalInstance(
                new ObjectSpawn(0x4100,0x660,0xCF,2,0,false,0));
        finalAnimal.setServices(h.services);
        placedAnimal.setServices(h.services);

        finalAnimal.update(0,h.player);
        placedAnimal.update(0,h.player);
        finalAnimal.update(1,h.player);
        placedAnimal.update(1,h.player);

        assertTrue(finalAnimal.isDestroyed(),"loc_86850 tail-jumps to Sprite_CheckDelete while waiting");
        assertTrue(placedAnimal.isDestroyed(),"loc_89D14 tail-jumps to Sprite_CheckDelete while waiting");
    }

    @Test void finalButtonPressChangesOnlyItsMappingNotItsPositionOrSolidDimensions() {
        Harness h = harness();
        FbzEndEggCapsuleButtonInstance button = new FbzEndEggCapsuleButtonInstance(
                new ObjectSpawn(0x307C,0x63C,0x81,0,0,false,0),null);
        button.setServices(h.services);
        int y = button.getY();
        SolidObjectParams params = button.getSolidParams();
        button.update(0,null);
        button.update(1,null);
        assertEquals(y,button.getY());
        assertEquals(params,button.getSolidParams());
    }

    @Test void finalCapsuleGraphUsesTheNativePriorityWords() {
        FbzEndEggCapsuleInstance capsule = new FbzEndEggCapsuleInstance(0x307C,0x660);
        FbzEndEggCapsuleButtonInstance button = new FbzEndEggCapsuleButtonInstance(
                new ObjectSpawn(0x307C,0x63C,0x81,0,0,false,0),capsule);
        FbzEndEggCapsuleFragmentInstance finalFragment = new FbzEndEggCapsuleFragmentInstance(
                new ObjectSpawn(0x307C,0x658,0x81,0,0,false,0));
        FbzEggPrisonFragmentInstance prisonFragment = new FbzEggPrisonFragmentInstance(
                new ObjectSpawn(0x3090,0x658,0xCF,0,0,false,0));

        assertAll(
                () -> assertEquals(4,capsule.getPriorityBucket(),"$200"),
                () -> assertEquals(4,button.getPriorityBucket(),"$200"),
                () -> assertEquals(3,finalFragment.getPriorityBucket(),"$180"),
                () -> assertEquals(2,prisonFragment.getPriorityBucket(),"$100"));
    }

    @Test void finalAnimalsRetargetTowardTheNearestNativePlayerOnEveryFloorHit() {
        AbstractPlayableSprite p1=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite p2=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        AtomicInteger p1X=new AtomicInteger(0x3000);
        AtomicInteger p2X=new AtomicInteger(0x3400);
        when(p1.getCentreX()).thenAnswer(ignored->(short)p1X.get());
        when(p2.getCentreX()).thenAnswer(ignored->(short)p2X.get());
        when(extra.getCentreX()).thenReturn((short)0x307D);
        GameStateManager gameState=new GameStateManager();
        gameState.setEndOfLevelActive(false);
        FbzEndEggCapsuleAnimalInstance animal=new FbzEndEggCapsuleAnimalInstance(
                new ObjectSpawn(0x3080,0x660,0x81,0,0,false,0));
        animal.setServices(new StubObjectServices(){
            @Override public GameStateManager gameState(){return gameState;}
            @Override public ObjectPlayerQuery playerQuery(){
                return new ObjectPlayerQuery(()->p1,()->List.of(p2,extra));
            }
        });

        animal.retargetAfterNegativeFloorHit();
        assertEquals(-0x200,animal.xVelocityForTest(),
                "loc_8686C installs the native left velocity before testing _unkFAA8");
        gameState.setEndOfLevelActive(true);
        animal.retargetAfterNegativeFloorHit();
        assertEquals(-0x200,animal.xVelocityForTest(),
                "extra sidekicks cannot displace the closer native P1/P2 target");
        p1X.set(0x3200);
        p2X.set(0x2F00);
        animal.retargetAfterNegativeFloorHit();
        assertEquals(0x200,animal.xVelocityForTest(),
                "each bounce reruns Find_SonicTails instead of retaining the prior direction");
    }

    @Test void preventTailsRespawnDoesNotBlockTheNativeGroundedEndingPoseGate() {
        Harness h=harness();
        when(h.player.isPreventTailsRespawn()).thenReturn(true);
        FbzEndEggCapsuleInstance capsule=h.addCapsule(40);
        h.manager.update(0x2F80,h.player,null,0);
        capsule.signalButtonPressedForTest();
        for(int frame=1;frame<67;frame++)h.manager.update(0x2F80,h.player,null,frame);

        assertTrue(capsule.resultsAllocationAttemptedForTest(),
                "Check_TailsEndPose tests status/death, air, and routine only");
    }

    @Test void openLocksNativeP2AndEndingPoseHandshakeRetriesUntilEligible() {
        AbstractPlayableSprite p2 = mock(AbstractPlayableSprite.class);
        SidekickCpuController controller = mock(SidekickCpuController.class);
        AtomicBoolean air = new AtomicBoolean();
        AtomicBoolean dead = new AtomicBoolean();
        when(p2.getCpuController()).thenReturn(controller);
        when(p2.getAir()).thenAnswer(ignored -> air.get());
        when(p2.getDead()).thenAnswer(ignored -> dead.get());
        Harness h = harness(p2);
        FbzEndEggCapsuleInstance capsule = h.addCapsule(40);
        h.manager.update(0x2F80,h.player,null,1);
        capsule.signalButtonPressedForTest();
        h.manager.update(0x2F80,h.player,null,2);
        verify(controller).setController2SignedLocked(true);

        for(int i=0;i<65;i++) h.manager.update(0x2F80,h.player,null,3+i);
        assertTrue(capsule.resultsAllocationAttemptedForTest());
        air.set(true);
        capsule.update(68,h.player); // airborne: retry remains armed
        air.set(false);
        dead.set(true);
        capsule.update(69,h.player); // grounded but dead: retry remains armed
        assertFalse(capsule.tailsEndingPoseAppliedForTest());
        verify(controller,never()).queueNativeEndingPoseForNextPlayerSlot();

        dead.set(false);
        capsule.update(70,h.player);
        assertTrue(capsule.tailsEndingPoseAppliedForTest());
        InOrder order = inOrder(controller);
        order.verify(controller).setController2SignedLocked(true);
        order.verify(controller).setController2SignedLocked(false);
        order.verify(controller).queueNativeEndingPoseForNextPlayerSlot();
    }

    @Test void capsuleDoesNotConsumeResultsOwnedExitFlags() {
        Harness h = harness();
        FbzEndEggCapsuleInstance capsule = h.addCapsule(40);
        h.manager.update(0x2F80,h.player,null,1);
        capsule.signalButtonPressedForTest();
        h.manager.update(0x2F80,h.player,null,2);
        for(int i=0;i<65;i++) h.manager.update(0x2F80,h.player,null,3+i);

        h.services.gameState.setEndOfLevelFlag(true);
        h.services.gameState.setEndOfLevelActive(false);
        capsule.update(68,h.player);

        assertTrue(h.services.gameState.isEndOfLevelFlag(),
                "Obj_LevelResults owns publishing End_of_level_flag to the transition consumer");
        assertFalse(h.services.gameState.isEndOfLevelActive(),
                "the capsule must not rewrite the results object's exit ordering");
    }

    @Test void unexpectedMainPlayerQueryFailureIsNotHiddenByResultsEligibility() throws Exception {
        FbzEndEggCapsuleInstance capsule=new FbzEndEggCapsuleInstance(0x307C,0x660);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenThrow(new IllegalStateException("broken query"));
        capsule.setServices(services);
        setField(capsule,"initialized",true);
        setField(capsule,"opened",true);
        setField(capsule,"postOpenTimer",0);

        assertThrows(IllegalStateException.class,()->capsule.update(0,null));
    }

    private static void setField(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target,value);
    }

    private static List<String> graphSignature(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast)
                .filter(object -> object.getName().startsWith("FBZEndEgg"))
                .map(object -> object.getSlotIndex() + ":" + object.getClass().getSimpleName()
                        + ":" + object.getX() + ":" + object.getY())
                .sorted().toList();
    }

    private static Harness harness() {
        return harness(null);
    }

    private static Harness harness(AbstractPlayableSprite nativeP2) {
        ObjectManager[] holder = new ObjectManager[1];
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getAir()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x2F80);
        when(camera.getY()).thenReturn((short) 0x600);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        GameStateManager gameState = new GameStateManager();
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        RecordingServices services = new RecordingServices(holder,player,nativeP2,camera,gameState,rng);
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        return new Harness(manager, player, rng, services);
    }

    private static final class RecordingServices extends StubObjectServices {
        private final ObjectManager[] holder;
        private final AbstractPlayableSprite player;
        private final AbstractPlayableSprite nativeP2;
        private final Camera camera;
        private final GameStateManager gameState;
        private final GameRng rng;
        private int breakSfxCount;
        private RecordingServices(ObjectManager[] holder, AbstractPlayableSprite player,
                AbstractPlayableSprite nativeP2, Camera camera,
                GameStateManager gameState, GameRng rng) {
            this.holder=holder;this.player=player;this.nativeP2=nativeP2;
            this.camera=camera;this.gameState=gameState;this.rng=rng;
        }
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> player,
                        () -> nativeP2 == null ? List.of() : List.of(nativeP2));
            }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public GameRng rng() { return rng; }
            @Override public int currentAct() { return 1; }
            @Override public void playSfx(int id) {
                if(id==com.openggf.game.sonic3k.audio.Sonic3kSfx.BREAK.id)breakSfxCount++;
            }
    }

    private record Harness(ObjectManager manager, AbstractPlayableSprite player, GameRng rng,
                           RecordingServices services) {
        FbzEndEggCapsuleInstance addCapsule(int slot) {
            FbzEndEggCapsuleInstance capsule = new FbzEndEggCapsuleInstance(0x307C, 0x660);
            manager.addDynamicObjectAtSlot(capsule, slot);
            return capsule;
        }
    }
}
