package com.openggf.game.sonic2.timing;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.Sonic2PlcLoader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;

/**
 * Cost-only interpretation of the retail Sonic 2 level-entry path from the
 * final fade V-int through {@code Level_PlayBgm}.
 *
 * <p>The title-card streams come from the user's ROM and are interpreted one
 * real {@code NemDec} branch at a time; output art is never materialized here.
 * Instruction clocks use the MC68000 standard-access tables in Motorola's
 * <a href="https://www.nxp.com/docs/en/reference-manual/MC68000UM.pdf">MC68000
 * User's Manual, section 8</a>. The control flow is the pinned disassembly's
 * {@code NemDec}/{@code NemDecPrepare} at {@code s2.asm:1806-2037}, called by
 * {@code LoadTitleCard} at {@code s2.asm:29207-29250}.</p>
 *
 * <p>VDP bounds use the H40 access/DMA rates in Sega's
 * <a href="https://segaretro.org/images/9/95/GenesisSoftwareManual.pdf">Genesis
 * Software Manual</a>. The upper Z80 arbitration allowance is one longest
 * standard instruction (23 T-states), from Zilog's
 * <a href="https://www.zilog.com/docs/z80/um0080.pdf">Z80 CPU User Manual</a>;
 * it assumes no externally extended WAIT. Board-specific wait/arbitration is
 * therefore an explicit sub-frame bound, not a runtime-measured constant.</p>
 */
public final class Sonic2PreBgmTimingModel {
    private static final int TITLE_LETTER_OFFSET_TABLE = 0x15820;
    private static final int TITLE_LETTER_LISTS = 0x15832;
    private static final int PALETTE_POINTERS = 0x2782;

    private static final int NTSC_MASTER_CLOCKS_PER_SCANLINE = 3_420;
    private static final int NTSC_SCANLINES = 262;
    private static final int M68K_MASTER_DIVISOR = 7;
    private static final int Z80_MASTER_DIVISOR = 15;

    // Genesis Software Manual, VDP Access Timing / DMA tables, H40 column.
    private static final int H40_ACTIVE_68K_TO_VDP_BYTES_PER_LINE = 18;
    private static final int H40_VBLANK_68K_TO_VDP_BYTES_PER_LINE = 205;
    private static final int H40_ACTIVE_FILL_BYTES_PER_LINE = 17;
    private static final int H40_VBLANK_FILL_BYTES_PER_LINE = 204;
    private static final int NTSC_ACTIVE_SCANLINES = 226;
    private static final int VDP_FIFO_BYTES = 8; // four 16-bit FIFO entries

    private Sonic2PreBgmTimingModel() {
    }

    public static Evidence analyze(Rom rom, int zoneId, int actId,
                                   OptionalInt initialLifePlc) throws IOException {
        int zoneCount = (Sonic2Constants.ART_LOAD_CUES_ADDR - Sonic2Constants.LEVEL_DATA_DIR)
                / Sonic2Constants.LEVEL_DATA_DIR_ENTRY_SIZE;
        if (zoneId < 0 || zoneId >= zoneCount) {
            throw new IllegalArgumentException("Sonic 2 zone id out of range: " + zoneId);
        }
        NemesisWork vram = analyzeNemesis(
                new RomCursor(rom, Sonic2Constants.ART_NEM_TITLE_CARD_ADDR),
                Destination.VRAM);
        NemesisWork ram = analyzeNemesis(
                new RomCursor(rom, Sonic2Constants.ART_NEM_TITLE_CARD2_ADDR),
                Destination.RAM);
        LevelEntryWork level = analyzeLevelEntry(
                rom, zoneId, actId, initialLifePlc);
        CpuWork cpu = cpuWork(vram, ram, level, initialLifePlc.isPresent());
        DeviceBounds devices = deviceBounds(level);
        double cpuRows = m68kClocksToRows(cpu.totalClocks());
        double lowerRows = cpuRows + devices.minimumRows();
        double upperRows = cpuRows + devices.maximumRows();
        int lowerBucket = (int) Math.floor(lowerRows);
        int upperBucket = (int) Math.floor(upperRows);
        if (lowerBucket != upperBucket) {
            throw new IllegalStateException("S2 Level_PlayBgm hardware bounds cross a row bucket: "
                    + lowerRows + ".." + upperRows);
        }
        return new Evidence(vram, ram, level, cpu, devices,
                lowerRows, upperRows, lowerBucket);
    }

