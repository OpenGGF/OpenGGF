package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.objects.S3kDezFloatingPlatformObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezFloatingPlatformHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear(); }
    @ParameterizedTest @ValueSource(ints={320,800})
    void placedHorizontalPlatformCarriesAndRecreatesAcrossOscillatorTurn(int width) {
        var f=boot(width,0x1540,0x790);
        f.sprite().setAir(true);f.sprite().setRingCount(7);
        f.stepIdleFrames(35);
        var p=placed();
        assertTrue(f.sprite().isOnObject(),"land on actual Act 2 record 179");
        int playerX=f.sprite().getCentreX(), platformX=p.getX();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var rows=frames(f,180);
        assertNotEquals(platformX,p.getX(),"oscillator moves the placed platform");
        assertEquals(p.getX()-platformX,f.sprite().getCentreX()-playerX,"standing player is carried without input");
        assertFalse(f.sprite().getDead());
        p.setDestroyed(true);f.stepIdleFrames(1); // retire the placed SST entry before forcing recreation
        registry.restore(before);assertNotSame(p,placed());
        assertEquals(rows,frames(f,180));
    }
    @Test void artUsesActTwoExtraBankAndAlternatingRomMappings() throws Exception {
        boot(320,0x1570,0x790);
        var maps=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x25ACA,2);
        assertEquals(List.of(2,3),maps.stream().map(f->f.pieces().size()).toList());
        var first=maps.getFirst().pieces().getFirst();
        assertEquals(-32,first.xOffset());assertEquals(-16,first.yOffset());
        assertEquals(4,first.widthTiles());assertEquals(4,first.heightTiles());assertEquals(0,first.tileIndex());
        var glow=maps.get(1).pieces().getFirst();
        assertEquals(-12,glow.xOffset());assertEquals(-4,glow.yOffset());assertEquals(0x10,glow.tileIndex());
        assertEquals(3,glow.widthTiles());assertEquals(1,glow.heightTiles());
        var entry=Sonic3kPlcArtRegistry.getPlan(11,1).levelArt().stream()
                .filter(e->e.key().equals(Sonic3kObjectArtKeys.DEZ_FLOATING_PLATFORM)).findFirst().orElseThrow();
        assertEquals(0x33A,entry.artTileBase());assertEquals(1,entry.palette());assertEquals(2,entry.mappingFrameCount());
        assertTrue(Sonic3kPlcArtRegistry.getPlan(11,0).levelArt().stream().noneMatch(e->e.key().equals(entry.key())));
    }
    private S3kDezFloatingPlatformObjectInstance placed() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o instanceof S3kDezFloatingPlatformObjectInstance && o.getSpawn().subtype()==1 && o.getY()==0x7D0)
                .map(o->(S3kDezFloatingPlatformObjectInstance)o).findFirst().orElseThrow(()->new AssertionError(GameServices.level().getObjectManager().getActiveObjects().stream().filter(o->o instanceof S3kDezFloatingPlatformObjectInstance).map(o->o.getX()+","+o.getY()+","+o.getSpawn().subtype()).toList()));
    }
    private List<String> frames(HeadlessTestFixture f,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {f.stepIdleFrames(1);var p=f.sprite();var o=placed();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getXSpeed()+","+p.getYSpeed()+","+p.isOnObject()+","+o.getX()+","+o.getY()+","+o.mappingFrameForTest());}
        return rows;
    }
    private HeadlessTestFixture boot(int width,int x,int y) {
        var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==320?WidescreenAspect.NATIVE_4_3:WidescreenAspect.SUPER_32_9).name());c.resolveDisplayAspect();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(11,1).startPosition((short)x,(short)y).startPositionIsCentre().build();
        assertEquals(width,GameServices.camera().getWidth());return f;
    }
}
