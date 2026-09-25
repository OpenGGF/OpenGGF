package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezMinibossEye {
    private static final class Parent extends DezMinibossSprite implements RewindRecreatable {
        Parent() { this(new ObjectSpawn(0x3740,0x2C0,0xA6,0,0,false,0)); }
        private Parent(ObjectSpawn spawn) { super(spawn,"EyeTestParent"); }
        @Override public Parent recreateForRewind(RewindRecreateContext context) { return new Parent(context.spawn()); }
        @Override public void update(int vIntRunCount,PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    private record Fixture(Parent parent,DezMinibossEye eye,TestPlayableSprite player) { }
    private Fixture fixture() throws Exception {
        var parent=new Parent(); var eye=new DezMinibossEye(parent); var player=new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player,parent.getX());
        var services=mock(ObjectServices.class);
        when(services.rom()).thenReturn(TestEnvironment.objectServices().rom());
        when(services.romReader()).thenReturn(TestEnvironment.objectServices().romReader());
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(()->player,List::of));
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(mock(PaletteOwnershipRegistry.class));
        eye.setServices(services); return new Fixture(parent,eye,player);
    }

    @Test void eyeTracksOnlyPrimaryAndCannotPublishHitsBeforeTheBodyArmsIt() throws Exception {
        var f=fixture(); f.parent.word44=0xBEEF;
        f.eye.update(0,null);
        assertEquals(0,f.parent.word44);
        assertEquals(0x17,f.eye.getCollisionFlags());
        assertFalse(f.eye.publishesTouchResponseListEntryThisFrame());
        f.eye.onPlayerAttack(f.player,null); f.eye.update(1,null);
        assertEquals(0,f.parent.collisionProperty);
        int[] offsets={0,1,2,4,5,6,8,9,10,11,12};
        int[] frames={2,2,3,3,3,4,4,4,5,5,5};
        for(int sign:new int[]{-1,1}) for(int i=0;i<offsets.length;i++) {
            NativePositionOps.writeXPosPreserveSubpixel(f.player,f.parent.getX()+sign*i*16);
            f.eye.update(0,null);
            assertEquals(f.parent.getX()+sign*offsets[i],f.eye.getX());
            assertEquals(frames[i],f.eye.frame); assertEquals(f.parent.getY(),f.eye.getY());
        }
        NativePositionOps.writeXPosPreserveSubpixel(f.player,f.parent.getX()+0x1000);
        f.eye.update(0,null); assertEquals(f.parent.getX()+12,f.eye.getX());
    }

    @Test void hitIsPublishedOnTheEyePassOnceAndCollisionReturnsAfterThirtyTwoPasses() throws Exception {
        var f=fixture(); f.parent.codePointer=0x7DE6E; f.eye.update(0,null);
        assertEquals(0x17,f.eye.getCollisionFlags());
        f.eye.onPlayerAttack(f.player,null);
        assertEquals(0,f.parent.collisionProperty);
        assertEquals(0xFE,f.eye.getCollisionProperty());
        for(int pass=0;pass<32;pass++) {
            f.eye.update(pass,null);
            assertEquals(1,f.parent.collisionProperty);
            assertEquals(pass==31?0x17:0,f.eye.getCollisionFlags());
            if(pass<31) f.eye.onPlayerAttack(f.player,null);
        }
        f.eye.onPlayerAttack(f.player,null); f.eye.update(32,null);
        assertEquals(2,f.parent.collisionProperty);
    }

    @Test void phaseChangeSuppressesTouchUntilTheSecondPhaseOwnEntry() throws Exception {
        var f=fixture(); f.parent.codePointer=0x7DE6E; f.eye.update(0,null);
        f.parent.status=0x40; f.eye.update(1,null);
        assertEquals(0x17,f.eye.getCollisionFlags());
        assertFalse(f.eye.publishesTouchResponseListEntryThisFrame());
        f.eye.onPlayerAttack(f.player,null); f.eye.update(2,null);
        assertEquals(0,f.parent.collisionProperty);
        f.parent.status=0; f.eye.update(3,null);
        assertEquals(0x17,f.eye.getCollisionFlags());
        assertFalse(f.eye.publishesTouchResponseListEntryThisFrame(),"routine 6 changes to 8 without publishing touch");
        assertEquals(0xFF,f.eye.getCollisionProperty());
        f.eye.update(4,null); assertEquals(0x17,f.eye.getCollisionFlags());
        f.eye.onPlayerAttack(f.player,null); f.eye.update(5,null);
        assertEquals(1,f.parent.collisionProperty);
    }

    @Test void finalDeathConvertsTheEyeAfterItsOwnRoutineAndClearsCollision() throws Exception {
        var f=fixture(); f.parent.codePointer=0x7DE6E; f.eye.update(0,null);
        NativePositionOps.writeXPosPreserveSubpixel(f.player,f.parent.getX()-0x100);
        f.parent.status=0x80; f.eye.update(1,null);
        assertEquals(0,f.eye.getCollisionFlags()); assertTrue(f.eye.visible);
        assertEquals(-0x300,f.eye.xVelocity); assertEquals(-0x200,f.eye.yVelocity);
        assertEquals(f.parent.getX()-12,f.eye.getX());
    }

    @Test void realObjectManagerRecreatesParentLinkAndReplaysTheFlash() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(11,0)
                .startPosition((short)0x3740,(short)0x2C0).startPositionIsCentre().build();
        fixture.sprite().setDebugMode(true);
        DezMinibossTestSupport.retirePlacedEncounter(fixture);
        var manager=GameServices.level().getObjectManager();
        var parent=new Parent(); parent.codePointer=0x7DE6E;
        manager.addDynamicObject(parent);
        var eye=new DezMinibossEye(parent); manager.addDynamicObject(eye);
        fixture.stepIdleFrames(1);
        assertTrue(eye.publishesTouchResponseListEntryThisFrame());
        eye.onPlayerAttack(fixture.sprite(),null);
        fixture.stepIdleFrames(7);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved=registry.capture();
        var expected=rows(fixture,eye,32);
        eye.setDestroyed(true); parent.setDestroyed(true); fixture.stepIdleFrames(1);
        registry.restore(saved);
        var restored=manager.getActiveObjects().stream().filter(o->o instanceof DezMinibossEye)
                .map(o->(DezMinibossEye)o).findFirst().orElseThrow();
        assertNotSame(eye,restored); assertNotSame(parent,restored.parentForTest());
        assertTrue(manager.getActiveObjects().contains(restored.parentForTest()));
        assertEquals(expected,rows(fixture,restored,32));
    }

    private List<String> rows(HeadlessTestFixture fixture,DezMinibossEye eye,int count) {
        List<String> rows=new ArrayList<>();
        for(int i=0;i<count;i++) {
            fixture.stepIdleFrames(1);
            rows.add(eye.getX()+":"+eye.getY()+":"+eye.frame+":"+eye.getCollisionFlags()
                    +":"+eye.getCollisionProperty()+":"+eye.parentForTest().collisionProperty
                    +":"+eye.publishesTouchResponseListEntryThisFrame());
        }
        return rows;
    }
}
