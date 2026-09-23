package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import java.nio.ByteBuffer;
import java.util.Objects;

/** DEZ3 ($1700) event words shared by the arena, plane renderer and final boss. */
public final class DezFinalBossZoneRuntimeState implements S3kZoneRuntimeState {
    private final S3kEmeraldPaletteState emeraldPalette = new S3kEmeraldPaletteState();
    public S3kEmeraldPaletteState emeraldPalette() { return emeraldPalette; }

    private final PlayerCharacter playerCharacter;
    private final DezFinalPlaneState plane = new DezFinalPlaneState();
    private final S3kScreenShake screenShake = new S3kScreenShake();
    private short windowBase = 0x6C0;
    private short bossX = 0x3C0;
    private short bossY = 0xF8;
    private short redrawRequest;
    private short breakRequest;
    private short mouthPhase;
    /** _unkFAA9: 0 hidden, 1 opening/closing, $80 fully open. */
    private short mouthStatus;
    private short retainedPlaneX;
    private short laserOffset;
    private short uploadedLaserOffset = (short) 0xFF00; // DEZ3_ScreenInit: st writes only the high byte.
    private short breakFrontier = 0x80;
    private short foregroundRoutine;
    private short backgroundRoutine;
    private short bossSignals;
    private short eventsFg5;
    private short planeX;
    private short planeY;
    private short screenY = 0x20;

    public DezFinalBossZoneRuntimeState(PlayerCharacter playerCharacter) {
        this.playerCharacter = Objects.requireNonNull(playerCharacter);
    }
    @Override public int zoneIndex() { return 0x17; }
    @Override public int actIndex() { return 0; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    public DezFinalPlaneState plane() { return plane; }

    public S3kScreenShake screenShake() { return screenShake; }

    public int windowBase() { return windowBase & 0xFFFF; }
    public void windowBase(int value) { windowBase = (short) value; }
    public int bossX() { return bossX & 0xFFFF; }
    public int bossY() { return bossY & 0xFFFF; }
    public void bossPosition(int x, int y) { bossX = (short) x; bossY = (short) y; }
    public int redrawRequest() { return redrawRequest & 0xFFFF; }
    public void redrawRequest(int value) { redrawRequest = (short) value; }
    public int breakRequest() { return breakRequest & 0xFFFF; }
    public void breakRequest(int value) { breakRequest = (short) value; }
    public int mouthStatus() { return mouthStatus & 0xFF; }
    public void mouthStatus(int value) { mouthStatus = (short) (value & 0xFF); }
    public int mouthPhase() { return mouthPhase & 0xFFFF; }
    public void mouthPhase(int value) { mouthPhase = (short) value; }
    public int retainedPlaneX() { return retainedPlaneX; }
    public void retainedPlaneX(int value) { retainedPlaneX = (short) value; }
    public int laserOffset() { return laserOffset & 0xFFFF; }
    public void laserOffset(int value) { laserOffset = (short) value; }
    public int uploadedLaserOffset() { return uploadedLaserOffset & 0xFFFF; }
    public void uploadedLaserOffset(int value) { uploadedLaserOffset = (short) value; }
    public int breakFrontier() { return breakFrontier & 0xFFFF; }
    public void breakFrontier(int value) { breakFrontier = (short) value; }
    public int foregroundRoutine() { return foregroundRoutine & 0xFFFF; }
    public void foregroundRoutine(int value) { foregroundRoutine = (short) value; }
    public int backgroundRoutine() { return backgroundRoutine & 0xFFFF; }
    public void backgroundRoutine(int value) { backgroundRoutine = (short) value; }
    public int bossSignals() { return bossSignals & 0xFF; }
    public void bossSignals(int value) { bossSignals = (short) (value & 0xFF); }
    public int eventsFg5() { return eventsFg5 & 0xFFFF; }
    public void eventsFg5(int value) { eventsFg5 = (short) value; }
    public int planeX() { return planeX; }
    public int planeY() { return planeY; }
    public int screenY() { return screenY; }

    /** sub_5A508 / sub_5A76C; all three camera-copy writes are words. */
    public void publishPlanePosition(int cameraX, int shake) {
        screenY = (short) (0x20 + shake);
        planeX = windowBase == 0 ? 0 : (short) (cameraX - bossX + windowBase);
        planeY = (short) (screenY - bossY + 0x180);
    }

    @Override public byte[] captureBytes() {
        var buffer = ByteBuffer.allocate(18 * Short.BYTES + S3kEmeraldPaletteState.SNAPSHOT_BYTES + S3kScreenShake.captureBytes() + DezFinalPlaneState.SNAPSHOT_BYTES);
        emeraldPalette.captureTo(buffer);
        screenShake.captureTo(buffer);
        plane.capture(buffer);
        return buffer
                .putShort(windowBase).putShort(bossX).putShort(bossY)
                .putShort(redrawRequest).putShort(breakRequest).putShort(mouthPhase).putShort(mouthStatus)
                .putShort(retainedPlaneX).putShort(laserOffset).putShort(uploadedLaserOffset)
                .putShort(breakFrontier).putShort(foregroundRoutine).putShort(backgroundRoutine)
                .putShort(bossSignals).putShort(eventsFg5).putShort(planeX).putShort(planeY)
                .putShort(screenY).array();
    }
    @Override public void restoreBytes(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        emeraldPalette.restoreFrom(buffer);
        screenShake.restoreFrom(buffer);
        plane.restore(buffer);
        windowBase = buffer.getShort(); bossX = buffer.getShort(); bossY = buffer.getShort();
        redrawRequest = buffer.getShort(); breakRequest = buffer.getShort(); mouthPhase = buffer.getShort(); mouthStatus = buffer.getShort();
        retainedPlaneX = buffer.getShort(); laserOffset = buffer.getShort(); uploadedLaserOffset = buffer.getShort();
        breakFrontier = buffer.getShort(); foregroundRoutine = buffer.getShort(); backgroundRoutine = buffer.getShort();
        bossSignals = buffer.getShort(); eventsFg5 = buffer.getShort(); planeX = buffer.getShort(); planeY = buffer.getShort();
        screenY = buffer.getShort();
    }
}
