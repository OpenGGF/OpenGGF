package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/** Shared RAM owned by AnPal_SOZ2, AnimateTiles_SOZ2 and the light/event objects. */
public final class SozLightingState {
    static final int SNAPSHOT_BYTES = 10 * Integer.BYTES;
    private int darknessLevel;
    private int masterTimer;
    private int fadeRemaining;
    private int fadeOffset;
    private int sandOffset;
    private int fadeStep;
    private int fadeTimer;
    private int sandTimer;
    private int torchTimer;
    private int torchFrame;

    /** A cold Act 2 load leaves RAM zeroed; only sub_55EFC seeds this dark state. */
    public void initializeSeamlessDarkness() {
        darknessLevel = 5;
        masterTimer = 1799;
        fadeRemaining = 0;
        fadeStep = 4;
        fadeOffset = 0xD0;
    }

    /** Obj_SOZLightSwitch: reverse the fade from its current step, including mid-fade. */
    public void resetLight() {
        darknessLevel = 0;
        masterTimer = 899;
        fadeRemaining = (byte) -fadeStep;
        fadeTimer = 0;
    }

    /** loc_56540 performs this once when entering the boss background. */
    public void enterBossLight() {
        resetLight();
        holdBossTimers();
    }

    /** loc_565DE holds these timers without restarting the in-progress brightening. */
    public void holdBossTimers() {
        masterTimer = 0x7FFF;
        torchTimer = 0x7F;
    }

    /** Source offsets to upload this tick; -1 means that destination is unchanged. */
    public record PaletteUpdate(int lightOffset, int sandOffset) { }

    public PaletteUpdate tickPalette() {
        // AnPal_SOZ2 ($2598): all word decrements and the fade byte use signed expiry.
        masterTimer = (short) (masterTimer - 1);
        if (masterTimer < 0) {
            masterTimer = 899;
            if (darknessLevel < 5 && (++darknessLevel & 1) == 0) {
                fadeRemaining = 2;
                fadeTimer = 0;
            }
        }
        int lightWrite = -1;
        if (fadeRemaining != 0) {
            fadeTimer = (short) (fadeTimer - 1);
            if (fadeTimer < 0) {
                fadeTimer = 3;
                int direction = fadeRemaining > 0 ? 1 : -1;
                fadeRemaining = (byte) (fadeRemaining - direction);
                fadeOffset = (fadeOffset + direction * 0x34) & 0xFFFF;
                fadeStep = (fadeStep + direction) & 0xFFFF;
                lightWrite = fadeOffset;
            }
        }
        int sandWrite = sandOffset;
        sandTimer = (short) (sandTimer - 1);
        if (sandTimer < 0) {
            sandTimer = 5;
            sandOffset = (sandOffset + 8) & 0x1F;
        } else if (lightWrite < 0) {
            return new PaletteUpdate(-1, -1);
        }
        // loc_261A forces this write on a fade step even before the sand timer expires.
        return new PaletteUpdate(lightWrite, (fadeStep << 5) + sandWrite);
    }

    /** AnimateTiles_SOZ2: return the six-tile source frame, or -1 between uploads. */
    public int tickTorch() {
        torchTimer = (byte) (torchTimer - 1);
        if (torchTimer >= 0) return -1;
        torchTimer = 7;
        int oldFrame = torchFrame;
        torchFrame = oldFrame >= 2 ? 0 : oldFrame + 1;
        int bank = fadeStep & 6;
        bank += bank >> 1;
        return bank == 6 ? bank : bank + oldFrame;
    }

    public void resumeTorch() { torchTimer = 0; }

    public int darknessLevel() { return darknessLevel; }
    public int masterTimer() { return masterTimer; }
    public int fadeRemaining() { return fadeRemaining; }
    public int fadeStep() { return fadeStep; }
    public int fadeOffset() { return fadeOffset; }
    public int sandOffset() { return sandOffset; }
    public int fadeTimer() { return fadeTimer; }
    public int sandTimer() { return sandTimer; }
    public int torchTimer() { return torchTimer; }
    public int torchFrame() { return torchFrame; }

    void capture(ByteBuffer buffer) {
        buffer.putInt(darknessLevel).putInt(masterTimer).putInt(fadeRemaining)
                .putInt(fadeOffset).putInt(sandOffset).putInt(fadeStep).putInt(fadeTimer)
                .putInt(sandTimer).putInt(torchTimer).putInt(torchFrame);
    }

    void restore(ByteBuffer buffer) {
        darknessLevel = buffer.getInt();
        masterTimer = buffer.getInt();
        fadeRemaining = buffer.getInt();
        fadeOffset = buffer.getInt();
        sandOffset = buffer.getInt();
        fadeStep = buffer.getInt();
        fadeTimer = buffer.getInt();
        sandTimer = buffer.getInt();
        torchTimer = buffer.getInt();
        torchFrame = buffer.getInt();
    }
}
