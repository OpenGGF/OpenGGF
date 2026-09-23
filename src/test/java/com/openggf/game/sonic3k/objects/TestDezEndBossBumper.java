package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
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
class TestDezEndBossBumper {
    private static final class Parent extends DezEndBossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3500,0x2A0,0xA7,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"EndBossTestParent"); }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int clock,PlayableEntity player) { }
    }
    private record Fixture(Parent parent,DezEndBossBumper bumper,TestPlayableSprite p1,TestPlayableSprite p2) { }
    private Fixture fixture(int subtype) throws Exception {
        var parent=new Parent(); var bumper=new DezEndBossBumper(parent,subtype);
        var p1=new TestPlayableSprite(); var p2=new TestPlayableSprite();
        position(p1,0x3500,0x320); position(p2,0x3580,0x2A0);
        var services=mock(ObjectServices.class);
        when(services.romReader()).thenReturn(TestEnvironment.objectServices().romReader());
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(()->p1,()->List.of(p2)));
        bumper.setServices(services);
        return new Fixture(parent,bumper,p1,p2);
    }
    private static void position(TestPlayableSprite p,int x,int y) {
        NativePositionOps.writeXPosPreserveSubpixel(p,x); NativePositionOps.writeYPosPreserveSubpixel(p,y);
    }
    @Test void tablePlacesAllFourCardinalDirectionsAndEachHalfSelectsItsNativeFrame() throws Exception {
        var f=fixture(0);
        int[][] cases={{0,128,0,0,40,4,0},{128,0,64,40,0,10,0},
                {0,-128,128,0,-40,4,1},{-128,0,192,-40,0,10,1}};
        for(int[] c:cases) {
            position(f.p1,0x3500+c[0],0x2A0+c[1]); f.bumper.update(0,null);
            assertEquals(c[2],f.bumper.angleForTest());
            assertEquals(0x3500+c[3],f.bumper.getX()); assertEquals(0x2A0+c[4],f.bumper.getY());
            assertEquals(c[5],f.bumper.frame); assertEquals(c[6]!=0,f.bumper.flipX);
            assertEquals(f.bumper.flipX,f.bumper.flipY);
        }
    }
    @Test void openingClampLeavesUpperHalfFreeAndBothSubtypesTrackTheirOwnNativeSlot() throws Exception {
        var f=fixture(0); f.parent.control=8; f.bumper.update(0,null);
        assertEquals(0x30,f.bumper.angleForTest());
        position(f.p1,0x34F0,0x320); f.bumper.update(0,null);
        assertEquals(0xD0,f.bumper.angleForTest());
        position(f.p1,0x3500,0x220); f.bumper.update(0,null);
        assertEquals(0x80,f.bumper.angleForTest());
        var second=fixture(2); second.bumper.update(0,null);
        assertEquals(0x40,second.bumper.angleForTest());
    }
    @Test void contactWaitsForOwnPassAndSimultaneousNativeContactsSelectP2() throws Exception {
        var f=fixture(0); f.bumper.update(0,null);
        f.p1.setXSpeed((short)77); f.p2.setXSpeed((short)88);
        f.bumper.orCollisionProperty(3);
        assertEquals(88,f.p2.getXSpeed());
        f.bumper.update(0,null);
        assertEquals(77,f.p1.getXSpeed());
        assertEquals((short)(TrigLookupTable.sinHex(0xFE)<<3),f.p2.getXSpeed());
        assertEquals((short)(TrigLookupTable.cosHex(0xFE)<<3),f.p2.getYSpeed());
        assertTrue(f.p2.getAir()); assertEquals(0,f.bumper.getCollisionProperty());
        assertEquals(0,f.parent.collisionProperty,"contact never damages the parent");
        assertFalse(TouchResponseAttackable.class.isInstance(f.bumper));
    }
    @Test void launchUsesAllFourVintPhasesWithoutChangingPlayerAnimation() throws Exception {
        var f=fixture(0); f.p1.setAnimationId(2); f.p1.setJumping(true); f.p1.setRollingJump(true);
        f.bumper.update(0,null);
        for(int clock=0;clock<4;clock++) {
            f.bumper.orCollisionProperty(1); f.bumper.update(clock,null);
            assertEquals((short)(TrigLookupTable.sinHex((clock-2)&255)<<3),f.p1.getXSpeed());
            assertEquals((short)(TrigLookupTable.cosHex((clock-2)&255)<<3),f.p1.getYSpeed());
            assertEquals(2,f.p1.getAnimationId());
        }
        assertFalse(f.p1.isJumping()); assertFalse(f.p1.getRollingJump());
    }
    @Test void deathStillConsumesPendingContactThenPublishesNoMoreTouch() throws Exception {
        var f=fixture(2); f.bumper.update(0,null); f.bumper.orCollisionProperty(1);
        f.parent.status=0x80; f.bumper.update(1,null);
        assertTrue(f.p1.getAir()); assertTrue(f.bumper.visible);
        assertEquals(0,f.bumper.getCollisionFlags());
        assertFalse(f.bumper.publishesTouchResponseListEntryThisFrame());
        assertEquals(0x200,f.bumper.xVelocity); assertEquals(-0x200,f.bumper.yVelocity);
        f.bumper.orCollisionProperty(3); assertEquals(0,f.bumper.getCollisionProperty());
    }
    @Test void realObjectManagerRecreatesParentAndBothBumpersWithoutHealingOrDuplicating() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(11,1)
                .startPosition((short)0x3500,(short)0x320).startPositionIsCentre().build();
        fixture.sprite().setDebugMode(true); DezEndBossTestSupport.retirePlacedEncounter(fixture);
        var manager=GameServices.level().getObjectManager();
        var parent=new Parent(); manager.addDynamicObject(parent);
        var first=new DezEndBossBumper(parent,0); var second=new DezEndBossBumper(parent,2);
        manager.addDynamicObject(first); manager.addDynamicObject(second); fixture.stepIdleFrames(1);
        first.orCollisionProperty(1);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        var expected=rows(fixture,first,second,40);
        first.setDestroyed(true); second.setDestroyed(true); parent.setDestroyed(true); fixture.stepIdleFrames(1);
        registry.restore(saved);
        var restored=manager.getActiveObjects().stream().filter(DezEndBossBumper.class::isInstance)
                .map(DezEndBossBumper.class::cast).toList();
        assertEquals(2,restored.size());
        var a=restored.stream().filter(o->o.getSpawn().subtype()==0).findFirst().orElseThrow();
        var b=restored.stream().filter(o->o.getSpawn().subtype()==2).findFirst().orElseThrow();
        assertNotSame(first,a); assertNotSame(parent,a.parentForTest()); assertSame(a.parentForTest(),b.parentForTest());
        assertEquals(expected,rows(fixture,a,b,40));
    }
    private List<String> rows(HeadlessTestFixture f,DezEndBossBumper a,DezEndBossBumper b,int count) {
        List<String> result=new ArrayList<>();
        for(int i=0;i<count;i++) {
            f.stepIdleFrames(1); result.add(a.getX()+":"+a.getY()+":"+a.frame+":"+b.getX()+":"+b.getY()
                    +":"+b.frame+":"+f.sprite().getCentreX()+":"+f.sprite().getCentreY());
        }
        return result;
    }
}
