package com.openggf.game.sonic3k.timing;

import java.util.List;
import java.util.Objects;

/**
 * 68000 bus freeze of the 68k-bus DMA transfers one S3K V-int performs, from the shipped ROM's VInt_8 transfers
 * and the pinned GPGX 051d430d VDP rules ({@code core/vdp_ctrl.c} blob e99ab3d6).
 *
 * <p>GPGX {@code vdp_dma_update}: the rate is {@code dma_timing[(status & 8) || !(reg[1] & 0x40)][reg[12] & 1]}
 * access slots per line ({16, 18} active, {166, 204} blank, for {H32, H40}). A 68k-to-VRAM transfer
 * ({@code dma_type 1}, VDP code bits 2-1 clear) spends two slots per word ({@code rate >> 1}); a 68k-to-CRAM or VSRAM
 * transfer ({@code dma_type 0}) loses the refresh slots of a blank line (166 becomes 161, 204 becomes 198). In
 * VBLANK the transfer may use {@code (lines_per_frame - viewport.h - 1) * MCYCLES_PER_LINE - cycles}; when its word
 * count is strictly below the words that fit ({@code dma_cycles * rate / MCYCLES_PER_LINE}), the 68k is frozen for
 * {@code words * MCYCLES_PER_LINE / rate} master cycles (integer division per transfer). An exact fit takes the whole
 * remaining VBLANK, and a longer transfer continues into active display at the active rate. This class models the
 * strict case only and refuses the others: the per-V-int ledger does not know the V-int handler's own instruction
 * time, so whether a transfer ends inside VBLANK is a precondition it reports, never assumes silently.
 */
public final class S3kVIntDma {
    /** GPGX {@code MCYCLES_PER_LINE}. */
    public static final int MCLK_PER_LINE = 3420;

    private S3kVIntDma() {
    }

    /** VDP destination selected by the DMA command's code bits. */
    public enum Target {
        VRAM, CRAM, VSRAM;

        /** GPGX {@code dma_type = (code & 0x06) ? 0 : 1} for a 68k-bus DMA; VRAM write code 1, CRAM 3, VSRAM 5. */
        static Target fromCode(int code) {
            return switch (code & 0x0F) {
                case 0x01 -> VRAM;
                case 0x03 -> CRAM;
                case 0x05 -> VSRAM;
                default -> throw new IllegalArgumentException(String.format("not a VDP write code: %02X", code));
            };
        }
    }

    /** One 68k-bus DMA: word count, target and destination address in that target. */
    public record Transfer(int words, Target target, int destination) {
        public Transfer {
            Objects.requireNonNull(target, "target");
            if (words <= 0 || words > 0x10000) {
                throw new IllegalArgumentException("DMA word count out of range: " + words);
            }
        }
    }

    /** Access rate in words per line for a blank-display (VBLANK or display-disabled) 68k-bus DMA. */
    public static int blankWordsPerLine(Target target, boolean h40) {
        int rate = h40 ? 204 : 166;
        if (target == Target.VRAM) {
            return rate >> 1;
        }
        return h40 ? 198 : 161;
    }

    /**
     * Master cycles the 68k is frozen for a transfer that ends inside VBLANK. {@code remainingVBlankMclk} is GPGX's
     * {@code dma_cycles} at the trigger; the transfer must be strictly shorter than what fits.
     */
    public static long freezeEndingInVBlank(Transfer transfer, boolean h40, long remainingVBlankMclk) {
        int rate = blankWordsPerLine(transfer.target(), h40);
        long fitting = remainingVBlankMclk * rate / MCLK_PER_LINE;
        if (transfer.words() >= fitting) {
            throw new IllegalArgumentException(String.format(
                    "%d words do not end strictly inside the remaining %d VBLANK cycles (%d fit)",
                    transfer.words(), remainingVBlankMclk, fitting));
        }
        return blankFreeze(transfer, h40);
    }

    /** {@code words * MCYCLES_PER_LINE / rate}, the freeze of a transfer that ends strictly inside VBLANK. */
    public static long blankFreeze(Transfer transfer, boolean h40) {
        return (long) transfer.words() * MCLK_PER_LINE / blankWordsPerLine(transfer.target(), h40);
    }

    /** Sum of {@link #blankFreeze} over the transfers, each floored separately as GPGX does. */
    public static long blankFreeze(List<Transfer> transfers, boolean h40) {
        long total = 0;
        for (Transfer transfer : transfers) {
            total += blankFreeze(transfer, h40);
        }
        return total;
    }

