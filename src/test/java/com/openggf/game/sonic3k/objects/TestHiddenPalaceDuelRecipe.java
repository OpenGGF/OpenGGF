package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.difficulty.HiddenPalaceDuelRecipe;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.mods.objects.HiddenPalaceFactory;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Native animation/contact/copy/rewind checks; no complete HPZ route claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHiddenPalaceDuelRecipe {
    private Rom rom;
    private ObjectManager objects;
    private StubObjectServices services;
    private ZoneRuntimeRegistry zones;
    private CutsceneKnucklesHpzInstance duel;
    private TestablePlayableSprite sonic;
    private PlayableEntity leader;
    private int clock;

    @BeforeEach void setup() throws Exception {
        rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        var reader=RomByteReader.fromRom(rom);
        GraphicsManager.getInstance().initHeadless();
        zones=new ZoneRuntimeRegistry();zones.install(new HpzZoneRuntimeState(0x16,1,PlayerCharacter.SONIC_ALONE));
        var camera=new Camera();camera.setX((short)0xFE0);camera.setY((short)0x380);
        sonic=new TestablePlayableSprite("sonic",(short)0,(short)0);
        sonic.setCentreX((short)0x1000);sonic.setCentreY((short)0x42C);leader=sonic;
        services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return objects;}
            @Override public Rom rom(){return rom;}
            @Override public RomByteReader romReader(){return reader;}
            @Override public ZoneRuntimeRegistry zoneRuntimeRegistry(){return zones;}
            @Override public Camera camera(){return camera;}
            @Override public GraphicsManager graphicsManager(){return GraphicsManager.getInstance();}
        };
        services.withPlayerQuery(new ObjectPlayerQuery(()->leader,List::of));
        objects=new ObjectManager(List.of(),null,0,null,null,GraphicsManager.getInstance(),camera,services);
        AbstractObjectInstance.updateCameraBounds(0xFE0,0x380,0x1120,0x460,0);
    }
    @AfterEach void cleanup() throws Exception {
        rom.close();GraphicsManager.getInstance().resetState();AbstractObjectInstance.resetCameraBoundsForTests();
    }
    private ObjectSpawn spawn(){return new ObjectSpawn(0x1080,0x42C,0x82,0x28,0,false,0,41);}
    private void create(HiddenPalaceDuelRecipe recipe){
        var placed=objects.createDynamicObject(()->new CutsceneKnucklesHpzInstance(spawn(),recipe));
        placed.update(clock++,leader);
        duel=objects.activeObjectsOfType(CutsceneKnucklesHpzInstance.class).stream().filter(o->o!=placed&&!o.isDestroyed()).findFirst().orElseThrow();
    }
    private void ready(){set("initialized",true);set("collisionProperty",8);}
    private void charge(){invoke("loc641C4");}
    private void step(){duel.update(clock++,leader);}
    private Object get(String name){try{var f=CutsceneKnucklesHpzInstance.class.getDeclaredField(name);f.setAccessible(true);return f.get(duel);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{var f=CutsceneKnucklesHpzInstance.class.getDeclaredField(name);f.setAccessible(true);f.set(duel,value);}catch(Exception e){throw new AssertionError(e);}}
    private void invoke(String name){try{var m=CutsceneKnucklesHpzInstance.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(duel);}catch(Exception e){throw new AssertionError(e);}}

    @Test void copiedActorRetainsPresetAndFullChargeAlternatesFeintAndRealDash(){
        create(HiddenPalaceDuelRecipe.FEINT);assertEquals(HiddenPalaceDuelRecipe.FEINT,get("duelRecipe"));
        assertEquals(48,duel.getSlotIndex());ready();charge();
        for(int i=0;i<59;i++){step();assertEquals(0x12,duel.routineForTest());}
        step();assertEquals(0x02,duel.routineForTest());assertEquals(0xDA,duel.mappingFrameForTest());
        for(int i=0;i<31;i++){step();assertEquals(0x02,duel.routineForTest());}
        step();assertEquals(0x08,duel.routineForTest(),"the 32nd guard update follows the native jump/glide path");
        charge();for(int i=0;i<60;i++)step();
        assertEquals(0x14,duel.routineForTest());assertEquals(0x800,Math.abs((Integer)get("xVel")));
        assertEquals(8,duel.collisionPropertyForTest());
    }

    @Test void feintRetiresTheNativeChargeDustAndNextRealChargeHasOnlyOneLiveChild(){
        create(HiddenPalaceDuelRecipe.FEINT);ready();charge();
        for(int i=0;i<60;i++)objects.update(0xFE0,leader,List.of(),clock++,false);
        assertEquals(0x02,duel.routineForTest());
        objects.update(0xFE0,leader,List.of(),clock++,false);
        assertTrue(objects.activeObjectsOfType(HpzKnucklesDustObjectInstance.class).stream()
                .noneMatch(o->!o.isDestroyed()),"native parent bit0 retires charge dust at feint release");
        charge();
        for(int i=0;i<60;i++){
            objects.update(0xFE0,leader,List.of(),clock++,false);
            assertTrue(objects.activeObjectsOfType(HpzKnucklesDustObjectInstance.class).stream()
                    .filter(o->!o.isDestroyed()).count()<=1,"the next real charge cannot overlap stale feint dust");
        }
        assertEquals(0x14,duel.routineForTest());
        objects.update(0xFE0,leader,List.of(),clock++,false);
        assertTrue(objects.activeObjectsOfType(HpzKnucklesDustObjectInstance.class).stream()
                .noneMatch(o->!o.isDestroyed()),"real release retains the same native dust retirement");
    }

    @Test void feintGuardHasAnActualNativeUpperSidePunishWithoutInvincibility(){
        create(HiddenPalaceDuelRecipe.FEINT);ready();charge();for(int i=0;i<60;i++)step();
        sonic.setCentreX((short)(duel.getX()+8));sonic.setCentreY((short)(duel.getY()-20));sonic.setAnimationId(2);
        assertEquals(0,sonic.getInvincibleFrames());assertEquals(0,sonic.getRingCount());
        step();assertEquals(7,duel.collisionPropertyForTest());assertEquals(0x16,duel.routineForTest());
    }

    @Test void captureBeforeCommitRecreatesCopyAndReplaysFeintThenRealChoice(){
        create(HiddenPalaceDuelRecipe.FEINT);ready();charge();for(int i=0;i<20;i++)step();
        var registry=new RewindRegistry();registry.register(objects.rewindSnapshottable());registry.register(zones);
        var before=registry.capture();int savedClock=clock;
        for(int i=0;i<50;i++)step();var expected=registry.capture();
        objects.setRewindInPlaceRestoreEnabledForTest(false);registry.restore(before);clock=savedClock;
        duel=objects.activeObjectsOfType(CutsceneKnucklesHpzInstance.class).stream().filter(o->!o.isDestroyed()).findFirst().orElseThrow();
        for(int i=0;i<50;i++)step();
        var actual=registry.capture();
        var differences=RewindSnapshotDiff.diffKey("object-manager",expected.get("object-manager"),actual.get("object-manager"));
        assertTrue(differences.isEmpty(),differences::toString);
        assertEquals(HiddenPalaceDuelRecipe.FEINT,get("duelRecipe"));
        charge();assertFalse((Boolean)get("feintCharge"),"the next charge stays real after recreation");
    }

    @Test void stockAlwaysReleasesDash(){
        create(HiddenPalaceDuelRecipe.STOCK);ready();charge();for(int i=0;i<60;i++)step();
        assertEquals(0x14,duel.routineForTest());
    }

    @Test void knucklesStillDeletesTheCopiedDuel(){
        create(HiddenPalaceDuelRecipe.FEINT);leader=mock(Knuckles.class);step();assertTrue(duel.isDestroyed());
    }

    @Test void supportedFacadeRejectsSanctuaryActorsAndOtherCutsceneObjectKinds(){
        assertThrows(IllegalArgumentException.class,()->HiddenPalaceFactory.duel(new ObjectSpawn(0,0,0xB0,0,0,false,0),HiddenPalaceDuelRecipe.FEINT));
        assertThrows(IllegalArgumentException.class,()->HiddenPalaceFactory.duel(new ObjectSpawn(0,0,0x82,0x24,0,false,0),HiddenPalaceDuelRecipe.FEINT));
        assertInstanceOf(CutsceneKnucklesHpzInstance.class,HiddenPalaceFactory.duel(spawn(),HiddenPalaceDuelRecipe.FEINT));
    }
}
