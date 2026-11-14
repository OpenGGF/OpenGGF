package com.openggf.game.soniccd.timeattack;

import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The Time Attack menu (ATTACK.MMD, {@code Time Attack/Main.asm}) as program logic over the picture it
 * describes: a 64 KiB VRAM (tiles, the two 64x32 planes at $C000 and $E000, the scroll table at $B000), a
 * 64-colour CRAM and plane B's vertical scroll. Each native routine is a method of the same name; the main
 * code runs as straight-line Java on a {@link CdProgramFiber} and waits for V-blank where native sets
 * {@code $FF3730} and calls {@code TimeAttack_WaitVdpTransfer} (Time Attack/Main.asm:2378-2382). VDP command constants
 * are translated to VRAM addresses here (the command is quoted in each comment); no VDP port is modelled.
 *
 * <p>Line numbers are {@code Time Attack/Main.asm} at the pinned scddisasm unless another file is named.
 * Not modelled: native lag frames (the Nemesis decode of a zone picture spans several native frames), the
 * Z80 bus requests, and the sub-CPU handshakes, which become direct calls to the backup-RAM owner.
 */
public final class CdTimeAttackProgram {
    /** What the save commands reach: IPX's {@code saveDisabled} choice is the backup RAM's (Time Attack/Main.asm:2309-2335). */
    public interface Saves {
        CdSaveRecord read();
        void write(CdSaveRecord record);
    }

    /** IPX variables the menu reads and writes (Main CPU Variables.inc:24-37; lives $FF1508). */
    public interface Host {
        int timeAttackLevel();
        int timeAttackTime();
        int lives();
        int timeAttackUnlock();
        int titleFlags();
        void setTitleFlags(int flags);
    }

    /** Typed audio requests by ID; the audio lane plays them. */
    public enum AudioRequest {
        /** SPCmd_LoadTimeAttack plays the D.A. Garden track on USA (SPX.asm:1349-1356). */
        DA_GARDEN_MUSIC,
        /** {@code SCMD_FADECDA} from EnterSelectedStage (Time Attack/Main.asm:985-987). */
        FADE_OUT_CDDA
    }

    private static final int PLANE_A = 0xC000, PLANE_B = 0xE000, HSCROLL = 0xB000;
    private static final int ZONE_VIEW = 0xFF70;
    private static final int PICTURE_BYTES = 0x120 * 8 * 4;            // PrepareSelectionGraphicsPushTransferLength          // hscroll of both planes in the zone list

    private final CdTimeAttackAssets assets;
    private final Saves saves;
    private final Host host;
    private final CdProgramFiber fiber;
    private final byte[] image;                           // the program image at $FF2000 with its buffers
    private final int imageBase;

    // The picture.
    final byte[] vram = new byte[0x10000];
    private final BitSet dirtyTiles = new BitSet(0x800);
    final int[] cram = new int[64];
    private int vscrollB;
    private boolean display;

    // Program variables ($FF34xx, $FF37xx, $FFAAxx).
    private int transfer;                                 // $FF3730
    private int animCounter;                              // $FF3732
    private int held, pressed;                            // $FF3734, $FF3735
    private int frameCounter;                             // $FFAA5A
    private int hscrollA, hscrollB;                       // $FF3468, $FF346A
    private int slideStep, slideOffset;                   // $FF346C, $FF346E
    private int periodScroll, periodStep;                 // $FF3470, $FF3472
    private int row;                                      // $FF3474: zone or special stage, -1 = EXIT
    private int lastRow;                                  // $FF3476
    private int act;                                      // $FF3478
    private int headerMode;                               // $FF347A: 0 EXIT, 1 NEXT, 2 GO
    private int rank;                                     // $FF347C
    private boolean entering;                             // $FF347E
    private int recordRow = -1, recordRank = -1;          // $FF3450, the inserted record
    private int total;                                    // $FF3464
    private final byte[] shownInitials = new byte[4];     // $FF3460
    private CdSaveRecord record;                          // Word RAM $200000

    private int pad;
    private int exitLevel = -1;
    private final List<AudioRequest> audio = new ArrayList<>();

    public CdTimeAttackProgram(CdTimeAttackAssets assets, Saves saves, Host host) {
        this.assets = assets;
        this.saves = saves;
        this.host = host;
        this.image = assets.image();
        this.imageBase = assets.origin();
        this.fiber = new CdProgramFiber(this::start);
        audio.add(AudioRequest.DA_GARDEN_MUSIC);
    }

    /**
     * One frame: the V-blank work native queued, then the main code until it waits again. {@code pad} is
     * ReadController1's byte (Time Attack/Main.asm:2840-2866): bit 7 Start, 6 A, 5 C, 4 B, 3 Right, 2 Left, 1 Down, 0 Up.
     */
    public void frame(int pad) {
        this.pad = pad & 0xFF;
        fiber.step();
    }

    /** The menu has returned {@code d0} (EnterSelectedStage, Time Attack/Main.asm:985-1043). */
    public boolean done() { return fiber.finished() && exitLevel >= 0; }

