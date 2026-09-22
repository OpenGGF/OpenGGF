package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** SSZ1's HScroll_table+$80..$167 collapse channels, owned by ScreenEvents. */
public final class SszLaunchState {
    static final int CAPTURE_BYTES = 10 * (2 + 4 + 4 + 2) + 4 * Long.BYTES + 4 * Integer.BYTES + S3kScreenShake.captureBytes() + 8 + 64 * 32 * 4;
    private final long[] jobs = {-1, -1, -1, -1};
    private byte[] backgroundPlane = new byte[64 * 32 * 4];
    private boolean backgroundPlaneValid;
    private int backgroundRoundedY;
    public boolean hasBackgroundPlane() { return backgroundPlaneValid; }
    public byte[] backgroundPlane() { return backgroundPlane.clone(); }
    public void setBackgroundPlane(byte[] bytes) {
        if (bytes.length != backgroundPlane.length) throw new IllegalArgumentException("Plane B size");
        backgroundPlane = bytes.clone();
        backgroundPlaneValid = true;
    }
    public int backgroundRoundedY() { return backgroundRoundedY; }
    public void setBackgroundRoundedY(int y) { backgroundRoundedY = y; }
    private int tileRowOffset;
    public int tileRowOffset() { return tileRowOffset; }
    public void setTileRowOffset(int value) { tileRowOffset = (short) value; }
    private int deathEggFall;
    private int foregroundY;
    private int appliedShake;
    private final S3kScreenShake shake = new S3kScreenShake();
    public S3kScreenShake shake() { return shake; }
    public int appliedShake() { return appliedShake; }
    public void advanceShake(int clock, boolean dead) {
        appliedShake = shake.offset();
        shake.setup(clock, dead);
    }
    public long job(int index) { return jobs[index]; }
    public void setJob(int index, long ordinal) { jobs[index] = ordinal; }
    public int foregroundY() { return foregroundY; }
    public void advanceDeathEgg(int cameraY) {
        foregroundY = (short) (cameraY - 0x110) >> 2;
        if ((cameraY & 65535) == 0x110) {
            deathEggFall += 0x6000;
            foregroundY += (short) (deathEggFall >> 16);
        }
    }
    private final short[] delays = new short[10];
    private final int[] velocities = new int[10];
    private final int[] offsets = new int[10];
    private final short[] scroll = new short[10];

    public void initialize() {
        Arrays.fill(delays, (short) -1);
        Arrays.fill(velocities, 0);
        Arrays.fill(offsets, 0);
        Arrays.fill(scroll, (short) 0);
    }

    public void setDelay(int column, int value) { delays[column] = (short) value; }
    public int delay(int column) { return delays[column]; }
    public int velocity(int column) { return velocities[column]; }
    public int offset(int column) { return offsets[column]; }
    public int scroll(int column) { return scroll[column] & 0xFFFF; }

    /** sub_5750C: integrate the old long velocity; the delay-zero branch starts next frame. */
    public boolean advance(int cameraYCopy) {
        int clamped = 0;
        for (int i = 0; i < 10; i++) {
            if (delays[i] == 0) {
                int oldVelocity = velocities[i];
                velocities[i] += 0x800;
                offsets[i] -= oldVelocity;
            } else if (delays[i] > 0) {
                delays[i]--;
            }
            int y = ((offsets[i] >> 16) + cameraYCopy) & 0xFFFF;
            if (y < 0x580) {
                velocities[i] = 0;
                y = 0x580;
                clamped++;
            }
            scroll[i] = (short) y;
        }
        return clamped == 10;
    }

    void capture(ByteBuffer buffer) {
        buffer.putInt(backgroundPlaneValid ? 1 : 0).putInt(backgroundRoundedY).put(backgroundPlane);
        for (long job : jobs) buffer.putLong(job);
        buffer.putInt(tileRowOffset).putInt(deathEggFall).putInt(foregroundY).putInt(appliedShake);
        shake.captureTo(buffer);
        for (int i = 0; i < 10; i++)
            buffer.putShort(delays[i]).putInt(velocities[i]).putInt(offsets[i]).putShort(scroll[i]);
    }
    void restore(ByteBuffer buffer) {
        backgroundPlaneValid = buffer.getInt() != 0;
        backgroundRoundedY = buffer.getInt();
        buffer.get(backgroundPlane);
        for (int i = 0; i < jobs.length; i++) jobs[i] = buffer.getLong();
        tileRowOffset = buffer.getInt();
        deathEggFall = buffer.getInt();
        foregroundY = buffer.getInt();
        appliedShake = buffer.getInt();
        shake.restoreFrom(buffer);
        for (int i = 0; i < 10; i++) {
            delays[i] = buffer.getShort();
            velocities[i] = buffer.getInt();
            offsets[i] = buffer.getInt();
            scroll[i] = buffer.getShort();
        }
    }
}