    /** VBLANK master cycles GPGX gives DMA from the start of a frame: {@code (lines - 224 - 1) * 3420} (V28). */
    public static long vblankMclk(boolean pal) {
        return (long) ((pal ? 313 : 262) - 224 - 1) * MCLK_PER_LINE;
    }

    /**
     * The fixed VInt_8 DMA transfers, decoded from the shipped ROM's {@code dma68kToVDP} instruction immediates and
     * refused if any pinned instruction differs. VInt_8_NoFlash ($838) sends Normal_palette or, with
     * {@code Water_full_screen_flag} set, Water_palette to CRAM; VInt_8_Cont ($88C) sends H_scroll_buffer to VRAM
     * $F000 and then Sprite_table (or the alternate page in Competition mode) to VRAM $F800, before
     * {@code bsr.w Process_DMA_Queue} ($916). The Hyper Sonic flash branch replaces the palette DMA with CPU writes.
     */
    public record VInt8Transfers(Transfer palette, Transfer horizontalScroll, Transfer spriteTable, boolean h40) {
        /** {@code Level} writes {@code move.w #$8C81,(a6)} (40-cell display) at ROM $616A before LevelLoop. */
        static final int LEVEL_REGISTER_12_PC = 0x616A;

        public static VInt8Transfers decode(byte[] rom) {
            Objects.requireNonNull(rom, "rom");
            Transfer normal = transfer(rom, 0x844, 0x854, 0x858);
            Transfer water = transfer(rom, 0x86A, 0x87A, 0x87E);
            Transfer scroll = transfer(rom, 0x892, 0x8A2, 0x8A6);
            Transfer sprites = transfer(rom, 0x8F8, 0x908, 0x90C);
            Transfer alternate = transfer(rom, 0x8D2, 0x8E2, 0x8E6);
            if (!normal.equals(water) || !sprites.equals(alternate)) {
                throw new IllegalArgumentException("VInt_8 branch transfers differ in length or target");
            }
            if (word(rom, 0x916) != 0x6100) {
                throw new IllegalArgumentException("VInt_8_Cont does not call Process_DMA_Queue at $916");
            }
            require(rom, LEVEL_REGISTER_12_PC, 0x3CBC);
            int register12 = word(rom, LEVEL_REGISTER_12_PC + 2);
            if ((register12 & 0xFF00) != 0x8C00) {
                throw new IllegalArgumentException("Level does not set VDP register 12 at $616A");
            }
            return new VInt8Transfers(normal, scroll, sprites, (register12 & 0x01) != 0);
        }

        /** In ROM order, without the palette transfer when the Hyper Sonic flash branch runs. */
        public List<Transfer> inOrder(boolean hyperFlashBranch) {
            return hyperFlashBranch ? List.of(horizontalScroll, spriteTable)
                    : List.of(palette, horizontalScroll, spriteTable);
        }

        /**
         * One {@code dma68kToVDP}: {@code move.l #$94hh93ll,(a5)} carries the length registers 20/19; the command is
         * {@code move.w #hi,(a5)} then {@code move.w #lo,(DMA_trigger_word).w}.
         */
        private static Transfer transfer(byte[] rom, int lengthPc, int commandHighPc, int commandLowPc) {
            require(rom, lengthPc, 0x2ABC);
            require(rom, commandHighPc, 0x3ABC);
            require(rom, commandLowPc, 0x31FC);
            int reg20 = word(rom, lengthPc + 2);
            int reg19 = word(rom, lengthPc + 4);
            if ((reg20 & 0xFF00) != 0x9400 || (reg19 & 0xFF00) != 0x9300) {
                throw new IllegalArgumentException(String.format("no DMA length at %06X", lengthPc));
            }
            int words = (reg20 & 0xFF) << 8 | (reg19 & 0xFF);
            int high = word(rom, commandHighPc + 2);
            int low = word(rom, commandLowPc + 2);
            if ((low & 0x80) == 0) {
                throw new IllegalArgumentException(String.format("command at %06X does not start DMA", commandHighPc));
            }
            int code = (high >>> 14) | ((low >>> 2) & 0x3C);
            int address = (high & 0x3FFF) | ((low & 3) << 14);
            return new Transfer(words == 0 ? 0x10000 : words, Target.fromCode(code), address);
        }

        private static void require(byte[] rom, int pc, int opcode) {
            if (word(rom, pc) != opcode) {
                throw new IllegalArgumentException(String.format(
                        "ROM opcode at %06X is %04X, not the pinned %04X", pc, word(rom, pc), opcode));
            }
        }

        private static int word(byte[] rom, int pc) {
            return (rom[pc] & 0xFF) << 8 | (rom[pc + 1] & 0xFF);
        }
    }
}
