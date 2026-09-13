package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;

import java.util.Optional;

/**
 * Resolves lock-on addresses to the reader that physically holds them.
 *
 * <p>{@code s2.lockon.asm} assembles KiS2 against three windows: the S&amp;K
 * cart at {@code $000000-$1FFFFF}, the Sonic 2 cart at {@code $200000-$2FFFFF}
 * (S2 offset + {@code $200000}) and the 256 KiB chip at {@code $300000-$33FFFF}.
 * Pointer tables such as {@code Off_Objects_KiS2} mix all three, so a
 * resolver keyed on address range is the only carve-out-free way to read
 * them. A window whose reader is absent resolves to {@link Optional#empty()}.
 */
public final class LockOnAddressSpace {

    /** A resolved pointer: the reader holding it and the address inside that reader. */
    public record Read(RomByteReader reader, int localAddress, Window window) {
    }

    public enum Window {
        SK, S2, CHIP
    }

    private final RomByteReader sk;
    private final RomByteReader s2;
    private final RomByteReader chip;

    public LockOnAddressSpace(RomByteReader sk, RomByteReader s2, RomByteReader chip) {
        this.sk = sk;
        this.s2 = s2;
        this.chip = chip;
    }

    /** Tier one: S&amp;K half and S2 cart only; the chip is unavailable. */
    public static LockOnAddressSpace tierOne(RomByteReader sk, RomByteReader s2) {
        return new LockOnAddressSpace(sk, s2, null);
    }

    public static Window windowOf(long address) {
        if (address >= Kis2Constants.SK_WINDOW_START && address < Kis2Constants.SK_WINDOW_END) {
            return Window.SK;
        }
        if (address >= Kis2Constants.S2_WINDOW_START && address < Kis2Constants.S2_WINDOW_END) {
            return Window.S2;
        }
        if (address >= Kis2Constants.CHIP_WINDOW_START && address < Kis2Constants.CHIP_WINDOW_END) {
            return Window.CHIP;
        }
        throw new IllegalArgumentException("Address 0x" + Long.toHexString(address)
                + " is outside the lock-on address space");
    }

    public Optional<Read> resolve(long address) {
        Window window = windowOf(address);
        RomByteReader reader = switch (window) {
            case SK -> sk;
            case S2 -> s2;
            case CHIP -> chip;
        };
        if (reader == null) {
            return Optional.empty();
        }
        int local = (int) (address - switch (window) {
            case SK -> Kis2Constants.SK_WINDOW_START;
            case S2 -> Kis2Constants.S2_WINDOW_START;
            case CHIP -> Kis2Constants.CHIP_WINDOW_START;
        });
        if (local >= reader.size()) {
            return Optional.empty();
        }
        return Optional.of(new Read(reader, local, window));
    }
}