    /** {@code timeAttackLevel}: 0 exits to the title, 1-21 a zone act, 22-28 a special stage. */
    public int exitLevel() { return exitLevel; }

    public List<AudioRequest> drainAudio() {
        List<AudioRequest> out = List.copyOf(audio);
        audio.clear();
        return out;
    }

    public void close() { fiber.close(); }

    // ---------------------------------------------------------------- main code

    private void start() {
        // Start (Time Attack/Main.asm:19-70): clear the work area $FFAA00-$FFD11F ($9C8+1 longs) and the palette buffers.
        for (int at = 0xFFAA00; at < 0xFFAA00 + 4 * 0x9C8 + 4; at += 2) putImageWord(at, 0);
        waitTransfer(6);
        // VDP registers $FF5238 (Time Attack/Main.asm:74-78): planes A $C000 and B $E000, scroll table $B000, H40, display off.
        java.util.Arrays.fill(vram, (byte) 0);                        // Time Attack/Main.asm:80-98
        dirtyTiles.set(0, 0x800);
        nemesisToVram(0xFF52CC, 0x0020);                              // $40200000
        nemesisToVram(0xFF63FC, 0x1120);                              // $51200000
        nemesisToVram(0xFF5CBA, 0x1A60);                              // $5A600000
        nemesisToVram(0xFF6708, 0x2020);                              // $60200000
        nemesisToVram(0xFF58C6, 0x2580);                              // $65800000
        nemesisToVram(0xFF5EE4, 0x2FA0);                              // $6FA00000
        nemesisToVram(0xFF68E0, 0x3D00);                              // $7D000000
        nemesisToVram(0xFF69BE, 0x3DE0);                              // $7DE00000
        nemesisToVram(0xFF702A, 0x5540);                              // $55400001
        nemesisToVram(0xFF74B8, 0x5720);                              // $57200001
        nemesisToVram(0xFF756A, 0x5980);                              // $59800001
        nemesisToVram(0xFF785E, 0x61A0);                              // REGION=USA (Time Attack/Main.asm:166-174), $61A00001
        for (int i = 0; i < 32; i++) vramByte(0x61C0 + i, 0xFF);      // $61C00001, eight longs of $FF (Time Attack/Main.asm:176-194)
        nemesisToVram(0x217FC4, 0x8C00);                              // $4C000002 (Time Attack/Main.asm:196-200)
        // DMA $0800 words from $FFE000 to plane A (Time Attack/Main.asm:204-216: $94089300, $96F09500, $977F, $4000/$83).
        for (int i = 0; i < 0x1000; i += 2) vramWord(PLANE_A + i, imageWord(0xFFE000 + i));
        // Picture map 7 to $FFCE60 with base $E460, then plane A $C1AA, 16 columns by 18 rows (Time Attack/Main.asm:220-242).
        int[] map = assets.enigma(0x210000 + signed16(assets.word(0x210000 + 0x0E)), 0xE460);
        putWords(0xFFCE60, map);
        copyRect(0xFFCE60, 0xC1AA, 16, 18);
        // StartRegionIndexValue (Time Attack/Main.asm:244-260): the last row the unlock reaches.
        int unlock = host.timeAttackUnlock() - 3;
        if (unlock < 0 || unlock > 0x12) unlock = 0;
        lastRow = unlock / 3;
        record = saves.read();                                        // StartSendInitialSubCpuCommand (262)
        rank = 0;                                                     // StartClearRecordRank (264)
        int level = host.timeAttackLevel();
        if (level < 0 || level > 0x1C) level = 0;                     // Time Attack/Main.asm:266-274
        if (level != 0 && host.lives() != 0) {                        // Time Attack/Main.asm:278-282
            int inserted = CdTimeAttackRecords.insert(record, level - 1, host.timeAttackTime(), record.defaultInitials());
            if (inserted != 0) {
                rank = inserted;
                recordRow = level - 1;
                recordRank = inserted - 1;
                saves.write(record);                                  // StartSendRecordUpdate (352)
            }
        }
        prepareTimeAttackRecords();                                   // Time Attack/Main.asm:354
        copyRect(0xFFD300, 0xC184, 16, 16);                           // $41840003 (Time Attack/Main.asm:356-368)
        copyRect(0xFFD500, 0xC1D0, 16, 16);                           // $41D00003 (Time Attack/Main.asm:370-382)
        for (int i = 0; i < 0x200; i++) putImageWord(0xFFD300 + 2 * i, imageWord(0xFFD300 + 2 * i) | 0x8000);   // 384-392
        int digits = assets.label("timeDigitTileMap");
        for (int i = 0; i < 0x4E; i++) putImageWord(digits + 2 * i, imageWord(digits + 2 * i) | 0x8000);        // 394-402
        if (level == 0) {                                             // Time Attack/Main.asm:404-418
            hscrollA = ZONE_VIEW;
            hscrollB = ZONE_VIEW;
            waitTransfer(2);
            display = true;                                           // move.w #$8174,$c00004
            fadePaletteIn();
            row = 0;                                                  // Time Attack/Main.asm:420-432
            act = 0;
            frameCounter = 0x10;
            waitTransfer(0x0E);
            run(Next.LIST);
            return;
        }
        if (level < 0x16) {                                           // StartConvertStageIndex (434-452)
            row = (level - 1) / 3;
            act = (level - 1) % 3;
            hscrollA = ZONE_VIEW;
            hscrollB = ZONE_VIEW;
        } else {                                                      // StartConvertPeriodIndex (454-458)
            row = level - 0x16;
            act = 0;
        }
        waitTransfer(2);                                              // StartSetStageTransferCount (460-462)
        entering = true;                                              // st.b $FF347E (464)
        prepareSelectionGraphics(row);                                // 466-468
        for (int i = 0; i < 8; i++) {                                 // 470-480: palette line 3 becomes the fade target
            putImageLong(0xFF52AC + 4 * i, imageLong(0xFFD100 + 4 * i));
            putImageLong(0xFFD100 + 4 * i, 0);
        }
        run(Next.PANEL);
    }

