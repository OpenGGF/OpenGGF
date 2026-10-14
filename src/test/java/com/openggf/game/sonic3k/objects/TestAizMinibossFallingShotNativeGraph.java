package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Literal FallingShot/Hitbox lifecycle and declared full-world replay, not a cold route certificate. */
class TestAizMinibossFallingShotNativeGraph {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @Test void cutsceneCounterOneAdmitsTheLiteralFlareAndFallingShotPair() throws Exception {
        var h = harness(320);
        h.barrel(7).setPositionCounter(2);
        call(h.barrel(7), "fireCutsceneShot");
        h.step(0);
        assertInstanceOf(AizMinibossBarrelShotFlareChild.class, owner(h, 9));
        var shot = assertInstanceOf(AizMinibossNapalmProjectile.class, owner(h, 10),
                "ChildObjDat_690A8 names FallingShot, not the advanced common-shot approximation");
        assertEquals(2, shot.getSpawn().subtype());
        assertEquals(0, shot.getCollisionFlags(), "Init clears the cutscene barrel's collision byte");
        assertEquals(h.barrel(7).getY() + 4, shot.getY(), "same higher-slot Init draws without moving");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void initCopiesTheActualBarrelRoleOnceAndRetainsRawStateAcrossPause(boolean cutscene) throws Exception {
        var h = harness(320);
        var barrel = barrel(h, cutscene);
        var shot = addShot(h, barrel, 10);
        assertEquals(0, shot.getCollisionFlags(), "cleared SST before SetUp_ObjAttributes");
        h.step(0);
        assertEquals(cutscene ? 0 : 0x98, shot.getCollisionFlags());
        int birthY = shot.getY();
        assertEquals(0x60, field(shot, "timer"));
        for (int entry = 1; entry <= 0x61; entry++) h.step(entry);
        assertEquals(birthY - 0x61 * 4, shot.getY());
        assertEquals("PAUSE", field(shot, "state").toString());
        assertEquals(8, field(shot, "timer"));
        for (int entry = 0x62; entry <= 0x6A; entry++) h.step(entry);
        assertEquals("DROP_WAIT", field(shot, "state").toString());
        h.step(0x6B);
        assertEquals("FALL", field(shot, "state").toString());
        try (var terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), eq(8)))
                    .thenReturn(new TerrainCheckResult(20, (byte) 0, 0));
            h.step(0x6C);
        }
        assertEquals(0x0D, field(shot, "mappingFrame"),
                "Pause/Drop do not reset the cleared Animate_Raw cursor/timer bank");
    }

    @Test void routineRunsBeforeParentStatusAndGoDeleteClearsOnlyTheFollowingDispatch() throws Exception {
        var h = harness(320);
        var shot = addShot(h, h.barrel(7), 9);
        h.step(0);
        int y = shot.getY();
        h.boss().getState().defeated = true;
        h.step(1);
        assertEquals(y - 4, shot.getY(), "wrapper reads parent status only after Rise movement");
        assertSame(shot, owner(h, 9), "Go_Delete_Sprite marks the live physical slot");
        assertFalse(shot.isDestroyed());
        assertEquals(0, shot.getCollisionFlags(), "the parent-status branch omits AddList/Draw");
        h.step(2);
        assertNull(owner(h, 9), "next Delete_Current_Sprite dispatch clears the physical owner");
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void impactChildrenCopyCollisionAndCannotReuseTheRetiringParentSlot(boolean cutscene) throws Exception {
        var h = harness(320);
        var shot = addShot(h, barrel(h, cutscene), 10);
        h.step(0);
        call(shot, "explode"); // declared native ObjHitFloor callback boundary
        assertSame(shot, owner(h, 10));
        assertFalse(shot.isDestroyed(), "Go_Delete callback does not clear the parent before child Init");
        assertEquals(cutscene ? 0 : 0x98, shot.getCollisionFlags());
        var children = h.manager.getActiveObjects().stream()
                .filter(AizMinibossNapalmExplosionChild.class::isInstance)
                .map(AizMinibossNapalmExplosionChild.class::cast).toList();
        assertEquals(7, children.size());
        assertEquals(List.of(0,2,4,6,8,10,12), children.stream().map(c -> c.getSpawn().subtype()).toList());
        for (var child : children) assertTrue(child.getSlotIndex() > 10);
        h.step(1);
        assertNull(owner(h, 10));
        for (var child : children) {
            assertEquals(!cutscene, field(child,"hazardous"), "Init copies the live shot collision role");
            assertEquals(0, child.getCollisionFlags(), "Hitbox Init has no AddList/Draw tail");
        }
        var harmful = new java.util.HashSet<Integer>();
        for (int entry = 2; entry <= 27; entry++) {
            h.step(entry);
            for (var child : children) if (child.getCollisionFlags() != 0) harmful.add(child.getSpawn().subtype());
        }
        assertEquals(cutscene ? 0 : 7, harmful.size(), "each independent subtype reaches its own routine-4 window");
    }

    @Test void finalRawCallbackStillPublishesFrameFiveAndTouchBeforePhysicalClear() {
        var h = harness(320);
        var child = h.manager.createDynamicObjectAtSlot(() -> new AizMinibossNapalmExplosionChild(0x3050,0x340,12,true), 9);
        // Init, Wait(-1), first Animate; then 2+2+3+4+5+5 = 21 mapping publications.
        for (int entry = 0; entry <= 23; entry++) h.step(entry);
        assertSame(child, owner(h, 9), "F4/Go_Delete stays occupied through Draw_And_Touch_Sprite");
        assertFalse(child.isDestroyed());
        assertEquals(0x97, child.getCollisionFlags());
        assertEquals(0, fieldUnchecked(child,"rawCursor"));
        assertEquals(0, fieldUnchecked(child,"animationTimer"));
        assertEquals(5, fieldUnchecked(child,"animationIndex"));
        h.step(24);
        assertNull(owner(h, 9));
    }

    @Test void purePhaseOneRetainsEveryCapturedSpawnFieldWithoutParentLookup() {
        var h = harness(320);
        var shot = addShot(h, h.barrel(7), 9);
        var spawn = new ObjectSpawn(0x3055,0x244,0x90,2,3,false,0x4242);
        assertEquals(spawn, shot.recreateForRewind(new RewindRecreateContext(spawn,null,h.services)).getSpawn());
        var child = new AizMinibossNapalmExplosionChild(1,2,12,false);
        assertEquals(spawn, child.recreateForRewind(new RewindRecreateContext(spawn,null,h.services)).getSpawn());
    }

    @ParameterizedTest @ValueSource(ints = {320,352,400,528,800})
    void forcedGraphRecreationReplaysAdmissionImpactAndRetirementTwice(int width) throws Exception {
        var h = harness(width);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        addShot(h, h.barrel(7), 9);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        for (int entry = 0; entry < 45; entry++) h.step(entry);
        var rising = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same("object-manager",before,rewind.capture());
            assertSame(h.boss(), field(owner(h,9),"parent"));
            assertSame(h.barrel(7), field(owner(h,9),"barrel"));
            for (int entry = 0; entry < 45; entry++) h.step(entry);
            same("object-manager",rising,rewind.capture());
            rewind.restore(rising); same("object-manager",rising,rewind.capture());
            call(owner(h,9), "explode");
            assertNotNull(owner(h,9), "impact's Go_Delete marker must remain an occupied owner");
            var marker = rewind.capture();
            for (int entry = 45; entry < 90; entry++) h.step(entry);
            var cleared = rewind.capture();
            rewind.restore(marker); same("object-manager",marker,rewind.capture());
            for (int entry = 45; entry < 90; entry++) h.step(entry);
            same("object-manager",cleared,rewind.capture());
            rewind.restore(before); same("object-manager",before,rewind.capture());
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void loadedAizWorldRecreatesAndReplaysTheDeclaredFallingGraphWithoutFilteringRegistryKeys() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0,0).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(6);
        var manager = GameServices.level().getObjectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        int x = fixture.camera().getX() + 100, y = fixture.camera().getY() + 100;
        var boss = manager.createDynamicObject(() -> new TestAizMinibossFlareNativeGraph.FrozenBoss(
                new ObjectSpawn(x,y,0x90,0,0,false,0)));
        var barrel = manager.createDynamicObject(() -> new AizMinibossFlameBarrelChild(boss,0,true));
        boss.getChildComponents().add(barrel);
        var shot = manager.createDynamicObject(() -> new AizMinibossNapalmProjectile(boss,barrel,x,y+4,2));
        int slot = shot.getSlotIndex();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        fixture.stepIdleFrames(45);
        var rising = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before); sameWorld(before,registry.capture());
            fixture.stepIdleFrames(45); sameWorld(rising,registry.capture());
            registry.restore(rising); sameWorld(rising,registry.capture());
            var retiring = NativeSstSlotAccess.currentOwner(manager,slot);
            call(retiring,"explode");
            assertSame(retiring,NativeSstSlotAccess.currentOwner(manager,slot));
            assertFalse(retiring.isDestroyed());
            var impact = registry.capture();
            fixture.stepIdleFrames(45);
            var after = registry.capture();
            registry.restore(impact); sameWorld(impact,registry.capture());
            fixture.stepIdleFrames(45); sameWorld(after,registry.capture());
            registry.restore(before); sameWorld(before,registry.capture());
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width) {
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        h.services.withRomReader(rawReader());
        return h;
    }
    /** Independent shipped banks for unit services; production reads the actual ROM. */
    static RomByteReader rawReader() {
        var rom=mock(RomByteReader.class);
        int[] addresses={0x6913F,0x6914C,0x83FCC};
        byte[][] banks={{7,1,7,1,8,1,9,3,10,3,11,3,(byte)0xF4},
                {1,12,13,(byte)0xFC}, {0,0,0,1,1,1,2,2,3,3,4,4,5,4,(byte)0xF4}};
        for(int b=0;b<addresses.length;b++) {
            when(rom.slice(addresses[b],banks[b].length)).thenReturn(banks[b].clone());
            for(int i=0;i<banks[b].length;i++)when(rom.readU8(addresses[b]+i)).thenReturn(banks[b][i]&0xFF);
        }
        return rom;
    }
    private static Object fieldUnchecked(Object o,String name) {
        try{return field(o,name);}catch(Exception e){throw new AssertionError(e);}
    }

    @ParameterizedTest @ValueSource(ints={0,2})
    void commonSecondChildAlsoHasSubtypeTwoIndependentOfItsBarrelIndex(int index) throws Exception {
        var h=harness(320);
        var barrel=index==0?h.barrel(7):h.manager.createDynamicObjectAtSlot(
                ()->new AizMinibossFlameBarrelChild(h.boss(),index,true),9);
        if(index==2)h.boss().getChildComponents().add(barrel);
        barrel.setPositionCounter(3);
        call(barrel,"fireCutsceneShot");
        h.step(0);
        var shot=h.manager.getActiveObjects().stream().filter(AizMinibossBarrelShotChild.class::isInstance)
                .map(AizMinibossBarrelShotChild.class::cast).findFirst().orElseThrow();
        assertEquals(2,shot.getSpawn().subtype(),"CreateChild1_Normal assigns second child subtype2");
        assertEquals(index*2,field(shot,"barrelSubtype"),"position-table input stays independent");
    }

    private static AizMinibossFlameBarrelChild barrel(TestAizMinibossFlareNativeGraph.Harness h, boolean cutscene) {
        if(cutscene) return h.barrel(7);
        var barrel = h.manager.createDynamicObjectAtSlot(() -> new AizMinibossFlameBarrelChild(h.boss(),0,false),9);
        h.boss().getChildComponents().add(barrel); return barrel;
    }
    private static AizMinibossNapalmProjectile addShot(TestAizMinibossFlareNativeGraph.Harness h,
            AizMinibossFlameBarrelChild barrel, int slot) {
        return h.manager.createDynamicObjectAtSlot(() -> new AizMinibossNapalmProjectile(h.boss(),barrel,
                barrel.getX(),barrel.getY()+4,2),slot);
    }
    private static ObjectInstance owner(TestAizMinibossFlareNativeGraph.Harness h,int slot) {
        return NativeSstSlotAccess.currentOwner(h.manager,slot);
    }
    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void call(Object object,String name) throws Exception {
        Method m=object.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(object);
    }
    private static void same(String key,Object expected,Object actual) {
        var diff=RewindSnapshotDiff.diffKey(key,expected,actual);assertTrue(diff.isEmpty(),()->diff.toString());
    }
    private static void sameWorld(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet())same(key,expected.get(key),actual.get(key));
    }
}
