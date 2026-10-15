package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.difficulty.MushroomHillWindRecipe;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.mods.objects.MushroomHillFactory;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Native cadence/graph evidence only; no authored MHZ placement or rendered route claim. */
class TestMushroomHillPulsedWind {
    private static final int X=0x220,Y=0x120;

    @BeforeEach void setup(){
        TestEnvironment.resetAll();SessionManager.clear();
        var camera=TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();camera.setX((short)0x180);camera.setY((short)0x80);
        AbstractObjectInstance.updateCameraBounds(0x180,0x80,0x2C0,0x180,0);
    }
    @AfterEach void cleanup(){AbstractObjectInstance.resetCameraBoundsForTests();TestEnvironment.resetAll();}

    @Test void pulsesActualNativePressureAndDebrisWhileAnimationSoundAndCooldownStayStock(){
        var ordinary=new Scene(null);var explicitStock=new Scene(MushroomHillWindRecipe.STOCK);
        var pulsed=new Scene(MushroomHillWindRecipe.PULSED);
        int windUpdates=0;
        for(int update=0;update<256;update++){
            boolean windBoundary=ordinary.actor.getStateName().equals("BREATHING")
                    && ordinary.actor.getMappingFrame()>=7;
            int beforeShots=pulsed.actor.getBreathProjectileCount();
            ordinary.step();explicitStock.step();pulsed.step();
            assertEquals(ordinary.actor.getStateName(),explicitStock.actor.getStateName());
            assertEquals(ordinary.actor.getMappingFrame(),explicitStock.actor.getMappingFrame());
            assertEquals(ordinary.actor.getBreathProjectileCount(),explicitStock.actor.getBreathProjectileCount());
            assertEquals(ordinary.dx,explicitStock.dx);
            assertEquals(ordinary.actor.getStateName(),pulsed.actor.getStateName());
            assertEquals(ordinary.actor.getMappingFrame(),pulsed.actor.getMappingFrame());
            assertEquals(ordinary.actor.getTimer(),pulsed.actor.getTimer());
            if(windBoundary){
                assertTrue(ordinary.dx>0,"native force is measured on a standing player in front");
                boolean pressure=windUpdates%32<20;
                assertEquals(pressure?ordinary.dx:0,pulsed.dx,"actual pulse update "+windUpdates);
                assertEquals(pressure && (update&7)==0?1:0,
                        pulsed.actor.getBreathProjectileCount()-beforeShots,"native low-three-bit emission gate");
                windUpdates++;
            }else assertEquals(0,pulsed.dx,"full original wind-up remains quiet");
            if(ordinary.actor.getStateName().equals("COOLDOWN"))break;
        }
        assertTrue(windUpdates>=64,"observe two complete pulse periods");
        assertTrue(pulsed.actor.getBreathProjectileCount()<ordinary.actor.getBreathProjectileCount());
        assertEquals(1,pulsed.services.sounds.stream().filter(id->id==Sonic3kSfx.ENEMY_BREATH.id).count());
        assertEquals(0x60,pulsed.actor.getTimer());
        for(int i=0;i<96;i++){
            pulsed.step();assertEquals("COOLDOWN",pulsed.actor.getStateName());
        }
        pulsed.step();assertEquals("IDLE",pulsed.actor.getStateName(),"native $60 underflow takes97 updates");
    }

    @Test void pulseStillRespectsNativeDuckAndSidekickPressureRules(){
        var scene=new Scene(MushroomHillWindRecipe.PULSED);
        var sidekick=new TestablePlayableSprite("tails",(short)(X+0x40),(short)Y);
        sidekick.setCentreX((short)(X+0x40));sidekick.setCentreY((short)Y);
        scene.services.withSidekicks(List.of(sidekick));
        scene.player.setAnimationId(8);
        while(!scene.actor.isWindActive() && scene.clock<160)scene.step();
        assertTrue(scene.actor.isWindActive());assertEquals(0,scene.dx);
        assertEquals(X+0x40+12,sidekick.getCentreX(),"first native P2 push is actually measured from its centre");
        scene.player.setAnimationId(0);scene.player.setObjectControlled(true);
        for(int i=1;i<20;i++){
            sidekick.setCentreX((short)(X+0x40));scene.step();assertEquals(0,scene.dx);
            assertEquals(X+0x40+12,sidekick.getCentreX(),"native P2 pressure update "+i);
        }
        sidekick.setCentreX((short)(X+0x40));scene.step();
        assertEquals(X+0x40,sidekick.getCentreX(),"native P2 also receives the committed pulse gap");
    }