    /** Where the menu's code continues: the list loop, the panel (StartUploadSelectionScreen) or the end. */
    private enum Next { LIST, PANEL, DONE }

    private void run(Next next) {
        while (next != Next.DONE) {
            next = switch (next) {
                case LIST -> selectionLoop();
                case PANEL -> uploadSelectionScreen();
                case DONE -> Next.DONE;
            };
        }
    }

    /** StartSelectionLoop (Time Attack/Main.asm:488-612): the zone or special-stage list. */
    private Next selectionLoop() {
        while (true) {
            if (slideStep != 0) {                                     // StartAnimatePeriodTransition (614-646)
                if (hscrollA != 0 && hscrollA != ZONE_VIEW) {
                    advanceSlide();
                    continue;
                }
                slideStep = 0;
                slideOffset = 0;
                if (row >= 0) prepareSelectionGraphics(row);
                frameCounter = 0;
                continue;
            }
            drawSelectionHighlight(frameCounter & 0x10);               // 494-498
            if ((pressed & 0xF0) != 0) {                              // 500-528
                if (row >= 0) {
                    drawSelectionHighlight(1);
                    for (int i = 0; i < 8; i++) waitTransfer(0x10);
                    return Next.PANEL;
                }
                drawSelectionHighlight(0);
                enterSelectedStage();
                return Next.DONE;
            }
            if ((host.titleFlags() & 0x02) != 0) {                    // StartCheckRecordFlags (530-558)
                int button = 3, step = -8;                            // special stages: Right slides to the zones
                if (hscrollA != 0) { button = 2; step = 8; }          // zones: Left slides to the special stages
                if ((held & (1 << button)) != 0) {
                    slideStep = step;
                    slideOffset = step;
                    drawSelectionHighlight(1);
                    advanceSlide();
                    continue;
                }
            }
            int next = row;                                           // StartCheckStageNavigation (560-612)
            if ((held & 1) != 0) next--;
            if ((held & 2) != 0) next++;
            if (next < -1) next = lastRow;
            if (next > lastRow) next = -1;
            if (next != row) {
                drawSelectionHighlight(1);
                row = next;
                prepareSelectionGraphics(row);
                drawSelectionHighlight(0);
                waitTransfer(0x10);
                frameCounter = 0;
            }
            waitTransfer(0x10);
        }
    }

    /** StartAdvancePeriodTransition (Time Attack/Main.asm:622-632): both planes slide one step. */
    private void advanceSlide() {
        hscrollA = (hscrollA + slideStep) & 0xFFFF;
        hscrollB = (hscrollB + slideStep) & 0xFFFF;
        waitTransfer(4);
    }

    /** StartUploadSelectionScreen (Time Attack/Main.asm:648-772), then the act or special-stage panel's loop. */
    private Next uploadSelectionScreen() {
        drawSelectionHighlight(1);
        uploadSelectionTileBlock();
        if (hscrollA == 0) {
            copyRect(0xFFD700, 0xE024, 18, 32);                       // $60240003
            copyRect(0xFF749C + 4 * row, 0xE4BC, 1, 2);               // $64BC0003: the special stage's number
        } else {
            copyRect(0xFFDB80, 0xE02C, 18, 32);                       // $602C0003
            copyRect(0xFF7100 + row * 0x84, 0xE2B0, 11, 6);           // $62B00003: the zone's name
        }
        uploadSelectionTitleRow();
        renderSelectedTimeTable();
        waitTransfer(0x10);
        slideOffset = (hscrollA >> 8) != 0 ? 8 : -8;                  // tst.b $FF3468 (730-738)
        do {
            hscrollB = (hscrollB + slideOffset) & 0xFFFF;
            waitTransfer(0x10);
        } while (hscrollB != ZONE_VIEW && hscrollB != 0);
        slideOffset = 0;
        if (entering) {                                               // StartCheckPeriodAnimation (762-770)
            display = true;
            fadePaletteIn();
            frameCounter = 0x10;
        }
        return checkRecordInsertion();
    }

