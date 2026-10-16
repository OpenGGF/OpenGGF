package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native CreateChild4_LinkListRepeated/MonkeyDudeArm dispatch, independent of a trace. */
class TestMonkeyDudeNativeSstGraph {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void initCreatesFiveRealHigherSlotActorsAndRunsOnlyTheirNativeInit(int flip) {
        Harness h=new Harness();
        MonkeyDudeBadnikInstance body=h.body(4,0x1E58,flip);
        h.step(1);
        assertEquals(0,h.arms().size(),"Obj_WaitOffscreen release returns before MonkeyDude_Init");
        h.step(2);
        assertEquals(5,h.arms().size(),"CreateChild4_LinkListRepeated creates five actual SST owners");
        for(int index=0;index<5;index++) {
            var arm=h.arms().get(index);
            assertEquals(5+index,arm.getSlotIndex());
            assertEquals(index*2,arm.getSpawn().subtype());
            assertEquals(0x1E58+(index==0?(flip==0?-0xE:0xE):0),arm.getX());
            assertEquals(0x490-(index==0?2:0),arm.getY());
            assertEquals(6,arm.getPriorityBucket(),"ObjDat3_MonkeyDudeArmSegment writes $300");
        }
        assertFalse(body.isDestroyed());
        h.step(3);
        assertNotEquals(0x490,h.arms().get(1).getY(),
                "the follower begins its own circular routine only on the next dispatch");
    }

    @Test void unreleasedWaitOffscreenRecordClearsImmediatelyOutsideItsNativeCoarseWindow() {
        Harness h=new Harness();
        // No preceding visible draw: loc_85AD2 still owns this record, not the
        // normal MonkeyDude tail whose status7 deletion is deliberately delayed.
        var body=h.manager.createDynamicObjectAtSlot(()->new MonkeyDudeBadnikInstance(
                new ObjectSpawn(0x1E58,0x900,0x8E,0x10,1,false,0)),4);
        h.step(1);assertFalse(body.isDestroyed());assertTrue(h.arms().isEmpty());
        when(h.camera.getX()).thenReturn((short)0x3000);
        h.step(2);
        assertTrue(body.isDestroyed(),"loc_85AFC jumps directly to Delete_Current_Sprite");
        assertNull(NativeSstSlotAccess.currentOwner(h.manager,4));
    }