    private static LevelEntryWork analyzeLevelEntry(
            Rom rom, int zoneId, int actId,
            OptionalInt initialLifePlc) throws IOException {
        int[] zonePlcs = Sonic2PlcLoader.getZonePlcIds(rom, zoneId);
        int primaryEntries = zonePlcs[0] == 0
                ? 0 : Sonic2PlcLoader.parsePlc(rom, zonePlcs[0]).entries().size();
        int standardEntries = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_STD2)
                .entries().size();
        int lifeEntries = initialLifePlc.isPresent()
                ? Sonic2PlcLoader.parsePlc(rom, initialLifePlc.getAsInt()).entries().size()
                : 0;

        int letterOffset = Byte.toUnsignedInt(rom.readByte(TITLE_LETTER_OFFSET_TABLE + zoneId));
        int letterCursor = TITLE_LETTER_LISTS + letterOffset;
        int titleLongWrites = 0;
        while ((rom.readByte(letterCursor) & 0x80) == 0) {
            titleLongWrites += Byte.toUnsignedInt(rom.readByte(letterCursor + 1)) * 8;
            letterCursor += 2;
        }

        WaterPath waterPath;
        int underwaterPaletteLongwords;
        if (zoneId == Sonic2ZoneConstants.ROM_ZONE_CPZ && actId == 1) {
            waterPath = WaterPath.CPZ;
            underwaterPaletteLongwords = paletteLongwords(rom, 0x16);
        } else if (zoneId == Sonic2ZoneConstants.ROM_ZONE_ARZ) {
            waterPath = WaterPath.ARZ;
            underwaterPaletteLongwords = paletteLongwords(rom, 0x17);
        } else if (zoneId == Sonic2ZoneConstants.ROM_ZONE_HPZ) {
            waterPath = WaterPath.HPZ;
            underwaterPaletteLongwords = paletteLongwords(rom, 0x15);
        } else {
            waterPath = WaterPath.NONE;
            underwaterPaletteLongwords = 0;
        }

