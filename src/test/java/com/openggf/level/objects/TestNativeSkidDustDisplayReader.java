package com.openggf.level.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionArtResidency;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestNativeSkidDustDisplayReader {
    @Test void readingTheActualMappingFrameAndParentDoesNotPreloadDrawOrChangeAnyCapturedState() throws Exception {
        var rom=TestEnvironment.currentRom();var reader=RomByteReader.fromRom(rom);
        var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var renderer=mock(PlayerSpriteRenderer.class);
        var player=new Sonic("sonic",(short)0,(short)0);
        player.setSpindashDustController(new SpindashDustController(player,renderer,132));
        var owner=ObjectConstructionContext.construct(new StubObjectServices(),()->SkidDustObjectInstance.create(player));
        assertNotNull(owner);var saved=owner.captureRewindState();var resident=art.capture();
        for(int read=0;read<100;read++) {
            var pose=ObjectDrawInternalAccess.skidDustDisplayPose(owner);
            assertEquals(0x11,pose.frame());assertEquals("sonic",pose.parentCode());
            assertEquals(saved,owner.captureRewindState(),"reader must not set the draw-time preload flag");
            assertEquals(resident,art.capture(),"reader cannot submit or publish art");
        }
        verifyNoInteractions(renderer);
        int mapping=Sonic2Constants.MAP_UNC_OBJ08_ADDR;
        assertTrue(reader.readU16BE(mapping+reader.readU16BE(mapping+0x11*2))>0,"native frame11 has pieces");
        int dplc=Sonic2Constants.MAP_R_UNC_OBJ08_ADDR;
        assertEquals(0,reader.readU16BE(dplc+reader.readU16BE(dplc+0x11*2)),"visible skid frames reuse the preload");
        assertTrue(reader.readU16BE(dplc+reader.readU16BE(dplc+0x15*2))>0,"frame15 is the separate preload");
        // 1P control: the existing ordinary draw still performs its one-time preload then visible frame.
        owner.appendRenderCommands(new ArrayList<>());
        var order=inOrder(renderer);
        order.verify(renderer).drawFrame(eq(0x15),anyInt(),anyInt(),anyBoolean(),eq(false));
        order.verify(renderer).drawFrame(eq(0x11),anyInt(),anyInt(),anyBoolean(),eq(false));
        assertNotEquals(saved,owner.captureRewindState());
        var afterDraw=owner.captureRewindState();ObjectDrawInternalAccess.skidDustDisplayPose(owner);
        assertEquals(afterDraw,owner.captureRewindState());assertEquals(resident,art.capture());
        owner.appendRenderCommands(new ArrayList<>());
        verify(renderer,times(1)).drawFrame(eq(0x15),anyInt(),anyInt(),anyBoolean(),eq(false));
        verify(renderer,times(2)).drawFrame(eq(0x11),anyInt(),anyInt(),anyBoolean(),eq(false));
    }
}
