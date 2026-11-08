package com.openggf.game.sonic2.titlecard;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestObj34CompetitionProjection {
    @Test void bothTablesReceiveTheOwnersPendingRequestsAndBothSpritePagesUseNativeMappings() throws Exception {
        var title=new TitleCardManager(()->null,()->false);
        title.obj34=new Obj34();title.obj34.init(0,0x12,true);
        var planes=new Sonic2CompetitionPlanes();var cameras=new Sonic2CompetitionPlaneScroll();
        var consumer=new Sonic2CompetitionTitleCardPlanes(title,planes,new Sonic2CompetitionPlaneDraw(new byte[65536]),cameras);
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        var sprites=new Sonic2CompetitionTitleCardDisplay(title,rom);
        for(int pass=0;pass<45;pass++){
            consumer.serviceVBlank();title.obj34.consumeFills();title.obj34.tick();
        }
        consumer.serviceVBlank();title.obj34.consumeFills();
        for(var plane:java.util.List.of(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1,Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P2)){
            assertEquals(0x82ed,planes.cell(plane,20,2)); // blue (ArtTile+$5A)/2
            assertEquals(0x82ec,planes.cell(plane,0,2));  // red
            assertEquals(0xa2ee,planes.cell(plane,20,10)); // yellow
            assertEquals(planes.cell(Sonic2CompetitionPlanes.PlaneId.FOREGROUND_P1,0,2),planes.cell(plane,0,2));
        }
        assertEquals(5,sprites.admissions().size());
        assertTrue(sprites.admissions().stream().allMatch(a->a.bucket()==0 && a.occupant().mapping().address()==0x147ba));
        var emptyRings=new Sonic2CompetitionDisplayInputs.RingWindow(java.util.List.of());
        var hud=new Sonic2CompetitionDisplayInputs.PlayerHud(0,0,0,0);
        var input=new Sonic2CompetitionDisplayInputs.Build(new Sonic2CompetitionDisplayInputs.Camera(0,0),
                new Sonic2CompetitionDisplayInputs.Camera(0,0),new Sonic2CompetitionDisplayInputs.Hud(false,0,hud,hud,0),
                emptyRings,emptyRings,0,sprites.admissions(),false);
        var builder=new Sonic2CompetitionSatBuilder(rom);
        var first=builder.buildPlayer1(input);var second=builder.buildPlayer2(first,input);
        var firstName=first.entries().get(2);var secondName=second.entries().getFirst();
        assertEquals(firstName.rawY()+224,secondName.rawY());
        assertEquals(firstName.word(),secondName.word());
        assertEquals(rom.readU16BE(0x147ba+rom.readS16BE(0x147ba)+6),firstName.word());
    }

    @Test void rowRevealUsesLiveCamerasWhileP2CopyRemainsStale() {
        var title=new TitleCardManager(()->null,()->false);title.obj34=new Obj34();title.obj34.init(0,0x12,true);
        title.obj34.settle();title.obj34.armLeave();
        var planes=new Sonic2CompetitionPlanes();var cameras=new Sonic2CompetitionPlaneScroll();
        cameras.initialize(0,0,256,256);cameras.copyAtVInt(false);
        cameras.completeCameraPass(0,0,0,32,64,false);
        cameras.completeCameraPass(1,256,256,1024,704,false);
        cameras.copyAtVInt(true);
        var draw=new Sonic2CompetitionPlaneDraw(new byte[65536]);
        var consumer=new Sonic2CompetitionTitleCardPlanes(title,planes,draw,cameras);
        while(title.obj34.parts[Obj34.BACKGROUND].routine!=20){title.obj34.consumeFills();title.obj34.tick();}
        // The lookup write location exposes which camera bank the consumer used,
        // even with transparent block data. Sentinel cells survive everywhere else.
        for(var plane:Sonic2CompetitionPlanes.PlaneId.values())for(int y=0;y<32;y++)for(int x=0;x<64;x++)planes.write(plane,x,y,0xffff);
        var expected=new Sonic2CompetitionPlanes();
        for(var plane:Sonic2CompetitionPlanes.PlaneId.values())for(int y=0;y<32;y++)for(int x=0;x<64;x++)expected.write(plane,x,y,0xffff);
        expected.apply(draw.titleCardForegroundRows(new Sonic2CompetitionPlaneDraw.CameraCopy(32,64),
                new Sonic2CompetitionPlaneDraw.CameraCopy(1024,704),208));
        var before=cameras.capture();consumer.serviceVBlank();assertEquals(before,cameras.capture());
        for(int i=0;i<3;i++)assertArrayEquals(expected.capture().cells()[i],planes.capture().cells()[i]);
        var wrong=new Sonic2CompetitionPlanes();
        for(var plane:Sonic2CompetitionPlanes.PlaneId.values())for(int y=0;y<32;y++)for(int x=0;x<64;x++)wrong.write(plane,x,y,0xffff);
        wrong.apply(draw.titleCardForegroundRows(
                new Sonic2CompetitionPlaneDraw.CameraCopy(32,64),new Sonic2CompetitionPlaneDraw.CameraCopy(256,256),208));
        assertThrows(AssertionError.class,()->assertArrayEquals(wrong.capture().cells()[1],planes.capture().cells()[1]),"stale P2 copy is a failing negative control");
    }
}
