package com.openggf.game.soniccd.objects;

/** Package-private semantic-family storage adapter. No public raw-offset write port.
 * All values remain same-owner full64 CdObjectState images; Root adopts no inferred bytes.
 * Native layout: N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff src/Level/_Definitions.inc:25-66.
 * Existing enum aliases cover scratch storage; lookup deliberately avoids adding aliases to the central file.
 */
final class CdPinballImage {
    private CdObjectState state;
    CdPinballImage(CdObjectState state) { this.state=java.util.Objects.requireNonNull(state); }
    CdObjectState state() { return state; }
    int b(int at) { return Byte.toUnsignedInt(state.copyBytes()[at]); }
    int w(int at) { return b(at)*256+b(at+1); }
    int l(int at) { return w(at)<<16|w(at+2); }
    void b(int at,int value) {
        for(var f:CdObjectState.ByteField.values()) if(f.byteOffset()==at) {state=state.moveByte(f,value);return;}
        for(var f:CdObjectState.WordField.values()) if(at>=f.byteOffset()&&at<f.byteOffset()+2) {
            int old=state.word(f);int v=at==f.byteOffset()?((value&255)<<8)|(old&255):(old&0xff00)|(value&255);
            state=state.moveWord(f,v);return;
        }
        for(var f:CdObjectState.LongField.values()) if(at>=f.byteOffset()&&at<f.byteOffset()+4) {
            int shift=(3-at+f.byteOffset())*8;
            state=state.moveLong(f,(state.longBits(f)&~(255<<shift))|((value&255)<<shift));return;
        }
        throw new IllegalArgumentException("No declared object storage at "+at);
    }
    void w(int at,int value) { b(at,value>>>8);b(at+1,value); }
    void l(int at,int value) { w(at,value>>>16);w(at+2,value); }
}