    /** StartCheckRecordInsertion (Time Attack/Main.asm:820-874) and the panel loop StartReadSelectionController (876-982). */
    private Next checkRecordInsertion() {
        if (entering) {
            entering = false;
            if (rank != 0) {
                waitTransfer(0x0E);
                animateRecordInsertion();
                waitFrames(0x0F);
                record = saves.read();
                enterRecordInitials();
                record.setInitials(recordRow, recordRank, record.defaultInitials());   // move.l $2002A0,(a0)
                saves.write(record);
                waitFrames(0x0F);
            }
            headerMode = 2;
        } else {
            headerMode = hscrollA == 0 ? 0 : 1;
        }
        frameCounter = 0x10;
        while (true) {
            if ((pressed & 0xF0) != 0) {
                if (headerMode == 0) {                                // StartResetPeriodSubIndex (774-818)
                    leavePanel();
                    return Next.LIST;
                }
                if (headerMode != 1) {
                    enterSelectedStage();
                    return Next.DONE;
                }
                selectNextTimePeriod();
            }
            uploadIndexedStageData(frameCounter & 0x10);              // 892-896
            int mode = headerMode;
            if ((held & 0x04) != 0) {
                mode--;
                if (hscrollA == 0) mode += 3;
            }
            if ((held & 0x08) != 0) {
                mode++;
                if (hscrollA == 0) mode -= 3;
            }
            mode = Math.max(0, Math.min(2, mode));
            if (mode != headerMode) {                                 // 936-960
                uploadIndexedStageData(1);
                headerMode = mode;
                frameCounter = 0;
                for (int i = 0; i < 16; i++) {
                    uploadIndexedStageData(frameCounter & 0x10);
                    waitTransfer(0x10);
                }
            }
            if (headerMode == 1) {                                    // 962-976
                if ((held & 1) != 0) selectPreviousTimePeriod();
                if ((held & 2) != 0) selectNextTimePeriod();
            }
            waitTransfer(0x10);
        }
    }

    /** StartResetPeriodSubIndex (Time Attack/Main.asm:774-818): the panel slides back to the list. */
    private void leavePanel() {
        act = 0;
        uploadIndexedStageData(0);
        slideOffset = (hscrollA >> 8) != 0 ? -8 : 8;
        do {
            hscrollB = (hscrollB + slideOffset) & 0xFFFF;
            waitTransfer(0x10);
        } while (hscrollB != 0 && hscrollB != ZONE_VIEW);
        slideOffset = 0;
        drawSelectionHighlight(1);
        frameCounter = 0x10;
    }

    /** EnterSelectedStage (Time Attack/Main.asm:985-1043): fade out and return the level. */
    private void enterSelectedStage() {
        audio.add(AudioRequest.FADE_OUT_CDDA);
        uploadIndexedStageData(0);
        fadePaletteOut();
        if (row < 0) {
            exitLevel = 0;
        } else if (hscrollA != 0) {
            exitLevel = row * 3 + act + 1;
        } else {
            exitLevel = 0x16 + row;
        }
    }

    private void selectPreviousTimePeriod() {                         // Time Attack/Main.asm:1049-1065
        if (hscrollA == 0) return;
        act = act - 1 < 0 ? 2 : act - 1;
        animateTimePeriodChange(-8);
    }

    private void selectNextTimePeriod() {                             // Time Attack/Main.asm:1069-1083
        if (hscrollA == 0) return;
        act = act + 1 > 2 ? 0 : act + 1;
        animateTimePeriodChange(8);
    }

    /** AnimateTimePeriodChange (Time Attack/Main.asm:1087-1111): plane B scrolls 32 frames; the panel redraws on the way. */
    private void animateTimePeriodChange(int step) {
        uploadIndexedStageData(0);
        periodStep = step;
        for (int d6 = 0x1F; d6 >= 0; d6--) {
            updateTimePeriodAnimationFrame(d6, step);
            periodScroll = (periodScroll + periodStep) & 0xFFFF;
            waitTransfer(8);
        }
        frameCounter = 0x10;
        periodStep = 0;
    }

    /** UpdateTimePeriodAnimationFrame (Time Attack/Main.asm:1115-1157) through its dispatch table at $FF2A6A. */
    private void updateTimePeriodAnimationFrame(int d6, int step) {
        if (((d6 + 1) & 1) != 0) return;
        int index = (d6 + 1) >> 1;
        if (step >= 0) index += 0x11;
        switch (index) {
            case 5, 32 -> uploadIndexedStageData(1);                  // $FF2AB0: moveq #1,d0; bsr UploadIndexedStageData
            case 8, 29 -> uploadSelectionTitleRow();                  // $FF2AB8
            case 11, 23 -> renderSelectedTimeTable();                 // $FF2B0E
            default -> { }                                            // $FF2AAE: rts
        }
    }

    // ---------------------------------------------------------------- records

