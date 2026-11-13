package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.state.Sonic2LevelOnlyObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionFixedShieldDisplay {
    @Test void actualReservedOwnersKeepTheirRomSlotsAndStatusFlipsAndEachViewsAdmission() throws Exception {
        var h=harness();
        try {
            var first=ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(h.objects,0x86,
                    ()->new ShieldObjectInstance(h.session.player(0)));
            var second=ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(h.objects,0x87,
                    ()->new ShieldObjectInstance(h.session.player(1)));
            first.update(0,null);second.update(0,null);
            var admissions=Sonic2CompetitionObjectDisplay.admissions(h.objects,h.art);
            assertEquals(List.of(0x86,0x87),admissions.stream().map(Admission::slot).toList());
            assertEquals(List.of(1,1),admissions.stream().map(Admission::bucket).toList());
            // Obj38_Main (s2.asm:42393-42401), Ani_obj38 (s2.asm:42682-42684).
            var rom=RomByteReader.fromRom(TestEnvironment.currentRom());int init=0x1D904;
            int rawArt=rom.readU16BE(init+32),frame=rom.readU8(0x1DBD6+rom.readU16BE(0x1DBD6)+1);
            int adjusted=(rawArt&0xF800)|((rawArt&0x7FF)>>>1);
            for(int human=0;human<2;human++) {
                var slot=admissions.get(human).occupant();var player=h.session.player(human);
                assertEquals(new ObjectSlot(0x38,rom.readU16BE(init+14)|(human==1?3:0),
                        player.getCentreX(),player.getCentreY(),rom.readU16BE(init+26),0,
                        new Mapping(rom.readU32BE(init+6),frame),adjusted|(human==1?0x8000:0),null),slot);
            }
            var builder=new Sonic2CompetitionSatBuilder(rom);
            var build=new Build(new Sonic2CompetitionDisplayInputs.Camera(0,0),new Sonic2CompetitionDisplayInputs.Camera(300,0),
                    new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0),
                    new RingWindow(List.of()),new RingWindow(List.of()),0,admissions,false);
            var p1=builder.buildPlayer1(build);
            assertEquals(List.of(new Visibility(0x86,true),new Visibility(0x87,false)),p1.visibility());
            assertEquals(List.of(new Visibility(0x86,true),new Visibility(0x87,true)),builder.buildPlayer2(p1,build).visibility());
            assertSame(first,NativeSstSlotAccess.currentOwner(h.objects,0x86));
            assertSame(second,NativeSstSlotAccess.currentOwner(h.objects,0x87));
            assertNull(first.getSpawn(),"native identity is a verified fixed role, never a fabricated shared spawn");
            second.setVisible(false);
            assertEquals(List.of(0x86),Sonic2CompetitionObjectDisplay.admissions(h.objects,h.art).stream().map(Admission::slot).toList());
        } finally {h.graphics.resetState();}
    }
    @Test void anOrdinaryNullSpawnShieldCannotAcquireAnInventedFixedSlot() throws Exception {
        var h=harness();
        try {
            var owner=ObjectConstructionContext.construct(h.objects.getObjectServices(),
                    ()->new ShieldObjectInstance(h.session.player(0)));
            h.objects.addDynamicObjectAtSlot(owner,40);
            assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.admissions(h.objects,h.art));
            assertEquals(40,owner.getSlotIndex());
        } finally {h.graphics.resetState();}
    }
    private record Harness(ObjectManager objects,ObjectArtProvider art,Sonic2CompetitionSession session,GraphicsManager graphics) { }
    private static Harness harness() throws Exception {
        var session=new Sonic2CompetitionSession();
        var sonic=new Sonic("sonic",(short)0,(short)0);var tails=new Tails("tails",(short)0,(short)0);tails.setCpuControlled(false);
        session.bindPlayers(sonic,tails);
        NativePositionOps.writeXPosResetSubpixel(sonic,100);NativePositionOps.writeYPosResetSubpixel(sonic,80);
        NativePositionOps.writeXPosResetSubpixel(tails,500);NativePositionOps.writeYPosResetSubpixel(tails,80);
        sonic.setDirection(Direction.RIGHT);sonic.setAir(false);sonic.setHighPriority(false);
        tails.setDirection(Direction.LEFT);tails.setAir(true);tails.setHighPriority(true);
        var policy=mock(WorldSessionPolicyState.class);when(policy.getService(com.openggf.game.competition.NativeCompetitionRoster.class)).thenReturn(session);
        var module=mock(GameModule.class);when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world->policy);
        var world=new WorldSession(module);var graphics=new GraphicsManager();graphics.initHeadless();
        var sheet=new Sonic2ObjectArt(TestEnvironment.currentRom(),RomByteReader.fromRom(TestEnvironment.currentRom())).loadShieldSheet();
        var renderer=new PatternSpriteRenderer(sheet,graphics);var render=mock(ObjectRenderManager.class);
        when(render.getShieldRenderer()).thenReturn(renderer);when(render.getSheet(ObjectArtKeys.SHIELD)).thenReturn(sheet);
        var art=mock(ObjectArtProvider.class);when(art.getRenderer(ObjectArtKeys.SHIELD)).thenReturn(renderer);
        var services=new StubObjectServices(){
            @Override public WorldSession worldSession(){return world;}
            @Override public ObjectRenderManager renderManager(){return render;}
        };
        var registry=mock(ObjectRegistry.class);when(registry.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
        var objects=new ObjectManager(List.of(),new Sonic2LevelOnlyObjectRegistry(registry),0,null,null,graphics,mock(Camera.class),services);
        return new Harness(objects,art,session,graphics);
    }
}
