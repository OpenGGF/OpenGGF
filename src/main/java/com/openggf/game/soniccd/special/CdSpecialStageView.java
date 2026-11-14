package com.openggf.game.soniccd.special;

/**
 * The graphics-operation view ({@code gfxVars}, Sub.asm:50-81): the camera Sonic hands over
 * each update ({@code ObjSonic .SetPosition}, Sonic/Main.asm:110-116) and the sines
 * {@code GetGfxSines} (scddisasm/src/Special Stage/Sub.asm:2580-2609) takes of its pitch and yaw at the start of the next
 * update. FOV $80 and centre -$40 are set once by {@code InitGfxOperation} (scddisasm/src/Special Stage/Sub.asm:2572-2573).
 */
public final class CdSpecialStageView {
    static final int FOV = 0x80;
    static final int CENTER = -0x40;

    private int cameraX;
    private int cameraY;
    private int cameraZ;
    private int pitch;
    private int yaw;
    private int pitchSin;
    private int pitchCos;
    private int yawSin;
    private int yawCos;

    /** {@code .SetPosition}: Sonic's X, Y, Z, pitch and yaw become the camera (words). */
    public void setCamera(int x, int y, int z, int pitch, int yaw) {
        this.cameraX = (short) x;
        this.cameraY = (short) y;
        this.cameraZ = (short) z;
        this.pitch = (short) pitch;
        this.yaw = (short) yaw;
    }

    /** {@code GetGfxSines}. */
    public void takeSines(short[] sineTable) {
        pitchSin = CdSpecialMath.sine(sineTable, pitch);
        pitchCos = CdSpecialMath.cosine(sineTable, pitch);
        yawSin = CdSpecialMath.sine(sineTable, yaw);
        yawCos = CdSpecialMath.cosine(sineTable, yaw);
    }

    public int cameraX() { return cameraX; }
    public int cameraY() { return cameraY; }
    public int cameraZ() { return cameraZ; }
    public int pitch() { return pitch; }
    public int yaw() { return yaw; }
    public int fov() { return FOV; }
    public int center() { return CENTER; }
    public int pitchSin() { return pitchSin; }
    public int pitchCos() { return pitchCos; }
    public int yawSin() { return yawSin; }
    public int yawCos() { return yawCos; }

    /** Sets the sines directly (a native view read back for comparison). */
    void setSines(int pitchSin, int pitchCos, int yawSin, int yawCos) {
        this.pitchSin = (short) pitchSin;
        this.pitchCos = (short) pitchCos;
        this.yawSin = (short) yawSin;
        this.yawCos = (short) yawCos;
    }
}
