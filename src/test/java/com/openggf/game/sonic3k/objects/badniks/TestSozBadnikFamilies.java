package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestSozBadnikFamilies {
    private final List<AbstractObjectInstance> children = new ArrayList<>();
    private StubObjectServices services;
    @BeforeEach void setup() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 4096, 4096, 0);
        ObjectManager manager = mock(ObjectManager.class);
        doAnswer(call -> { children.add(call.getArgument(0)); return null; }).when(manager).addDynamicObjectAfterCurrent(any());
        services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(()->null,List::of); }
        };
    }
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    private ObjectSpawn spawn(int id, int subtype, int flags) { return new ObjectSpawn(200, 200, id, subtype, flags, false, 0); }
    private void init(AbstractS3kBadnikInstance badnik) { badnik.setServices(services); badnik.update(0, null); badnik.update(1, null); }
    private void childrenTick(int clock, PlayableEntity player) { for (var child : List.copyOf(children)) { child.setServices(services); child.update(clock, player); } }

    @Test void skorpHasSixLinkedSlotsAndOnlyLastIsHarmful() {
        var skorp = new SkorpBadnikInstance(spawn(0x94, 8, 0)); init(skorp);
        assertEquals(6, children.size()); childrenTick(1, null);
        for (int i=0;i<6;i++) assertEquals(i==5 ? 0x87:0, ((TouchResponseProvider)children.get(i)).getCollisionFlags());
        assertEquals(208, children.getFirst().getX());
        assertEquals(184, children.getFirst().getY());
        skorp.setDestroyed(true); childrenTick(2, null);
        assertTrue(children.stream().allMatch(AbstractObjectInstance::isDestroyed));
    }
    @Test void skorpAttackFreezesBodyThenCompletesTailReturn() {
        var skorp = new SkorpBadnikInstance(spawn(0x94, 8, 0)); init(skorp); childrenTick(1,null);
        PlayableEntity target = mock(PlayableEntity.class);
        when(target.getCentreX()).thenReturn((short)140); when(target.getCentreY()).thenReturn((short)200);
        skorp.update(2, target); childrenTick(2, target);
        int x=skorp.getX(); boolean movingAgain=false;
        try (MockedStatic<ObjectTerrainUtils> terrain=mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(),anyInt(),eq(11))).thenReturn(new TerrainCheckResult(0,(byte)0,1));
            for(int f=3;f<220;f++) { skorp.update(f,null); childrenTick(f,null); if(skorp.getX()!=x) {movingAgain=true;break;} }
        }
        assertTrue(movingAgain,"tip completion releases parent's attack latch");
    }
    @Test void sandwormEmergesAfter128WaitTicksAndFiveSegmentsLagBySixTicks() {
        var worm = new SandwormBadnikInstance(spawn(0x95,0,0)); init(worm);
        assertEquals(13,children.size()); childrenTick(1,null);
        for(int f=2;f<129;f++) {worm.update(f,null);childrenTick(f,null);}
        assertEquals(0,worm.getCollisionFlags());
        worm.update(129,null); childrenTick(129,null);
        assertEquals(0x0B,worm.getCollisionFlags()); assertEquals(104,worm.getX());
        var first=(TouchResponseProvider)children.get(0);
        assertEquals(0,first.getCollisionFlags());
        for(int f=130;f<=135;f++) childrenTick(f,null);
        assertEquals(0x8B,first.getCollisionFlags());
        assertEquals(0,((TouchResponseProvider)children.get(1)).getCollisionFlags());
    }
    @Test void sandwormSplashAndWarningsExpireIndependentlyOfHead() {
        var worm = new SandwormBadnikInstance(spawn(0x95,0,0)); init(worm); childrenTick(1,null);
        worm.setDestroyed(true);
        for(int f=2;f<200;f++) childrenTick(f,null);
        assertTrue(children.stream().allMatch(AbstractObjectInstance::isDestroyed));
    }
    @Test void rocknEyesFollowBothWalkingDirectionsThroughTheShell() throws Exception {
        var facing=AbstractBadnikInstance.class.getDeclaredField("facingLeft");facing.setAccessible(true);
        var rock = new RocknBadnikInstance(spawn(0x96,0,0)); init(rock); childrenTick(1,null);
        PlayableEntity target=mock(PlayableEntity.class);
        when(target.getCentreX()).thenReturn((short)200); when(target.getCentreY()).thenReturn((short)200);
        for(int f=2;f<=19;f++){rock.update(f,target);childrenTick(f,target);}
        try (MockedStatic<ObjectTerrainUtils> terrain=mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(()->ObjectTerrainUtils.checkFloorDist(anyInt(),anyInt(),eq(7)))
                    .thenReturn(new TerrainCheckResult(0,(byte)0,1));
            for(short targetX:new short[]{150,250,150}) {
                when(target.getCentreX()).thenReturn(targetX);
                rock.update(20,target);childrenTick(20,target);
                assertEquals(targetX<rock.getX(),facing.getBoolean(rock));
                for(var child:children) assertEquals(facing.getBoolean(rock),facing.getBoolean(child),
                        "Refresh_ChildPositionAdjusted must propagate facing to shell and eyes");
            }
        }
    }
    @Test void rocknWaitsForShellEightTickDelayAndEightRiseSteps() {
        var rock = new RocknBadnikInstance(spawn(0x96,0x22,0)); init(rock); childrenTick(1,null);
        assertEquals(1,children.size());
        PlayableEntity target=mock(PlayableEntity.class); when(target.getCentreX()).thenReturn((short)200); when(target.getCentreY()).thenReturn((short)200);
        rock.update(2,target); childrenTick(2,target);
        assertEquals(2,children.size()); assertEquals(0,rock.getCollisionFlags());
        for(int f=3;f<=18;f++) {rock.update(f,target);childrenTick(f,target);}
        assertEquals(176,children.get(0).getY()); assertEquals(0,rock.getCollisionFlags());
        rock.update(19,target); assertEquals(0xD9,rock.getCollisionFlags());
        assertTrue(rock.usesS3kTouchSpecialPropertyResponse(),"$D9 must not decode as a boss/hurt contact");
        assertEquals(SolidObjectParams.of(35,16,17),((SolidObjectProvider)children.get(0)).getSolidParams());
        rock.setDestroyed(true); childrenTick(20,target);
        assertTrue(children.stream().allMatch(AbstractObjectInstance::isDestroyed));
    }
}