    @ParameterizedTest @ValueSource(ints={0,23,40})
    void recreationReplaysRecipePhaseRngAndCommittedNativeChildren(int windPhase){
        var scene=new Scene(MushroomHillWindRecipe.PULSED);
        scene.player.setAnimationId(8); // Actor is outside this object-only graph; keep its position stationary.
        if(windPhase==0){
            while(!(scene.actor.getStateName().equals("BREATHING") && scene.actor.getMappingFrame()==6)
                    && scene.clock<160)scene.step();
        }else{
            while(!scene.actor.isWindActive() && scene.clock<160)scene.step();
            for(int i=1;i<windPhase;i++)scene.step();
        }
        assertTrue(scene.clock<160);
        var rewind=new RewindRegistry();rewind.register(scene.manager.rewindSnapshottable());rewind.register(scene.rng);
        var before=rewind.capture();int savedClock=scene.clock;
        for(int i=0;i<36;i++)scene.step();var expected=rewind.capture();
        scene.manager.setRewindInPlaceRestoreEnabledForTest(false);rewind.restore(before);
        var original=scene.actor;
        scene.actor=scene.manager.activeObjectsOfType(CluckoidBadnikInstance.class).getFirst();
        assertNotSame(original,scene.actor);scene.clock=savedClock;
        for(int i=0;i<36;i++)scene.step();var actual=rewind.capture();
        assertTrue(RewindSnapshotDiff.diffKey("object-manager",expected.get("object-manager"),
                actual.get("object-manager")).isEmpty(),()->RewindSnapshotDiff.diffKey("object-manager",
                expected.get("object-manager"),actual.get("object-manager")).toString());
        assertEquals(expected.get(scene.rng.key()),actual.get(scene.rng.key()));
    }

    @Test void fullNativeSlotsDoNotExtendOrRetryThePulse(){
        var scene=new Scene(MushroomHillWindRecipe.PULSED);
        for(int i=0;i<89;i++)scene.manager.createDynamicObject(CapacityObject::new);
        assertEquals(90,scene.manager.getActiveObjects().size());
        for(int i=0;i<256 && !scene.actor.getStateName().equals("COOLDOWN");i++)scene.step();
        assertEquals("COOLDOWN",scene.actor.getStateName());assertEquals(0x60,scene.actor.getTimer());
        assertEquals(90,scene.manager.getActiveObjects().size());assertTrue(scene.leaves().isEmpty());
        assertEquals(1,scene.services.sounds.stream().filter(id->id==Sonic3kSfx.ENEMY_BREATH.id).count());
    }

    @Test void unloadStopsNewPressureAndDebrisButDetachedNativeLeavesKeepMoving(){
        var scene=new Scene(MushroomHillWindRecipe.PULSED);
        while(scene.leaves().isEmpty() && scene.clock<160)scene.step();
        assertFalse(scene.leaves().isEmpty());
        var leaf=scene.leaves().getFirst();int oldX=leaf.getX(),oldY=leaf.getY();
        int shots=scene.actor.getBreathProjectileCount();scene.actor.onUnload();
        assertTrue(scene.actor.isDestroyed());
        for(int i=0;i<80;i++){scene.step();assertEquals(0,scene.dx);}
        assertEquals(shots,scene.actor.getBreathProjectileCount());
        assertFalse(leaf.isDestroyed(),"ordinary owner unload does not revoke a committed leaf");
        leaf.update(scene.clock,null);
        assertTrue(oldX!=leaf.getX() || oldY!=leaf.getY(),"native detached motion still executes");
        var arrow=scene.manager.activeObjectsOfType(CluckoidBadnikInstance.ArrowChild.class).getFirst();
        arrow.update(scene.clock,null);assertTrue(arrow.isDestroyed());
    }

    @ParameterizedTest @EnumSource(MushroomHillWindRecipe.class)
    void retainedWindDispatchHonorsOptedRetirementAndStockDefault(MushroomHillWindRecipe recipe){
        var observed=observeLateDispatch(new Scene(recipe));
        if(recipe==MushroomHillWindRecipe.PULSED){
            assertTrue(observed.ownerDestroyed());assertTrue(observed.arrowDestroyed());
            assertEquals(0,observed.pressureUpdates());assertEquals(0,observed.emissions());
        }else{
            //82258 preserves the inherited stock negative separately. This
            // opt-in packet changes no stock unload contract: explicit STOCK
            // must still match the native constructor without a recipe.
            assertEquals(observeLateDispatch(new Scene(null)),observed);
        }
    }

    private record LateDispatch(boolean ownerDestroyed,boolean arrowDestroyed,
                                int pressureUpdates,int emissions) { }

    private LateDispatch observeLateDispatch(Scene scene){
        while(scene.leaves().isEmpty() && scene.clock<160)scene.step();
        assertFalse(scene.leaves().isEmpty());
        var arrow=scene.manager.activeObjectsOfType(CluckoidBadnikInstance.ArrowChild.class).getFirst();
        var leaf=scene.leaves().getFirst();int oldX=leaf.getX(),oldY=leaf.getY();
        int shots=scene.actor.getBreathProjectileCount();
        scene.manager.removeDynamicObject(scene.actor);
        assertEquals(-1,scene.actor.getSlotIndex());
        assertTrue(scene.manager.activeObjectsOfType(CluckoidBadnikInstance.class).isEmpty());
        // Explicit retained-reference dispatch is NOT ordinary manager scheduling.
        int pressureUpdates=0;
        for(int tick=0;tick<80;tick++){scene.step();if(scene.dx!=0)pressureUpdates++;}
        arrow.update(scene.clock,null);leaf.update(scene.clock,null);
        assertFalse(leaf.isDestroyed(),"native detached debris keeps its independent motion");
        assertTrue(oldX!=leaf.getX() || oldY!=leaf.getY());
        return new LateDispatch(scene.actor.isDestroyed(),arrow.isDestroyed(),pressureUpdates,
                scene.actor.getBreathProjectileCount()-shots);
    }