    /** PrepareTimeAttackRecords (Time Attack/Main.asm:1389-1501): zone and special-stage totals and the unlocks. */
    private void prepareTimeAttackRecords() {
        int sum = 0;
        int buffer = 0xFFD54E;
        for (int zone = 0; zone < 7; zone++) {
            int current = 0;
            for (int a = 0; a < 3; a++) {
                current = CdTimeAttackRecords.add(current, CdTimeAttackRecords.normalize(record.time(zone * 3 + a, 0)));
            }
            renderTimeRecordToBuffer(current, buffer);
            buffer += 0x40;
            sum = CdTimeAttackRecords.add(sum, current);
        }
        renderTimeRecord(sum, 0xCADE);                                // $4ADE0003
        total = sum;
        if (host.timeAttackUnlock() == 0x15) {                        // Time Attack/Main.asm:1445-1469
            int flags = CdTimeAttackRecords.unlocks(host.titleFlags(), sum);
            host.setTitleFlags(flags);
            CdSaveVariables v = record.variables();                   // move.b $FF0F1D,$2002A8
            record.store(new CdSaveVariables(v.savedLevel(), v.goodFutures(), flags, v.timeAttackUnlock(), v.unknown(),
                    v.curSpecStage(), v.timeStones()));
        }
        sum = 0;
        buffer = 0xFFD342;
        for (int stage = 0; stage < 7; stage++) {
            int current = CdTimeAttackRecords.normalize(record.time(21 + stage, 0));
            renderTimeRecordToBuffer(current, buffer);
            buffer += 0x40;
            sum = CdTimeAttackRecords.add(sum, current);
        }
        renderTimeRecord(sum, 0xCA92);                                // $4A920003
    }

    /** RenderSelectedTimeTable (Time Attack/Main.asm:1201-1291): three times and their initials. */
    private void renderSelectedTimeTable() {
        int recordRowIndex;
        int times, initials;
        if (hscrollA == 0) {
            recordRowIndex = 21 + row + act;
            times = 0xE7B0;                                           // $67B00003
            initials = 0xE7C2;                                        // $67C20003
        } else {
            recordRowIndex = row * 3 + act;
            times = 0xE7B2;                                           // $67B20003
            initials = 0xE7C4;                                        // $67C40003
        }
        for (int r = 0; r < 3; r++) renderTimeRecord(CdTimeAttackRecords.normalize(record.time(recordRowIndex, r)), times + r * 0x180);
        for (int r = 0; r < 3; r++) uploadCompactTileBlock(record.initials(recordRowIndex, r), initials + r * 0x180);
    }

    /** AnimateRecordInsertion (Time Attack/Main.asm:1973-2037): the new rank's label blinks until a button. */
    private void animateRecordInsertion() {
        int shown = 0;
        int label = rank * 0x0C;
        int mask = 16 / (4 - rank) - 1;
        int at = (hscrollA == 0 ? 0xE7AA : 0xE7AC) + (rank - 1) * 0x180;   // $67AA0003 / $67AC0003
        frameCounter = 0;
        do {
            if ((frameCounter & mask) == 0) shown ^= label;
            uploadVdpTileBlock(at, shown);
            waitTransfer(0x10);
        } while (pressed == 0);
        uploadVdpTileBlock(at, label);
    }

    /** EnterRecordInitials (Time Attack/Main.asm:2041-2101) with HandleSelectionInput and UpdateInitialsEntryDisplay. */
    private void enterRecordInitials() {
        int cursor = 0, delay = 0;
        int at = (hscrollA == 0 ? 0xE7C2 : 0xE7C4) + (rank - 1) * 0x180;  // $67C20003 / $67C40003
        frameCounter = 0;
        copyInitials();
        while (true) {
            waitTransfer(0x10);
            if (delay != 0) {
                delay--;
            } else {
                int[] state = handleSelectionInput(cursor);
                if (state != null) { cursor = state[0]; delay = state[1]; }
            }
            if ((frameCounter & 0x0F) == 0) shownInitials[cursor] ^= initialsByte(cursor);
            uploadCompactTileBlock(word(shownInitials), at);
            if ((held & 0x80) != 0) break;
        }
        uploadCompactTileBlock(record.defaultInitials(), at);
    }

    /** HandleSelectionInput (Time Attack/Main.asm:2105-2227): {cursor, delay}, or null when nothing changed. */
    private int[] handleSelectionInput(int cursor) {
        if ((held & 0x04) != 0 && cursor > 0) return selectionChanged(cursor - 1);
        if ((held & 0x08) != 0 && cursor < 2) return selectionChanged(cursor + 1);
        if ((held & 0x10) != 0 && cursor > 0) return selectionChanged(cursor - 1);
        if ((held & 0x40) != 0) {
            if (cursor == 2) { held |= 0x80; return null; }
            if (cursor < 2) return selectionChanged(cursor + 1);
        }
        if ((held & 0x20) != 0) {
            if (cursor == 2) { held |= 0x80; return null; }
            if (cursor < 2) return selectionChanged(cursor + 1);
        }
        int value = initialsByte(cursor);
        if ((held & 1) != 0) value--;
        else if ((held & 2) != 0) value++;
        else return null;
        if (value < 1) value = 0x24;
        if (value > 0x24) value = 1;
        int initials = record.defaultInitials();
        int shift = 24 - 8 * cursor;
        record.setDefaultInitials((initials & ~(0xFF << shift)) | (value << shift));
        copyInitials();
        frameCounter = 1;
        return new int[] {cursor, 0x0B};
    }

