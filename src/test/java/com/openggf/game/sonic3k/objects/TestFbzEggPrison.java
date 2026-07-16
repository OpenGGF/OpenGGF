package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.sonic3k.objects.badniks.BlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekBadnikInstance;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.SpillAnimationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzEggPrison {
    @BeforeEach void initGraphics(){GraphicsManager.getInstance().initHeadless();}
    @AfterEach void resetGraphics(){GraphicsManager.getInstance().resetState();}
    @Test void buttonIsClassifiedByItsExactGraphRewindCoverage() {
        var result = RewindRoundTripHarness.probeClass(FbzEggPrisonButtonInstance.class.getName());
        var graph = assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.GraphCovered.class, result,
                () -> "button graph coverage: " + result);
        assertEquals("com.openggf.game.sonic3k.objects.TestFbzEggPrison", graph.evidence());
    }
    @Test void nativeChildTablesAreExactForPlacedSubtypes() {
        assertArrayEquals(new int[]{0, 16, -16, 28, -28}, FbzEggPrisonInstance.animalOffsets());
        assertArrayEquals(new int[]{-8, 8, 16, -16, 24, -24}, FbzEggPrisonInstance.ringOffsets());
        assertArrayEquals(new int[]{-24, 24}, FbzEggPrisonInstance.blasterOffsets());
        assertArrayEquals(new int[]{-8, 8}, FbzEggPrisonInstance.technoSqueekOffsets());
        assertArrayEquals(new int[]{0, -16, 16, -24, 24}, FbzEggPrisonInstance.fragmentOffsets());
    }

    @Test void subtypeDispatchCountsMatchSub89Dac() {
        assertEquals(new FbzEggPrisonInstance.ReleaseCounts(5, 0, 0, 0),
                FbzEggPrisonInstance.releaseCounts(0));
        assertEquals(new FbzEggPrisonInstance.ReleaseCounts(0, 6, 0, 0),
                FbzEggPrisonInstance.releaseCounts(1));
        assertEquals(new FbzEggPrisonInstance.ReleaseCounts(0, 0, 2, 2),
                FbzEggPrisonInstance.releaseCounts(2));
    }

    @Test void bodyHasTheNativeSolidDimensionsAndButtonIsARealChildType() {
        var prison = new FbzEggPrisonInstance(new ObjectSpawn(0x3000, 0x700, 0xCF, 2, 0, false, 1));
        assertEquals(0x2B, prison.getSolidParams().halfWidth());
        assertEquals(0x18, prison.getSolidParams().airHalfHeight());
        var button = new FbzEggPrisonButtonInstance(
                new ObjectSpawn(0x3000, 0x700 - 0x24, 0, 0, 0, false, 0), prison);
        assertEquals(0x1B, button.getSolidParams().halfWidth());
        assertEquals(0x700 - 0x24, button.getY());
    }

    @Test void springPlungerLaunchesNativePlayersBeforeSafeExtraParticipants() {
        AbstractPlayableSprite main=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite nativeP2=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2,extra)));
        when(services.solidExecution()).thenReturn(ObjectSolidExecutionContext.inert());
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        plunger.setServices(services);
        SolidContact standing=new SolidContact(true,false,false,true,false);
        plunger.onSolidContact(main,standing,0);
        plunger.onSolidContact(nativeP2,standing,0);
        plunger.onSolidContact(extra,standing,0);

        plunger.update(1,main);

        assertEquals(0xC,plunger.mappingFrame());
        for(AbstractPlayableSprite participant:List.of(main,nativeP2,extra)) {
            verify(participant).setYSpeed((short)-0xA00);
            verify(participant).setAir(true);
            verify(participant).setOnObject(false);
        }
        verify(services,times(3)).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
    }

    @Test void springPlungerStandingLaunchReturnsAGroundedHurtPlayerToRoutineTwo() {
        TestPlayableSprite player=new TestPlayableSprite();
        player.setHurt(true);
        player.setAir(false);
        player.setOnObject(true);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(()->player,List::of));
        when(services.solidExecution()).thenReturn(ObjectSolidExecutionContext.inert());
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        plunger.setServices(services);
        plunger.onSolidContact(player,new SolidContact(true,false,false,true,false),0);

        plunger.update(1,player);

        assertAll(
                ()->assertFalse(player.isHurt(),
                        "sub_8635E writes routine=2 instead of retaining the hurt routine"),
                ()->assertTrue(player.getAir()),
                ()->assertFalse(player.isOnObject()),
                ()->assertEquals((short)-0xA00,player.getYSpeed()),
                ()->assertEquals(0x10,player.getAnimationId()));
    }

    @Test void springPlungerResolvesSolidObjectFullAtItsManualSstCheckpoint() {
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));

        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT,plunger.solidExecutionMode(),
                "loc_89C86 must consume SolidObjectFull standing bits in the same SST entry");
    }

    @Test void extraStandingAloneUsesPressedFrameWithoutBecomingNativeAuthority() {
        AbstractPlayableSprite main=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite nativeP2=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2,extra)));
        when(services.solidExecution()).thenReturn(ObjectSolidExecutionContext.inert());
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        plunger.setServices(services);
        plunger.onSolidContact(extra,new SolidContact(true,false,false,true,false),0);

        plunger.update(1,main);

        assertEquals(6,plunger.mappingFrame());
        verify(extra).setYSpeed((short)-0xA00);
        verify(main,never()).setYSpeed(anyShort());
        verify(nativeP2,never()).setYSpeed(anyShort());
    }

    @Test void subtypeTwoBuildsTheExactRealSstReleaseGraph() {
        Harness h=harness();
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,2,0,true,0,77));
        h.manager.addDynamicObjectAtSlot(prison,40);
        h.manager.update(0x2F00,null,null,1);
        assertEquals(1,h.manager.activeObjectsOfType(FbzEggPrisonButtonInstance.class).size());

        prison.triggerFromButton(h.player);
        h.manager.update(0x2F00,null,null,2);

        assertEquals(2,h.manager.activeObjectsOfType(BlasterBadnikInstance.class).size());
        assertEquals(2,h.manager.activeObjectsOfType(TechnoSqueekBadnikInstance.class).size());
        assertEquals(1,h.manager.activeObjectsOfType(FbzEggPrisonExplosionController.class).size());
        assertEquals(1,h.manager.activeObjectsOfType(
                FbzEndEggCapsuleExplosionController.FbzEndEggCapsuleNormalExplosion.class).size(),
                "Obj_NormalExpControl performs its first attempt on the controller's creation entry");
        assertEquals(5,h.manager.activeObjectsOfType(FbzEggPrisonFragmentInstance.class).size());
        assertTrue(h.manager.activeObjectsOfType(FbzEggPrisonFragmentInstance.class).stream()
                .allMatch(fragment -> fragment.activeUpdatesForTest() == 0),
                "loc_89D78 setup+draw must not run Obj_FlickerMove on the creation entry");
        assertTrue(prison.releaseAttemptedForTest());
        assertEquals(0x2B,prison.getSolidParams().halfWidth(),"opened prison remains a full solid");
    }

    @Test void parentAndButtonJoinAutoSolidResolutionOnlyOnTheirSecondSstEntries() {
        Harness h=harness();
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,85));
        h.manager.addDynamicObjectAtSlot(prison,40);
        assertFalse(prison.isSolidFor(h.player));

        h.manager.update(0x2F00,null,null,1);
        FbzEggPrisonButtonInstance button=h.manager
                .activeObjectsOfType(FbzEggPrisonButtonInstance.class).getFirst();
        assertAll(
                () -> assertEquals(1,prison.routineEntriesForTest()),
                () -> assertEquals(1,button.routineEntriesForTest()),
                () -> assertFalse(prison.isSolidFor(h.player)),
                () -> assertFalse(button.isSolidFor(h.player)));

        h.manager.update(0x2F00,null,null,2);
        assertTrue(prison.isSolidFor(h.player));
        assertTrue(button.isSolidFor(h.player));
    }

    @Test void subtypeOneRingsDrawAtSpawnAndDelayPhysicsUntilTheirSecondSstEntries() {
        Harness h=harness();
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,1,0,true,0,86));
        h.manager.addDynamicObjectAtSlot(prison,40);
        h.manager.update(0x2F00,null,null,1);
        prison.triggerFromButton(h.player);
        h.manager.update(0x2F00,null,null,2);

        List<LostRingObjectInstance> rings=h.manager.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(6,rings.size());
        assertEquals(0x3000-8,rings.getFirst().getX());
        assertEquals(0x700-4,rings.getFirst().getY(),
                "loc_89D44 setup+draw does not execute Obj_Bouncing_Ring movement");

        h.manager.update(0x2F00,null,null,3);
        assertEquals(0x3000-7,rings.getFirst().getX());
        assertEquals(0x700-5,rings.getFirst().getY());
    }

    @Test void rememberedBitIsReadAfterButtonAllocationAndRestoresBrokenVisualWithoutLoot() {
        ObjectManager manager=mock(ObjectManager.class);
        AbstractPlayableSprite player=mock(AbstractPlayableSprite.class);
        ObjectServices services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return manager;}
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->player,List::of);}
        };
        ObjectSpawn spawn=new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,83);
        when(manager.isSpawnStateBitSet(spawn,0)).thenReturn(true);
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(spawn);
        prison.setServices(services);

        prison.update(0,player);

        var order=inOrder(manager);
        order.verify(manager).addDynamicObjectAfterCurrent(any(FbzEggPrisonButtonInstance.class));
        order.verify(manager).isSpawnStateBitSet(spawn,0);
        assertEquals(1,prison.mappingFrameForTest());
        assertFalse(prison.releaseAttemptedForTest(),"remembered reload emits no loot or explosions");
    }

    @Test void unexpectedRememberedStateFailureIsNotTreatedAsAnUnbrokenPrison() {
        ObjectManager manager=mock(ObjectManager.class);
        ObjectServices services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return manager;}
        };
        ObjectSpawn spawn=new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,83);
        when(manager.isSpawnStateBitSet(spawn,0)).thenThrow(new IllegalStateException("broken state"));
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(spawn);
        prison.setServices(services);

        assertThrows(IllegalStateException.class,()->prison.update(0,null));
    }

    @Test void unexpectedNativeParticipantQueryFailureIsNotHiddenByThePlunger() {
        ObjectPlayerQuery query=mock(ObjectPlayerQuery.class);
        when(query.playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2))
                .thenThrow(new IllegalStateException("broken participants"));
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(query);
        when(services.solidExecution()).thenReturn(ObjectSolidExecutionContext.inert());
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        plunger.setServices(services);

        assertThrows(IllegalStateException.class,()->plunger.update(0,null));
    }

    @Test void arbitrarySidekickButtonContactCanOpenWithoutBecomingNativeP1P2Authority() {
        Harness h=harness();
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,84));
        h.manager.addDynamicObjectAtSlot(prison,40);
        h.manager.update(0x2F00,null,null,1);
        FbzEggPrisonButtonInstance button=h.manager
                .activeObjectsOfType(FbzEggPrisonButtonInstance.class).getFirst();

        button.onSolidContact(extra,new SolidContact(true,false,false,true,false),1);
        h.manager.update(0x2F00,null,null,2);

        assertEquals(5,h.manager.activeObjectsOfType(FbzEggPrisonFragmentInstance.class).size());
    }

    @Test void forcedRecreationRestoresTheButtonParentByStableObjectIdentity() {
        Harness h=harness();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzEggPrisonInstance prison=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,78));
        h.manager.addDynamicObjectAtSlot(prison,40);
        h.manager.update(0x2F00,null,null,1);
        RewindRegistry registry=new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        CompositeSnapshot snapshot=registry.capture();

        registry.restore(snapshot);

        FbzEggPrisonInstance restored=h.manager.activeObjectsOfType(FbzEggPrisonInstance.class).getFirst();
        FbzEggPrisonButtonInstance button=h.manager.activeObjectsOfType(FbzEggPrisonButtonInstance.class).getFirst();
        assertNotSame(prison,restored);
        assertSame(restored,button.parentForTest());
        assertEquals(40,restored.getSlotIndex());
    }

    @Test void adjacentPrisonsRestoreEachButtonToItsExactCapturedParentAndSlot() {
        Harness h=harness();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzEggPrisonInstance left=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3000,0x700,0xCF,0,0,true,0,79));
        FbzEggPrisonInstance right=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3040,0x700,0xCF,0,0,true,0,80));
        h.manager.addDynamicObjectAtSlot(left,40);
        h.manager.addDynamicObjectAtSlot(right,42);
        h.manager.update(0x2F00,null,null,1);

        FbzEggPrisonButtonInstance leftButton=buttonAtX(h.manager,0x3000);
        FbzEggPrisonButtonInstance rightButton=buttonAtX(h.manager,0x3040);
        assertEquals(41,leftButton.getSlotIndex());
        assertEquals(43,rightButton.getSlotIndex());
        var captureIds=h.manager.captureIdentityContext().requireIdentityTable();
        var leftId=captureIds.idFor(left);
        var rightId=captureIds.idFor(right);
        var leftButtonId=captureIds.idFor(leftButton);
        var rightButtonId=captureIds.idFor(rightButton);

        RewindRegistry registry=new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        CompositeSnapshot snapshot=registry.capture();
        registry.restore(snapshot);

        FbzEggPrisonInstance restoredLeft=prisonAtX(h.manager,0x3000);
        FbzEggPrisonInstance restoredRight=prisonAtX(h.manager,0x3040);
        FbzEggPrisonButtonInstance restoredLeftButton=buttonAtX(h.manager,0x3000);
        FbzEggPrisonButtonInstance restoredRightButton=buttonAtX(h.manager,0x3040);
        assertSame(restoredLeft,restoredLeftButton.parentForTest());
        assertSame(restoredRight,restoredRightButton.parentForTest());
        assertNotSame(restoredRight,restoredLeftButton.parentForTest());
        assertNotSame(restoredLeft,restoredRightButton.parentForTest());
        assertEquals(40,restoredLeft.getSlotIndex());
        assertEquals(41,restoredLeftButton.getSlotIndex());
        assertEquals(42,restoredRight.getSlotIndex());
        assertEquals(43,restoredRightButton.getSlotIndex());
        var restoredIds=h.manager.captureIdentityContext().requireIdentityTable();
        assertEquals(leftId,restoredIds.idFor(restoredLeft));
        assertEquals(rightId,restoredIds.idFor(restoredRight));
        assertEquals(leftButtonId,restoredIds.idFor(restoredLeftButton));
        assertEquals(rightButtonId,restoredIds.idFor(restoredRightButton));
    }

    @Test void missingCapturedParentRemainsNullWithoutNeighbourHealing() {
        Harness h=harness();
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzEggPrisonInstance neighbour=new FbzEggPrisonInstance(
                new ObjectSpawn(0x3040,0x700,0xCF,0,0,true,0,81));
        FbzEggPrisonButtonInstance orphan=new FbzEggPrisonButtonInstance(
                new ObjectSpawn(0x3000,0x6DC,0,0,0,true,0,82),null);
        h.manager.addDynamicObjectAtSlot(orphan,41);
        h.manager.addDynamicObjectAtSlot(neighbour,42);

        RewindRegistry registry=new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        CompositeSnapshot snapshot=registry.capture();
        registry.restore(snapshot);

        FbzEggPrisonButtonInstance restored=buttonAtX(h.manager,0x3000);
        assertNull(restored.parentForTest());
        assertEquals(41,restored.getSlotIndex());
        assertEquals(42,prisonAtX(h.manager,0x3040).getSlotIndex());
    }

    private static FbzEggPrisonButtonInstance buttonAtX(ObjectManager manager,int x) {
        return manager.activeObjectsOfType(FbzEggPrisonButtonInstance.class).stream()
                .filter(button -> button.getX()==x).findFirst().orElseThrow();
    }

    private static FbzEggPrisonInstance prisonAtX(ObjectManager manager,int x) {
        return manager.activeObjectsOfType(FbzEggPrisonInstance.class).stream()
                .filter(prison -> prison.getX()==x).findFirst().orElseThrow();
    }

    private static Harness harness(){
        ObjectManager[] holder=new ObjectManager[1];
        AbstractPlayableSprite player=mock(AbstractPlayableSprite.class);
        Camera camera=new Camera(){
            @Override public short getX(){return (short)0x2F00;}
            @Override public short getY(){return (short)0x600;}
            @Override public short getWidth(){return 320;}
            @Override public short getHeight(){return 224;}
            @Override public boolean isVerticalWrapEnabled(){return false;}
        };
        RingManager ringManager=mock(RingManager.class);
        SpillAnimationState spillAnimation=new SpillAnimationState();
        spillAnimation.reset();
        when(ringManager.getSpillAnimationState()).thenReturn(spillAnimation);
        ObjectServices services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return holder[0];}
            @Override public Camera camera(){return camera;}
            @Override public GraphicsManager graphicsManager(){return GraphicsManager.getInstance();}
            @Override public ObjectPlayerQuery playerQuery(){return new ObjectPlayerQuery(()->player,List::of);}
            @Override public RingManager ringManager(){return ringManager;}
        };
        ObjectManager manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,
                null,null,GraphicsManager.getInstance(),camera,services);
        holder[0]=manager;manager.reset(0);
        return new Harness(manager,player);
    }
    private record Harness(ObjectManager manager,AbstractPlayableSprite player){}
}
