package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.game.ShieldType;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.difficulty.DeathEggGuardRecipe;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.mods.objects.DeathEggFactory;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Controlled native owner/manager evidence; no creator admission or cold DEZ route is claimed. */
class TestDeathEggAlertGuard {
    @ParameterizedTest
    @CsvSource({"STOCK,95,true","STOCK,96,false","STOCK,127,false","STOCK,128,false",
            "ALERT,95,true","ALERT,96,true","ALERT,127,true","ALERT,128,false"})
    void initialDetectionKeepsTheExactExclusiveBoundaries(DeathEggGuardRecipe recipe, int distance, boolean bonks) {
        for (int flags : new int[]{0,1}) {
            World world = new World(recipe, flags);
            world.activate();
            world.player.setCentreX((short) (World.X + (flags == 0 ? -distance : distance)));
            world.body.update(3, world.player);
            assertEquals(bonks, world.body.isBonkingForTest(), "native facing=" + flags + " distance=" + distance);
        }
    }

    @ParameterizedTest
    @CsvSource({"STOCK,95,true","STOCK,96,false","STOCK,127,false","STOCK,128,false",
            "ALERT,95,true","ALERT,96,true","ALERT,127,true","ALERT,128,false"})
    void patrolDetectionAfterACompletedBonkKeepsTheSameExclusiveBoundaries(
            DeathEggGuardRecipe recipe,int distance,boolean bonks){
        for(int flags:new int[]{0,1}){
            World world=new World(recipe,flags);world.activate();
            world.player.setCentreX((short)(World.X+(flags==0?-80:80)));
            world.body.update(3,world.player);assertTrue(world.body.isBonkingForTest());
            world.player.setCentreX((short)(World.X+500));
            int clock=4;
            while(world.body.isBonkingForTest()&&clock<404){
                world.body.update(clock,world.player);world.body.maceForTest().update(clock++,world.player);
            }
            assertFalse(world.body.isBonkingForTest(),"native mace must return before checking another PATROL trigger");
            assertFalse(world.body.isBonkInProgress());
            world.player.setCentreX((short)(world.body.getX()
                    +(world.body.isFacingLeftForTest()?-distance:distance)));
            world.body.update(clock,world.player);
            assertEquals(bonks,world.body.isBonkingForTest(),"post-bonk PATROL facing="+flags+" distance="+distance);
        }
    }

    @Test void playerOneAndDirectionRemainNativeAndSidekickDoesNotStartTheSlam() {
        for (int flags : new int[]{0,1}) {
            World wrongSide = new World(DeathEggGuardRecipe.ALERT, flags);
            wrongSide.activate();
            wrongSide.player.setCentreX((short) (World.X + (flags == 0 ? 80 : -80)));
            wrongSide.body.update(3, wrongSide.player);
            assertFalse(wrongSide.body.isBonkingForTest());
            World p2 = new World(DeathEggGuardRecipe.ALERT, flags);
            p2.activate();
            p2.player.setCentreX((short) (World.X + 500));
            p2.sidekick.setCentreX((short) (World.X + (flags == 0 ? -80 : 80)));
            p2.body.update(3, p2.sidekick);
            assertFalse(p2.body.isBonkingForTest(), "native Player1 remains authoritative even when P2 is the update argument");
        }
    }

