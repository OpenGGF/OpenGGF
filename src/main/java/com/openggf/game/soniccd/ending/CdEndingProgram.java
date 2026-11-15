package com.openggf.game.soniccd.ending;

import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdFmvStreamer;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.util.Objects;

/**
 * The ending's main program (ENDING.MMD, FMV/Main (Ending).asm; the listing is a raw dump, so states are named by
 * the retail addresses the dispatch tables resolve to) as an explicit state machine, one {@link #frame(int)} per
 * V-int. The same program runs both endings; the sub file streams {@code GOODEND.STM} or {@code BADEND.STM}.
 *
 * <p>Each Word RAM bank starts with a two-character tag the main state 4/$24 dispatcher reads
 * ({@code Ending_DispatchWordRamCommand}, :178-198; {@code Ending_LoadWordRamCommandBlock}, :320-340):
 * <ul>
 *   <li>{@code "08"}: eight full video frames, exactly as the opening plays them (states 6-$1C, :200-318);</li>
 *   <li>{@code "SR"}: the credits screen: the program's own still picture and map, then four $1680-byte tile
 *       blocks from the bank (states $30-$3C, :404-486), after which the credits event stream starts
 *       ({@code Ending_StartEventPlayback}, :487-505);</li>
 *   <li>{@code "15"}: fifteen small frames (one $12C0-byte section with its palette into the still picture, four
 *       V-ints each; states $26-$2C, :344-400 and V-int 9, :936-967);</li>
 *   <li>{@code "NO"}: no frames; the bank only paces the credits ({@code Ending_StartEventPlaybackWait});</li>
 *   <li>{@code "RS"}: the end text: V-int $B clears the credits and draws "YOU'RE TOO COOL!" or the bad
 *       ending's text, whose two colours then fade in ({@code Ending_RequestEventPlaybackStop}, :507-512;
 *       :1035-1139; :894-911).</li>
 * </ul>
 * The credits scroll plane B by $0.9100 lines a V-int and draw one 40-cell row from {@code EndingEventData} each
 * 8 lines ({@code Ending_ProcessEventStream}, :514-588), on every V-int, lag or not. When the sub program ends,
 * the program waits 61 V-ints and shows the final image (Presented by SEGA) for 600 V-ints or until Start is held
 * ({@code Ending_ImageStartupInitialize}, :14847-14911), fading it in and out by colour channel (:15009-15120).
 *
 * <p>Start can stop the video only when the byte at $FF0F23 is set ({@code Ending_RequestSubCpuTransfer},
 * :630-638); nothing on the disc writes it, so the shipped ending cannot be skipped.
 */
public final class CdEndingProgram {
    private enum Phase { LOOP, EXIT_DELAY, IMAGE, DONE }

    private static final int TAG_08 = 0x3038, TAG_15 = 0x3135, TAG_SR = 0x5352, TAG_NO = 0x4E4F, TAG_RS = 0x5253;

    private final CdProgramScreenState screen = new CdProgramScreenState();
    /** A native video-memory pattern address is tile {@code address / 32}. */
    private static final int TILE = CdProgramScreenState.TILE_BYTES;
    private final CdFmvStreamer streamer;
    private final CdFmvAssets assets;
    private final boolean goodEnding;
    private final byte[] events;
    private final byte[] stopColours;
    // Work RAM ($FFC000-): current palette c000, previous c020, counters and the credits stream.
    private final int[] current = new int[16], previous = new int[16];
    private int frameCount;                                // c080
    private int dataAddress;                               // a0 / c082 into the main bank
    private int state;                                     // c086
    private boolean eventsActive;                          // c088
    private long scroll;                                   // c08a (16.16 lines)
    private int lastRow;                                   // c08e
    private int eventCursor;                               // c090
    private boolean endTextFade;                           // c094
    private int vint;                                      // fa40
    private int vintCounter;                               // fa44
    private boolean vsyncFlag;                             // fa00
    private int padHold;
    private boolean dmaActive;                             // $FF8000 transfer queue
    private int dmaTile, dmaSource, dmaWords;          // the queued transfer: first tile, bank offset, words
    private Phase phase = Phase.LOOP;
    private boolean waitingSwap;
    private int exitDelay;
    private long frames;
    private final CdEndingImage image;

