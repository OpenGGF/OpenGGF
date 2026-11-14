package com.openggf.game.soniccd.fmv;

import java.util.Objects;

/**
 * The opening video's main program (OPEN_M.MMD, FMV/Main (Opening).asm) as an explicit state machine, one
 * {@link #frame(int)} per V-int. Its {@code MainLoop} runs the data-read routine ({@code readRoutine},
 * {@code FrameReadIndex} :170-185) then waits for the V-int, whose routine ({@code vintRoutine},
 * {@code VInterrupt .Routines} :606-616) copies what the read routine queued.
 *
 * <p>A video frame is 32 bytes of palette and three DMA sections ($12C0, $12C0, $1280 bytes = 448 tiles) in the
 * Word RAM bank; eight frames fill a bank. Even frames go to buffer 2 (tiles $1D0, plane A columns 68-99), odd
 * ones to buffer 1 (tiles $10, columns 4-35), and the horizontal scroll shows the buffer finished last. An even
 * frame takes seven main-loop passes and an odd one eight (with the NTSC delay frame, {@code FrameRead_Buf1Done}
 * :295-303), 7.5 V-ints a frame; after the eighth frame the program waits for the next bank
 * ({@code FrameRead_NextFrame .SwapWordRAM} :327-331).
 */
public final class CdOpeningProgram {
    /** Pad bits as the program reads them (start = bit 7). */
    public static final int START = 0x80;

    private enum Phase { INIT, LOOP, EXIT_DELAY, FADE, DONE }

    private final CdProgramScreenState screen = new CdProgramScreenState();
    /** A native video-memory pattern address is tile {@code address / 32}. */
    private static final int TILE = CdProgramScreenState.TILE_BYTES;
    private final CdFmvStreamer streamer;
    private final byte[] palOpening;
    private final int[] palette = new int[0x40];          // palette (Variables :22): work line, display line, border
    private int frameCount;
    private int readRoutine;
    private int vintRoutine;
    private boolean dmaActive;
    private int dmaTile;                                   // the queued frameDMA: first tile, bank offset, bytes
    private int dmaSource;
    private int dmaBytes;
    private int dataAddress;                               // a0 into the main bank
    private boolean vsyncFlag;
    private int padHold, padTap;
    private boolean updateBorderPal;
    private int palFadeDelay, palFadeCount;
    private Phase phase = Phase.INIT;
    private boolean waitingSwap;
    private int exitDelay;
    private long frames;

    public CdOpeningProgram(CdFmvAssets assets, CdFmvStreamer streamer) {
        this.streamer = Objects.requireNonNull(streamer, "streamer");
        palOpening = assets.bytes("palOpening");
        start(assets);
    }

    /** {@code Start} (:76-136) up to the first bank: hardware set-up, tilemaps and border, display on. */
    private void start(CdFmvAssets assets) {
        // InitMD (:524-560). VDPRegs (:567-584): H40 ($8C81) and 128 x 32-cell planes ($9003); plane A at $C000,
        // plane B at $A000, the scroll table at $E400; display off ($8134). The VRAM clear empties tiles and planes;
        // plane A's $1000 words of $E7E1 fill every cell; Pal_OpenFMV fills the palette; VSRAM 0.
        screen.setLayout(320, 128, 32);
        screen.setDisplay(false);
        screen.clearTilesAndPlanes();
        screen.fillPlane(CdProgramScreenState.Plane.A, 0xE7E1);
        for (int i = 0; i < 64; i++) screen.setPalette(i, word(palOpening, 2 * i));
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
        // Display buffer 2 (:93-94): $0200,$0200 into the scroll table, both planes' horizontal scroll.
        screen.setHScroll(CdProgramScreenState.Plane.A, 0x0200);
        screen.setHScroll(CdProgramScreenState.Plane.B, 0x0200);
        // DrawFMVTilemaps (:454-470): buffer 1 at $C608 (cell 4, row 6) from tile $10, buffer 2 at $C688 (cell 68).
        drawMap(4, 0x10);
        drawMap(68, 0x1D0);
        // DrawBorder (:599-615): border art (DMA68K of $1C00 bytes to $7D00, tile $3E8, past the art into the map)
        // and its 40 x 28 map at $A000, plane B from cell 0, row 0.
        byte[] art = assets.from("artBorderOpening", 0x1C00);
        screen.writeTiles(0x7D00 / TILE, art, 0, 0x1C00);
        byte[] map = assets.bytes("mapBorderOpening");
        for (int row = 0; row < 0x1C; row++) {
            for (int column = 0; column < 0x28; column++) {
                screen.setPlaneCell(CdProgramScreenState.Plane.B, column, row, word(map, 2 * (row * 0x28 + column)));
            }
        }
        screen.setDisplay(true);                           // bset #6,vdpReg1+1 (:122-123)
        readRoutine = 4;
        waitingSwap = true;                                // .WaitSubCPU, then WaitWordRAMSwap (:125-133)
        phase = Phase.LOOP;
    }

