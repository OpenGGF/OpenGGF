package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerMotionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.openggf.game.soniccd.objects.TestCdIntroFamily.*;
import static com.openggf.game.soniccd.objects.CdIntroSpikes.PlayerStore.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdRollTunnel {
    static CdPlayerMotionState motion(int x,int y,int vx,int vy,int angle,int flags,int routine,int anim) {
        return new CdPlayerMotionState((x<<16)|0x1234,(y<<16)|0x5678,vx,vy,-123,routine,angle,flags,
                0x85,9,19,anim,0x55,0x67,0x89,-1234,0xAB,0xED,0xCD,0xEF);
    }
    static CdRollTunnel tunnel() throws Exception {return new CdRollTunnel(CdRollTunnel.Assets.fromAssets(assets()));}
    static CdObjectState object(int subtype) {var p=pool(32,0x35,0);w(p,32,8,0x100);w(p,32,0xC,0x200);b(p,32,0x28,subtype);return p.state(32);}
    static final CdRollTunnel.Gates NORMAL=new CdRollTunnel.Gates(1,0,0,0);
    @ParameterizedTest @ValueSource(ints={-41,-40,39,40,0x7FFF,0x8000})
    void rectangleXUsesWrappedSignedLowWordThenUnsignedUpperBound(int delta) throws Exception {
        var m=motion((0x100+delta)&65535,0x200,0,0,0,0,2,0);var r=tunnel().step(object(0),m,NORMAL);
        if(delta==-40||delta==39)assertEquals(0xA00,r.motion().xVelocity());else assertSame(m,r.motion());
    }
    @ParameterizedTest @ValueSource(ints={-41,-40,39,40})
    void rectangleYEdgesAreIndependentOfX(int delta) throws Exception {
        var m=motion(0x100,(0x200+delta)&65535,0,0,0,0,2,0);var r=tunnel().step(object(0),m,NORMAL);
        assertEquals(delta==-40||delta==39,!r.executedWrites().isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={0,1,-1,0x9FF,-0x9FF,0xA00,-0xA00,0x1000,-0x1000,-32768})
    void horizontalMinimumPreservesSignAndUnsigned8000Magnitude(int v) throws Exception {
        var r=tunnel().step(object(0),motion(0x100,0x200,v,77,0,0,2,0),NORMAL);
        int expected=v==0?0xA00:Math.abs(v)<0xA00?(v<0?-0xA00:0xA00):v;
        assertEquals(expected,r.motion().xVelocity());assertEquals(expected,r.motion().groundVelocity());assertEquals(77,r.motion().yVelocity());
        assertEquals(0x2055678,r.motion().centreY16_16());assertEquals(0xED,r.motion().playerControl());
        assertEquals(Set.of(X_VELOCITY,GROUND_VELOCITY,FLAGS,Y_RADIUS,X_RADIUS,Y_WORD,ANIMATION),r.executedWrites());
    }
    @ParameterizedTest @ValueSource(ints={0x5F,0x60,0x9F,0xA0,0xFF})
    void horizontalAngleSectorFlipsGroundOnlyAtLiteral60Through9f(int angle) throws Exception {
        var r=tunnel().step(object(0),motion(0x100,0x200,-0xB00,77,angle,4,2,0),NORMAL);
        assertEquals(-0xB00,r.motion().xVelocity());assertEquals(angle>=0x60&&angle<=0x9F?0xB00:-0xB00,r.motion().groundVelocity());
        assertEquals(0x2005678,r.motion().centreY16_16());assertEquals(0,r.motion().animation());
        assertEquals(Set.of(X_VELOCITY,GROUND_VELOCITY,FLAGS),r.executedWrites());
    }
    @ParameterizedTest @ValueSource(ints={0,-1,0xCFF,-0xCFF,0xD00,-0xD00,-32768})
    void verticalMinimumSetsAirAndRollWhileLeavingXUnchanged(int v) throws Exception {
        var r=tunnel().step(object(1),motion(0x100,0x200,123,v,0,0x80,2,0),NORMAL);
        int expected=v==0?0xD00:Math.abs(v)<0xD00?(v<0?-0xD00:0xD00):v;
        assertEquals(expected,r.motion().yVelocity());assertEquals(expected,r.motion().groundVelocity());assertEquals(123,r.motion().xVelocity());assertEquals(0x86,r.motion().flags());
    }
    @ParameterizedTest @ValueSource(ints={2,3,4,5,255})
    void controlledRawSubtypesFollowExactSignAndRightBeforeLeftBranches(int subtype) throws Exception {
        var m=motion(0x100,0x200,0,-1,0,0,2,0);
        var r=tunnel().step(object(subtype),m,new CdRollTunnel.Gates(1,0,0,0xD));
        if(subtype==2) {assertEquals(0xD00,r.motion().xVelocity());assertEquals(-1,r.motion().yVelocity());assertEquals(4,r.motion().flags());}
        else if(subtype==4){assertEquals(-0xA00,r.motion().yVelocity());assertEquals(-0xA00,r.motion().groundVelocity());assertEquals(6,r.motion().flags());}
        else{assertEquals(0,r.motion().xVelocity());assertEquals(4,r.motion().flags());}
        var down=tunnel().step(object(subtype),motion(0x100,0x200,0,1,0,0,2,0),new CdRollTunnel.Gates(1,0,0,4));
        if(subtype!=2&&subtype!=4){assertEquals(-0xD00,down.motion().xVelocity());assertEquals(6,down.motion().flags());}
    }
    @Test void debugHighByteAndPlayerGuardsDoNotNormalizeControlOrBlockInitAndDrawHighByte() throws Exception {
        var o=object(0);var m=motion(0x100,0x200,1,2,0,0,2,0);
        var r=tunnel().step(o,m,new CdRollTunnel.Gates(1,0x100,0x100,0));assertSame(m,r.motion());assertEquals(2,r.object().routine());assertTrue(r.draw());
        assertTrue(tunnel().step(o,m,new CdRollTunnel.Gates(1,1,1,0)).motion().xVelocity()>=0xA00);
        assertFalse(tunnel().step(o,m,new CdRollTunnel.Gates(1,1,1,0)).draw());
        for(var guarded:List.of(motion(0x100,0x200,1,2,0,0,6,0),motion(0x100,0x200,1,2,0,0,2,0x2B)))
            assertSame(guarded,tunnel().step(o,guarded,NORMAL).motion());
    }
    @Test void fullInitImageChangesOnlyDeclaredObjectFieldsAndCallerPoolIsUntouched() throws Exception {
        var o=object(1);var expected=o.copyBytes();bytes(expected,1,4,5,0x44);bytes(expected,4,0,0x23,0xA4,0xC8);expected[0x24]=2;expected[0x1A]=2;
        var r=tunnel().step(o,motion(0,0,0,0,0,0,2,0),NORMAL);assertArrayEquals(expected,r.object().copyBytes());
        assertEquals(0,o.routine());assertTrue(r.executedWrites().isEmpty());assertSame(o,r.expectedObject());
    }
    @Test void oddAndOutOfGraphRoutineFaultsBeforeAnyInitOrPlayerChange() throws Exception {
        for(int raw:List.of(1,3,4,255)){var o=object(0).moveByte(CdObjectState.ByteField.ROUTINE,raw);assertThrows(UnsupportedOperationException.class,()->tunnel().step(o,motion(0x100,0x200,0,0,0,0,2,0),NORMAL));}
    }
}
