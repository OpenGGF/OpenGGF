package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameRng;
import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossBeam {
    private static final class Parent extends DezMinibossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"BeamTestParent"); control=2; }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int clock,PlayableEntity player) { }
    }
    private HeadlessTestFixture boot() {
        var game=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x3740,(short)0x2C0).startPositionIsCentre().build();
        game.sprite().setDebugMode(true); game.stepIdleFrames(1); return game;
    }
    @Test void beamChargesOnVintParityWaitsSixtyFourPassesAndGrowsBeforeDamage() {
        var parent=new Parent(); var beam=new DezMinibossBeam(parent,0x28C);
        var p1=mock(PlayableEntity.class); var p2=mock(PlayableEntity.class);
        var manager=mock(ObjectManager.class);
        var services=new TestObjectServices(){ @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(()->p1,()->List.of(p2)); } }.withDirectObjectManager(manager);
        beam.setServices(services);
        for(int clock=0;clock<=10;clock++) {
            beam.update(clock,null);
            assertEquals((clock&1)==0,beam.visible);
            assertEquals(6,beam.frame);
        }
        assertEquals(2,beam.stateForTest()); assertEquals(0x24,beam.childDy);
        for(int clock=11;clock<74;clock++) { beam.update(clock,null); assertEquals(6,beam.frame); }
        beam.update(74,null); assertEquals(0x1B,beam.frame); assertEquals(0x20,beam.childDy);
        assertEquals(parent.getY()+0x24,beam.getY(),"callback changes dy after Refresh_ChildPosition");
        for(int clock=75;clock<=81;clock++) { beam.update(clock,null); assertEquals(0x1C+clock-75,beam.frame); }
        assertEquals(4,beam.stateForTest()); assertEquals(parent.getY()+0x58,beam.getY());
        verifyNoInteractions(p1,p2); verify(manager,times(2)).addDynamicObjectAfterCurrent(any());
    }

    @Test void activeDamageUsesHalfOpenCentreRectangleAndOnlyNativeSlotsWithStandingAndInvulnerabilityExemptions() {
        var parent=new Parent(); var beam=new DezMinibossBeam(parent,0x28C);
        var p1=mock(PlayableEntity.class); var p2=mock(PlayableEntity.class); var extra=mock(PlayableEntity.class);
        var services=spy(new TestObjectServices(){ @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(()->p1,()->List.of(p2,extra)); } }.withDirectObjectManager(mock(ObjectManager.class)));
        beam.setServices(services);
        for(int clock=0;clock<=81;clock++) beam.update(clock,null);
        int x=beam.getX(),y=beam.getY();
        when(p1.getCentreX()).thenReturn((short)(x-0x18)); when(p1.getCentreY()).thenReturn((short)(y-0x48));
        when(p1.getRingCount()).thenReturn(7);
        when(p2.getCentreX()).thenReturn((short)(x+0x17)); when(p2.getCentreY()).thenReturn((short)(y+0x47));
        when(p2.isCpuControlled()).thenReturn(true);
        beam.update(82,null);
        verify(services).spawnLostRings(p1,82); verify(p1).applyHurtOrDeath(x,DamageCause.NORMAL,true);
        verify(p2).applyHurt(x,DamageCause.NORMAL); verifyNoInteractions(extra);
        clearInvocations(p1,p2,services);
        when(p1.getCentreX()).thenReturn((short)(x+0x18)); when(p2.getCentreY()).thenReturn((short)(y+0x48));
        beam.update(83,null);
        verify(p1,never()).applyHurtOrDeath(anyInt(),any(DamageCause.class),anyBoolean());
        verify(p2,never()).applyHurt(anyInt(),any(DamageCause.class));
        when(p1.getCentreX()).thenReturn((short)x); when(p2.getCentreY()).thenReturn((short)y);
        when(p1.isOnObject()).thenReturn(true); when(p2.getInvulnerable()).thenReturn(true);
        beam.update(84,null);
        verify(p1,never()).applyHurtOrDeath(anyInt(),any(DamageCause.class),anyBoolean());
        verify(p2,never()).applyHurt(anyInt(),any(DamageCause.class));
        parent.control=0; beam.update(85,null); assertTrue(beam.pendingDelete); assertEquals(0x80,beam.status);
        beam.update(86,null); assertTrue(beam.isDestroyed());
    }

    @Test void feetPartialPrefixesUseArenaFloorAndDisappearWithBeamWithoutHealing() {
        for(int free=0;free<=2;free++) {
            var game=boot(); var manager=GameServices.level().getObjectManager(); var parent=new Parent(); manager.addDynamicObject(parent);
            var beam=new DezMinibossBeam(parent,0x28C); manager.addDynamicObject(beam);
            manager.reserveAllButNFreeSlots(free);
            game.stepIdleFrames(84);
            var feet=manager.activeObjectsOfType(DezMinibossBeam.Foot.class); assertEquals(free,feet.size());
            for(int i=0;i<feet.size();i++) { assertEquals(0x33C,feet.get(i).getY()); assertEquals(parent.getX()+(i==0?-16:16),feet.get(i).getX()); }
            parent.control=0; game.stepIdleFrames(1);
            for(var foot:feet) assertTrue(foot.pendingDelete);
            game.stepIdleFrames(1); assertTrue(beam.isDestroyed());
            for(var foot:feet) assertTrue(foot.isDestroyed());
        }
    }

    @Test void managerRecreatesBeamFeetAndReplaysChargeGrowthAndLiveBeam() {
        for(int passes:new int[]{5,77,90}) {
            var game=boot(); var manager=GameServices.level().getObjectManager(); var parent=new Parent(); manager.addDynamicObject(parent);
            var beam=new DezMinibossBeam(parent,0x28C); manager.addDynamicObject(beam); game.stepIdleFrames(passes);
            var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture(); var expected=rows(game,100);
            for(var o:List.copyOf(manager.getActiveObjects())) if(o instanceof DezMinibossSprite d) d.setDestroyed(true);
            game.stepIdleFrames(1); registry.restore(saved);
            var restored=manager.activeObjectsOfType(DezMinibossBeam.class).getFirst();
            assertNotSame(beam,restored); assertNotSame(parent,restored.parentForTest());
            for(var foot:manager.activeObjectsOfType(DezMinibossBeam.Foot.class)) assertSame(restored,foot.parentForTest());
            assertEquals(expected,rows(game,100));
        }
    }

    @Test void coverWaitsSixtyFourStationaryPassesAndSparkMovesOnItsFirstHiddenPass() {
        boot(); var parent=new Parent(); var rng=mock(GameRng.class);
        when(rng.nextRaw()).thenReturn(0x00030005);
        var services=new TestObjectServices(){ @Override public GameRng rng() { return rng; } };
        var cover=new DezMinibossDebris(parent,DezMinibossDebris.COVER,0,-0x28,0); cover.setServices(services);
        for(int i=0;i<64;i++) { cover.update(i,null); assertEquals(0x3718,cover.getX()); assertEquals(0x2C0,cover.getY()); assertTrue(cover.visible); }
        cover.update(64,null); assertEquals(0x3716,cover.getX()); assertEquals(0x2BE,cover.getY()); assertFalse(cover.visible);
        var spark=new DezMinibossDebris(parent,DezMinibossDebris.SPARK,0,0,0); spark.setServices(services); spark.update(0,null);
        assertEquals(0x3739,spark.getX()); assertEquals(0x2BA,spark.getY()); assertEquals(0x12,spark.frame);
        assertEquals(-0x2C8,spark.yVelocity); assertTrue(spark.flipX); assertFalse(spark.visible); verify(rng,times(1)).nextRaw();
        var body=new DezMinibossDebris(parent,DezMinibossDebris.BODY,6,12,24); body.setServices(services); body.update(0,null);
        assertEquals(0x26,body.frame); assertEquals(0x374C,body.getX()); assertEquals(0x2D8,body.getY()); assertFalse(body.visible);
        body.update(1,null); assertFalse(body.visible); body.update(2,null); assertTrue(body.visible);
    }
    private List<String> rows(HeadlessTestFixture game,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {
            game.stepIdleFrames(1);
            rows.add(GameServices.level().getObjectManager().getActiveObjects().stream().filter(o->o instanceof DezMinibossSprite)
                    .map(o->(DezMinibossSprite)o).map(o->o.getSlotIndex()+":"+o.posX+":"+o.posY+":"+o.frame+":"+o.childDy+":"+o.visible+":"+o.pendingDelete)
                    .sorted().toList().toString());
        }
        return rows;
    }
}
