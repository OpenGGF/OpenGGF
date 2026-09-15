package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/** Native SOZ screen-event RAM; one captured owner shared with arena objects and scroll. */
public final class SozEventState {
    static final int SNAPSHOT_BYTES = 22 * Integer.BYTES;
    private boolean initialized;
    private int foregroundRoutine;
    private int backgroundRoutine;
    private int specialRoutine;
    private int sandPosition;
    private int redrawRemaining;
    private int savedBackgroundX;
    private int savedBackgroundY;
    private int screenShakeFlag;
    private int screenShakeOffset;
    private int doorSignal;
    private int backgroundRowReplacement;
    private int fadePasses;
    private int fadeDelay;
    private int bossWallTimer;
    private int bossWallRoutine;
    private int bossWallHitY;
    private int bossX;
    private int bossY;
    private int bossArtPhase;
    private boolean backgroundCollision;
    private boolean seamlessEntry;

    public boolean initialized() { return initialized; }
    public void initialized(boolean value) { initialized = value; }
    public int foregroundRoutine() { return foregroundRoutine; }
    public void foregroundRoutine(int value) { foregroundRoutine = value; }
    public int backgroundRoutine() { return backgroundRoutine; }
    public void backgroundRoutine(int value) { backgroundRoutine = value; }
    public int specialRoutine() { return specialRoutine; }
    public void specialRoutine(int value) { specialRoutine = value; }
    public int sandPosition() { return sandPosition; }
    public void sandPosition(int value) { sandPosition = value; }
    public int sandHeight() { return (short) (sandPosition >> 16); }
    public int redrawRemaining() { return redrawRemaining; }
    public void redrawRemaining(int value) { redrawRemaining = value; }
    public int savedBackgroundX() { return savedBackgroundX; }
    public void savedBackgroundX(int value) { savedBackgroundX = value; }
    public int savedBackgroundY() { return savedBackgroundY; }
    public void savedBackgroundY(int value) { savedBackgroundY = value; }
    public int screenShakeFlag() { return screenShakeFlag; }
    public void screenShakeFlag(int value) { screenShakeFlag = (short) value; }
    public int screenShakeOffset() { return screenShakeOffset; }
    public void screenShakeOffset(int value) { screenShakeOffset = value; }
    public int doorSignal() { return doorSignal; }
    public void doorSignal(int value) { doorSignal = (short) value; }
    public int backgroundRowReplacement() { return backgroundRowReplacement; }
    public void backgroundRowReplacement(int value) { backgroundRowReplacement = value; }
    public int fadePasses() { return fadePasses; }
    public void fadePasses(int value) { fadePasses = value; }
    public int fadeDelay() { return fadeDelay; }
    public void fadeDelay(int value) { fadeDelay = value; }
    public int bossWallTimer() { return bossWallTimer; }
    public void bossWallTimer(int value) { bossWallTimer = value; }
    public int bossWallRoutine() { return bossWallRoutine; }
    public void bossWallRoutine(int value) { bossWallRoutine = value; }
    public int bossWallHitY() { return bossWallHitY; }
    public void bossWallHitY(int value) { bossWallHitY = value & 0xFFFF; }
    public int bossX() { return bossX; }
    public void bossX(int value) { bossX = value & 0xFFFF; }
    public int bossY() { return bossY; }
    public void bossY(int value) { bossY = value & 0xFFFF; }
    public int bossArtPhase() { return bossArtPhase; }
    public void bossArtPhase(int value) { bossArtPhase = value; }
    public boolean backgroundCollision() { return backgroundCollision; }
    public void backgroundCollision(boolean value) { backgroundCollision = value; }
    public boolean seamlessEntry() { return seamlessEntry; }
    public void seamlessEntry(boolean value) { seamlessEntry = value; }

    void capture(ByteBuffer buffer) {
        buffer.putInt(initialized ? 1 : 0).putInt(foregroundRoutine).putInt(backgroundRoutine)
                .putInt(specialRoutine).putInt(sandPosition).putInt(redrawRemaining)
                .putInt(savedBackgroundX).putInt(savedBackgroundY).putInt(screenShakeFlag)
                .putInt(screenShakeOffset).putInt(doorSignal).putInt(backgroundRowReplacement)
                .putInt(fadePasses).putInt(fadeDelay).putInt(bossWallTimer).putInt(bossWallRoutine)
                .putInt(bossWallHitY).putInt(bossX).putInt(bossY).putInt(bossArtPhase)
                .putInt(backgroundCollision ? 1 : 0).putInt(seamlessEntry ? 1 : 0);
    }

    void restore(ByteBuffer buffer) {
        initialized = buffer.getInt() != 0;
        foregroundRoutine = buffer.getInt();
        backgroundRoutine = buffer.getInt();
        specialRoutine = buffer.getInt();
        sandPosition = buffer.getInt();
        redrawRemaining = buffer.getInt();
        savedBackgroundX = buffer.getInt();
        savedBackgroundY = buffer.getInt();
        screenShakeFlag = buffer.getInt();
        screenShakeOffset = buffer.getInt();
        doorSignal = buffer.getInt();
        backgroundRowReplacement = buffer.getInt();
        fadePasses = buffer.getInt();
        fadeDelay = buffer.getInt();
        bossWallTimer = buffer.getInt();
        bossWallRoutine = buffer.getInt();
        bossWallHitY = buffer.getInt();
        bossX = buffer.getInt();
        bossY = buffer.getInt();
        bossArtPhase = buffer.getInt();
        backgroundCollision = buffer.getInt() != 0;
        seamlessEntry = buffer.getInt() != 0;
    }
}
