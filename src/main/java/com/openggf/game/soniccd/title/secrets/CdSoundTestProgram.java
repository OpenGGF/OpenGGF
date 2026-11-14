package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The sound test (SOSEL_.MMD, {@code Title Screen/Secrets/Sound Test.asm}) as an explicit state machine, one
 * {@link #frame(int)} per V-int. Left/Right picks FM, PCM or DA; Up/Down picks a number in that column; A, B or C
 * plays it (FM through the FM driver's queue, PCM and DA through sub-CPU commands, {@code SelectFM}/{@code SelectPCM}/
 * {@code SelectCDDA} :288-510). Start stops CD audio and PCM and returns the exit code of the first code the three
 * numbers match ({@code CheckEasterEgg} :242-284): 1 Special Stage 8 (after "WELCOME TO SECRET SPECIAL STAGE" and two
 * seconds), 2-6 the hidden pictures, 0 nothing. The program's own requests are drained each frame.
 */
public final class CdSoundTestProgram {
    /** One audio request: an FM sound ID for the FM driver's queue, or a sub-CPU command ID. */
    public record Request(boolean fm, int id) { }

    private enum Phase { FADE_IN, MAIN, FADE_OUT, WAIT_SS8, DONE }

    /**
     * Plane A cells of the three numbers' top rows ({@code DrawFMSel}/{@code DrawPCMSel}/{@code DrawCDDASel}, :615-655):
     * $C596, $C5AC and $C5C0 are row 11, cells 11, 22 and 32 of the 64-cell plane at $C000.
     */
    private static final int NUMBER_ROW = 11;
    private static final int[] NUMBER_COLUMN = {11, 22, 32};
    /** {@code TEXTPTR} positions of the strings (:867-899). */
    private static final int[][] TEXT_AT = {{15, 7}, {5, 11}, {15, 11}, {26, 11}, {4, 18}};
    private static final String[] TEXT = {"textSoundTest", "textFmNo", "textPcmNo", "textCddaNo", "textSpecStg8"};
    /** System.inc command IDs the program sends (SCMD_STOPCDDA, SCMD_STOPPCM), from the generated command table. */
    static final int STOP_CDDA = CdSubCpuAudio.commandId("SCMD_STOPCDDA"), STOP_PCM = CdSubCpuAudio.commandId("SCMD_STOPPCM");
    /** {@code FM_RINGL} and {@code FM_SSWARP}: {@code rsset $90} numbering (Sound Drivers/FM/_Sound IDs.inc:8-66). */
    static final int FM_RINGL = 0xA8, FM_SSWARP = 0xC8;

    private final CdSecretScreen screen = new CdSecretScreen();
    private final byte[] tilesUpper, tilesLower, digitsUpper, digitsLower, codes;
    private final byte[][] ids = new byte[3][];
    private final byte[][] text = new byte[5][];
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();
    // Selection data (:517-532), in the program image with these first values.
    private int lrDelay = 0, fmDelay = 2, pcmDelay = 4, cddaDelay = 6;
    private int soundType;
    private final int[] selection = new int[3];
    private Phase phase = Phase.FADE_IN;
    private int step;
    private boolean firstPass = true;
    private int exitCode = -1;
    private int waitFrames;
    private boolean debugCheat;

    public CdSoundTestProgram(CdFmvAssets assets) {
        tilesUpper = assets.bytes("soundTestTilesUpper");
        tilesLower = assets.bytes("soundTestTilesLower");
        digitsUpper = assets.bytes("soundTestDigitsUpper");
        digitsLower = assets.bytes("soundTestDigitsLower");
        codes = assets.bytes("soundTestCodes");
        ids[0] = assets.bytes("soundTestFmIds");
        ids[1] = assets.bytes("soundTestPcmIds");
        ids[2] = assets.bytes("soundTestCddaIds");
        for (int i = 0; i < TEXT.length; i++) text[i] = assets.bytes(TEXT[i]);
        // Start (:28-71): InitMD, scroll table clear, art, background, header, palette, then fade from black.
        // VDPRegs (:970-990): H40, 64 x 32-cell planes, plane A $C000, plane B $E000, scroll table $F400 (cleared).
        screen.initMd(320, 64, 32);
        screen.screen.setHScroll(CdProgramScreenState.Plane.A, 0);
        screen.screen.setHScroll(CdProgramScreenState.Plane.B, 0);
        byte[] art = CdSecretScreen.nemesis(assets.bytes("artSoundTest"));
        screen.screen.writeTiles(0, art, 0, art.length);         // NemDec from VRAM 0
        screen.drawMap(CdProgramScreenState.Plane.B, assets.bytes("mapSoundTestBg"), 0x28, 0x1C);   // $E000
        drawText(0, 0x8000);
        byte[] pal = assets.bytes("palSoundTest");
        for (int i = 0; i < 64; i++) screen.palette[i] = CdSecretScreen.word(pal, 2 * i);
        screen.requestCram();
        screen.prepFade(false);
        screen.vsync();
    }

    /** One V-int and the program up to its next VSync. */
    public void frame(int pad) {
        screen.vInterrupt(pad);
        switch (phase) {
            case FADE_IN -> {
                screen.fadeStep(CdSecretScreen.Fade.FROM_BLACK, step++);
                if (step == CdSecretScreen.FADE_STEPS) phase = Phase.MAIN;
            }
            case MAIN -> main();
            case WAIT_SS8 -> {
                if (--waitFrames >= 0) { screen.vsync(); return; }
                requests.add(new Request(true, FM_SSWARP));
                fadeOut(CdSecretScreen.Fade.TO_WHITE);
            }
            case FADE_OUT -> {
                if (step == CdSecretScreen.FADE_STEPS) { phase = Phase.DONE; return; }
                screen.fadeStep(fade, step++);
            }
            case DONE -> { }
        }
    }

    private CdSecretScreen.Fade fade;

    private void fadeOut(CdSecretScreen.Fade kind) {
        fade = kind;
        phase = Phase.FADE_OUT;
        step = 0;
        screen.fadeStep(fade, step++);
    }

    private void main() {
        if (!pending.isEmpty()) {                          // the rest of a pass that waited for a VSync
            pending.poll().run();
            screen.vsync();
            return;
        }
        if (!firstPass && (screen.hold & CdSecretScreen.START) != 0) {
            // btst #7,ctrlHold after the VSync (:122-131): stop CD audio, VSync, stop PCM, VSync, then the exit.
            requests.add(new Request(false, STOP_CDDA));
            pending.add(() -> requests.add(new Request(false, STOP_PCM)));
            pending.add(this::exit);
            screen.vsync();
            return;
        }
        firstPass = false;
        pass();
    }

    /** One {@code MainLoop} pass up to its VSync (:74-120). */
    private void pass() {
        lrDelay = (lrDelay + 1) & 0xFFFF;
        fmDelay = (fmDelay + 1) & 0xFFFF;
        pcmDelay = (pcmDelay + 1) & 0xFFFF;
        cddaDelay = (cddaDelay + 1) & 0xFFFF;
        if ((lrDelay & 7) == 0) {
            if ((screen.hold & CdSecretScreen.LEFT) != 0) soundType = soundType - 1 < 0 ? 2 : soundType - 1;
            if ((screen.hold & CdSecretScreen.RIGHT) != 0) soundType = soundType + 1 > 2 ? 0 : soundType + 1;
        }
        drawText(1, 0x6000);                               // DrawTypesText: all three, then the chosen one highlighted
        drawText(2, 0x6000);
        drawText(3, 0x6000);
        drawText(1 + soundType, 0x2000);
        for (int column = 0; column < 3; column++) drawNumber(column);
        int delay = soundType == 0 ? fmDelay : soundType == 1 ? pcmDelay : cddaDelay;
        if ((delay & 7) == 0) {                            // UpdateSelection (:565-580): Up decreases, Down increases
            int max = ids[soundType].length - 1;
            int v = selection[soundType];
            if ((screen.hold & CdSecretScreen.UP) != 0) v = v - 1 < 0 ? max : v - 1;
            if ((screen.hold & CdSecretScreen.DOWN) != 0) v = v + 1 > max ? 0 : v + 1;
            selection[soundType] = v;
        }
        if ((screen.tap & (CdSecretScreen.A | CdSecretScreen.B | CdSecretScreen.C)) != 0) {
            int id = ids[soundType][selection[soundType]] & 0xFF;
            switch (soundType) {
                case 0 -> requests.add(new Request(true, id));
                case 1 -> {                                // stop CD audio, VSync, stop PCM, VSync, play
                    requests.add(new Request(false, STOP_CDDA));
                    pending.add(() -> requests.add(new Request(false, STOP_PCM)));
                    pending.add(() -> requests.add(new Request(false, id)));
                }
                default -> {                               // stop PCM, VSync, play
                    requests.add(new Request(false, STOP_PCM));
                    pending.add(() -> requests.add(new Request(false, id)));
                }
            }
        }
        screen.vsync();
    }

    /** {@code CheckEasterEgg}, then the exit routine (:137-240). */
    private void exit() {
        exitCode = 0;
        for (int code = 1; ; code++) {
            int fm = CdSecretScreen.word(codes, 6 * code);
            if ((short) fm < 0) { exitCode = 0; break; }
            if (fm == selection[0] && CdSecretScreen.word(codes, 6 * code + 2) == selection[1]
                    && CdSecretScreen.word(codes, 6 * code + 4) == selection[2]) { exitCode = code; break; }
        }
        switch (exitCode) {
            case 0 -> fadeOut(CdSecretScreen.Fade.TO_BLACK);
            case 1 -> {
                requests.add(new Request(true, FM_RINGL));
                drawText(4, 0x6000);
                waitFrames = 60 * 2 - 1;                   // move.w #(60*2)-1,d7 / dbf: 120 VSyncs
                phase = Phase.WAIT_SS8;
                screen.vsync();
            }
            default -> {
                requests.add(new Request(true, FM_SSWARP));
                if (exitCode == 4) debugCheat = true;      // move.w #1,debugCheat (SoundTest_Tails)
                fadeOut(CdSecretScreen.Fade.TO_WHITE);
            }
        }
    }

    /** {@code DrawText}: two plane A rows per character from the tile tables plus the base. */
    private void drawText(int which, int base) {
        int x = TEXT_AT[which][0], y = TEXT_AT[which][1];
        byte[] string = text[which];
        for (int i = 0; i < string.length; i++) {
            int c = string[i] & 0xFF;
            screen.textCell(x + i, y, ((tilesUpper[c] & 0xFF) + base) & 0xFFFF);
            screen.textCell(x + i, y + 1, ((tilesLower[c] & 0xFF) + base) & 0xFFFF);
        }
    }

    /** {@code DrawNumber} with two decimal digits ({@code DrawNum_10}). */
    private void drawNumber(int column) {
        int value = selection[column];
        int[] digits = {value / 10, value % 10};
        for (int i = 0; i < 2; i++) {
            screen.textCell(NUMBER_COLUMN[column] + i, NUMBER_ROW, CdSecretScreen.word(digitsUpper, 2 * digits[i]));
            screen.textCell(NUMBER_COLUMN[column] + i, NUMBER_ROW + 1, CdSecretScreen.word(digitsLower, 2 * digits[i]));
        }
    }

    /** The audio requests of the last frame, in order. */
    public List<Request> drainRequests() {
        List<Request> out = List.copyOf(requests);
        requests.clear();
        return out;
    }

    public boolean done() { return phase == Phase.DONE; }

    /** The exit code ({@code d0}) the program returns, -1 before it returns. */
    public int exitCode() { return phase == Phase.DONE ? exitCode : -1; }

    /** {@code debugCheat} as {@code SoundTest_Tails} sets it. */
    public boolean debugCheat() { return debugCheat; }

    public int[] selection() { return selection.clone(); }

    public int soundType() { return soundType; }

    public CdProgramScreenState screen() { return screen.screen; }
}
