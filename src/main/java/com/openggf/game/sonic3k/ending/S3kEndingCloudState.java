package com.openggf.game.sonic3k.ending;

import java.nio.ByteBuffer;

/** _unkFAB9 / _unkFAC2 / _unkFAC4, shared by the two native ending scenes. */
public final class S3kEndingCloudState {
    static final int BYTES=3*Integer.BYTES;
    private int mask,xVelocity,yVelocity;
    public int mask(){return mask;}
    public int xVelocity(){return xVelocity;}
    public int yVelocity(){return yVelocity;}
    public void configure(int mask,int xVelocity,int yVelocity){
        this.mask=mask&0xFF;this.xVelocity=(short)xVelocity;this.yVelocity=(short)yVelocity;
    }
    void captureTo(ByteBuffer b){b.putInt(mask).putInt(xVelocity).putInt(yVelocity);}
    void restoreFrom(ByteBuffer b){mask=b.getInt();xVelocity=b.getInt();yVelocity=b.getInt();}
}