    private int[] selectionChanged(int cursor) {
        copyInitials();
        frameCounter = 0;
        return new int[] {cursor, 0x0B};
    }

    private void copyInitials() {
        int v = record.defaultInitials();
        for (int i = 0; i < 4; i++) shownInitials[i] = (byte) (v >>> (24 - 8 * i));
    }

    private int initialsByte(int i) { return (record.defaultInitials() >>> (24 - 8 * i)) & 0xFF; }

    private static int word(byte[] four) {
        return ((four[0] & 0xFF) << 24) | ((four[1] & 0xFF) << 16) | ((four[2] & 0xFF) << 8) | (four[3] & 0xFF);
    }

    private void waitFrames(int count) {                              // WaitFrames (Time Attack/Main.asm:2293-2305)
        for (int i = 0; i <= count; i++) waitTransfer(0x10);
    }

    // ---------------------------------------------------------------- drawing helpers

    /** RenderTimeRecord (Time Attack/Main.asm:1562-1660): {@code mm'ss"cc} in two plane rows; three-digit minutes start one cell left. */
    private void renderTimeRecord(int time, int at) {
        int map = assets.label("timeDigitTileMap") + 2;
        for (int half = 0; half < 2; half++) {
            int a = at + half * 0x80;
            int m = map + half * 0x4E;
            int minutes = (time >> 16) & 0xFF;
            if (minutes >= 100) {
                vramWord(a - 2, imageWord(m + 2 * (minutes / 100)));
                minutes %= 100;
            }
            int[] words = {imageWord(m + 2 * (minutes / 10)), imageWord(m + 2 * (minutes % 10)), imageWord(m + 0x48),
                    imageWord(m + 2 * (((time >> 8) & 0xFF) / 10)), imageWord(m + 2 * (((time >> 8) & 0xFF) % 10)), imageWord(m + 0x4A),
                    imageWord(m + 2 * ((time & 0xFF) / 10)), imageWord(m + 2 * ((time & 0xFF) % 10))};
            for (int i = 0; i < words.length; i++) vramWord(a + 2 * i, words[i]);
        }
    }

    /** RenderTimeRecordToBuffer (Time Attack/Main.asm:1671-1741): the same into a list buffer, rows $20 bytes apart. */
    private void renderTimeRecordToBuffer(int time, int buffer) {
        int map = assets.label("timeRecordTileMap");
        for (int half = 0; half < 2; half++) {
            int m = map + half * 0x18;
            int b = buffer + half * 0x20;
            int minutes = (time >> 16) & 0xFF, seconds = (time >> 8) & 0xFF, hundredths = time & 0xFF;
            int[] words = {imageWord(m + 2 * (minutes / 10)), imageWord(m + 2 * (minutes % 10)), imageWord(m + 0x14),
                    imageWord(m + 2 * (seconds / 10)), imageWord(m + 2 * (seconds % 10)), imageWord(m + 0x16),
                    imageWord(m + 2 * (hundredths / 10)), imageWord(m + 2 * (hundredths % 10))};
            for (int i = 0; i < words.length; i++) putImageWord(b + 2 * i, words[i]);
        }
    }

    /** UploadCompactTileBlock (Time Attack/Main.asm:1748-1786): three character codes, two plane rows. */
    private void uploadCompactTileBlock(int codes, int at) {
        int map = assets.label("timeDigitTileMap");
        for (int half = 0; half < 2; half++) {
            for (int i = 0; i < 3; i++) {
                int code = (codes >>> (24 - 8 * i)) & 0xFF;
                vramWord(at + half * 0x80 + 2 * i, imageWord(map + half * 0x4E + 2 * code));
            }
        }
    }

    /** DrawSelectionHighlight (Time Attack/Main.asm:1790-1862): the bar behind the selected row on plane B. */
    private void drawSelectionHighlight(int mode) {
        int at = stageRow();
        if (mode != 0) {
            fillRect(at, 16, 2, 0);
            return;
        }
        int top = row < 0 ? 0x8128 : 0x808E, bottom = 0x8093;        // $80938128 / $8093808E
        fillRect(at, 16, 1, top);
        fillRect(at + 0x80, 16, 1, bottom);
    }

    /** UploadSelectionTileBlock (Time Attack/Main.asm:1866-1902): the selected row's text, with priority, on plane B. */
    private void uploadSelectionTileBlock() {
        int source = 0xFFD300 + (hscrollA != 0 ? 0x200 : 0) + ((row + 1) << 6);
        copyRect(source, stageRow(), 16, 2);
    }

    /** BuildStageVdpCommand (Time Attack/Main.asm:1906-1928): $61840003 or $61D00003, plus two rows per entry. */
    private int stageRow() {
        return (hscrollA != 0 ? 0xE1D0 : 0xE184) + ((row + 1) & 0xFF) * 0x100;
    }