    @ParameterizedTest @EnumSource(MushroomHillWindRecipe.class)
    void normalManagerDispatchHonorsOptedArrowRetirementAndStockDefault(MushroomHillWindRecipe recipe){
        var observed=observeNormalDispatch(new Scene(recipe));
        if(recipe==MushroomHillWindRecipe.PULSED){
            assertTrue(observed.arrowDestroyed());assertFalse(observed.arrowManaged());
        }else{
            //7159 retains the real managed STOCK-arrow negative. Do not turn
            // that inherited visual-child gap into an unrelated stock fix.
            assertEquals(observeNormalDispatch(new Scene(null)),observed);
        }
    }

    private record NormalDispatch(boolean arrowDestroyed,boolean arrowManaged,int arrowSlot) { }

    private NormalDispatch observeNormalDispatch(Scene scene){
        while(scene.leaves().isEmpty() && scene.clock<160)scene.step();
        var arrow=scene.manager.activeObjectsOfType(CluckoidBadnikInstance.ArrowChild.class).getFirst();
        int shots=scene.actor.getBreathProjectileCount(),timer=scene.actor.getTimer();
        scene.manager.removeDynamicObject(scene.actor);
        int playerX=scene.player.getCentreX();
        scene.manager.update(0x180,scene.player,List.of(),scene.clock++,false);
        assertEquals(shots,scene.actor.getBreathProjectileCount());assertEquals(timer,scene.actor.getTimer());
        assertEquals(playerX,scene.player.getCentreX(),"removed body has no ordinary pressure dispatch");
        assertFalse(com.openggf.level.objects.TouchResponseProvider.class.isInstance(arrow),
                "native arrow is a visual child, never a touch-response provider");
        return new NormalDispatch(arrow.isDestroyed(),scene.manager.getActiveObjects().contains(arrow),arrow.getSlotIndex());
    }

    @Test void facadeRejectsOtherKindsAndRetainsTheExactNativeRecord(){
        var spawn=new ObjectSpawn(X,Y,0x90,2,1,false,42);
        var actor=MushroomHillFactory.cluckoid(spawn,MushroomHillWindRecipe.PULSED);
        assertInstanceOf(CluckoidBadnikInstance.class,actor);assertEquals(spawn,actor.getSpawn());
        assertThrows(IllegalArgumentException.class,()->MushroomHillFactory.cluckoid(
                new ObjectSpawn(X,Y,0x91,2,1,false,42),MushroomHillWindRecipe.PULSED));
        assertThrows(NullPointerException.class,()->MushroomHillFactory.cluckoid(null,MushroomHillWindRecipe.PULSED));
        assertThrows(NullPointerException.class,()->MushroomHillFactory.cluckoid(spawn,null));
    }

    private static final class Scene {
        final RecordingServices services=new RecordingServices();
        final GameRng rng=new GameRng(GameRng.Flavour.S3K,1);
        final TestablePlayableSprite player=new TestablePlayableSprite("sonic",(short)(X+0x40),(short)Y);
        final ObjectManager manager;
        CluckoidBadnikInstance actor;
        int clock,dx;
        Scene(MushroomHillWindRecipe recipe){
            var camera=TestEnvironment.activeGameplayMode().getCamera();
            services.withGameState(mock(GameStateManager.class)).withRng(rng).withCamera(camera);
            manager=new ObjectManager(List.of(),new MhzRegistry(),0,null,null,null,camera,services);
            services.withDirectObjectManager(manager);manager.reset(0x180);
            actor=manager.createDynamicObject(()->{
                var spawn=new ObjectSpawn(X,Y,0x90,0,1,false,42);
                var result=recipe==null?new CluckoidBadnikInstance(spawn):new CluckoidBadnikInstance(spawn,recipe);
                result.setServices(services);return result;
            });
        }
        void step(){player.setCentreX((short)(X+0x40));actor.update(clock++,player);dx=player.getCentreX()-(X+0x40);}
        List<CluckoidBadnikInstance.BreathDebrisChild> leaves(){
            return manager.activeObjectsOfType(CluckoidBadnikInstance.BreathDebrisChild.class);
        }
    }
    private static final class RecordingServices extends TestObjectServices {
        final List<Integer> sounds=new ArrayList<>();
        @Override public void playSfx(int id){sounds.add(id);}
    }
    private static final class MhzRegistry extends Sonic3kObjectRegistry {
        @Override protected int currentRomZoneId(){return Sonic3kZoneIds.ZONE_MHZ;}
    }
    private static final class CapacityObject extends AbstractObjectInstance {
        CapacityObject(){super(new ObjectSpawn(0,0,0,0,0,false,0),"capacity");}
        @Override public void update(int frame,PlayableEntity player){}
        @Override public void appendRenderCommands(List<GLCommand> commands){}
    }
}