    private void drawMap(int firstColumn, int tile) {
        for (int row = 6; row < 6 + 14; row++) {
            for (int column = 0; column < 32; column++) screen.setPlaneCell(CdProgramScreenState.Plane.A, firstColumn + column, row, tile++);
        }
    }

    /** One V-int and the main program's work until its next VSync. */
    public void frame(int pad) {
        frames++;
        vInterrupt(pad);
        streamer.advanceVint();
        main();
        streamer.playVint();
    }

    /** {@code VInterrupt} (:589-660): its work runs only after a VSync request; otherwise it is a lag frame. */
    private void vInterrupt(int pad) {
        if (!vsyncFlag) return;
        vsyncFlag = false;
        switch (vintRoutine) {
            case 1 -> { displayBuffer(2); copyFrameData(); }
            case 2, 3, 4, 6, 7, 8 -> copyFrameData();
            case 5 -> { displayBuffer(1); copyFrameData(); }
            default -> loadFramePal();
        }
        // ReadControllers (:536-568): held buttons and newly pressed ones.
        padTap = pad & ~padHold;
        padHold = pad;
    }

    private void displayBuffer(int buffer) {
        // VInt_DisplayBuf2 writes $0200,$0000 to the scroll table; VInt_DisplayBuf1 $0000,$0000: plane A moved
        // 512 pixels shows buffer 2's cells (columns 68-99), unmoved buffer 1's (4-35); plane B (the border) in place.
        screen.setHScroll(CdProgramScreenState.Plane.A, buffer == 2 ? 0x0200 : 0);
        screen.setHScroll(CdProgramScreenState.Plane.B, 0);
    }

    private void copyFrameData() {
        if (dmaActive) {
            dmaActive = false;
            screen.writeTiles(dmaTile, streamer.shownBank(), dmaSource, dmaBytes);
        }
        loadFramePal();
    }

    /** {@code VInt_LoadFramePal}: the display line into CRAM line 0, the border line too once the fade asks. */
    private void loadFramePal() {
        for (int i = 0; i < 16; i++) screen.setPalette(i, palette[0x10 + i]);
        if (updateBorderPal) {
            updateBorderPal = false;
            for (int i = 0; i < 16; i++) screen.setPalette(0x10 + i, palette[0x20 + i]);
        }
        screen.setDisplay(true);
    }

    private void main() {
        switch (phase) {
            case LOOP -> loop();
            case EXIT_DELAY -> exitDelay();
            case FADE -> fade();
            default -> { }
        }
    }

    private void loop() {
        // MainLoop's exit test runs after each VSync (:146-152).
        if (!waitingSwap && streamer.finished()) {
            phase = Phase.EXIT_DELAY;
            vintRoutine = 0;                               // Exit: clr.w vintRoutine (:168)
            exitDelay = 61;                                // move.w #60,d1 / dbf: 61 VSyncs
            exitDelay();
            return;
        }
        while (true) {
            if (waitingSwap) {
                if (!waitWordRamSwap()) return;            // still waiting: a lag frame
                waitingSwap = false;
            }
            if (!readRoutine()) {                          // .SwapWordRAM: no VSync, read the new bank at once
                waitingSwap = true;
                continue;
            }
            vsyncFlag = true;
            return;
        }
    }

    /** {@code WaitWordRAMSwap} (:572-596): true once the banks swapped or the sub program has finished. */
    private boolean waitWordRamSwap() {
        if (streamer.finished()) return true;
        if (!streamer.bankReady() || streamer.stopRequested()) return false;
        streamer.takeBank();
        return true;
    }

