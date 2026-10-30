package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.Sonic3k;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.difficulty.MarbleGardenBossRecipe;
import com.openggf.game.sonic3k.objects.bosses.S3kSharedBossCameraGate;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelData;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Native placed-A2 ownership and destructive replay, not a physical route certificate. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMgzEndBossKnuxAdmission {
    @org.junit.jupiter.api.AfterEach void cleanup(){com.openggf.game.session.SessionManager.clear();}
    static final ObjectSpawn ORIGINAL = new ObjectSpawn(0x3E78,0,0xA2,0,0,false,0,0x7FE/6);

    @Test void originalRomHasExactlyOneUngatedA2WithTheNativePlacement() throws Exception {
        var level = new Sonic3k(TestEnvironment.currentRom()).loadLevel(LevelData.S3K_MARBLE_GARDEN_2.getLevelIndex());
        var a2 = level.getObjects().stream().filter(s -> s.objectId()==0xA2).toList();
        assertEquals(1,a2.size());
        var spawn=a2.getFirst();
        assertEquals(ORIGINAL.x(),spawn.x()); assertEquals(0,spawn.y()); assertEquals(0,spawn.subtype());
        assertEquals(0x7FE/6,spawn.layoutIndex(),"original six-byte layout record offset $7FE");
        assertInstanceOf(MgzEndBossKnuxInstance.class,new Sonic3kObjectRegistry().create(spawn),
                "registry dispatch remains ungated; no synthetic event owner or character check");
    }

    @ParameterizedTest
    @CsvSource({"15479,0,false","15993,0,false","15736,-1,false","15736,297,false",
            "15480,0,true","15992,0,true","15480,296,true","15992,296,true"})
    void checkCameraInRangeUsesInclusiveNativeWordEdges(int x,int y,boolean admitted) throws Exception {
        var f=new Fixture(true); f.camera.setX((short)x);f.camera.setY((short)y);
        assertEquals(0,f.module.samples,"construction must not ask the creator");
        f.tick();
        assertEquals(admitted?1:0,f.module.samples);
        assertEquals(admitted?1:0,number(f.boss(),"admissionPhase"));
        assertEquals(1,f.manager.getActiveObjects().size());
        assertTrue(f.boss().isHidden()); assertEquals(0,f.boss().getCollisionFlags());
        assertNull(f.boss().getMultiTouchRegions());
        f.boss().onPlayerAttack(null,null); assertEquals(8,f.boss().getCollisionProperty());
        assertEquals(admitted?1:0,f.fades);assertEquals(0,f.music);
    }

    @Test void admissionSamplesFreshWithdrawnServiceAndCommitsOnlyOnce() throws Exception {
        var f=new Fixture(true);f.camera.setX((short)0x3C77);f.tick();assertEquals(0,f.module.samples);
        f.module.recipe=null;f.atDestination();f.tick();
        assertFalse((boolean)value(f.boss(),"counterCadence"));
        f.module.recipe=MarbleGardenBossRecipe.COUNTER_CADENCE;
        for(int i=0;i<124;i++)f.tick();
        assertEquals(1,f.module.samples);assertFalse((boolean)value(f.boss(),"counterCadence"));
        assertEquals(1,f.roots());
    }

    @Test void firstProviderFaultPropagatesWithTheOriginalPlacedRootAndNoChildren() {
        var f=new Fixture(true);f.atDestination();f.module.abort=true;
        assertThrows(FirstFault.class,f::tick);
        assertEquals(1,f.module.samples);assertEquals(1,f.roots());
        assertEquals(1,f.manager.getActiveObjects().size());assertEquals(0,f.fades);assertEquals(0,f.music);
        assertFalse(((AbstractLevelEventManager)f.module.getLevelEventProvider()).isBossActive());
        // No retry after this discarded frame: the actual host owns teardown.
    }

    @Test void activeKnucklesBossBlinkAdvancesOnUpdatesRatherThanPictures() throws Exception {
        var f = new Fixture(true);
        f.atDestination();
        f.tick();
        for (int i = 0; i < 122; i++) f.tick();
        var boss = f.boss();
        assertFalse(boss.isHidden());
        int before = number(boss, "renderTick");
        for (int i = 0; i < 5; i++) boss.appendCompositeChild(MgzEndBossRenderChild.ROLE_POD);
        assertEquals(before, number(boss, "renderTick"));
        f.tick();
        assertEquals(before + 1, number(boss, "renderTick"));
        f.roundTrip();
        boss = f.boss();
        assertEquals(before + 1, number(boss, "renderTick"));
        f.tick();
        assertEquals(before + 2, number(f.boss(), "renderTick"));
    }

    @Test void raw120WaitAndCameraApproachFinishBeforeTheNextFightDispatch() throws Exception {
        var f=new Fixture(true);f.camera.setX((short)0x3C78);f.camera.setY((short)0);
        f.camera.setMinY((short)7);f.camera.setMaxYTarget((short)0x400);
        f.camera.setMinX((short)0x100);f.camera.setMaxX((short)0x5000);
        f.tick();var boss=f.boss();
        assertTrue(((AbstractLevelEventManager)f.module.getLevelEventProvider()).isBossActive());
        assertEquals(7,number(boss,"storedMinY"));assertEquals(0x400,number(boss,"storedMaxY"));
        assertEquals(0x100,number(boss,"storedMinX"));assertEquals(0x5000,number(boss,"storedMaxX"));
        assertEquals(120,gate(boss).captureRewindStateValue().musicWaitTimer());
        assertEquals(1,f.fades);assertEquals(1,f.manager.getActiveObjects().size());
        for(int i=0;i<120;i++)f.tick();
        assertEquals(0,f.music);assertEquals(0,gate(boss).captureRewindStateValue().musicWaitTimer());
        f.tick();assertEquals(1,f.music);assertEquals(Sonic3kMusic.MINIBOSS.id,f.musicId);
        assertEquals(1,number(boss,"admissionPhase"),"music alone does not complete camera approach");
        assertEquals(0,f.camera.getMinY());assertEquals(0x3C78,f.camera.getMinX()&65535);
        assertEquals(1,f.manager.getActiveObjects().size());
        f.atDestination();f.tick();
        assertEquals(2,number(boss,"admissionPhase"));assertEquals(1,f.manager.getActiveObjects().size());
        // loc_6C6FA only changes the code pointer: inherited routine-0/raw120
        // presentation gate remains closed until loc_6C72A on the next pass.
        assertTrue(boss.isHidden());assertEquals(0,boss.getCollisionFlags());
        assertNull(boss.getMultiTouchRegions());
        assertEquals(0x28,f.camera.getMinY());assertEquals(0x28,f.camera.getMaxYTarget());
        assertEquals(0x3D78,f.camera.getMinX()&65535);assertEquals(0x3D78,f.camera.getMaxX()&65535);
        f.tick();assertEquals(8,f.manager.activeObjectsOfType(MgzEndBossRenderChild.class).size());
        assertEquals(1,f.manager.activeObjectsOfType(MgzEndBossKnuxDrillChild.class).size());
        assertEquals(2,boss.getNativeRoutineForTesting());assertEquals(8,boss.getCollisionProperty());
        assertFalse(boss.isHidden());assertEquals(0x0F,boss.getCollisionFlags());
        assertNotNull(boss.getMultiTouchRegions());
        assertEquals(1,f.fades);assertEquals(1,f.music);assertEquals(1,f.module.samples);
    }

    @ParameterizedTest @ValueSource(ints={0,1,2})
    void destructiveRestorePreservesDormantStagingAndCommittedChildReplay(int phase) throws Exception {
        var f=new Fixture(true);
        if(phase>0){f.atDestination();f.tick();for(int i=0;i<(phase==1?37:122);i++)f.tick();}
        else {f.camera.setX((short)0x3C77);f.tick();}
        var source=f.boss();int samples=f.module.samples;int count=f.manager.getActiveObjects().size();
        var state=gate(source).captureRewindStateValue();
        f.roundTrip();var restored=f.boss();assertNotSame(source,restored);
        assertEquals(phase,number(restored,"admissionPhase"));assertEquals(state,gate(restored).captureRewindStateValue());
        assertEquals(count,f.manager.getActiveObjects().size());assertEquals(samples,f.module.samples);
        if(phase==0){f.module.recipe=null;f.atDestination();f.tick();assertEquals(1,f.module.samples);
            assertFalse((boolean)value(f.boss(),"counterCadence"));return;}
        f.module.abort=true; // Captured native commitment survives withdrawal; never query again.
        var snapshot=f.rewind.capture(); int start=f.frame;
        for(int i=0;i<360;i++)f.tick();String forward=f.signature();
        f.rewind.restore(snapshot);f.frame=start;
        for(int i=0;i<360;i++)f.tick();assertEquals(forward,f.signature());
        assertEquals(samples,f.module.samples);assertEquals(1,f.roots());
        var parts=f.manager.activeObjectsOfType(MgzEndBossRenderChild.class);
        assertEquals(8,parts.size());assertEquals(8,f.boss().getChildComponents().stream()
                .filter(MgzEndBossRenderChild.class::isInstance).count());
        for(var part:parts)assertSame(f.boss(),value(part,"parent"));
    }

    static final class Module extends Sonic3kGameModule {
        int samples;boolean abort;MarbleGardenBossRecipe recipe=MarbleGardenBossRecipe.COUNTER_CADENCE;
        @Override public <T>T getGameService(Class<T> type){if(type==MarbleGardenBossRecipe.class){
            samples++;if(abort)throw new FirstFault();return type.cast(recipe);}return super.getGameService(type);}
    }
    static final class FirstFault extends ObjectCallbackAbortException {FirstFault(){super("controlled native admission fault",null);}}
    static final class Fixture {
        final Camera camera=new Camera();final Module module=new Module();final ObjectManager manager;
        final RewindRegistry rewind=new RewindRegistry();int frame,fades,music,musicId;
        Fixture(boolean placed){
            GraphicsManager.getInstance().initHeadless();atDestination();
            var services=new StubObjectServices(){
                @Override public Camera camera(){return camera;}
                @Override public GameModule gameModule(){return module;}
                @Override public ObjectManager objectManager(){return manager;}
                @Override public void fadeOutMusic(){fades++;}
                @Override public void playMusic(int id){music++;musicId=id;}
            };
            manager=new ObjectManager(placed?List.of(ORIGINAL):List.of(),new Sonic3kObjectRegistry(),0,null,null,
                    GraphicsManager.getInstance(),camera,services);
            manager.setRewindInPlaceRestoreEnabledForTest(false);manager.reset(0x3D78);
            rewind.register(manager.rewindSnapshottable());rewind.register(services.rng());
        }
        ObjectManager objectManager(){return manager;}
        void roundTrip(){rewind.restore(rewind.capture());}
        void atDestination(){camera.setX((short)0x3D78);camera.setY((short)0x28);}
        void tick(){manager.update(0x3D78,null,List.of(),frame++,false);}
        MgzEndBossKnuxInstance boss(){return manager.activeObjectsOfType(MgzEndBossKnuxInstance.class).getFirst();}
        long roots(){return manager.activeObjectsOfType(MgzEndBossKnuxInstance.class).size();}
        String signature(){return manager.getActiveObjects().stream().map(o->{try{return o.getClass().getSimpleName()+":"
                +o.getX()+":"+o.getY()+":"+((o instanceof MgzEndBossKnuxInstance b)?b.getNativeRoutineForTesting()+":"+b.getNativeTimerForTesting():"");}
                catch(Exception failure){throw new AssertionError(failure);}}).sorted().toList().toString();}
    }
    private static S3kSharedBossCameraGate gate(Object boss)throws Exception{return (S3kSharedBossCameraGate)value(boss,"cameraGate");}
    private static int number(Object object,String name)throws Exception{return ((Number)value(object,name)).intValue();}
    private static Object value(Object object,String name)throws Exception{
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
