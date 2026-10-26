package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Real five-child production birth, fatal poll and Child_Draw*Sprite2 delete tail. */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizMinibossChildRetirement {
    @AfterEach void clear() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @Test void realFiveChildrenHoldTheLiteralWaitThenRetireAtTheirOwnPhysicalPassAndReplayTwice()
            throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        fixture.camera().setX((short)0x10E0);
        var boss = manager.createDynamicObject(() -> new AizMinibossInstance(
                new ObjectSpawn(0x11F0,0x289,0x91,0,0,false,0x289)));
        int pass=0;
        while (pass<256 && boss.getState().routine!=6) scan(fixture,pass++);
        assertEquals(6,boss.getState().routine,"natural arena wait must execute the actual five-child birth callback");
        var family=family(manager);
        assertEquals(5,family.size());
        int controlSlot=boss.getSlotIndex();
        Set<Integer> childSlots=family.stream().map(AbstractObjectInstance::getSlotIndex).collect(Collectors.toSet());
        assertEquals(5,childSlots.size());
        assertTrue(childSlots.stream().allMatch(s->s>controlSlot),
                "real CreateChild1_Normal admits after current; no artificial lower-slot variant");
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        int fatalPass=pass;
        int scoreBefore=GameServices.gameState().getScore();
        var before=registry.capture();
        var failures=new ArrayList<String>();
        enterFatal(fixture);
        scan(fixture,fatalPass);
        check(failures,family(manager).size()==5,"all five remain live on the fatal poll pass");
        assertEquals(0x3F,readInt(boss,"defeatHandoffTimer"));
        int retainedX=boss.getX(),retainedY=boss.getY();
        for(int tick=1;tick<=32;tick++) scan(fixture,fatalPass+tick);
        check(failures,family(manager).size()==5,"all five remain live halfway through literal64 wait");
        var liveWait=registry.capture();
        for(int tick=33;tick<=63;tick++) scan(fixture,fatalPass+tick);
        check(failures,family(manager).size()==5,"all five remain live through wait word zero");
        assertEquals(0,readInt(onlyBoss(manager),"defeatHandoffTimer"));
        scan(fixture,fatalPass+64);
        var flow=assertInstanceOf(S3kBossDefeatSignpostFlow.class,
                NativeSstSlotAccess.currentOwner(manager,controlSlot));
        check(failures,family(manager).size()==5,"physical replacement preserves the five child SSTs through their pending-delete pass");
        for(var child:family(manager)) {
            check(failures,readBoolean(child,"nativeRetirementPending"),"each higher child must install Delete_Current on its own flag-observing pass");
            if(child instanceof AizMinibossBodyChild) {
                check(failures,child.getX()==retainedX && child.getY()==retainedY+0x20,
                        "Refresh_ChildPositionAdjusted consumes retained boss XY, not flow getY=0");
            }
        }
        manager.validateRewindReferenceClosure();
        var pending=registry.capture();
        scan(fixture,fatalPass+65);
        check(failures,family(manager).isEmpty(),"next own Delete_Current passes retire the five children");
        for(int slot:childSlots) check(failures,NativeSstSlotAccess.currentOwner(manager,slot)==null,
                "retired child slot is genuinely free: "+slot);
        var deleted=registry.capture();
        int reused=fillOrdinaryFreeHolesUntilChildHole(manager,childSlots);
        for(int tick=66;tick<=73;tick++) scan(fixture,fatalPass+tick);
        var after=registry.capture();
        assertEquals(scoreBefore+1000,GameServices.gameState().getScore());
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());
            enterFatal(fixture);
            for(int tick=0;tick<=32;tick++) scan(fixture,fatalPass+tick);
            same(liveWait,registry.capture());
            for(int tick=33;tick<=64;tick++) scan(fixture,fatalPass+tick);
            same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(deleted,registry.capture());
            replayReuseTail(fixture,fatalPass,childSlots);same(after,registry.capture());
            registry.restore(liveWait);same(liveWait,registry.capture());
            for(int tick=33;tick<=64;tick++) scan(fixture,fatalPass+tick);
            same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(deleted,registry.capture());
            replayReuseTail(fixture,fatalPass,childSlots);same(after,registry.capture());
            registry.restore(pending);same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(deleted,registry.capture());
            replayReuseTail(fixture,fatalPass,childSlots);same(after,registry.capture());
            registry.restore(deleted);same(deleted,registry.capture());
            replayReuseTail(fixture,fatalPass,childSlots);same(after,registry.capture());
        }
        System.out.printf("AIZ_CHILD_TAIL control=%d children=%s fatalPass=%d retainedXY=%04X/%04X flow=%s reused=%d failures=%s%n",
                controlSlot,childSlots,fatalPass,retainedX,retainedY,flow.getClass().getSimpleName(),reused,failures);
        assertTrue(failures.isEmpty(),()->String.join("; ",failures));
    }

    @Test void naturalActiveBarrelsAndBodyRunThroughFatalWaitAndTheFinalReplacementPass() throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,1).build();
        var manager=GameServices.level().getObjectManager();
        fixture.camera().setX((short)0x10C0);
        var boss=manager.createDynamicObject(()->new AizMinibossInstance(
                new ObjectSpawn(0x11D0,0x289,0x91,0,0,false,0x289)));
        assertEquals(com.openggf.game.PlayerCharacter.KNUCKLES,
                com.openggf.game.sonic3k.runtime.S3kRuntimeStates.resolvePlayerCharacter(
                        GameServices.zoneRuntimeRegistry(),SonicConfigurationService.getInstance()));
        int pass=0;
        // Observe the ordinary Obj_Wait underflow inlet for StartSwing. No phase/timer writes:
        // this next parent routine sets activation before the post-routine fatal poll.
        while(pass<2048 && !(readInt(boss,"waitTimer")==0
                && readField(boss,"waitCallback").toString().equals("SWING_PREP_COMPLETE"))) scan(fixture,pass++);
        assertTrue(pass<2048,"natural StartSwing callback must become ready");
        assertEquals(5,family(manager).size());
        int controlSlot=boss.getSlotIndex(),fatalPass=pass;
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();
        var failures=new ArrayList<String>();
        enterFatal(fixture);
        scan(fixture,pass++);
        var body=manager.activeObjectsOfType(AizMinibossBodyChild.class).getFirst();
        int firstFrame=readInt(body,"mappingFrame");
        scan(fixture,pass++);
        check(failures,readInt(body,"mappingFrame")!=firstFrame,
                "Animate_Raw continues before status7 collision suppression");
        for(;pass<=fatalPass+63;pass++) scan(fixture,pass);
        var beforeReplacement=registry.capture();
        check(failures,manager.activeObjectsOfType(AizMinibossFlameBarrelChild.class).stream()
                .anyMatch(b->stateName(b).equals("FIRING_MINIBOSS")),
                "natural delayed third barrel reaches its firing routine before replacement");
        scan(fixture,pass++);
        assertInstanceOf(S3kBossDefeatSignpostFlow.class,NativeSstSlotAccess.currentOwner(manager,controlSlot));
        var finalShots=manager.activeObjectsOfType(AizMinibossNapalmProjectile.class);
        check(failures,finalShots.size()==1,"final flag-observing barrel routine allocates the flare/FallingShot pair");
        if(!finalShots.isEmpty()) check(failures,readBoolean(finalShots.getFirst(),"pendingDelete"),
                "new FallingShot executes INIT before physical grandparent status7 marks next-pass deletion");
        check(failures,manager.activeObjectsOfType(AizMinibossBarrelShotFlareChild.class).size()>0,
                "independent flare allocation is preserved on the final routine pass");
        manager.validateRewindReferenceClosure();
        var pending=registry.capture();
        scan(fixture,pass++);
        check(failures,family(manager).isEmpty(),"pending child clear follows its own next entry");
        check(failures,manager.activeObjectsOfType(AizMinibossNapalmProjectile.class).isEmpty(),
                "pending FallingShot clears without a stale Java boss reference");
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());enterFatal(fixture);
            for(int tick=0;tick<=63;tick++) scan(fixture,fatalPass+tick);
            same(beforeReplacement,registry.capture());
            scan(fixture,fatalPass+64);same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(after,registry.capture());
            registry.restore(beforeReplacement);same(beforeReplacement,registry.capture());
            scan(fixture,fatalPass+64);same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(after,registry.capture());
            registry.restore(pending);same(pending,registry.capture());
            scan(fixture,fatalPass+65);same(after,registry.capture());
        }
        System.out.printf("AIZ_ACTIVE_TAIL control=%d fatalPass=%d finalShots=%d failures=%s%n",
                controlSlot,fatalPass,finalShots.size(),failures);
        assertTrue(failures.isEmpty(),()->String.join("; ",failures));
    }

    private static Object readField(Object owner,String name) throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static String stateName(Object owner) {
        try{return readField(owner,"state").toString();}catch(Exception e){throw new AssertionError(e);}
    }

    @Test void retainedHeaderKeepsKnownPoseFlagsAndOffsetsThroughWholeWorldRecreation() throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,1).build();
        var manager=GameServices.level().getObjectManager();
        var flow=manager.createDynamicObject(()->new S3kBossDefeatSignpostFlow(0x1111,0,
                S3kBossDefeatSignpostFlow.CleanupAction.NONE));
        assertThrows(IllegalStateException.class,flow::retainedChildDrawHeader,
                "ordinary flow constructors do not invent unknown native fields");
        // Explicit native parent-header component inlet, not trace or allocator state.
        flow.retainChildDrawHeader(new S3kChildDrawParentHeader(0xFFFE,0x7FF,0x83,7,true));
        flow.beginNativeReplacement(flow.getSlotIndex());
        var expected=new S3kChildDrawParentHeader(0xFFFE,0x7FF,3,0x17,true);
        assertEquals(expected,flow.retainedChildDrawHeader());
        assertEquals(0,flow.getY(),"public sign flow coordinate is separate from retained parent y_pos");
        int slot=flow.getSlotIndex();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();
        flow.offsetNativePositionWordsPreserveSubpixel(-0x20,-0x80);
        flow.clearRetainedBarrelActivation();
        var offset=new S3kChildDrawParentHeader(0xFFDE,0x77F,3,0x15,true);
        assertEquals(offset,flow.retainedChildDrawHeader());
        assertEquals(0x10F1,flow.getX());
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());
            var restored=assertInstanceOf(S3kBossDefeatSignpostFlow.class,NativeSstSlotAccess.currentOwner(manager,slot));
            assertNotSame(flow,restored);assertEquals(expected,restored.retainedChildDrawHeader());
            restored.offsetNativePositionWordsPreserveSubpixel(-0x20,-0x80);
            restored.clearRetainedBarrelActivation();
            assertEquals(offset,restored.retainedChildDrawHeader());same(after,registry.capture());
            registry.restore(after);same(after,registry.capture());
        }
    }

    private static void replayReuseTail(HeadlessTestFixture fixture,int fatalPass,Set<Integer> childSlots) {
        fillOrdinaryFreeHolesUntilChildHole(GameServices.level().getObjectManager(),childSlots);
        for(int tick=66;tick<=73;tick++) scan(fixture,fatalPass+tick);
    }
    private static int fillOrdinaryFreeHolesUntilChildHole(ObjectManager manager,Set<Integer> childSlots) {
        // The ROM scene can already have lower holes. Use only ordinary allocations;
        // do not assign a child address or mistake the first free slot for a child hole.
        for(int attempt=0;attempt<94;attempt++) {
            int free=manager.firstFreeDynamicSlot();
            assertTrue(free>=0,"retired child holes must remain allocatable");
            var owner=manager.createDynamicObject(()->new ReuseWitness(new ObjectSpawn(0,0,0,0,0,false,0)));
            assertEquals(free,owner.getSlotIndex());
            if(childSlots.contains(owner.getSlotIndex())) return owner.getSlotIndex();
        }
        throw new AssertionError("ordinary free allocation never reached a retired child hole");
    }
    private static void enterFatal(HeadlessTestFixture fixture) {
        var boss=onlyBoss(GameServices.level().getObjectManager());
        boss.getState().hitCount=1; // Declared final-hit component stimulus; no routine/timer/slot/trace state writes.
        boss.onPlayerAttack(fixture.sprite(),null);
    }
    private static AizMinibossInstance onlyBoss(ObjectManager manager) {
        return manager.activeObjectsOfType(AizMinibossInstance.class).getFirst();
    }
    private static List<AbstractObjectInstance> family(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(o->o instanceof AizMinibossBodyChild
                || o instanceof AizMinibossArmChild || o instanceof AizMinibossFlameBarrelChild)
                .map(AbstractObjectInstance.class::cast).filter(o->!o.isDestroyed()).toList();
    }
    private static void scan(HeadlessTestFixture fixture,int pass) {
        GameServices.level().getObjectManager().update(fixture.camera().getX(),fixture.sprite(),List.of(),pass);
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet()) assertTrue(
                RewindSnapshotDiff.keyEquals(key,expected.get(key),actual.get(key)),
                ()->key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
    private static void check(List<String> failures,boolean condition,String message) { if(!condition)failures.add(message); }
    private static int readInt(Object owner,String name) throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(owner);
    }
    private static boolean readBoolean(Object owner,String name) throws Exception {
        for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass()) try {
            var f=c.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(owner);
        } catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static final class ReuseWitness extends AbstractObjectInstance implements SpawnRewindRecreatable {
        public ReuseWitness(ObjectSpawn spawn){super(spawn,"AizChildHoleReuse");setRomWorldPositioned(false);}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int vIntRunCount,PlayableEntity player){ }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands){ }
    }
}
