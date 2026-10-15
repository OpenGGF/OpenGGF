package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.difficulty.LavaReefHazardRecipe;
import com.openggf.level.objects.ObjectManager;
import com.openggf.mods.objects.LavaReefFactory;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cadence/presentation-call evidence, separate from an actual rendered safe-route witness. */
class TestLrzEchoJet {
    private static final int X=0x400,Y=0x500;
    private TestObjectServices services;
    private PatternSpriteRenderer renderer;
    private int clock;

    @BeforeEach void setup(){
        TestEnvironment.resetAll();SessionManager.clear();
        var camera=TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();camera.setX((short)(X-160));camera.setY((short)(Y-112));
        AbstractObjectInstance.updateCameraBounds(X-160,Y-112,X+160,Y+112,0);
        var render=mock(ObjectRenderManager.class);
        renderer=mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        when(render.getRenderer(Sonic3kObjectArtKeys.LRZ2_FLAME_THROWER)).thenReturn(renderer);
        services=new TestObjectServices(){@Override public ObjectRenderManager renderManager(){return render;}};
        services.withCamera(camera).withIsolatedObjectManager();
    }
    @AfterEach void cleanup(){AbstractObjectInstance.resetCameraBoundsForTests();TestEnvironment.resetAll();}
    private LrzFlameThrowerObjectInstance echo(int subtype){
        var object=new LrzFlameThrowerObjectInstance(new ObjectSpawn(X,Y,0x29,subtype,0,false,0),true);
        object.setServices(services);return object;
    }
    private List<LrzFlameObjectInstance> flames(){
        return services.objectManager().getActiveObjects().stream().filter(LrzFlameObjectInstance.class::isInstance)
                .map(LrzFlameObjectInstance.class::cast).toList();
    }
    private void step(LrzFlameThrowerObjectInstance object){object.update(clock++,null);}
    private void firstBurst(LrzFlameThrowerObjectInstance object){for(int i=0;i<120;i++)step(object);}

    @Test void firstNativeBurstThenThirtyTwoNozzlePresentationsThenSixEchoEmissions(){
        var object=echo(0x13);firstBurst(object);assertEquals(30,flames().size());
        step(object);assertTrue(object.isIdle());
        List<GLCommand> commands=new ArrayList<>();
        for(int i=0;i<32;i++){
            object.appendRenderCommands(commands);assertEquals(30,flames().size());step(object);
        }
        // All warning calls draw the body mapping, at the body position. There
        // are no harmless flame actors pretending to be damaging projectiles.
        verify(renderer,times(16)).drawFrameIndex(6,X,Y,false,false,1);
        verify(renderer,times(16)).drawFrameIndex(6,X,Y,false,false,2);
        for(int i=0;i<24;i++)step(object);
        assertEquals(36,flames().size());step(object);
        assertTrue(object.isIdle());assertEquals(0x13*4,object.phaseTimer());
        for(int i=0;i<0x13*4;i++){step(object);assertTrue(object.isIdle());}
        step(object);assertFalse(object.isIdle());assertEquals(120,object.phaseTimer());
    }

