package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed storage ingress only: no media, live Frame or route evidence. */
class TestCdPinballImage {
    @Test void wordWriteKeepsFractionAndAllOtherBytes() {
        var p = new CdObjectPool();
        var original = p.state(32).moveLong(CdObjectState.LongField.X_POSITION, 0x1234abcd);
        var image = new CdPinballImage(original);
        image.w(8, 0xffff);
        assertEquals(0xffffabcd, image.state().longBits(CdObjectState.LongField.X_POSITION));
        byte[] expected = original.copyBytes(); expected[8]=(byte)255; expected[9]=(byte)255;
        assertArrayEquals(expected, image.state().copyBytes());
    }
    @Test void lowByteWriteDoesNotClearHighTimer() {
        var image = new CdPinballImage(new CdObjectPool().state(32));
        image.w(0x2e,0x1234); image.b(0x2f,0xff);
        assertEquals(0x12ff,image.w(0x2e));
    }
    @Test void longOverflowWraps() {
        var image = new CdPinballImage(new CdObjectPool().state(32));
        image.l(8,0x7fffffff); image.l(8,image.l(8)+1);
        assertEquals(0x80000000,image.l(8));
    }
    @Test void everyFamilyScratchByteRetainsNeighbours() {
        for (int at=0x2a;at<64;at++) {
            var image = new CdPinballImage(new CdObjectPool().state(127));
            image.b(at,0xa5); var expected=new byte[64];expected[at]=(byte)0xa5;
            assertArrayEquals(expected,image.state().copyBytes(),"byte "+at);
        }
    }
    @Test void all96KeepNativePhysicalIdentity() {
        var pool=new CdObjectPool();
        for(int i=32;i<128;i++) {
            var image=new CdPinballImage(pool.state(i));image.w(0x2c,0xeeff);
            assertEquals(i,image.state().identity().wholeIndex());
            pool.write(image.state());
        }
        assertEquals(96,pool.capture().slots().size());
    }
    @Test void unsignedIntervalUsesWordWrap() {
        assertTrue(CdPinballContext.within(-24,24,48));
        assertFalse(CdPinballContext.within(24,24,48));
        assertTrue(CdPinballContext.within(0xffff,24,48));
    }
}
