package com.openggf.game.soniccd.device.pcm;

/** Byte registers in the PCM chip's own address space, before sub-CPU bus address translation. */
public enum CdPcmRegister {
    ENVELOPE(0), PAN(1), FREQUENCY_LOW(2), FREQUENCY_HIGH(3), LOOP_LOW(4), LOOP_HIGH(5),
    START(6), CONTROL(7), CHANNEL_OFF(8);

    private final int address;

    CdPcmRegister(int address) { this.address = address; }

    public int address() { return address; }
}