    /** One data-read routine; false when it went to wait for the next bank without a VSync. */
    private boolean readRoutine() {
        switch (readRoutine) {
            case 4 -> { dataAddress = 0; frameCount = 0; sect1(); }
            case 6 -> sect1();
            case 8 -> { vintRoutine = 2; readRoutine = 0x0A; queue(0x14C0 / TILE, 0x12C0); }
            case 0x0A -> { vintRoutine = 3; readRoutine = 0x0C; queue(0x2780 / TILE, 0x1280); }
            case 0x0C -> { vintRoutine = 4; readRoutine = 0x0E; }
            case 0x0E -> { vintRoutine = 0; return nextFrame(); }
            case 0x10 -> { vintRoutine = 6; readRoutine = 0x12; queue(0x4CC0 / TILE, 0x12C0); }
            case 0x12 -> { vintRoutine = 7; readRoutine = 0x14; queue(0x5F80 / TILE, 0x1280); }
            case 0x14 -> { vintRoutine = 8; return nextFrame(); }
            case 0x16 -> { return nextFrame(); }
            case 0x18 -> readRoutine = 0x1A;
            case 0x1A -> readRoutine = 0x1C;
            case 0x1C -> readRoutine = 6;
            default -> { }
        }
        return true;
    }

    /** {@code FrameRead_Sect1} (:191-219): move the work palette to the display line, load the next, queue section 1. */
    private void sect1() {
        byte[] bank = streamer.shownBank();
        System.arraycopy(palette, 0, palette, 0x10, 0x10);
        for (int i = 0; i < 16; i++) palette[i] = word(bank, dataAddress + 2 * i);
        dataAddress += 0x20;
        if ((frameCount & 1) == 0) {
            vintRoutine = 5;
            readRoutine = 0x10;
            queue(0x3A00 / TILE, 0x12C0);
        } else {
            readRoutine = 8;
            vintRoutine = 1;
            queue(0x0200 / TILE, 0x12C0);
        }
    }

    private void queue(int tile, int bytes) {
        dmaActive = true;
        dmaTile = tile;
        dmaSource = dataAddress;
        dmaBytes = bytes;
        dataAddress += bytes;
    }

    /** {@code FrameRead_NextFrame} (:317-331): the stop check, then the next frame or the next bank. */
    private boolean nextFrame() {
        if ((padHold & START) != 0) streamer.requestStop(); // CheckFMVStop (:488-492)
        vintRoutine = 0;
        frameCount++;
        if (frameCount < 8) {
            readRoutine = 0x18;
            return true;
        }
        readRoutine = 4;
        return false;
    }

    private void exitDelay() {
        if (exitDelay-- > 0) {
            vsyncFlag = true;
            return;
        }
        // bset #4,GAMAINFLAG; the sub program has already finished (.WaitSubCPU :180-187). FadeToBlack (:398-452).
        for (int i = 0; i < 16; i++) palette[0x20 + i] = word(palOpening, 0x20 + 2 * i);
        palFadeCount = 7;
        phase = Phase.FADE;
        fadeStep();
    }

    private void fade() {
        if (--palFadeDelay > 0) {
            vsyncFlag = true;
            return;
        }
        if (--palFadeCount < 0) {
            phase = Phase.DONE;                            // rts with d0.b = $0F: mmdReturnCode positive, no replay
            return;
        }
        fadeStep();
    }

    /** {@code .FadeLoop}: one step of red -2, green -$20, blue -$200 over the display and border lines. */
    private void fadeStep() {
        palFadeDelay = 2;
        for (int i = 0x10; i < 0x30; i++) {
            int c = palette[i];
            int r = c & 0xF, g = c & 0xF0, b = c & 0xF00;
            if (r != 0) r -= 2;
            if (g != 0) g -= 0x20;
            if (b != 0) b -= 0x200;
            palette[i] = b | g | r;
        }
        updateBorderPal = true;
        vsyncFlag = true;
    }

    public boolean done() { return phase == Phase.DONE; }

    /** {@code mmdReturnCode}: d0.b when the program returns; negative asks IPX to run it again. */
    public int returnCode() { return 0x0F; }

    public CdProgramScreenState screen() { return screen; }

    public CdFmvStreamer streamer() { return streamer; }

    public long frames() { return frames; }

    /** Program state for tests: readRoutine, vintRoutine and frameCount as native RAM holds them. */
    public int readRoutineValue() { return readRoutine; }

    public int vintRoutineValue() { return vintRoutine; }

    public int frameCountValue() { return frameCount; }

    static int word(byte[] data, int at) {
        return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF);
    }
}