    @Test void nativeOutOfRangePublishesStatusBeforeTheNextDispatchClearsEverySlot() {
        Harness h=new Harness();
        h.body(4,0x1E58,1);
        h.step(1);h.step(2);h.step(3);
        assertEquals(5,h.arms().size());
        var rewind=h.manager.rewindSnapshottable();
        var before=rewind.capture();
        when(h.camera.getX()).thenReturn((short)0x3000);
        h.step(4);
        assertEquals(6,h.manager.getActiveObjects().stream().filter(o->!o.isDestroyed()).count(),
                "loc_85088/Go_Delete_Sprite publish status7; Delete_Current_Sprite waits one pass");
        var pending=rewind.capture();
        h.step(5);
        assertTrue(h.manager.getActiveObjects().stream().noneMatch(o->!o.isDestroyed()));
        var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);
            assertSnapshot(before,rewind.capture());
            h.step(4);
            assertSnapshot(pending,rewind.capture());
            h.step(5);
            assertSnapshot(after,rewind.capture());
        }
    }

    @Test void independentPhysicalParentChainsPurelyRecreateAndReplayMovingFractionalChildren() {
        Harness h=new Harness();
        h.body(4,0x1E58,1);
        h.body(40,0x1E78,0);
        for(int frame=1;frame<=50;frame++)h.step(frame);
        assertEquals(10,h.arms().size());
        var rewind=h.manager.rewindSnapshottable();
        var before=rewind.capture();
        var rng=h.services.rng().capture();
        for(int frame=51;frame<=71;frame++)h.step(frame);
        var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);h.services.rng().restore(rng);
            assertSnapshot(before,rewind.capture());
            assertEquals(10,h.arms().size(),"pure recreation allocates no additional chain");
            for(int frame=51;frame<=71;frame++)h.step(frame);
            assertSnapshot(after,rewind.capture());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void physicalBodyOverwriteRetiresTheChainBeforeNextPassAndReplays(boolean powered) {
        Harness h=new Harness();
        h.body(4,0x1E58,1);
        h.step(1);h.step(2);h.step(3);
        var rewind=h.manager.rewindSnapshottable();
        var before=rewind.capture();var rng=h.services.rng().capture();
        overwriteBody(h,powered);
        var preInit=rewind.capture();rewind.restore(preInit);assertSnapshot(preInit,rewind.capture());
        h.step(4);
        assertEquals(0,((MonkeyDudeExplosionObjectInstance)NativeSstSlotAccess.currentOwner(h.manager,4)).nativeMappingFrame(),
                "loc_1E626 publishes the actual explosion mapping in its own slot dispatch");
        assertEquals(5,h.arms().size(),"Child_Draw_Sprite marks every linked record in the same pass");
        var pending=rewind.capture();
        h.step(5);
        assertTrue(h.arms().isEmpty(),"Delete_Current_Sprite clears each marked physical record next pass");
        assertInstanceOf(MonkeyDudeExplosionObjectInstance.class,NativeSstSlotAccess.currentOwner(h.manager,4));
        var after=rewind.capture();
        for(int cycle=0;cycle<2;cycle++) {
            rewind.restore(before);h.services.rng().restore(rng);
            assertSnapshot(before,rewind.capture());
            overwriteBody(h,powered);h.step(4);assertSnapshot(pending,rewind.capture());
            h.step(5);assertSnapshot(after,rewind.capture());
        }
    }

    @Test void anUnknownReplacementCannotSupplyInventedClearedNativeFields() {
        Harness h=new Harness();
        var body=h.body(4,0x1E58,1);h.step(1);h.step(2);h.step(3);
        var root=h.arms().getFirst();
        h.manager.removeDynamicObject(body);
        h.manager.createDynamicObjectAtSlot(()->new UnknownOwner(body.getSpawn()),4);
        var before=root.captureRewindState();
        assertThrows(IllegalStateException.class,()->root.update(4,h.main));
        assertSnapshot(before,root.captureRewindState());
    }

    private static void overwriteBody(Harness h,boolean powered) {
        var body=(MonkeyDudeBadnikInstance)NativeSstSlotAccess.currentOwner(h.manager,4);
        int oldMapping=body.nativeMappingFrame();
        if(powered)body.onPoweredScreenAttack(null);else body.onPlayerAttack(null,null);
        assertTrue(body.isDestroyed());
        var explosion=(MonkeyDudeExplosionObjectInstance)NativeSstSlotAccess.currentOwner(h.manager,4);
        assertEquals(oldMapping,explosion.nativeMappingFrame(),
                "Touch_EnemyNormal leaves the previous mapping byte until loc_1E626 initializes it");
        assertInstanceOf(MonkeyDudeExplosionObjectInstance.class,NativeSstSlotAccess.currentOwner(h.manager,4),
                "Touch_EnemyNormal rewrites the existing physical SST, retaining its status7 writer");
    }

    private static final class UnknownOwner extends AbstractObjectInstance {
        UnknownOwner(ObjectSpawn spawn) { super(spawn,"UnknownOwner"); }
        @Override public void update(int vIntRunCount,com.openggf.game.PlayableEntity main) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static void assertSnapshot(Object expected,Object actual) {
        assertTrue(RewindSnapshotDiff.diffKey("object-manager",expected,actual).isEmpty(),
                ()->RewindSnapshotDiff.diffKey("object-manager",expected,actual).toString());
    }

    static final class Harness {
        final Camera camera=mock(Camera.class);
        final ObjectManager manager;
        final TestObjectServices services;
        final AbstractPlayableSprite main=new TestablePlayableSprite("sonic",(short)0,(short)0);
        Harness() {
            main.setCentreX((short)0x1EA6);main.setCentreY((short)0x700);
            when(camera.getX()).thenReturn((short)0x1E00);
            when(camera.getY()).thenReturn((short)0x400);
            when(camera.getWidth()).thenReturn((short)320);
            when(camera.getHeight()).thenReturn((short)224);
            var render=mock(ObjectRenderManager.class);
            services=new TestObjectServices() {
                @Override public ObjectRenderManager renderManager() { return render; }
            }.withCamera(camera)
                    .withGraphicsManager(GraphicsManager.getInstance()).withGameModule(new Sonic3kGameModule());
            manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,
                    GraphicsManager.getInstance(),camera,services);
            services.withDirectObjectManager(manager);
            manager.reset(0x1E00);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
        }
        MonkeyDudeBadnikInstance body(int slot,int x,int flip) {
            return manager.createDynamicObjectAtSlot(()->new MonkeyDudeBadnikInstance(
                    new ObjectSpawn(x,0x490,0x8E,0x10,flip,false,0)),slot);
        }
        void step(int frame) { manager.update(0x1E00,main,List.of(),frame); }
        List<AbstractObjectInstance> arms() {
            return manager.getActiveObjects().stream().filter(o->!o.isDestroyed())
                    .filter(o->o.getName().equals("MonkeyDudeArm")).map(AbstractObjectInstance.class::cast)
                    .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
        }
    }
}
