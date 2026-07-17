package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.sonic3k.objects.badniks.BlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekBadnikInstance;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.SpillAnimationState;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzEggPrison {
    @BeforeEach void initGraphics(){GraphicsManager.getInstance().initHeadless();}
    @AfterEach void resetGraphics(){SessionManager.clear();GraphicsManager.getInstance().resetState();}
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
        assertTrue(prison.getSolidRoutineProfile().inclusiveRightEdge(),
                "sub_89D9C -> SolidObjectFull accepts relX == d1*2");
        var button = new FbzEggPrisonButtonInstance(
                new ObjectSpawn(0x3000, 0x700 - 0x24, 0, 0, 0, false, 0), prison);
        assertEquals(0x1B, button.getSolidParams().halfWidth());
        assertTrue(button.getSolidRoutineProfile().inclusiveRightEdge(),
                "sub_86A3E -> SolidObjectFull accepts relX == d1*2");
        assertEquals(0x700 - 0x24, button.getY());
    }

    @Test void springPlungerLaunchesNativePlayersBeforeSafeExtraParticipants() {
        AbstractPlayableSprite main=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite nativeP2=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2,extra)));
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        stubCheckpoint(services, plunger,
                List.of(main,nativeP2,extra), List.of(main,nativeP2,extra));
        plunger.setServices(services);

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
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        stubCheckpoint(services, plunger, List.of(player), List.of(player));
        plunger.setServices(services);

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

    @Test void springPlungerConsumesFreshStandingBitsAndLaunchesEveryParticipantInItsSstEntry() {
        TestEnvironment.activeGameplayMode();
        List<String> reactionOrder = new java.util.ArrayList<>();
        RecordingPlungerPlayer main = plungerLandingPlayer("p1", 0x24B3, reactionOrder);
        RecordingPlungerPlayer nativeP2 = plungerLandingPlayer("p2", 0x2493, reactionOrder);
        nativeP2.setCentreY((short) 0x0429);
        RecordingPlungerPlayer extra = plungerLandingPlayer("extra", 0x24AD, reactionOrder);
        List<PlayableEntity> sidekicks = List.of(nativeP2, extra);
        Camera camera = new Camera() {
            @Override public short getX() { return (short) 0x2400; }
            @Override public short getY() { return (short) 0x0380; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        DefaultSolidExecutionRegistry solidExecution = new DefaultSolidExecutionRegistry();
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> main, () -> sidekicks);
            }
            @Override public SolidExecutionRegistry solidExecutionRegistry() { return solidExecution; }
            @Override public void playSfx(int soundId) {
                if (soundId == com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id) {
                    reactionOrder.add("sfx");
                }
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(0);
        UnrelatedSlotOwner unrelatedOwner = new UnrelatedSlotOwner(
                new ObjectSpawn(0x2540, 0x0570, 0x74, 0x0F, 0, false, 0));
        manager.addDynamicObjectAtSlot(unrelatedOwner, 24);
        nativeP2.setLatchedSolidObject(0x74, unrelatedOwner);
        FbzSpringPlungerInstance plunger = new FbzSpringPlungerInstance(
                new ObjectSpawn(0x24B0, 0x043B, 0xD0, 0, 0, false, 0));
        manager.addDynamicObjectAtSlot(plunger, 28);
        assertEquals(28, plunger.getSlotIndex(), "the regression must execute the native slot-$28 SST");

        manager.update(0x2400, main, sidekicks, 1,
                false, true, true);

        for (RecordingPlungerPlayer participant : List.of(main, nativeP2, extra)) {
            assertAll(
                    () -> assertEquals((short) -0xA00, participant.getYSpeed(),
                            "loc_89C86 must consume the fresh standing bit before returning"),
                    () -> assertTrue(participant.getAir()),
                    () -> assertFalse(participant.isOnObject()),
                    () -> assertNull(manager.getRidingObject(participant),
                            "sub_8635E clears the participant's ride owner as it launches"),
                    () -> assertFalse(manager.hasObjectStandingBit(participant, plunger),
                            "sub_8635E clears this plunger's native standing bit"));
        }
        assertEquals(List.of("p1:launch", "sfx", "p2:launch", "sfx", "extra:launch", "sfx"),
                reactionOrder,
                "native P1/P2 launches and SFX must complete before the multi-sidekick extension");
        assertSame(unrelatedOwner, nativeP2.getLatchedSolidObjectInstance(),
                "sub_8635E clears Status_OnObj but does not replace Player_2's interact pointer");
        assertEquals(0xC, plunger.mappingFrame(),
                "a native standing bit selects the pressed native mapping frame");
    }

    private static RecordingPlungerPlayer plungerLandingPlayer(
            String label, int x, List<String> reactionOrder) {
        RecordingPlungerPlayer player = new RecordingPlungerPlayer(label, reactionOrder);
        player.setCentreX((short) x);
        player.setCentreY((short) 0x0423);
        player.setYSpeed((short) 0x05A0);
        player.setAir(true);
        player.setOnObject(false);
        return player;
    }

    private static final class RecordingPlungerPlayer extends TestPlayableSprite {
        private final String label;
        private final List<String> reactionOrder;

        private RecordingPlungerPlayer(String label, List<String> reactionOrder) {
            this.label = label;
            this.reactionOrder = reactionOrder;
        }

        @Override public void setYSpeed(short ySpeed) {
            super.setYSpeed(ySpeed);
            if (ySpeed == (short) -0xA00 && reactionOrder != null) {
                reactionOrder.add(label + ":launch");
            }
        }
    }

    private static final class UnrelatedSlotOwner extends AbstractObjectInstance {
        private UnrelatedSlotOwner(ObjectSpawn spawn) {
            super(spawn, "FBZPlungerUnrelatedP2Owner");
        }

        @Override public void update(int frameCounter, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    @Test void springPlungerPreservesRetailNativeStandingTruthTableAfterP1SfxClobbersD0() {
        boolean[][] rows = {
                {false, false, false, false},
                {false, true, false, true},
                {true, false, true, true},
                {true, true, true, true}
        };
        for (boolean[] row : rows) {
            boolean p1Standing = row[0];
            boolean p2Standing = row[1];
            boolean p1Launch = row[2];
            boolean p2Launch = row[3];
            AbstractPlayableSprite p1 = mock(AbstractPlayableSprite.class);
            AbstractPlayableSprite p2 = mock(AbstractPlayableSprite.class);
            ObjectServices services = mock(ObjectServices.class);
            when(services.playerQuery()).thenReturn(
                    new ObjectPlayerQuery(() -> p1, () -> List.of(p2)));
            FbzSpringPlungerInstance plunger = new FbzSpringPlungerInstance(
                    new ObjectSpawn(0x3000, 0x700, 0xD0, 0, 0, false, 0));
            java.util.ArrayList<PlayableEntity> standing = new java.util.ArrayList<>();
            if (p1Standing) standing.add(p1);
            if (p2Standing) standing.add(p2);
            stubCheckpoint(services, plunger, List.of(p1, p2), standing);
            plunger.setServices(services);

            plunger.update(1, p1);

            if (p1Launch) verify(p1).setYSpeed((short) -0xA00);
            else verify(p1, never()).setYSpeed((short) -0xA00);
            if (p2Launch) verify(p2).setYSpeed((short) -0xA00);
            else verify(p2, never()).setYSpeed((short) -0xA00);
            int launchCount = (p1Launch ? 1 : 0) + (p2Launch ? 1 : 0);
            verify(services, times(launchCount)).playSfx(
                    com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
            assertEquals(launchCount == 0 ? 5 : 0xC, plunger.mappingFrame(),
                    "native standing truth-table row must select the matching frame");
        }
    }

    @Test void springPlungerFailsClosedWhenQueryParticipantIsMissingFromFreshCheckpoint() {
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        FbzSpringPlungerInstance plunger = new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000, 0x700, 0xD0, 0, 0, false, 0));
        stubCheckpoint(services, plunger, List.of(), List.of());
        plunger.setServices(services);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> plunger.update(1, main));

        assertTrue(failure.getMessage().contains("checkpoint/query participant mismatch"));
        assertFalse(((Object) plunger) instanceof com.openggf.level.objects.SolidObjectListener,
                "manual checkpoint owners must not retain a stale compatibility-callback channel");
        verify(main, never()).setYSpeed((short) -0xA00);
        verify(services, never()).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
    }

    @Test void springPlungerFailsClosedWhenFreshCheckpointHasBatchOnlyParticipant() {
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite batchOnly = mock(AbstractPlayableSprite.class);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        FbzSpringPlungerInstance plunger = new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000, 0x700, 0xD0, 0, 0, false, 0));
        stubCheckpoint(services, plunger,
                List.of(main, batchOnly), List.of(main, batchOnly));
        plunger.setServices(services);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> plunger.update(1, main));

        assertTrue(failure.getMessage().contains("checkpoint/query participant mismatch"));
        verify(main, never()).setYSpeed((short) -0xA00);
        verify(batchOnly, never()).setYSpeed((short) -0xA00);
        verify(services, never()).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
    }

    @Test void springPlungerFreshNoContactOverridesStalePlayerStandingStatus() {
        TestPlayableSprite main = new TestPlayableSprite();
        main.setOnObject(true);
        main.setAir(false);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        FbzSpringPlungerInstance plunger = new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000, 0x700, 0xD0, 0, 0, false, 0));
        stubCheckpoint(services, plunger, List.of(main), List.of());
        plunger.setServices(services);

        plunger.update(1, main);

        assertEquals(5, plunger.mappingFrame());
        assertEquals(0, main.getYSpeed());
        assertFalse(main.getAir());
        assertTrue(main.isOnObject());
        verify(services, never()).playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.SPRING.id);
    }

    private static ObjectSolidExecutionContext checkpointContext(
            FbzSpringPlungerInstance plunger,
            List<? extends PlayableEntity> participants,
            List<? extends PlayableEntity> standingParticipants) {
        IdentityHashMap<PlayableEntity, PlayerSolidContactResult> results = new IdentityHashMap<>();
        for (PlayableEntity participant : participants) {
            boolean standing = containsIdentity(standingParticipants, participant);
            results.put(participant, new PlayerSolidContactResult(
                    standing ? ContactKind.TOP : ContactKind.NONE,
                    standing,
                    false,
                    false,
                    false,
                    PreContactState.ZERO,
                    PostContactState.ZERO,
                    0));
        }
        ObjectSolidExecutionContext context = mock(ObjectSolidExecutionContext.class);
        when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(plunger, results));
        return context;
    }

    private static void stubCheckpoint(
            ObjectServices services,
            FbzSpringPlungerInstance plunger,
            List<? extends PlayableEntity> participants,
            List<? extends PlayableEntity> standingParticipants) {
        ObjectSolidExecutionContext context = checkpointContext(
                plunger, participants, standingParticipants);
        when(services.solidExecution()).thenReturn(context);
    }

    private static boolean containsIdentity(
            List<? extends PlayableEntity> participants, PlayableEntity candidate) {
        for (PlayableEntity participant : participants) {
            if (participant == candidate) return true;
        }
        return false;
    }

    @Test void extraStandingAloneUsesPressedFrameWithoutBecomingNativeAuthority() {
        AbstractPlayableSprite main=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite nativeP2=mock(AbstractPlayableSprite.class);
        AbstractPlayableSprite extra=mock(AbstractPlayableSprite.class);
        ObjectServices services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2,extra)));
        FbzSpringPlungerInstance plunger=new FbzSpringPlungerInstance(
                new ObjectSpawn(0x3000,0x700,0xD0,0,0,false,0));
        stubCheckpoint(services, plunger,
                List.of(main,nativeP2,extra), List.of(extra));
        plunger.setServices(services);

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