        // ClearScreen (s2.asm:1458-1484) takes the shipped fixBugs=0 branch:
        // the two clearRAM calls each include the erroneous extra longword.
        // Level_ClrRam (s2.asm:4806-4817) likewise takes its retail over-clear.
        return new LevelEntryWork(List.of(0x40, 0x1000, 0x1000),
                161, 257, titleLongWrites,
                (zonePlcs[0] == 0 ? 0 : 1) + 1 + (initialLifePlc.isPresent() ? 1 : 0),
                primaryEntries + standardEntries + lifeEntries,
                2_730, 640, waterPath, underwaterPaletteLongwords);
    }

    private static int paletteLongwords(Rom rom, int paletteId) throws IOException {
        // PalPointers' final word is bytesToLcnt(size), i.e. longword count - 1
        // (s2.asm:3830-3876). Read it from the user ROM so modified palette
        // lengths change the loop cost rather than sharing a retail constant.
        return rom.read16BitAddr(PALETTE_POINTERS + paletteId * 8 + 6) + 1;
    }

    private static CpuWork cpuWork(NemesisWork vram, NemesisWork ram,
                                   LevelEntryWork level,
                                   boolean lifePlc) {
        // Named source blocks, all in standard MC68000 clocks. The variable
        // LoadPLC term follows s2.asm:2060-2088: 176 clocks per call plus 42
        // per copied six-byte entry. 308 is Level's one-player selection path
        // around those calls (s2.asm:4772-4804); selecting the life PLC walks
        // the additional Player_mode branch before the third call.
        int initialPlcs = 308
                + (level.plcCalls() * 176)
                + (level.plcEntries() * 42);

        // s2.asm:4772-4911 after the LoadPLC bodies. Each source block remains
        // visible here instead of collapsing the path into a fitted subtotal:
        // Level_SetPlayerMode / third-PLC branch, six fixBugs=0 clearRAM loops,
        // three no-water guards, VDP setup, 64-byte PalLoad_Now and PlayMusic.
        int playerModeAndThirdPlcBranch = 148 + (lifePlc ? 38 : 0);
        int laterClearRam = 6 * 24 + level.laterClearLongwords() * 22;
        int waterGuards = 16 + 8 + 16 + 8 + 16 + 10;
        int vdpSetup = 246;
        int paletteLoad = 604;
        int bgmSelectionAndPlayMusic = 142;

        int waterConditional = waterConditionalClocks(level);
        return new CpuWork(
                finalVintClocks(),
                finalFadeTailClocks(),
                levelEntryToClearScreenClocks(),
                clearScreenClocks(level),
                vram.totalClocks() + ram.totalClocks()
                        + loadTitleCardCallerClocks(level),
                pendingLagVintClocks(),
                initialPlcs,
                playerModeAndThirdPlcBranch,
                laterClearRam,
                waterGuards,
                waterConditional,
                vdpSetup,
                paletteLoad,
                bgmSelectionAndPlayMusic);
    }

    private static int waterConditionalClocks(LevelEntryWork level) {
        if (level.waterPath() == WaterPath.NONE) {
            return 0;
        }

        // Relative to the no-water path, enumerate all three conditional
        // regions in Level (s2.asm:4819-4829,4859-4877,4882-4896).
        int noWaterEntryGuards = 16 + 8 + 16 + 8 + 16 + 10;
        int waterEntry = switch (level.waterPath()) {
            case CPZ -> 16 + 10 + 16 + 16;
            case ARZ -> 16 + 8 + 16 + 10 + 16 + 16;
            case HPZ -> 16 + 8 + 16 + 8 + 16 + 8 + 16 + 16;
            case NONE -> throw new AssertionError();
        };

        int noWaterVdpExit = 12 + 10;
        int waterVdpExit = 12 + 8 + 12 + 4 + 12 + 8 + 8 + 18 + 8
                + 12 + 14 + 3 * 12 + 3 * 16;

        int noWaterPaletteTail = 604;
        int primaryPaletteAndWaterTest = 582 + 12 + 8;
        int paletteSelection = switch (level.waterPath()) {
            case HPZ -> 4 + 16 + 10;
            case CPZ -> 4 + 16 + 8 + 4 + 16 + 10;
            case ARZ -> 4 + 16 + 8 + 4 + 16 + 8 + 4;
            case NONE -> throw new AssertionError();
        };
        // PalLoad_Water_Now at s2.asm:3805-3818: fixed setup/return, one
        // 20-clock memory-to-memory MOVE.L per ROM-derived longword, and DBF.
        int paletteLongwords = level.underwaterPaletteLongwords();
        int waterPaletteRoutine = 90 + paletteLongwords * 20
                + (paletteLongwords - 1) * 10 + 14;
        int waterPaletteTail = primaryPaletteAndWaterTest + paletteSelection
                + 18 + waterPaletteRoutine + 12 + 10;

        return (waterEntry - noWaterEntryGuards)
                + (waterVdpExit - noWaterVdpExit)
                + (waterPaletteTail - noWaterPaletteTail);
    }

    private static int finalVintClocks() {
        // V_Int/VintRet + Vint_Fade at s2.asm:486-510,1068-1071;
        // Do_ControllerPal/ReadJoypads and the three DMA command setups at
        // s2.asm:1153-1180,1361-1381; empty ProcessDPLC at s2.asm:2202-2208.
        int vIntEnvelopeControllerAndDmaSetups = 1_620;
        int callDoControllerPal = 18;
        int fadeHintWriteAndEmptyProcessDplc = 16 + 12 + 12 + 10 + 16;
        return vIntEnvelopeControllerAndDmaSetups
                + callDoControllerPal + fadeHintWriteAndEmptyProcessDplc;
    }

    private static int finalFadeTailClocks() {
        // The 22nd Pal_FadeToBlack iteration at s2.asm:3368-3383: WaitForVint
        // returns, both 64-colour black palettes take UpdateColour's beq arm,
        // RunPLC_RAM observes an empty queue, then DBF exits.
        int waitForVintReturn = 36;
        int twoBlackPaletteWalks = 8_544;
        int emptyRunPlc = 56;
        int fadeLoopExitAndReturn = 50;
        return waitForVintReturn + twoBlackPaletteWalks
                + emptyRunPlc + fadeLoopExitAndReturn;
    }

    private static int levelEntryToClearScreenClocks() {
        // Level at s2.asm:4766-4769: demo guard, SR write and BSR ClearScreen.
        return 16 + 16 + 18;
    }

    private static int clearScreenClocks(LevelEntryWork level) {
        // ClearScreen at s2.asm:1458-1484. The 478-clock straight-line body
        // includes stop/start-Z80, three DMA-fill command/poll sequences, the
        // one-player branch and two long clears' setup/return. Each fixBugs=0
        // clearRAM long iteration is CLR.L (12) + DBF taken (10); each loop's
        // terminal DBF costs four clocks more than a taken iteration.
        int clearedLongwords = level.spriteTableClearLongwords()
                + level.horizontalScrollClearLongwords();
        return 478 + clearedLongwords * 22 + 2 * 24;
    }

    private static long loadTitleCardCallerClocks(LevelEntryWork level) {
        // LoadTitleCard at s2.asm:29207-29250: 940 straight-line/letter-list
        // clocks plus MOVE.L/DBF's 30-clock taken path for each ROM-derived
        // title longword.
        return 940L + 30L * level.titleVdpLongWrites();
    }

    private static int pendingLagVintClocks() {
        // V_Int -> Vint0_noWater -> VintRet at s2.asm:486-510,586-642. The
        // 640-byte transfer stall is separately bounded by deviceBounds; this
        // subtotal is only the CPU branch and DMA-register setup work.
        int vIntEnvelopeAndReturn = 518;
        int noWaterVscrollAndGuards = 190;
        int spriteDmaSetupAndSoundHandoff = 380;
        return vIntEnvelopeAndReturn + noWaterVscrollAndGuards
                + spriteDmaSetupAndSoundHandoff;
    }

    private static DeviceBounds deviceBounds(LevelEntryWork level) {
        // The Vint_Level immediately before the final fade anchor uploads the
        // 128-byte palette, 640-byte sprite table and 896-byte H-scroll table
        // (s2.asm:1005-1045). At H40 V-blank throughput the transfers consume
        // their byte length/rate; two extra scanlines bound command alignment.
        int priorVintBytes = 128 + 640 + 896;
        double priorVintMinimum = scanlinesToRows(
                (double) priorVintBytes / H40_VBLANK_68K_TO_VDP_BYTES_PER_LINE);
        double priorVintMaximum = scanlinesToRows(
                (double) priorVintBytes / H40_VBLANK_68K_TO_VDP_BYTES_PER_LINE + 2);

        // ClearScreen's three fills (s2.asm:1458-1484) begin after the final
        // fade V-blank. They consume the 226 active H40 lines, then finish at
        // the following V-blank rate. One alignment line per command bounds
        // the three independently programmed fills.
        int fillBytes = level.clearScreenDmaFillBytes().stream()
                .mapToInt(Integer::intValue).sum();
        double clearScanlines = NTSC_ACTIVE_SCANLINES
                + (double) (fillBytes
                - NTSC_ACTIVE_SCANLINES * H40_ACTIVE_FILL_BYTES_PER_LINE)
                / H40_VBLANK_FILL_BYTES_PER_LINE;
        double clearMinimum = scanlinesToRows(clearScanlines);
        double clearMaximum = scanlinesToRows(
                clearScanlines + level.clearScreenDmaFillBytes().size());

        // The fixBugs=0 Vint0_noWater path uploads the 640-byte sprite table
        // (s2.asm:586-642). Its phase is not distinguishable from level state,
        // so blank and active H40 rates form the conditional bounds.
        double pendingMinimum = scanlinesToRows((double) level.lagVintSpriteDmaBytes()
                / H40_VBLANK_68K_TO_VDP_BYTES_PER_LINE);
        double pendingMaximum = scanlinesToRows((double) level.lagVintSpriteDmaBytes()
                / H40_ACTIVE_68K_TO_VDP_BYTES_PER_LINE + 1);

        // LoadTitleCard's tight move.l/dbf loop is s2.asm:29231-29247. Each
        // longword supplies four bytes while its standard CPU path spends 30
        // clocks. The maximum is the active-display drain time remaining after
        // both the four-word FIFO and that CPU execution overlap; blank overlap
        // makes the lower bound zero.
        int titleBytes = level.titleVdpLongWrites() * Integer.BYTES;
        double titleTransferScanlines = (double) Math.max(0, titleBytes - VDP_FIFO_BYTES)
                / H40_ACTIVE_68K_TO_VDP_BYTES_PER_LINE;
        double titleCpuScanlines = (double) level.titleVdpLongWrites() * 30
                * M68K_MASTER_DIVISOR / NTSC_MASTER_CLOCKS_PER_SCANLINE;
        double titleFifoMaximum = scanlinesToRows(
                Math.max(0.0, titleTransferScanlines - titleCpuScanlines));

        // With no externally stretched WAIT, the bus request can wait at most
        // the remainder of one 23-T-state Z80 instruction.
        double z80Maximum = (double) 23 * Z80_MASTER_DIVISOR
                / (NTSC_MASTER_CLOCKS_PER_SCANLINE * NTSC_SCANLINES);
        return new DeviceBounds(priorVintMinimum, priorVintMaximum,
                clearMinimum, clearMaximum,
                pendingMinimum, pendingMaximum,
                titleFifoMaximum, z80Maximum);
    }

    private static double m68kClocksToRows(long clocks) {
        // NTSC frame = 3420 master clocks/line * 262 lines; 68000 is master/7.
        return (double) clocks * M68K_MASTER_DIVISOR
                / (NTSC_MASTER_CLOCKS_PER_SCANLINE * NTSC_SCANLINES);
    }

    private static double scanlinesToRows(double scanlines) {
        return scanlines / NTSC_SCANLINES;
    }

    private static NemesisWork analyzeNemesis(RomCursor source,
                                               Destination destination) throws IOException {
        int header = source.readWord();
        boolean xor = (header & 0x8000) != 0;
        int rows = (header & 0x7FFF) * 8;
        int pixelsRemaining = rows * 8;
        int[] codeTable = new int[256];

        long prepareClocks = 8; // NemDecPrepare's initial move.b (a0)+,d0
        int paletteSelectors = 0;
        int definitions = 0;
        int fullCodeStores = 0;
        int shortAliasStores = 0;
        int selector = source.readByte();
        while (true) {
            prepareClocks += 8; // cmpi.b #$ff,d0
            if (selector == 0xFF) {
                prepareClocks += 8 + 16; // bne not taken; rts
                break;
            }
            if ((selector & 0x80) == 0) {
                throw new IOException("Nemesis table does not start with a palette selector");
            }
            prepareClocks += 10 + 4; // bne taken; move.w d0,d7
            paletteSelectors++;
            int palette = selector & 0x0F;
            while (true) {
                int descriptor = source.readByte();
                prepareClocks += 8 + 8; // move.b; cmpi.b #$80
                if ((descriptor & 0x80) != 0) {
                    prepareClocks += 10; // bhs to description-end check
                    selector = descriptor;
                    break;
                }
                prepareClocks += 8; // bhs not taken
                definitions++;
                int repeat = (descriptor >>> 4) & 7;
                int length = descriptor & 0x0F;
                if (length == 0 || length > 8) {
                    throw new IOException("Invalid Nemesis code length " + length);
                }
                prepareClocks += 4 + 8 + 8 + 4 + 8 + 4 + 22 + 4 + 4 + 4;
                int suffixBits = 8 - length;
                int code = source.readByte();
                int entry = (length << 8) | (repeat << 4) | palette;
                if (suffixBits == 0) {
                    prepareClocks += 8 + 8 + 4 + 14 + 10;
                    codeTable[code] = entry;
                    fullCodeStores++;
                } else {
                    int shiftClocks = 6 + 2 * suffixBits;
                    prepareClocks += 10 + 8 + shiftClocks + 4 + 4
                            + shiftClocks + 4;
                    int aliases = 1 << suffixBits;
                    int first = (code << suffixBits) & 0xFF;
                    for (int alias = 0; alias < aliases; alias++) {
                        prepareClocks += 14 + 4 + (alias + 1 < aliases ? 10 : 14);
                        codeTable[first + alias] = entry;
                        shortAliasStores++;
                    }
                    prepareClocks += 10;
                }
            }
        }

        int d5 = (source.readByte() << 8) | source.readByte();
        int d6 = 16;
        long decodeClocks = 0;
        long pixelClocks = 0;
        long writerClocks = 0;
        int tableSymbols = 0;
        int inlineSymbols = 0;
        int tableRefills = 0;
        int inlinePrefixRefills = 0;
        int inlineDataRefills = 0;

        while (pixelsRemaining > 0) {
            int d7 = d6 - 8;
            decodeClocks += 4 + 4 + 4 + (6 + 2L * d7) + 8;
            int prefix = (d5 >>> d7) & 0xFF;
            int repeat;
            if (prefix >= 0xFC) {
                decodeClocks += 10; // bhs NemDec_InlineData
                inlineSymbols++;
                d6 -= 6;
                decodeClocks += 4 + 8;
                if (d6 >= 9) {
                    decodeClocks += 10;
                } else {
                    decodeClocks += 8 + 4 + 22 + 8;
                    d6 += 8;
                    d5 = refill(d5, source.readByte());
                    inlinePrefixRefills++;
                }
                d6 -= 7;
                decodeClocks += 4 + 4 + (6 + 2L * d6) + 4 + 8 + 8 + 8;
                int inline = (d5 >>> d6) & 0xFFFF;
                repeat = ((inline & 0x70) >>> 4) + 1;
                if (d6 >= 9) {
                    decodeClocks += 10;
                } else {
                    decodeClocks += 8 + 4 + 22 + 8 + 10;
                    d6 += 8;
                    d5 = refill(d5, source.readByte());
                    inlineDataRefills++;
                }
                decodeClocks += 14; // lsr.w #4,d0
            } else {
                decodeClocks += 8; // bhs not taken
                tableSymbols++;
                decodeClocks += 8 + 4 + 14 + 4 + 4 + 8;
                int entry = codeTable[prefix];
                int length = (entry >>> 8) & 0xFF;
                if (length == 0) {
                    throw new IOException("Missing Nemesis table entry for prefix 0x"
                            + Integer.toHexString(prefix));
                }
                d6 -= length;
                if (d6 >= 9) {
                    decodeClocks += 10;
                } else {
                    decodeClocks += 8 + 4 + 22 + 8;
                    d6 += 8;
                    d5 = refill(d5, source.readByte());
                    tableRefills++;
                }
                decodeClocks += 14 + 4 + 8 + 8 + 14;
                repeat = ((entry >>> 4) & 0x0F) + 1;
            }

            if (repeat > pixelsRemaining) {
                throw new IOException("Nemesis run exceeds declared output size");
            }
            for (int pixel = 0; pixel < repeat; pixel++) {
                boolean rowEnd = ((rows * 8 - pixelsRemaining + pixel + 1) & 7) == 0;
                pixelClocks += 16 + 4 + 4 + (rowEnd ? 8 : 10);
                boolean finalPixel = pixelsRemaining == repeat && pixel + 1 == repeat;
                if (rowEnd) {
                    writerClocks += 8;
                    if (xor) {
                        writerClocks += 8;
                    }
                    writerClocks += 12 + 8 + 4;
                    if (finalPixel) {
                        writerClocks += 8 + 16;
                    } else {
                        writerClocks += 10 + 4 + 4;
                    }
                }
                if (!finalPixel) {
                    pixelClocks += pixel + 1 < repeat ? 10 : 14 + 10;
                }
            }
            pixelsRemaining -= repeat;
        }

        long entryClocks = nemesisEntryAndExitClocks(destination, xor);
        return new NemesisWork(rows, rows * 8, xor,
                paletteSelectors, definitions, fullCodeStores, shortAliasStores,
                tableSymbols, inlineSymbols,
                tableRefills, inlinePrefixRefills, inlineDataRefills,
                entryClocks, prepareClocks, decodeClocks, pixelClocks, writerClocks);
    }

    private static int refill(int d5, int nextByte) {
        return ((d5 << 8) & 0xFF00) | nextByte;
    }

    private static long nemesisEntryAndExitClocks(Destination destination,
                                                   boolean xor) {
        // NemDec/NemDecToRAM/NemDecMain at s2.asm:1806-1847. These are the
        // literal Motorola table costs of the executed wrapper and fixed main
        // path; the table/decode/write loops are accounted branch-by-branch.
        int wrapper = 112 + 12
                + (destination == Destination.VRAM ? 12 + 10 : 0);
        int main = 8 + 8 + 8 + (xor ? 8 + 12 : 10)
                + 10 + 4 + 4 + 4 + 4 + 18
                + 8 + 22 + 8 + 8 + 18 + 116 + 16;
        return wrapper + main;
    }

    private enum Destination { VRAM, RAM }

    public record Evidence(NemesisWork titleCardVram, NemesisWork titleCardRam,
                           LevelEntryWork levelEntry, CpuWork cpu,
                           DeviceBounds devices, double lowerRows,
                           double upperRows, int terminalRowBucket) {
    }

    public record NemesisWork(int outputRows, int pixels, boolean xor,
                              int paletteSelectors, int codeDefinitions,
                              int fullCodeStores, int shortAliasStores,
                              int tableSymbols, int inlineSymbols,
                              int tableRefills, int inlinePrefixRefills,
                              int inlineDataRefills,
                              long entryClocks, long prepareClocks,
                              long decodeClocks, long pixelClocks,
                              long writerClocks) {
        public long totalClocks() {
            return entryClocks + prepareClocks + decodeClocks
                    + pixelClocks + writerClocks;
        }
    }

    public record LevelEntryWork(List<Integer> clearScreenDmaFillBytes,
                                 int spriteTableClearLongwords,
                                 int horizontalScrollClearLongwords,
                                 int titleVdpLongWrites,
                                 int plcCalls,
                                 int plcEntries,
                                 int laterClearLongwords,
                                 int lagVintSpriteDmaBytes,
                                 WaterPath waterPath,
                                 int underwaterPaletteLongwords) {
        public LevelEntryWork {
            clearScreenDmaFillBytes = List.copyOf(clearScreenDmaFillBytes);
        }
    }

    public enum WaterPath { NONE, CPZ, ARZ, HPZ }

    public record CpuWork(int finalVint,
                          int finalFadeReturnAndPalette,
                          int levelEntryToClearScreen,
                          int clearScreen,
                          long loadTitleCard,
                          int pendingLagVint,
                          int initialPlcs,
                          int playerModeAndThirdPlcBranch,
                          int laterClearRam,
                          int waterGuards,
                          int waterConditional,
                          int vdpSetup,
                          int paletteLoad,
                          int bgmSelectionAndPlayMusic) {
        public long totalClocks() {
            return finalVint + finalFadeReturnAndPalette + levelEntryToClearScreen
                    + clearScreen + loadTitleCard + pendingLagVint + initialPlcs
                    + playerModeAndThirdPlcBranch + laterClearRam + waterGuards
                    + waterConditional + vdpSetup + paletteLoad
                    + bgmSelectionAndPlayMusic;
        }
    }

    public record DeviceBounds(double priorVintMinimumRows,
                               double priorVintMaximumRows,
                               double clearScreenMinimumRows,
                               double clearScreenMaximumRows,
                               double pendingSpriteMinimumRows,
                               double pendingSpriteMaximumRows,
                               double titleFifoMaximumRows,
                               double z80ArbitrationMaximumRows) {
        public double minimumRows() {
            return priorVintMinimumRows + clearScreenMinimumRows
                    + pendingSpriteMinimumRows;
        }

        public double maximumRows() {
            return priorVintMaximumRows + clearScreenMaximumRows
                    + pendingSpriteMaximumRows + titleFifoMaximumRows
                    + z80ArbitrationMaximumRows;
        }
    }

    private static final class RomCursor {
        private final Rom rom;
        private int offset;

        private RomCursor(Rom rom, int offset) {
            this.rom = rom;
            this.offset = offset;
        }

        private int readByte() throws IOException {
            return Byte.toUnsignedInt(rom.readByte(offset++));
        }

        private int readWord() throws IOException {
            int value = rom.read16BitAddr(offset);
            offset += 2;
            return value;
        }
    }
}
