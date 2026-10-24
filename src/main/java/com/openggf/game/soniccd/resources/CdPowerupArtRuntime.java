package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdShieldArt;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure LoadShieldArt transform (Level/_Objects/Powerup/Main.asm:157-186, pin1ecc5f3a).
 * BCLR7 gates one raw $400-byte copy, followed by a complete $480-byte VRAM upload.
 * The final $80 bytes are caller-supplied resident CPU RAM, never source padding.
 * The caller supplies the actual authenticated asset owner and complete ingress;
 * this component creates no default RAM, service clock, renderer or source identity.
 *
 * The Runtime alone owns powerup, state capture/restore, V-int selection and DMA
 * publication. Accept the returned state and cleared byte once; do not replay a
 * request from drawing or restore. This is byte/order behavior, not chip timing.
 */
public final class CdPowerupArtRuntime {
    public static final int COPY_BYTES = 0x400;
    public static final int WINDOW_BYTES = 0x480;
    public static final int VRAM_BYTE_ADDRESS = 0xA880;

    /** Complete CPU and resident windows. Equality is byte content, not wrapper identity. */
    public record State(CdAssetBytes cpuBuffer, CdAssetBytes residentBank) {
        public State {
            Objects.requireNonNull(cpuBuffer, "cpuBuffer"); Objects.requireNonNull(residentBank, "residentBank");
            if (cpuBuffer.length() != WINDOW_BYTES || residentBank.length() != WINDOW_BYTES) {
                throw new IllegalArgumentException("Complete $480-byte powerup CPU and resident windows required");
            }
        }
        public State(byte[] cpuBuffer, byte[] residentBank) {
            this(CdAssetBytes.copyOf(cpuBuffer), CdAssetBytes.copyOf(residentBank));
        }
        @Override public boolean equals(Object other) {
            return other instanceof State state && Arrays.equals(cpuBuffer.copy(), state.cpuBuffer.copy())
                    && Arrays.equals(residentBank.copy(), state.residentBank.copy());
        }
        @Override public int hashCode() {
            return 31 * Arrays.hashCode(cpuBuffer.copy()) + Arrays.hashCode(residentBank.copy());
        }
    }

    /** Already reflected in Result.state; a typed immutable request, not a graphics call. */
    public record DmaRequest(CdAssetBytes bytes) {
        public DmaRequest {
            Objects.requireNonNull(bytes, "bytes");
            if (bytes.length() != WINDOW_BYTES) throw new IllegalArgumentException("Powerup DMA must be $480 bytes");
        }
        public int vramByteAddress() { return VRAM_BYTE_ADDRESS; }
    }
    public record Result(State state, int powerup, Optional<DmaRequest> dma) {
        public Result { Objects.requireNonNull(state); byteValue(powerup); Objects.requireNonNull(dma); }
    }

    /** The native read is outside or between the five registered pointer entries. No bytes are invented. */
    public static final class UnsupportedArt extends UnsupportedOperationException {
        private final int pointerOffset;
        private UnsupportedArt(int pointerOffset) {
            super("Unbound ShieldArtIndex pointer at byte offset $" + Integer.toHexString(pointerOffset));
            this.pointerOffset = pointerOffset;
        }
        public int pointerOffset() { return pointerOffset; }
    }

    private final CdShieldArt assets;
    public CdPowerupArtRuntime(CdShieldArt assets) { this.assets = Objects.requireNonNull(assets, "assets"); }

    /**
     * One selected LoadShieldArt call. Low selectors are not inspected unless bit7 was set.
     * Invalid active selectors stop before a Result exists; the external owner must close
     * failed admission rather than retry or claim to roll back a partially published bank.
     */
    public Result service(State state, int powerup) {
        Objects.requireNonNull(state, "state"); byteValue(powerup);
        int cleared = powerup & 0x7F; // BCLR #7,powerup; the previous bit selects BEQ.
        if ((powerup & 0x80) == 0) return new Result(state, cleared, Optional.empty());
        // MOVEQ0/MOVE.B, SUBQ.B #2, ADD.W d0,d0: underflow wraps only the low byte.
        int offset = ((cleared - 2) & 0xFF) * 2;
        if ((offset & 3) != 0 || offset >= 5 * 4) throw new UnsupportedArt(offset);
        var source = assets.copy(offset / 4).bytes();
        byte[] cpu = state.cpuBuffer().copy();
        source.buffer().get(cpu, 0, COPY_BYTES); // DBF $FF copies exactly $100 longwords.
        var complete = CdAssetBytes.copyOf(cpu); // Retain all $80 trailing RAM bytes.
        return new Result(new State(complete, complete), cleared, Optional.of(new DmaRequest(complete)));
    }

    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("powerup must be a native byte");
    }
}