    /** UploadIndexedStageData (Time Attack/Main.asm:1931-1969): one EXIT/NEXT/GO item, plain or lit. */
    private void uploadIndexedStageData(int lit) {
        int entry = 0xFF6946 + (hscrollA != 0 ? 0x3C : 0) + headerMode * 0x14;
        int command = imageLong(entry);
        int at = ((command >>> 16) & 0x3FFF) | ((command & 3) << 14);
        copyRect(entry + 4 + (lit != 0 ? 8 : 0), at, 4, 1);
    }

    /** UploadSelectionTitleRow (Time Attack/Main.asm:1160-1198): the act or special-stage number. */
    private void uploadSelectionTitleRow() {
        if (hscrollA == 0) copyRect(0xFF749C + 4 * row, 0xE4BC, 1, 2);   // $64BC0003
        else copyRect(0xFF749C + 4 * act, 0xE4BE, 1, 2);                 // $64BE0003
    }

    /** UploadVdpTileBlock (Time Attack/Main.asm:2263-2289): a 3x2 rank label from $FF7868 (REGION=USA). */
    private void uploadVdpTileBlock(int at, int offset) {
        copyRect(0xFF7868 + offset, at, 3, 2);
    }

    /** PrepareSelectionGraphics (Time Attack/Main.asm:1295-1385): the picture's palette, map and art. */
    private void prepareSelectionGraphics(int index) {
        int d7 = index;
        if (hscrollA != 0) d7 += 7;                                   // zone pictures follow the seven special stages
        if (row < 0) d7 = 0x0E;
        for (int i = 0; i < 8; i++) putImageLong(0xFFD100 + 4 * i, imageLong(0xFFD120 + d7 * 32 + 4 * i));
        byte[] art = assets.nemesis(0x210592 + assets.word(0x210592 + 2 * d7));   // into the buffer at $FFAA60
        for (int i = 0; i < Math.min(art.length, PICTURE_BYTES); i++) image[0xFFAA60 - imageBase + i] = art[i];
        int[] map = assets.enigma(0x210000 + signed16(assets.word(0x210000 + 2 * d7)), 0xE460);
        putWords(0xFFCE60, map);
        waitTransfer(entering ? 0x0C : 0x0A);
        // UploadRotatedVdpData (Time Attack/Main.asm:2869-2901): eight passes of the whole buffer ($120 * 8 longs) to $8C00;
        // the last pass is unrotated. A shorter picture leaves the buffer's older bytes after it.
        for (int i = 0; i < PICTURE_BYTES; i++) vramByte(0x8C00 + i, image[0xFFAA60 - imageBase + i]);
    }

    // ---------------------------------------------------------------- palette fades

    private void fadePaletteIn() {                                    // FadePaletteIn (Time Attack/Main.asm:2585-2621)
        for (int shift : new int[] {8, 4, 0}) {
            for (int level = 0; level < 16; level += 2) {
                for (int i = 0; i < 64; i++) {
                    int target = rotr(imageWord(0xFF524C + 2 * i), shift);
                    int current = rotr(imageWord(0xFFD0A0 + 2 * i), shift);
                    int channel = target & 0x0E;
                    if (channel > level) channel = level;
                    putImageWord(0xFFD0A0 + 2 * i, rotl((current & 0xEEE0) | channel, shift));
                }
                waitTransfer(6);
            }
        }
    }

    private void fadePaletteOut() {                                   // FadePaletteOut (Time Attack/Main.asm:2662-2731)
        for (int shift : new int[] {0, 4, 8}) {
            for (int level = 0; level < 16; level += 2) {
                for (int i = 0; i < 64; i++) {
                    int current = rotr(imageWord(0xFFD0A0 + 2 * i), shift);
                    int channel = (current & 0x0E) - level;
                    if (channel < 0) channel = 0;
                    putImageWord(0xFFD0A0 + 2 * i, rotl((current & 0xEEE0) | channel, shift));
                }
                waitTransfer(6);
            }
        }
    }

    private static int rotr(int w, int n) { return n == 0 ? w : ((w >>> n) | (w << (16 - n))) & 0xFFFF; }

    private static int rotl(int w, int n) { return n == 0 ? w : ((w << n) | (w >>> (16 - n))) & 0xFFFF; }

    // ---------------------------------------------------------------- V-blank

    /** Queue a slot and wait: the V-blank runs it before the frame is shown, then the main code continues. */
    private void waitTransfer(int slot) {
        transfer = slot;
        vblank();
        fiber.vblank();
    }