    @ParameterizedTest @EnumSource(DeathEggGuardRecipe.class)
    void subtypePatrolAndNativeAngularCommitmentRemainUnchanged(DeathEggGuardRecipe recipe) {
        World world = new World(recipe, 0);
        world.activate();
        assertEquals(31, world.body.walkTimerForTest());
        assertEquals(63, world.body.walkTimerReloadForTest());
        assertEquals(-0x80, world.body.xVelocityForTest());
        for (int i=0;i<32;i++) world.body.update(i+3, world.player);
        assertFalse(world.body.isFacingLeftForTest());
        assertEquals(0x80, world.body.xVelocityForTest());
        world.player.setCentreX((short) (world.body.getX()+80));
        world.body.update(40, world.player);
        assertTrue(world.body.isBonkingForTest());
        var mace = world.body.maceForTest();
        int x = world.body.getX();
        for (int i=0;i<300 && world.body.isBonkInProgress();i++) {
            world.body.update(41+i, world.player);
            mace.update(41+i, world.player);
            assertEquals(x, world.body.getX(), "body stays committed while the native mace owns the slam");
        }
        assertFalse(world.body.isBonkInProgress());
        assertEquals(0, mace.slamOffsetForTest());
        assertEquals(0x9A, mace.getCollisionFlags());
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void currentEngineChildAllocationPrefixesDoNotHealOnDestructiveRecreation(int freeChildren) throws Exception {
        World world = new World(DeathEggGuardRecipe.ALERT, 0);
        world.manager.reserveAllButNFreeSlots(freeChildren);
        world.activate();
        assertEquals(freeChildren, world.manager.activeObjectsOfType(SpikebonkerBadnikInstance.SpikebonkerMace.class).size());
        world.recreate();
        assertEquals(DeathEggGuardRecipe.ALERT, value(world.body,"guardRecipe"));
        assertEquals(freeChildren, world.manager.activeObjectsOfType(SpikebonkerBadnikInstance.SpikebonkerMace.class).size());
        for (int slot=4;slot<94;slot++) {
            final int candidateSlot=slot;
            if (slot!=world.body.getSlotIndex() && world.manager.getActiveObjects().stream()
                    .noneMatch(o -> ((AbstractObjectInstance)o).getSlotIndex()==candidateSlot)) {
                world.manager.releaseDynamicSlot(slot);
            }
        }
        world.body.update(4,world.player);
        assertEquals(freeChildren,world.manager.activeObjectsOfType(SpikebonkerBadnikInstance.SpikebonkerMace.class).size());
        var mace = world.body.maceForTest();
        world.manager.removeDynamicObject(world.body);
        assertTrue(mace == null || mace.isDestroyed(), "unload also tolerates the native child allocation failure");
    }

    @Test void capturedPresetAndCommittedMaceRelinkAndReplayAfterActualRecreation() throws Exception {
        World world = new World(DeathEggGuardRecipe.ALERT, 0);
        world.activate();
        world.player.setCentreX((short)(World.X-100));
        world.body.update(3,world.player);
        assertTrue(world.body.isBonkingForTest());
        world.body.maceForTest().update(3,world.player);
        for(int i=0;i<7;i++) world.body.maceForTest().update(i+4,world.player);
        var before=world.rewind.capture();
        var original=world.body;
        var originalMace=world.body.maceForTest();
        world.body.update(11,world.player);originalMace.update(11,world.player);
        var expected=world.rewind.capture();
        world.restore(before);
        assertNotSame(original,world.body);
        assertNotSame(originalMace,world.body.maceForTest());
        assertEquals(DeathEggGuardRecipe.ALERT,value(world.body,"guardRecipe"));
        assertSame(world.body,value(world.body.maceForTest(),"body"));
        world.body.update(11,world.player);world.body.maceForTest().update(11,world.player);
        var replay=world.rewind.capture();
        for(var key:expected.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),replay.get(key));
            assertTrue(diff.isEmpty(),key+": "+diff);
        }
    }

    @ParameterizedTest @EnumSource(DeathEggGuardRecipe.class)
    void actualManagerUnloadRetiresTheNativeMace(DeathEggGuardRecipe recipe) {
        World world=new World(recipe,0);world.activate();
        var mace=world.body.maceForTest();
        world.manager.removeDynamicObject(world.body);
        mace.update(4,world.player);
        assertTrue(mace.isDestroyed(),"native Child_CheckParent must not retain an unloaded body");
    }

    @ParameterizedTest @EnumSource(DeathEggGuardRecipe.class)
    void publishedLiveMaceActuallyHurtsBeforeOwnerTeardown(DeathEggGuardRecipe recipe) {
        World world = touchWorld(recipe);
        world.manager.runTouchResponsesForPlayer(world.player, 4, true);
        assertTrue(world.player.isHurt(), "positive native touch-list witness must reach the live mace");
        assertFalse(world.player.hasShield());
    }

    @ParameterizedTest @EnumSource(DeathEggGuardRecipe.class)
    void repeatedOwnerTeardownImmediatelyExcludesAnAlreadyPublishedMaceFromTouch(DeathEggGuardRecipe recipe) {
        World world = touchWorld(recipe);
        var mace = world.body.maceForTest();
        world.manager.removeDynamicObject(world.body);
        assertTrue(mace.isDestroyed(), "parent-owned retirement happens before the child's next update");
        world.manager.removeDynamicObject(world.body);
        world.body.onUnload();
        assertTrue(mace.isDestroyed(), "repeated native owner teardown stays retired");
        // Retain the actual preceding-pass collision list: the retired child may
        // still have an entry, so rebuild/pruning must not hide a live hurt box.
        world.manager.runTouchResponsesForPlayer(world.player, 4, true);
        assertFalse(world.player.isHurt(), "a retired mace must not hurt through its previously published entry");
        assertFalse(world.player.getDead());
        assertTrue(world.player.hasShield());
    }

    private static World touchWorld(DeathEggGuardRecipe recipe) {
        var table = org.mockito.Mockito.mock(TouchResponseTable.class);
        org.mockito.Mockito.when(table.getWidthRadius(0x1A)).thenReturn(16);
        org.mockito.Mockito.when(table.getHeightRadius(0x1A)).thenReturn(16);
        World world = new World(recipe, 0, table);
        world.camera.setX((short) 0x180);
        world.camera.setY((short) 0xC0);
        world.activate();
        world.manager.update(0x180, world.player, List.of(), 3);
        world.manager.refreshPostCameraRenderState();
        var mace = world.body.maceForTest();
        world.player.setGameRulesForTest(GameRules.SONIC_3K);
        world.player.setCentreX((short) mace.getX());
        // Below the body hitbox, inside the mace's lower native touch radius.
        world.player.setCentreY((short) (mace.getY() + 25));
        world.player.setShieldStateForTest(true, ShieldType.BASIC);
        world.manager.publishRepresentedInitialCollisionResponseList();
        world.manager.snapshotTouchResponseState(true);
        return world;
    }

    @ParameterizedTest @EnumSource(DeathEggGuardRecipe.class)
    void nativeBodyDefeatRetiresTheCommittedMace(DeathEggGuardRecipe recipe) {
        World world=new World(recipe,0);world.activate();
        var mace=world.body.maceForTest();
        world.body.onPlayerAttack(world.player,null);
        assertTrue(world.body.isDestroyed());
        mace.update(4,world.player);
        assertTrue(mace.isDestroyed());
    }

    @Test void facadeKeepsOriginalFieldsAndRejectsOtherIdsOrNulls() {
        var record=new ObjectSpawn(0x0B00,0x0848,0xA4,0x40,0,false,0x0848,47);
        assertEquals(record,DeathEggFactory.spikebonker(record,DeathEggGuardRecipe.ALERT).getSpawn());
        var flipped=new ObjectSpawn(0x12F8,0x0528,0xA4,0x40,2,false,0x4528,151);
        assertEquals(flipped,DeathEggFactory.spikebonker(flipped,DeathEggGuardRecipe.ALERT).getSpawn());
        assertThrows(IllegalArgumentException.class,()->DeathEggFactory.spikebonker(
                new ObjectSpawn(0,0,0xA5,0,0,false,0),DeathEggGuardRecipe.ALERT));
        assertThrows(NullPointerException.class,()->DeathEggFactory.spikebonker(null,DeathEggGuardRecipe.ALERT));
        assertThrows(NullPointerException.class,()->DeathEggFactory.spikebonker(record,null));
    }

    private static Object value(Object target,String name)throws Exception {
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }

    private static final class World {
        static final int X=0x200,Y=0x100;
        final TestablePlayableSprite player=new TestablePlayableSprite("sonic",(short)(X+500),(short)Y);
        final TestablePlayableSprite sidekick=new TestablePlayableSprite("tails",(short)(X+500),(short)Y);
        final ObjectPlayerQuery query=new ObjectPlayerQuery(()->player,()->List.of(sidekick));
        final TestObjectServices services=new TestObjectServices(){
            @Override public ObjectPlayerQuery playerQuery(){return query;}
        };
        final Camera camera=new Camera(SonicConfigurationService.createStandalone());
        final ObjectManager manager;
        final RewindRegistry rewind=new RewindRegistry();
        SpikebonkerBadnikInstance body;
        World(DeathEggGuardRecipe recipe,int flags) {
            this(recipe, flags, null);
        }
        World(DeathEggGuardRecipe recipe,int flags,TouchResponseTable table) {
            services.withCamera(camera).withGameState(new GameStateManager()).withGameModule(new Sonic3kGameModule());
            if (table != null) services.withDebugOverlay(org.mockito.Mockito.mock(com.openggf.debug.DebugOverlayManager.class));
            manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,table,null,camera,services);
            services.withDirectObjectManager(manager);manager.reset(0);
            body=manager.createDynamicObject(()->new SpikebonkerBadnikInstance(
                    new ObjectSpawn(X,Y,0xA4,0x20,flags,false,Y),recipe));
            rewind.register(manager.rewindSnapshottable());
        }
        void activate(){
            AbstractObjectInstance.updateCameraBounds(0,0,1024,1024,0);
            body.update(0,null);body.refreshPostCameraRenderState();body.update(1,null);body.update(2,null);
        }
        void recreate(){restore(rewind.capture());}
        void restore(com.openggf.game.rewind.CompositeSnapshot snapshot){
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
            rewind.restore(snapshot);body=manager.activeObjectsOfType(SpikebonkerBadnikInstance.class).getFirst();
        }
    }
}
