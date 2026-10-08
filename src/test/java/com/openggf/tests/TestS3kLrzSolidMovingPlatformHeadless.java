package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzSolidMovingPlatformObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzSolidMovingPlatformHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear(); }
    @ParameterizedTest @ValueSource(ints={320,800})
    void placedHorizontalPlatformCarriesAndRecreatesAcrossOscillatorTurn(int width) {
        var f=boot(width,0xF50,0x4E0);
        f.sprite().setAir(true);f.sprite().setRingCount(7);
        f.stepIdleFrames(35);
        var p=placed();
        assertTrue(f.sprite().isOnObject(),"land on actual LRZ Act 2 record 49");
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
    private LrzSolidMovingPlatformObjectInstance placed() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o instanceof LrzSolidMovingPlatformObjectInstance && o.getSpawn().subtype()==1 && o.getY()==0x520 && (o.getSpawn().renderFlags()&1)==0)
                .map(o->(LrzSolidMovingPlatformObjectInstance)o).findFirst().orElseThrow(()->new AssertionError(GameServices.level().getObjectManager().getActiveObjects().stream().filter(o->o instanceof LrzSolidMovingPlatformObjectInstance).map(o->o.getX()+","+o.getY()+","+o.getSpawn().subtype()).toList()));
    }
    private List<String> frames(HeadlessTestFixture f,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {f.stepIdleFrames(1);var p=f.sprite();var o=placed();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getXSpeed()+","+p.getYSpeed()+","+p.isOnObject()+","+o.getX()+","+o.getY()+","+o.mappingFrame());}
        return rows;
    }
    private HeadlessTestFixture boot(int width,int x,int y) {
        var c=SonicConfigurationService.getInstance();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==320?WidescreenAspect.NATIVE_4_3:WidescreenAspect.SUPER_32_9).name());c.resolveDisplayAspect();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(9,1).startPosition((short)x,(short)y).startPositionIsCentre().build();
        assertEquals(width,GameServices.camera().getWidth());return f;
    }
}