    /** VInterrupt (Time Attack/Main.asm:2384-2404) and its slots, dispatched through $FF34BE (VIntTransferDispatchTable). */
    private void vblank() {
        int slot = transfer;
        transfer = 0;
        switch (slot) {
            case 0 -> { }
            case 2, 4 -> { writeScroll(); updateController(); }       // VIntWriteDisplayPair
            case 6 -> { for (int i = 0; i < 64; i++) cram[i] = imageWord(0xFFD0A0 + 2 * i); updateController(); }
            case 8 -> { vscrollB = periodScroll; updateController(); } // VIntWriteTransferValue: VSRAM 2
            case 0x0A -> { for (int i = 0; i < 16; i++) cram[48 + i] = imageWord(0xFFD100 + 2 * i); copyRect(0xFFCE60, 0xC1AA, 16, 18); }
            case 0x0C -> copyRect(0xFFCE60, 0xC1AA, 16, 18);
            case 0x0E -> { animCounter = 0; tileAnimation(); updateController(); }
            case 0x10 -> { tileAnimation(); updateController(); }
            default -> throw new IllegalStateException("Time Attack transfer slot " + slot);
        }
        frameCounter = (frameCounter + 1) & 0xFFFF;
    }

    private void writeScroll() {
        vramWord(HSCROLL, hscrollA);
        vramWord(HSCROLL + 2, hscrollB);
    }

    /** VIntAdvanceTileAnimation (Time Attack/Main.asm:2512-2574): special stage 6's palette strip. */
    private void tileAnimation() {
        writeScroll();
        if (hscrollA != 0 || row != 5) return;
        int data = assets.label("tileAnimationData") + ((animCounter << 2) & 0xF0);
        for (int i = 0; i < 8; i++) {
            int colour = imageWord(data + 2 * i);
            putImageWord(0xFFD100 + 2 * i, colour);
            cram[48 + i] = colour;
        }
        animCounter++;
        if (animCounter >= 0x34) animCounter = 0;
    }

    /** UpdateControllerState (Time Attack/Main.asm:2490-2508). */
    private void updateController() {
        int previous = held;
        held = pad;
        pressed = (previous ^ held) & held;
    }

    // ---------------------------------------------------------------- memory helpers

    private void nemesisToVram(int address, int at) {
        byte[] art = assets.nemesis(address);
        for (int i = 0; i < art.length; i++) vramByte(at + i, art[i]);
    }

    private void copyRect(int source, int at, int columns, int rows) {   // CopyVdpRect (Time Attack/Main.asm:2784-2812)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) vramWord(at + r * 0x80 + 2 * c, imageWord(source + 2 * (r * columns + c)));
        }
    }

    private void fillRect(int at, int columns, int rows, int value) {   // FillVdpRect (Time Attack/Main.asm:2755-2781)
        for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++) vramWord(at + r * 0x80 + 2 * c, value);
    }

    private void vramWord(int at, int value) {
        vramByte(at, value >> 8);
        vramByte(at + 1, value);
    }

    private void vramByte(int at, int value) {
        at &= 0xFFFF;
        vram[at] = (byte) value;
        dirtyTiles.set(at >> 5);
    }

    private int imageWord(int address) {
        if (address < imageBase) return assets.word(address);
        int at = address - imageBase;
        return ((image[at] & 0xFF) << 8) | (image[at + 1] & 0xFF);
    }

    private int imageLong(int address) { return (imageWord(address) << 16) | imageWord(address + 2); }

    private void putImageWord(int address, int value) {
        int at = address - imageBase;
        image[at] = (byte) (value >> 8);
        image[at + 1] = (byte) value;
    }

    private void putImageLong(int address, int value) {
        putImageWord(address, value >>> 16);
        putImageWord(address + 2, value & 0xFFFF);
    }

    private void putWords(int address, int[] words) {
        for (int i = 0; i < words.length; i++) putImageWord(address + 2 * i, words[i]);
    }

    private static int signed16(int v) { return (short) v; }

    // ---------------------------------------------------------------- presentation

    /** The picture native shows this frame. */
    public CdProgramScreenRenderer.Frame screen() {
        int[] planeA = new int[64 * 32], planeB = new int[64 * 32];
        // Plane B's vertical scroll only moves in steps of 8 (AnimateTimePeriodChange), so it is whole rows.
        int rows = (vscrollB >> 3) & 0x1F;
        for (int i = 0; i < planeA.length; i++) {
            planeA[i] = ((vram[PLANE_A + 2 * i] & 0xFF) << 8) | (vram[PLANE_A + 2 * i + 1] & 0xFF);
            int source = (((i >> 6) + rows) & 0x1F) * 64 + (i & 63);
            planeB[i] = ((vram[PLANE_B + 2 * source] & 0xFF) << 8) | (vram[PLANE_B + 2 * source + 1] & 0xFF);
        }
        int a = ((vram[HSCROLL] & 0xFF) << 8) | (vram[HSCROLL + 1] & 0xFF);
        int b = ((vram[HSCROLL + 2] & 0xFF) << 8) | (vram[HSCROLL + 3] & 0xFF);
        IntUnaryOperator scrollA = line -> a, scrollB = line -> b;
        return new CdProgramScreenRenderer.Frame(vram, dirtyTiles, planeA, planeB, cram.clone(), scrollA, scrollB,
                List.of(), 320, display);
    }

    // For tests.
    int row() { return row; }
    int act() { return act; }
    int headerMode() { return headerMode; }
    int rank() { return rank; }
    int total() { return total; }
    CdSaveRecord record() { return record; }
}
