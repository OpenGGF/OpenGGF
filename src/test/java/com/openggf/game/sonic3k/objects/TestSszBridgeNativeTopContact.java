package com.openggf.game.sonic3k.objects;

import com.openggf.game.rules.GameRules;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal sub_45026 -> SolidObjectTop contact; no route/frame-specific runtime rule. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestSszBridgeNativeTopContact {
    @BeforeEach void nativeModule() {TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);}

    @Test void bridgeUsesTheD3SurfaceForNewTopContact() {
        var bridge=new SszCutsceneBridgeObjectInstance(new ObjectSpawn(0x2C0,0xC88,0x77,0,0,false,0));
        assertEquals(16,bridge.getSolidParams().airHalfHeight(),"native d2 remains intact");
        assertEquals(9,bridge.getSolidParams().groundHalfHeight(),"native d3");
        assertTrue(bridge.getSolidRoutineProfile().usesGroundHalfHeightForTopSolidContact(),
                "loc_1E44C subtracts d3, not d2, before the new-contact window");
    }

    // Native surface3208-9=3199; feet+4 yields accepted penetration1..16.
    // Exact0 and17 reject. Horizontal range is [x-96,x+96).
    @ParameterizedTest
    @CsvSource({"808,3176,false", "808,3177,true", "808,3191,true", "808,3192,true",
            "808,3193,false", "800,3191,true", "799,3191,false", "991,3191,true", "992,3191,false"})
    void actualSolidOwnerUsesNativeTopContactWindow(int x,int y,boolean expectedLanding) {
        var bridge=new SszCutsceneBridgeObjectInstance(new ObjectSpawn(0x2C0,0xC88,0x77,0,0,false,0));
        var registry=new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {return null;}
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int id) {return "declared local SSZ bridge";}
        };
        var manager=new ObjectManager(List.of(),registry,0,null,null);
        manager.reset(512);manager.addDynamicObject(bridge);
        var player=new NativeSonic(x,y);player.setAir(true);player.setYSpeed((short)0);
        AbstractObjectInstance.updateCameraBounds(512,3008,832,3232,0);
        manager.update(512,player,List.of(),0,false,true,false);
        assertEquals(896,bridge.getX(),"unreleased bridge has its native +192 offset");
        assertEquals(expectedLanding,manager.isRidingObject(player),"actual object solid contact");
        assertEquals(expectedLanding,manager.hasObjectStandingBit(player,bridge));
        assertEquals(!expectedLanding,player.getAir());
        assertEquals(expectedLanding ? 3179 : y,player.getCentreY(),"native relative Y snap");
    }
    private static final class NativeSonic extends Sonic {
        NativeSonic(int x,int y) {
            super("sonic",(short)x,(short)y);setGameRulesForTest(GameRules.SONIC_3K);
            setCentreXPreserveSubpixel((short)x);setCentreYPreserveSubpixel((short)y);
        }
    }
}
