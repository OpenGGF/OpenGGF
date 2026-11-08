package com.openggf.level.rings;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.NativeCompetitionRuntimeFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real REV01 mappings, completed two-view visibility, actual Obj37 floor/delete branch. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestS2LostRingNativeVisibility {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void secondViewPublicationOwnsTheFollowingFloorProbeAndRetainsItsMapping() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        // Obj37_Main's render-byte test and loc_121D0's actual 2P delete gate.
        assertEquals(0x4A28,rom.readU16BE(0x12196));
        assertEquals(1,rom.readU16BE(0x12198));
        assertEquals(0x4A78,rom.readU16BE(0x121D0));
        assertEquals(0xFFD8,rom.readU16BE(0x121D2));
        var roster=new NativeCompetitionRuntimeFixture();
        var module=new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                if(type==ObjectDrawInternalAccess.NativeDisplayOwner.class)
                    return type.cast((ObjectDrawInternalAccess.NativeDisplayOwner)Sonic2CompetitionObjectDisplay::adopted);
                return super.getGameService(type);
            }
        };
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        var rings=new RingManager(List.of(),TestRingNativeSlotDispatch.sheet(),null,null,null);
        var animation=rings.getSpillAnimationState();animation.restore(new int[]{255,0x501,2});
        var services=new StubObjectServices() {
            @Override public WorldSession worldSession() { return roster.world; }
            @Override public GameModule gameModule() { return module; }
            @Override public RingManager ringManager() { return rings; }
        };
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        var ring=new LostRingObjectInstance(new ObjectSpawn(1040,64,0x37,0,0,false,0)) {
            { initFixedPointForTest(1040,64,0,0,0,255); }
            @Override protected int resolveVblaCounter() { return 0; }
            @Override protected int ringCheckFloorDist(int x,int y) { return 0; }
        };
        assertEquals(1040,ring.getX());assertEquals(64,ring.getY());
        ring.setServices(services);ring.setSpillAnimation(animation);ring.initializeNativeDisplayState();
        assertTrue(ring.hasRomRenderFlagForFloorProbe(),"Init seeds bit7 before the first BuildSprites");
        ring.refreshPostCameraRenderState();
        assertTrue(ring.hasRomRenderFlagForFloorProbe(),"primary-only approximation cannot replace native Init");
        ring.updateMovement(); // actual routine2 copies global mapping2 before late animation
        animation.tick();
        var slot=Sonic2CompetitionObjectDisplay.project(ring,null,()->fail("closed actual-ring source"));
        assertNotNull(slot,"P2-only ring survives the ordinary renderer's P1 culling");
        assertEquals(0x12382,slot.mapping().address());assertEquals(2,slot.mapping().frame());
        assertEquals(0x235E,slot.artTile());assertEquals(8,slot.width());assertEquals(0x84,slot.renderFlags());
        assertThrows(AssertionError.class,()->assertEquals(3,slot.mapping().frame()),"negative mapping comparison control");
        var builder=new Sonic2CompetitionSatBuilder(rom);
        var input=new Build(new Camera(0,0),new Camera(1000,0),
                new Hud(false,0,new PlayerHud(0,0,0,0),new PlayerHud(0,0,0,0),0),
                new RingWindow(List.of()),new RingWindow(List.of()),0,
                List.of(new Admission(18,3,0,slot)),false);
        var first=builder.buildPlayer1(input);
        assertFalse(first.visibility().getFirst().onScreen());
        var second=builder.buildPlayer2(first,input);
        assertTrue(second.visibility().getFirst().onScreen());
        ObjectDrawInternalAccess.commitNativeDisplayVisibility(ring,second.visibility().getFirst().onScreen());
        ring.refreshPostCameraRenderState();ring.updateMovement();
        assertFalse(ring.isDestroyed(),"P2-visible ring must probe rather than take loc_121D0");
        ObjectDrawInternalAccess.commitNativeDisplayVisibility(ring,false);
        ring.updateMovement();assertTrue(ring.isDestroyed(),"negative visibility control executes shipped 2P deletion");
    }
}