    @Test void missingArtOrLostVisibilityAbandonsEchoAndRestoresFullNativeIdle(){
        var object=echo(0x93);firstBurst(object);step(object);
        when(renderer.isReady()).thenReturn(false);step(object);
        assertTrue(object.isIdle());assertEquals(0x13*4,object.phaseTimer());
        int before=flames().size();for(int i=0;i<50;i++)step(object);assertEquals(before,flames().size());
        when(renderer.isReady()).thenReturn(true);
        var second=echo(0x13);firstBurst(second);step(second);
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);step(second);
        assertTrue(second.isIdle());assertEquals(0x13*4,second.phaseTimer());
    }

    @Test void restoreAtWarningReplaysIdenticalEchoAnglesAndFullIdle(){
        var object=echo(0x93);firstBurst(object);step(object);for(int i=0;i<13;i++)step(object);
        var snapshot=object.captureRewindState();int savedClock=clock;
        for(int i=0;i<50;i++)step(object);
        int angle=object.angle(),timer=object.phaseTimer();boolean idle=object.isIdle();
        object.restoreRewindState(snapshot);clock=savedClock;
        for(int i=0;i<50;i++)step(object);
        assertEquals(angle,object.angle());assertEquals(timer,object.phaseTimer());assertEquals(idle,object.isIdle());
    }

    @Test void finiteFacadeRejectsOtherNativeKindsAndPreservesAxisAndSubtype(){
        assertThrows(IllegalArgumentException.class,()->LavaReefFactory.flameThrower(
                new ObjectSpawn(X,Y,0x28,0x93,0,false,0),LavaReefHazardRecipe.ECHO_JET));
        assertThrows(NullPointerException.class,()->LavaReefFactory.flameThrower(null,LavaReefHazardRecipe.ECHO_JET));
        assertThrows(NullPointerException.class,()->LavaReefFactory.flameThrower(
                new ObjectSpawn(X,Y,0x29,0x13,0,false,0),null));
        var actor=(LrzFlameThrowerObjectInstance)LavaReefFactory.flameThrower(
                new ObjectSpawn(X,Y,0x29,0x93,1,false,0),LavaReefHazardRecipe.ECHO_JET);
        assertEquals(LrzFlameThrowerObjectInstance.Axis.VERTICAL,actor.axis());
        assertEquals(0x13*4,actor.idleFrames());
    }

    private ObjectManager nativeManager(){
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,
                null,services.camera(),services);
        services.withDirectObjectManager(manager);manager.reset(X-160);return manager;
    }

    @Test void nativeSlotExhaustionSkipsShotsWithoutRetryingOrExtendingTheEcho(){
        var manager=nativeManager();
        var actor=manager.createDynamicObject(()->echo(0x13));
        for(int i=0;i<89;i++)manager.createDynamicObject(CapacityObject::new);
        assertEquals(90,manager.getActiveObjects().size());
        firstBurst(actor);step(actor);
        for(int i=0;i<32+24;i++)step(actor);
        assertTrue(flames().isEmpty());assertEquals(90,manager.getActiveObjects().size());
        step(actor);assertTrue(actor.isIdle());assertEquals(0x13*4,actor.phaseTimer());
    }

    @Test void managerRecreationRestoresWarningCommitAndExactFlameGraph(){
        var manager=nativeManager();
        var actor=manager.createDynamicObject(()->echo(0x93));
        firstBurst(actor);step(actor);for(int i=0;i<13;i++)step(actor);
        var registry=new RewindRegistry();registry.register(manager.rewindSnapshottable());
        var before=registry.capture();int savedClock=clock;
        for(int i=0;i<50;i++)step(actor);var expected=registry.capture();
        assertEquals(36,flames().size());
        manager.setRewindInPlaceRestoreEnabledForTest(false);registry.restore(before);
        var restored=manager.activeObjectsOfType(LrzFlameThrowerObjectInstance.class).getFirst();
        assertNotSame(actor,restored);clock=savedClock;
        for(int i=0;i<50;i++)step(restored);
        assertEquals(36,flames().size());
        assertTrue(RewindSnapshotDiff.diffKey("object-manager",expected.get("object-manager"),
                registry.capture().get("object-manager")).isEmpty());
    }

    @Test void nativeUnloadAbandonsQueuedEchoAndKeepsCommittedFlamesIndependent(){
        var actor=echo(0x13);firstBurst(actor);step(actor);int committed=flames().size();
        actor.onUnload();assertTrue(actor.isDestroyed());
        for(int i=0;i<200;i++)step(actor);
        assertEquals(committed,flames().size());
        for(var flame:List.copyOf(flames()))for(int i=0;i<25;i++)flame.update(i,null);
        assertTrue(flames().stream().allMatch(AbstractObjectInstance::isDestroyed));
    }

    @ParameterizedTest @EnumSource(LavaReefHazardRecipe.class)
    void retainedNozzleDispatchHonorsOptedRetirementAndStockDefault(LavaReefHazardRecipe recipe){
        var observed=observeRetainedNozzle(recipe);
        if(recipe==LavaReefHazardRecipe.ECHO_JET){
            assertTrue(observed.ownerDestroyed());assertEquals(0,observed.lateEmissions());
        }else{
            //1450 preserves the inherited retained-call STOCK negative. This
            // opt-in owner defense must not change the absent-recipe contract.
            setup();clock=0;
            assertEquals(observeRetainedNozzle(null),observed);
        }
    }

    private record RetainedNozzle(boolean ownerDestroyed,int lateEmissions,
                                 boolean idle,int phaseTimer,int angle) { }

    private RetainedNozzle observeRetainedNozzle(LavaReefHazardRecipe recipe){
        var manager=nativeManager();
        var actor=manager.createDynamicObject(()->{
            var spawn=new ObjectSpawn(X,Y,0x29,0x13,0,false,0);
            var nozzle=recipe==null?new LrzFlameThrowerObjectInstance(spawn):
                    (LrzFlameThrowerObjectInstance)LavaReefFactory.flameThrower(spawn,recipe);
            nozzle.setServices(services);return nozzle;
        });
        firstBurst(actor);step(actor);
        var committed=List.copyOf(flames());assertEquals(30,committed.size());
        manager.removeDynamicObject(actor);
        assertEquals(-1,actor.getSlotIndex());
        assertTrue(manager.activeObjectsOfType(LrzFlameThrowerObjectInstance.class).isEmpty());
        // Deliberate retained-reference dispatch, not ordinary manager scheduling.
        for(int tick=0;tick<200;tick++)step(actor);
        int lateEmissions=flames().size()-committed.size();
        for(var flame:committed)for(int tick=0;tick<25;tick++)flame.update(tick,null);
        assertTrue(committed.stream().allMatch(AbstractObjectInstance::isDestroyed));
        return new RetainedNozzle(actor.isDestroyed(),lateEmissions,actor.isIdle(),actor.phaseTimer(),actor.angle());
    }

    private static final class CapacityObject extends AbstractObjectInstance {
        CapacityObject(){super(new ObjectSpawn(0,0,0,0,0,false,0),"capacity");}
        @Override public void update(int frame,PlayableEntity player){}
        @Override public void appendRenderCommands(List<GLCommand> commands){}
    }

    @Test void retirementStopsNewShotsWhileAlreadyCommittedFlamesKeepNativeLifetime(){
        var object=echo(0x13);firstBurst(object);step(object);
        var shots=List.copyOf(flames());int count=shots.size();ObjectLifetimeOps.deleteNoRespawn(object);
        for(int i=0;i<200;i++)step(object);assertEquals(count,flames().size());
        var shot=shots.getFirst();assertFalse(shot.isDestroyed());
        for(int i=0;i<25;i++)shot.update(i,null);
        assertTrue(shot.isDestroyed(),"ordinary owner death does not erase an already committed native flame");
    }
}
