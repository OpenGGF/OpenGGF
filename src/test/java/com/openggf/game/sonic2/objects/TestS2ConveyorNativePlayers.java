package com.openggf.game.sonic2.objects;

import com.openggf.data.RomByteReader;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Declared geometry checks for Obj72's two native slots; retail operands, no trace state import. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestS2ConveyorNativePlayers {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothNativeSlotsMoveOnceWithoutChangingFractionOrVelocity(boolean cpu) throws Exception {
        var bytes=RomByteReader.fromRom(TestEnvironment.currentRom());
        // Obj72_Main (s2.asm:55421-55426) explicitly loads both actor addresses.
        assertEquals(0x43F8,bytes.readU16BE(0x28980));assertEquals(0xB000,bytes.readU16BE(0x28982));
        assertEquals(0x610A,bytes.readU16BE(0x28984));
        assertEquals(0x43F8,bytes.readU16BE(0x28986));assertEquals(0xB040,bytes.readU16BE(0x28988));
        assertEquals(0x6104,bytes.readU16BE(0x2898A));
        for(int flip=0;flip<2;flip++) {
            var first=actor(new Sonic("sonic",(short)0,(short)0));
            var second=actor(new Tails("tails",(short)0,(short)0));second.setCpuControlled(cpu);
            var extra=actor(new Tails("extra",(short)0,(short)0));
            var belt=belt(flip,first,second,extra);
            belt.update(17,first);
            int expected=1000+(flip==0?2:-2);
            for(var player:List.of(first,second)) {
                assertEquals(expected,player.getCentreX());assertEquals(0x7F00,player.getXSubpixelRaw());
                assertEquals(0x300,player.getXSpeed());assertEquals(0x400,player.getGSpeed());
            }
            assertEquals(1000,extra.getCentreX(),"ROM visits only the two native slots");
        }
    }
    @Test void sidekickAdmissionDoesNotDependOnTheLeaderBeingInside() {
        var first=actor(new Sonic("sonic",(short)0,(short)0));first.setCentreX((short)800);
        var second=actor(new Tails("tails",(short)0,(short)0));
        belt(1,first,second).update(0,first);
        assertEquals(800,first.getCentreX());assertEquals(998,second.getCentreX());
    }
    @Test void eachSlotKeepsItsOwnAirAndHalfOpenBoundsTests() {
        var first=actor(new Sonic("sonic",(short)0,(short)0));
        var second=actor(new Tails("tails",(short)0,(short)0));second.setAir(true);
        belt(0,first,second).update(0,first);assertEquals(1002,first.getCentreX());assertEquals(1000,second.getCentreX());
        second.setAir(false);second.setCentreX((short)1032);
        belt(0,first,second).update(0,first);assertEquals(1032,second.getCentreX(),"right edge excluded");
        second.setCentreX((short)1000);second.setCentreY((short)112);
        belt(0,first,second).update(0,first);assertEquals(1000,second.getCentreX(),"bottom edge excluded");
    }
    private static <T extends AbstractPlayableSprite> T actor(T value) {
        value.setCentreX((short)1000);value.setCentreY((short)100);value.setAir(false);
        value.setSubpixelRaw(0x7F00,0x9800);value.setXSpeed((short)0x300);value.setGSpeed((short)0x400);return value;
    }
    private static CNZConveyorBeltObjectInstance belt(int flip,AbstractPlayableSprite first,AbstractPlayableSprite... followers) {
        var services=mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(()->first,()->List.of(followers)));
        var belt=new CNZConveyorBeltObjectInstance(new ObjectSpawn(1000,112,0x72,2,flip,false,112),"belt");
        belt.setServices(services);return belt;
    }
}
