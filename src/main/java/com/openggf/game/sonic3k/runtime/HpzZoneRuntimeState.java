package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared Hidden Palace state for the Super Emerald sanctuary
 * ({@code $1701}) and the playable Hidden Palace act ({@code $1601}).
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
    private static final int CAPTURE_BYTES =
            S3kScreenShake.captureBytes() + Integer.BYTES + 2 * Short.BYTES + 3;
    /** {@code AnPal_HPZ}: {@code Palette_cycle_counter0} steps by 4 and wraps at {@code $28}. */
    private static final int PALETTE_CYCLE_STEP = 4;
    private static final int PALETTE_CYCLE_WRAP = 0x28;
    private static final int PALETTE_CYCLE_PERIOD = 7;

    private final int zoneIndex;
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final S3kScreenShake screenShake = new S3kScreenShake();
    private int appliedScreenShakeOffset;
    private short paletteCycleCounter0;
    private short paletteCycleCounter1;
    private boolean paletteCycleSuppressed;
    private boolean paletteControlAllocated;
    private boolean foregroundCollapsePending;

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

    /**
     * Runs one {@code AnPal_HPZ} pass (sonic3k.asm:3934-3951) and returns the
     * {@code AnPal_PalHPZ} byte offset to copy into {@code Normal_palette_line_4+$2},
     * or {@code -1} when the pass writes nothing. The counters start cleared,
     * as level initialization clears {@code Palette_cycle_counters}.
     */
    public int tickPaletteCycle() {
        // tst.b (Palette_cycle_counters+$00).w / bne.s locret_2AF4
        if (paletteCycleSuppressed) {
            return -1;
        }
        // subq.w #1,(Palette_cycle_counter1).w / bpl.s locret_2AF4
        paletteCycleCounter1--;
        if (paletteCycleCounter1 >= 0) {
            return -1;
        }
        paletteCycleCounter1 = PALETTE_CYCLE_PERIOD;
        int offset = paletteCycleCounter0;
        paletteCycleCounter0 += PALETTE_CYCLE_STEP;
        if ((paletteCycleCounter0 & 0xFFFF) >= PALETTE_CYCLE_WRAP) {
            paletteCycleCounter0 = 0;
        }
        return offset;
    }

    /** ROM writers of {@code Palette_cycle_counter1}, e.g. {@code Obj_HPZMasterEmerald}'s {@code 8000-1}. */
    public void setPaletteCycleCounter1(int value) {
        paletteCycleCounter1 = (short) value;
    }

    /** {@code st}/{@code clr.b (Palette_cycle_counters+$00).w} from the HPZ teleporter. */
    public void setPaletteCycleSuppressed(boolean suppressed) {
        paletteCycleSuppressed = suppressed;
    }

    public boolean paletteCycleSuppressed() {
        return paletteCycleSuppressed;
    }

    /** {@code Events_bg+$00}: {@code HPZ_ScreenEvent} has attempted the palette-control allocation. */
    public boolean paletteControlAllocated() {
        return paletteControlAllocated;
    }

    public void markPaletteControlAllocated() {
        paletteControlAllocated = true;
    }

    /** {@code Events_fg_4}: raised by the Knuckles fight collapse, consumed by {@code HPZ_ScreenEvent}. */
    public void requestForegroundCollapse() {
        foregroundCollapsePending = true;
    }

    public boolean consumeForegroundCollapse() {
        boolean pending = foregroundCollapsePending;
        foregroundCollapsePending = false;
        return pending;
    }

    @Override
    public byte[] captureBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(CAPTURE_BYTES);
        screenShake.captureTo(buffer);
        buffer.putInt(appliedScreenShakeOffset);
        buffer.putShort(paletteCycleCounter0);
        buffer.putShort(paletteCycleCounter1);
        buffer.put((byte) (paletteCycleSuppressed ? 1 : 0));
        buffer.put((byte) (paletteControlAllocated ? 1 : 0));
        buffer.put((byte) (foregroundCollapsePending ? 1 : 0));
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
        paletteCycleCounter0 = buffer.getShort();
        paletteCycleCounter1 = buffer.getShort();
        paletteCycleSuppressed = buffer.get() != 0;
        paletteControlAllocated = buffer.get() != 0;
        foregroundCollapsePending = buffer.get() != 0;
    }
}
