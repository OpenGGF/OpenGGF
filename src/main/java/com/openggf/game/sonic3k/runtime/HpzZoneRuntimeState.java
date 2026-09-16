package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared Hidden Palace sanctuary state ({@code $1701} and the engine
 * alias {@code $1601}).
 *
 * <p>The sanctuary has no zone-events class; its only per-frame event state is
 * the screen shake the falling-crystal ceremony raises. {@code HPZS_ScreenEvent}
 * (sonic3k.asm:120823-120826) adds {@code Screen_shake_offset} to
 * {@code Camera_Y_pos_copy}, {@code HPZS_BackgroundEvent} (120845-120855)
 * folds the same word into the background scroll, then tail-calls
 * {@code ShakeScreen_Setup} to produce the next frame's value. The engine runs
 * that setup at the head of its screen-event pass instead, so
 * {@link #appliedScreenShakeOffset()} is the word both events read this frame
 * and {@link #screenShake()} already holds the next one.
 */
public final class HpzZoneRuntimeState implements S3kZoneRuntimeState {
    private static final int CAPTURE_BYTES = S3kScreenShake.captureBytes() + Integer.BYTES;

    private final int zoneIndex;
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final S3kScreenShake screenShake = new S3kScreenShake();
    private int appliedScreenShakeOffset;

    public HpzZoneRuntimeState(int zoneIndex, int actIndex, PlayerCharacter playerCharacter) {
        this.zoneIndex = zoneIndex;
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
    }

    @Override public int zoneIndex() { return zoneIndex; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }

    /** ROM {@code Screen_shake_flag} / {@code Screen_shake_offset} owner. */
    public S3kScreenShake screenShake() {
        return screenShake;
    }

    /**
     * Runs {@code ShakeScreen_Setup} for this frame. Called once per frame
     * before {@code HPZS_ScreenEvent}; the offset the previous setup produced
     * becomes {@link #appliedScreenShakeOffset()} for the rest of the frame.
     */
    public void advanceScreenShake(int levelFrameCounter, boolean playerDeadOrRestarting) {
        appliedScreenShakeOffset = screenShake.offset();
        screenShake.setup(levelFrameCounter, playerDeadOrRestarting);
    }

    /** {@code Screen_shake_offset} as {@code HPZS_ScreenEvent} and {@code HPZS_BackgroundEvent} read it this frame. */
    public int appliedScreenShakeOffset() {
        return appliedScreenShakeOffset;
    }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(CAPTURE_BYTES);
        screenShake.captureTo(buffer);
        buffer.putInt(appliedScreenShakeOffset);
        return buffer.array();
    }

    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length < CAPTURE_BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        screenShake.restoreFrom(buffer);
        appliedScreenShakeOffset = buffer.getInt();
    }
}