    /**
     * @param goodEnding {@code endingID} = $7F ({@code GoodEnding}, IPX.asm:320-321), else 0
     */
    public CdEndingProgram(CdFmvAssets assets, CdFmvStreamer streamer, boolean goodEnding) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.streamer = Objects.requireNonNull(streamer, "streamer");
        this.goodEnding = goodEnding;
        this.events = assets.bytes("endEvents");
        this.stopColours = assets.bytes("endStopColours");
        this.image = new CdEndingImage(assets, screen);
        start();
    }

    /** {@code Ending_StartupInitialize} (:28-63) up to the first bank. */
    private void start() {
        // Ending_InitializePaletteAndFont (:640-680). EndingPaletteData (:714-722): H40 ($8C81), 128 x 32-cell planes
        // ($9003), plane A at $C000, plane B at $A000, the scroll table at $E400, display off ($8134). The VRAM fill
        // empties tiles and planes; plane A's $1000 words of $E7E1 fill every cell; the palette from EndingFontData.
        screen.setLayout(320, 128, 32);
        screen.setDisplay(false);
        screen.clearTilesAndPlanes();
        screen.fillPlane(CdProgramScreenState.Plane.A, 0xE7E1);
        byte[] cram = assets.bytes("endCram");
        for (int i = 0; i < 64; i++) screen.setPalette(i, word(cram, 2 * i));
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
        screen.setHScroll(CdProgramScreenState.Plane.A, 0x0200);   // move.l #$2000200 to the scroll table (:36-37)
        screen.setHScroll(CdProgramScreenState.Plane.B, 0x0200);
        clearInitialRows();                                // Ending_ClearInitialVdpRows (:590-614)
        // Ending_ClearVdpNameTables (:787-817): VRAM 0-$1F (tile 0), then plane B's ($A000) 40 x 28 cells.
        screen.clearTiles(0, 1);
        for (int row = 0; row < 0x1C; row++) for (int c = 0; c < 0x28; c++) screen.setPlaneCell(CdProgramScreenState.Plane.B, c, row, 0);
        screen.setDisplay(true);                           // bset #6,$fa47 (:49-50)
        waitingSwap = true;                                // Ending_WaitSubCpuStartLoop, then WaitWordRamSwap (:52-62)
        state = 4;
    }

    private void clearInitialRows() {
        // $C608 and $C688: plane A cells 4 and 68 of rows 6-19, from tiles $10 and $1D0.
        int tile = 0x10;
        for (int row = 6; row < 20; row++) for (int c = 0; c < 32; c++) screen.setPlaneCell(CdProgramScreenState.Plane.A, 4 + c, row, tile++);
        tile = 0x1D0;
        for (int row = 6; row < 20; row++) for (int c = 0; c < 32; c++) screen.setPlaneCell(CdProgramScreenState.Plane.A, 68 + c, row, tile++);
    }

    /** One V-int and the main program's work until its next VSync. */
    public void frame(int pad) {
        frames++;
        if (phase == Phase.IMAGE) {
            image.vInterrupt(pad);
        } else {
            vInterrupt(pad);
        }
        streamer.advanceVint();
        switch (phase) {
            case LOOP -> loop();
            case EXIT_DELAY -> exitDelay();
            case IMAGE -> { if (image.step()) phase = Phase.DONE; }
            default -> { }
        }
        streamer.playVint();
    }

    // ------------------------------------------------------------------------------------------------ V-int

    /** {@code Ending_VBlankInterrupt} (:819-922). */
    private void vInterrupt(int pad) {
        if (vsyncFlag) {
            vsyncFlag = false;
            switch (vint) {
                case 1 -> { scroll(0x0200); transferCommon(); }
                case 5 -> { scroll(0); transferCommon(); }
                case 2, 3, 4, 6, 7, 8 -> transfer();
                case 9 -> {                                // $FF2952: fixed $960 words, then the current palette
                    if (dmaActive) {
                        dmaActive = false;
                        screen.writeTiles(dmaTile, streamer.shownBank(), dmaSource, 0x960 * 2);
                    }
                    for (int i = 0; i < 16; i++) screen.setPalette(i, current[i]);
                }
                case 10 -> initialiseCreditsScreen();
                case 11 -> uploadEndText();
                default -> { }
            }
            afterTransfer(pad);
        }
        if (eventsActive) {                                // VBlankNoTimer: every V-int (:913-919)
            processEventStream();
            screen.setVScroll(CdProgramScreenState.Plane.A, 0);
            screen.setVScroll(CdProgramScreenState.Plane.B, (int) (scroll >>> 16));
        }
    }

    private void scroll(int a) {
        // The scroll table's two words: plane A moved $200 shows buffer 2 (cells 68-99), unmoved buffer 1.
        screen.setHScroll(CdProgramScreenState.Plane.A, a);
        screen.setHScroll(CdProgramScreenState.Plane.B, 0);
    }

    /** {@code Ending_VBlankTransferCommon}: CRAM line 0 from the previous palette, then the queued transfer. */
    private void transferCommon() {
        for (int i = 0; i < 16; i++) screen.setPalette(i, previous[i]);
        transfer();
    }

    private void transfer() {
        if (dmaActive) {
            dmaActive = false;
            screen.writeTiles(dmaTile, streamer.shownBank(), dmaSource, dmaWords * 2);
        }
    }

    /** {@code Ending_VBlankAfterTransfer} (:882-911): display on, pad, the counter and the end-text colour fade. */
    private void afterTransfer(int pad) {
        screen.setDisplay(true);
        padHold = pad;
        vintCounter = (vintCounter + 1) & 0xFFFF;
        if (!endTextFade || vintCounter < 0x2C || (vintCounter & 1) != 0) return;
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
        int at = (vintCounter - 0x2C) * 2;
        int a = (short) word(stopColours, at), b = word(stopColours, at + 2);
        if (a < 0) {
            endTextFade = false;
            return;
        }
        screen.setPalette(0x2B, a);                        // CRAM $56 and $5E: entries $2B and $2F
        screen.setPalette(0x2F, b);
    }

    /** V-int 10 ($FF298C, :969-1033): the credits screen's still picture, map and palette. */
    private void initialiseCreditsScreen() {
        byte[] still = assets.bytes("endStillPicture");
        screen.writeTiles(0x0200 / TILE, still, 0x20, 0x960 * 2);   // DMA $960 words from EndingTimeAttackData+$20 to $0200
        byte[] map = assets.bytes("endCreditsMap");
        for (int row = 0; row < 14; row++) for (int c = 0; c < 32; c++)
            screen.setPlaneCell(CdProgramScreenState.Plane.A, 4 + c, 6 + row, word(map, 2 * (row * 32 + c)));   // from $C608
        scroll(0);
        for (int i = 0; i < 16; i++) screen.setPalette(i, current[i]);
        vint = 7;
    }

    /** V-int 11 ($FF2A0E, :1035-1139): clear the credits and draw the end text for this ending. */
    private void uploadEndText() {
        clearInitialRows();
        java.util.Arrays.fill(current, 0);                 // Ending_ClearCommandWorkspace
        java.util.Arrays.fill(previous, 0);
        for (int i = 0; i < 64; i++) screen.setPalette(i, 0);
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
        if (goodEnding) {
            rows(assets.bytes("endTextGood"), 0x0C, 0x15, 2, 0x10);   // $B518: plane B ($A000) cell 12, row 21
            byte[] font = assets.bytes("endFontGood");
            screen.writeTiles(0x76C0 / TILE, font, 0, 0x130 * 2);
        } else {
            rows(assets.bytes("endTextBad"), 0x05, 0x15, 5, 0x1E);    // $B50A: plane B cell 5, row 21
            byte[] font = assets.bytes("endFontBad");
            screen.writeTiles(0x76C0 / TILE, font, 0, 0x1B0 * 2);
        }
        vint = 0;
        vintCounter = 0;
    }

    private void rows(byte[] words, int column, int row, int rows, int width) {
        for (int r = 0; r < rows; r++) for (int c = 0; c < width; c++)
            screen.setPlaneCell(CdProgramScreenState.Plane.B, column + c, row + r, word(words, 2 * (r * width + c)));
    }

    /** {@code Ending_ProcessEventStream} (:514-588): scroll on, and a new row each 8 lines. */
    private void processEventStream() {
        scroll = (scroll + 0x9100) & 0xFFFFFFFFL;
        int line = (int) (scroll >>> 16);
        if (((line - lastRow) & 0xFFFF) >= 0x8000 || ((line - lastRow) & 0xFFFF) < 8) return;
        lastRow = line;
        // The row's address $A000 + ((line + $E0) & $F8) << 5 is plane B (128 cells, $100 bytes a row) row
        // ((line + $E0) & $F8) / 8, from cell 0.
        int row = ((line + 0xE0) & 0xF8) >> 3;
        int first = word(events, eventCursor);
        if (first == 0xFFFF) {
            eventsActive = false;                          // clr.w $c088 after the end marker
            return;
        }
        int count = word(events, eventCursor + 2);
        eventCursor += 4;
        int written = 0;
        for (int i = 0; i <= count; i++) screen.setPlaneCell(CdProgramScreenState.Plane.B, written++, row, first);
        int pad = 0x28 - count;
        while (true) {
            int w = word(events, eventCursor);
            eventCursor += 2;
            if (w == 0xFFFF) break;
            screen.setPlaneCell(CdProgramScreenState.Plane.B, written++, row, w);
            pad--;
        }
        for (int i = 0; i < pad; i++) screen.setPlaneCell(CdProgramScreenState.Plane.B, written++, row, 0);
    }

    // ------------------------------------------------------------------------------------------------ main

    private void loop() {
        // The main loop's exit test after each wait (FMV/Main (Ending).asm:71-79).
        if (!waitingSwap && streamer.finished()) {
            for (int i = 0; i < 16; i++) screen.setPalette(i, 0); // move.l #$c0000000 and 16 zero words (:82-86)
            vint = 0;                                      // Ending_WaitForSubCpuCompletion (:99-112)
            exitDelay = 61;
            phase = Phase.EXIT_DELAY;
            exitDelay();
            return;
        }
        while (true) {
            if (waitingSwap) {
                if (!waitWordRamSwap()) return;            // still waiting: a lag frame
                waitingSwap = false;
                if (resumeVsync) {                         // the state that waited returns to its VSync
                    resumeVsync = false;
                    vsyncFlag = true;
                    return;
                }
            }
            int result = runState();
            if (result == VSYNC) {
                vsyncFlag = true;
                return;
            }
            waitingSwap = true;
            resumeVsync = result == SWAP_THEN_VSYNC;
        }
    }

    private boolean resumeVsync;
    /** How a state pass ends: its VSync; a bank wait, then the next state at once; a bank wait, then its VSync. */
    private static final int VSYNC = 0, SWAP_NO_VSYNC = 1, SWAP_THEN_VSYNC = 2;

    /** {@code Ending_WaitWordRamSwap} (:751-785). */
    private boolean waitWordRamSwap() {
        if (streamer.finished()) return true;
        if (!streamer.bankReady() || streamer.stopRequested()) return false;
        streamer.takeBank();
        return true;
    }

    /** One pass of the state the dispatch table at $FF2194 names. */
    private int runState() {
        byte[] bank = streamer.shownBank();
        switch (state) {
            case 4 -> { return dispatch(bank, false); }                  // $FF21D2
            case 6 -> frame08();                                         // $FF220A
            case 8 -> { vint = 2; state = 0x0A; queue(0x14C0 / TILE, 0x960, 0x12C0); }
            case 0x0A -> { vint = 3; state = 0x0C; queue(0x2780 / TILE, 0x940, 0x1280); }
            case 0x0C -> { vint = 4; state = 0x0E; }
            case 0x0E -> { vint = 0; return advance(); }
            case 0x10 -> { vint = 6; state = 0x12; queue(0x4CC0 / TILE, 0x960, 0x12C0); }
            case 0x12 -> { vint = 7; state = 0x14; queue(0x5F80 / TILE, 0x940, 0x1280); }
            case 0x14 -> { vint = 8; return advance(); }
            case 0x16 -> { return advance(); }
            case 0x18 -> state = 0x1A;
            case 0x1A -> state = 0x1C;
            case 0x1C -> state = 6;
            case 0x24 -> { return dispatch(bank, true); }                // $FF2332
            case 0x26 -> frame15(bank);                                  // $FF2366
            case 0x28 -> {                                               // $FF239E
                state = 0x2A;
                if (frameCount >= 0x0F) {
                    frameCount = 0;
                    state = 0x24;
                    return SWAP_THEN_VSYNC;
                }
            }
            case 0x2A -> state = 0x2C;
            case 0x2C -> state = 0x26;
            case 0x30 -> { stillBlock(0x14C0 / TILE); state = 0x32; vint = 7; }
            case 0x32 -> { stillBlock(0x2B40 / TILE); state = 0x34; }
            case 0x34 -> { stillBlock(0x41C0 / TILE); state = 0x36; }
            case 0x36 -> { stillBlock(0x5840 / TILE); state = 0x38; }
            case 0x38 -> state = 0x3A;                                   // USA: no fifth block (:461-472)
            case 0x3A -> state = 0x3C;
            case 0x3C -> {                                               // Ending_StartEventPlayback (:487-505)
                vint = 0;
                state = 0x24;
                bank[0] = 0x4E;                                          // "NO" written into the main bank
                bank[1] = 0x4F;
                eventsActive = true;
                scroll = 0;
                lastRow = 0;
                eventCursor = 0;
                return SWAP_THEN_VSYNC;
            }
            default -> { }
        }
        return VSYNC;
    }

    /** The tag dispatchers $FF21D2 (state 4, "08" first) and $FF2332 (state $24, "15" first). */
    private int dispatch(byte[] bank, boolean fifteenFirst) {
        dataAddress = 0;
        frameCount = 0;
        int tag = word(bank, 0);
        if (tag == TAG_SR) {                               // Ending_LoadTimeAttackData (:404-414)
            dataAddress = 0x10;
            byte[] still = assets.bytes("endStillPicture");
            for (int i = 0; i < 16; i++) current[i] = word(still, 2 * i);
            vint = 10;
            state = 0x30;
            return VSYNC;
        }
        if (tag == TAG_NO) return SWAP_THEN_VSYNC;         // Ending_StartEventPlaybackWait
        if (tag == TAG_RS) {                               // Ending_RequestEventPlaybackStop
            vint = 11;
            endTextFade = true;
            return SWAP_THEN_VSYNC;
        }
        boolean fifteen = fifteenFirst ? tag != TAG_08 : tag == TAG_15;
        dataAddress = 0x10;
        if (fifteen) frame15(bank);
        else frame08();
        return VSYNC;
    }

    /** $FF220A: palette in (the previous kept for the V-int), section 1 to the buffer this frame's parity names. */
    private void frame08() {
        byte[] bank = streamer.shownBank();
        System.arraycopy(current, 0, previous, 0, 16);
        for (int i = 0; i < 16; i++) current[i] = word(bank, dataAddress + 2 * i);
        dataAddress += 0x20;
        vint = 5;
        state = 0x10;
        int vram = 0x3A00;
        if ((frameCount & 1) != 0) {
            state = 8;
            vint = 1;
            vram = 0x0200;
        }
        queue(vram / TILE, 0x960, 0x12C0);
    }

    /** $FF2366: one small frame: palette, $12C0 bytes to $0200 (V-int 9 copies $960 words). */
    private void frame15(byte[] bank) {
        for (int i = 0; i < 16; i++) current[i] = word(bank, dataAddress + 2 * i);
        dataAddress += 0x20;
        dmaActive = true;
        dmaTile = 0x0200 / TILE;
        dmaSource = dataAddress;
        dataAddress += 0x12C0;
        frameCount++;
        state = 0x28;
        vint = 9;
    }

    private void stillBlock(int tile) {
        dmaActive = true;
        dmaTile = tile;
        dmaSource = dataAddress;
        dmaWords = 0xB40;
        dataAddress += 0x1680;
    }

    private void queue(int tile, int words, int advance) {
        dmaActive = true;
        dmaTile = tile;
        dmaSource = dataAddress;
        dmaWords = words;
        dataAddress += advance;
    }

    /** $FF22EE: the stop request (never honoured, see the class note), then the next frame or the next bank. */
    private int advance() {
        // Ending_RequestSubCpuTransfer: tst.b $ff0f23 is zero on the shipped disc, so Start is ignored.
        vint = 0;
        frameCount++;
        if (frameCount < 8) {
            state = 0x18;
            return VSYNC;
        }
        state = 4;
        return SWAP_NO_VSYNC;
    }

    private void exitDelay() {
        if (exitDelay-- > 0) {
            vsyncFlag = true;
            return;
        }
        // bset #4; clear $FFB200-$FFB2FF; $FFB256 = $046A, $FFB25E = $00EE; jmp to the image (:93-97).
        image.begin();
        phase = Phase.IMAGE;
        if (image.step()) phase = Phase.DONE;
    }

    public boolean done() { return phase == Phase.DONE; }

    /** {@code mmdReturnCode}: d0.b after the image's last fade ($10, positive): IPX does not run it again. */
    public int returnCode() { return 0x10; }

    public CdProgramScreenState screen() { return screen; }

    public CdFmvStreamer streamer() { return streamer; }

    public long frames() { return frames; }

    public int stateValue() { return state; }

    public int vintValue() { return vint; }

    public boolean creditsActive() { return eventsActive; }

    /** The main program has seen the sub program finish (the 61-V-int wait, the image or done). */
    public boolean exiting() { return phase != Phase.LOOP; }

    public boolean inImage() { return phase == Phase.IMAGE || phase == Phase.DONE; }

    static int word(byte[] data, int at) {
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }

    static byte[] nemesis(byte[] data) {
        try {
            return NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(data)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
