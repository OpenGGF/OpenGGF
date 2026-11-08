package com.openggf.game.sonic2.competition;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.physics.Direction;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionDustDisplay {
    @Test void theActualSstWalkRunsTailDustShieldsAndStarsInRomOrderExactlyOnce() throws Exception {
        NativeTeleportTestWorld.run(w->{
            var objects=w.host.context().getObjectManager();
            var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
            assertEquals(132,(rom.readU16BE(0x447E)-0xB000)/0x40,"InitPlayers Sonic_Dust address operand");
            assertEquals(133,(rom.readU16BE(0x44BA)-0xB000)/0x40,"InitPlayers Tails_Dust address operand");
            for(int human=0;human<2;human++) {w.session.player(human).giveShield();w.session.player(human).giveInvincibility();}
            var order=new ArrayList<Integer>();
            for(int slot:new int[]{128,132,133,134,135,136,140}) {
                var original=NativeSstSlotAccess.currentOwner(objects,slot);assertNotNull(original);
                var observed=spy(original);
                doAnswer(inv->{order.add(slot);inv.callRealMethod();return null;}).when(observed).update(anyInt(),any());
                ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(objects,slot,()->observed);
            }
            var dust1=spy(w.session.player(0).getSpindashDustController());
            var dust2=spy(w.session.player(1).getSpindashDustController());
            w.session.player(0).setSpindashDustController(dust1);w.session.player(1).setSpindashDustController(dust2);
            w.tick();assertEquals(List.of(128,132,133,134,135,136,140),order);
            verify(dust1,times(1)).update();verify(dust2,times(1)).update();
        });
    }
    @Test void completedDustFramesStatusPriorityAndDplcCommandsComeFromRomAcrossLoopsAndRestore() throws Exception {
        NativeTeleportTestWorld.run(w->{
            var context=w.host.context();var module=context.getWorldSession().getGameModule();
            var source=module.getGameService(Sonic2CompetitionDustDisplay.class);
            var art=module.getGameService(Sonic2CompetitionArtResidency.class);
            var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
            int script=0x1DF38+rom.readU16BE(0x1DF38+4);int[] timer={0,0},cursor={0,0},selected={0,0},last={0,0};
            for(int human=0;human<2;human++){var p=w.session.player(human);p.setSpindash(true);p.setAir(false);p.setHighPriority(human==1);source.chargeStarted(p);}
            for(int pass=0;pass<40;pass++) {
                art.serviceDmaQueueVBlank();var expected=new ArrayList<Sonic2CompetitionArtResidency.Upload>();
                for(int human=0;human<2;human++) {
                    var player=w.session.player(human);
                    NativePositionOps.writeXPosResetSubpixel(player,100+pass*3+human*400);
                    NativePositionOps.writeYPosResetSubpixel(player,200+human*40);
                    player.setDirection(pass%2==0?Direction.RIGHT:Direction.LEFT);
                    if(--timer[human]<0) {
                        timer[human]=rom.readU8(script);
                        if(rom.readU8(script+1+cursor[human])==255)cursor[human]=0;
                        selected[human]=rom.readU8(script+1+cursor[human]++);
                    }
                    NativeSstSlotAccess.currentOwner(context.getObjectManager(),132+human).update(pass,player);
                    var pose=source.fixed(human);int tile=human==0?0x49C:0x48C;
                    assertEquals(selected[human],pose.mapping().frame());
                    assertEquals(player.getCentreX()&65535,pose.x());
                    assertEquals((player.getCentreY()-(human==1?4:0))&65535,pose.y());
                    assertEquals(4|(pass%2),pose.renderFlags());assertEquals(16,pose.width());
                    assertEquals(Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(tile)|(human==1?0x8000:0),pose.artTile());
                    assertEquals(Sonic2Constants.MAP_UNC_OBJ08_ADDR,pose.mapping().address());
                    if(last[human]!=selected[human]) {
                        int base=Sonic2Constants.MAP_R_UNC_OBJ08_ADDR;
                        int p=base+rom.readU16BE(base+selected[human]*2),count=rom.readU16BE(p);p+=2;
                        for(int i=0;i<count;i++,p+=2) {
                            int word=rom.readU16BE(p),tiles=(word>>>12)+1;
                            expected.add(new Sonic2CompetitionArtResidency.Upload(tile,
                                    rom.slice(Sonic2Constants.ART_UNC_SPLASH_DUST_ADDR+(word&4095)*32,tiles*32)));
                            tile+=tiles;
                        }
                        last[human]=selected[human];
                    }
                }
                assertEquals(expected,art.capture().pending(),"native P1 then P2 DPLC order at pass "+pass);
            }
            var registry=context.getRewindRegistry();var saved=registry.capture();var expected=source.capture();var resident=art.capture();
            for(int human=0;human<2;human++) {w.session.player(human).setSpindash(false);source.chargeReleased(w.session.player(human));
                NativeSstSlotAccess.currentOwner(context.getObjectManager(),132+human).update(100,w.session.player(human));}
            assertEquals(0,source.fixed(0).mapping().frame(),"explicit release writes anim0 before Obj08 runs");
            var oldFixed=NativeSstSlotAccess.currentOwner(context.getObjectManager(),132);
            assertNotEquals(expected,source.capture());registry.restore(saved);
            assertInstanceOf(Sonic2CompetitionFixedAccessory.class,NativeSstSlotAccess.currentOwner(context.getObjectManager(),132));
            assertNotSame(oldFixed,NativeSstSlotAccess.currentOwner(context.getObjectManager(),132),"destructive fixed-owner recreation");
            assertEquals(expected,source.capture());assertEquals(resident,art.capture());
            var p=w.session.player(0);var held=p.getSpindashDustController().captureRewindState();p.setSpindash(false);
            NativeSstSlotAccess.currentOwner(context.getObjectManager(),132).update(101,p);
            assertNull(source.fixed(0),"Obj08_ResetDisplayMode has no display call this pass");
            assertEquals(held,p.getSpindashDustController().captureRewindState(),"a no-AnimateSprite reset holds the old cursor");
            NativeSstSlotAccess.currentOwner(context.getObjectManager(),132).update(102,p);
            assertEquals(0,source.fixed(0).mapping().frame());
        });
    }
    @Test void actualAllocatedSkidChildKeepsItsBirthArtAndRomDisplayUntilTheFollowingDeletePass() throws Exception {
        NativeTeleportTestWorld.run(w->{
            var context=w.host.context();var objects=context.getObjectManager();var module=context.getWorldSession().getGameModule();
            var source=module.getGameService(Sonic2CompetitionDustDisplay.class);
            var art=module.getGameService(Sonic2CompetitionArtResidency.class);art.serviceDmaQueueVBlank();
            var player=w.session.player(0);player.setHighPriority(true);
            var child=ObjectConstructionContext.construct(objects.getObjectServices(),()->SkidDustObjectInstance.create(player));
            objects.addDynamicObject(child);int slot=child.getSlotIndex();assertTrue(slot>=16 && slot<128);
            source.captureChildren(0);assertNull(source.child(child),"new lower SST has not executed yet");
            player.setHighPriority(false);
            var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
            int script=0x1DF38+rom.readU16BE(0x1DF38+6),timer=0,cursor=0,frame=0;
            for(int pass=1;pass<=18;pass++) {
                child.update(pass,player);
                if(pass==18){assertTrue(child.isDestroyed());assertNull(source.child(child));break;}
                if(--timer<0) {timer=rom.readU8(script);int next=rom.readU8(script+1+cursor++);if(next<128)frame=next;}
                var state=child.captureRewindState();var resident=art.capture();var sourceState=source.capture();
                var display=source.child(child);
                assertEquals(frame,display.mapping().frame(),"native selected frame at child pass "+pass);
                assertEquals(0x8000|Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(0x49C),display.artTile());
                assertEquals(4,display.renderFlags(),"Obj08 children clear status instead of inheriting parent flip");
                assertEquals(4,display.width());assertEquals(child.getSpawn().x(),display.x());assertEquals(child.getSpawn().y(),display.y());
                assertEquals(state,child.captureRewindState());assertEquals(resident,art.capture());assertEquals(sourceState,source.capture());
            }
        });
    }
    @Test void teleportFreezesFixedDustAndTheirArtAndRestoresTheCompletedPoseMidFreeze() throws Exception {
        NativeTeleportTestWorld.run(w->{
            var context=w.host.context();var source=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionDustDisplay.class);
            var player=w.session.player(0);player.setSpindash(true);player.setAir(false);source.chargeStarted(player);
            NativeSstSlotAccess.currentOwner(context.getObjectManager(),132).update(1,player);
            var pose=source.capture();var controller=player.getSpindashDustController().captureRewindState();
            w.qualifyTeleport();assertEquals(2,source.animation(0));assertEquals(0,source.animation(1));
            for(int i=0;i<20;i++)w.tick();
            assertEquals(pose,source.capture());assertEquals(controller,player.getSpindashDustController().captureRewindState());
            var registry=context.getRewindRegistry();var saved=registry.capture();int remaining=w.session.teleportTimer();
            for(int i=0;i<70;i++)w.tick();assertNotEquals(pose,source.capture());
            assertEquals(0,source.animation(1),"receiving the other actor's flag never writes Dust.anim2");
            registry.restore(saved);assertEquals(remaining,w.session.teleportTimer());assertEquals(pose,source.capture());
            for(int i=0;i<10;i++){w.tick();assertEquals(pose,source.capture());}
            for(int i=0;i<remaining+2;i++)w.tick();
            assertEquals(0,w.session.teleportTimer());
            for(int slot:new int[]{128,132,133})assertInstanceOf(Sonic2CompetitionFixedAccessory.class,
                    NativeSstSlotAccess.currentOwner(context.getObjectManager(),slot),"fixed owner after resumed execution");
            assertEquals(0,source.animation(1));
        });
    }
}
