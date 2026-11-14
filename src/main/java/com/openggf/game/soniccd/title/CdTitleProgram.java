package com.openggf.game.soniccd.title;

import com.openggf.game.soniccd.program.CdProgramScreenRenderer.Sprite;
import com.openggf.game.soniccd.title.CdTitleAssets.Piece;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * The title screen's main-CPU program ({@code Title Screen/Main.asm}) as game logic, one call of
 * {@link #frame} per native V-blank: the program runs up to its next {@code VSync}, then that
 * V-blank's effects (controller read, timer, cloud image copy) apply. It owns the screen state the
 * renderer draws: a tile store indexed like the program's VRAM tiles, two 64x32 plane maps, the
 * 64-colour palette, per-line scroll and the sprite list built by {@code RunObjects}. Nothing here
 * models VDP registers, DMA or timing; loads that native spreads over lag frames complete in the
 * frame that requests them. Paths are relative to {@code docs/scddisasm/src/Title Screen}.
 */
public final class CdTitleProgram {
    /** Typed sound requests for the audio owner; this program plays nothing itself. */
    public enum SoundRequest { RING }

    /** The program's position between V-blanks. */
    public enum Phase { BOOT, ENABLE, DELAY, SONIC_INIT, FADE_IN, WAIT_TURN, FLASH_WHITE, MENU_A1, MENU_A2,
        MENU_B1, MENU_B2, FADE_OUT, DONE }

    // Main.asm variables and constants.
    private static final int OBJECTS = 10;                      // object0-object9 outside Japan (Main.asm:51-62)
    private static final int USA_DELAY = 48;                    // move.w #48-1,d7 (Main.asm:209-214)
    private static final int FADE_FRAMES = 22;                  // move.w #(7*3),d4; dbf (Main.asm:929-937 and fades)
    private static final int MENU_TIMER = 0x3FC, MENU_TIMER_RESET = 0x1E0;   // ObjMenu (Menu/Main.asm:31-35, 72)
    private static final int CLOUD_LINES = 88;                  // (IMGHEIGHT-8) lines (Main.asm:503, 537)
    private static final int WATER_TOP = 160, WATER_LINES = 64; // hscroll+(160*4), 64 lines (Main.asm:510-531)

    private final CdTitleAssets assets;
    private final CdTitleClouds clouds;
    private final int titleFlags;
    final byte[] vram = new byte[0x10000];
    final BitSet dirtyTiles = new BitSet(0x800);
    final int[] planeA = new int[64 * 32];
    final int[] planeB = new int[64 * 32];
    final int[] palette = new int[64];
    private final int[] fadePalette = new int[64];
    final int[] scrollB = new int[224];
    private final int[] scrollBuf = new int[WATER_LINES];
    final List<Sprite> sprites = new ArrayList<>();
    private final TitleObject[] slots = new TitleObject[OBJECTS];
    private final List<SoundRequest> sounds = new ArrayList<>();

    private Phase phase = Phase.BOOT;
    private int counter;
    private boolean displayEnabled;
    private int vintRoutine = 4;
    private int titleMode;
    private int timer;
    private int exitFlag;
    private int exitCode = -1;
    private int menuSel;
    private final int[] menuOptions = new int[8];
    private int p1Held, p1Tap, p2Held;
    private boolean cloudsControl;
    private int palCycleFrame, palCycleDelay;
    private byte[] cloudsImage = new byte[CdTitleClouds.IMAGE_BYTES];
    private byte[] rendered = cloudsImage;
    private int halves;
    private long frames;

    public CdTitleProgram(CdTitleAssets assets, int titleFlags) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.clouds = new CdTitleClouds(assets);
        this.titleFlags = titleFlags & 0xFF;
    }

    /**
     * One native frame: the V-blank that ends the previous {@code VSync} wait (controller read in
     * routines 0-3 and 5, timer, counters), then the program up to its next {@code VSync}.
     *
     * @param p1 player 1 held buttons in native order (bit 0 Up .. bit 7 Start)
     * @param p2 player 2 held buttons
     */
    public void frame(int p1, int p2) {
        frames++;
        if (phase != Phase.BOOT) vblank(p1 & 0xFF, p2 & 0xFF);
        switch (phase) {
            case BOOT -> boot();
            case ENABLE -> {                                     // enableDisplay; vintRoutine 4 (Main.asm:203-206)
                displayEnabled = true;
                vintRoutine = 4;
                phase = Phase.DELAY;
                counter = 0;
            }
            case DELAY -> { if (++counter == USA_DELAY) phase = Phase.SONIC_INIT; }
            case SONIC_INIT -> {                                 // RunObjects; vint 5 (Main.asm:217-219)
                runObjects();
                vintRoutine = 5;
                phase = Phase.FADE_IN;
                counter = 0;
            }
            case FADE_IN -> fadeInSonic();
            case WAIT_TURN -> waitSonicTurn();
            case FLASH_WHITE -> flashWhite();
            case MENU_A1 -> { if (!endHalf()) menuA1(); }
            case MENU_A2 -> {                                    // ClearSprites; RunObjects; vint 1 (Main.asm:279-282)
                runObjects();
                vintRoutine = 1;
                phase = Phase.MENU_B1;
            }
            case MENU_B1 -> menuB1();
            case MENU_B2 -> {                                    // ClearSprites; RunObjects; vint 3 (Main.asm:299-302)
                runObjects();
                vintRoutine = 3;
                phase = Phase.MENU_A1;
            }
            case FADE_OUT -> fadeOut();
            case DONE -> { }
        }
        copyClouds();
    }

    /** Start (Main.asm:132-201): hardware init, tilemaps, art, Sonic, the clouds map, then VSync. */
    private void boot() {
        System.arraycopy(assets.palInit, 0, palette, 0, 64);    // InitMD .LoadPal (scddisasm/src/Title Screen/Main.asm:818-823)
        drawTilemaps();
        loadArt(0xD800, assets.artPressStart);
        loadArt(0xDC00, assets.artMenuArrow);
        loadArt(0xDE00, assets.artCopyright);                    // REGION=USA (Main.asm:175-181)
        loadArt(0xDFC0, assets.artTm);
        loadArt(0xF000, assets.artBanner);
        loadArt(0x6D00, assets.artSonic);
        spawn(new Sonic());
        loadArt(0xBC20, assets.artSolidColor);
        drawCloudsMap();
        vintRoutine = 0;                                         // cleared variables: the first VSync runs routine 0
        phase = Phase.ENABLE;
    }

    /** FadeFromBlack of palette line 3 (Main.asm:221-226, 999-1022), then the first turn wait. */
    private void fadeInSonic() {
        if (counter == 0) {
            System.arraycopy(assets.palTitle, 0x30, fadePalette, 0x30, 16);
            for (int i = 0x30; i < 0x40; i++) palette[i] = 0;
        } else {
            fadeFromBlack(0x30, 16);
        }
        if (++counter <= FADE_FRAMES) return;
        phase = Phase.WAIT_TURN;
        waitSonicTurn();
    }

    /** .WaitSonicTurn (Main.asm:228-238); the turned frame goes straight on to .FlashWhite. */
    private void waitSonicTurn() {
        titleMode = 1;
        runObjects();
        if ((titleMode & 0x80) == 0) return;
        titleMode &= ~0x80;
        loadArt(0x6D80, assets.artEmblem);                       // .FlashWhite (Main.asm:240-251)
        System.arraycopy(assets.palTitle, 0, fadePalette, 0, 64);
        for (int i = 0; i < 0x30; i++) palette[i] = 0xEEE;       // FadeFromWhite2 (scddisasm/src/Title Screen/Main.asm:1086-1099)
        palette[0x2E] = 0;
        phase = Phase.FLASH_WHITE;
        counter = 0;
    }

    /** FadeFromWhite2's 22 frames, then the planet, menu, copyright and TM (Main.asm:252-262). */
    private void flashWhite() {
        fadeFromWhite(0, 0x30);
        if (++counter < FADE_FRAMES) return;
        spawn(new Planet());
        spawn(new Menu());
        spawn(new Copyright());
        spawn(new Tm());
        menuA1();
    }

    /** MainLoop, first half (Main.asm:266-277): show clouds buffer 2, render into buffer 1. */
    private void menuA1() {
        titleMode = 2;
        renderClouds();
        runObjects();
        paletteCycle();
        scrollBackground(0x100);
        vintRoutine = 0;
        phase = Phase.MENU_A2;
    }

    /** End of the first half and the second half (Main.asm:284-297). */
    private void menuB1() {
        if (endHalf()) return;
        runObjects();
        paletteCycle();
        renderClouds();
        scrollBackground(0);
        vintRoutine = 2;
        phase = Phase.MENU_B2;
    }

    /** GetCloudsImage, then {@code tst.b exitFlag} (Main.asm:284-290, 304-310). */
    private boolean endHalf() {
        cloudsImage = rendered;
        halves++;
        if (exitFlag == 0) return false;
        vintRoutine = 5;                                         // .Exit/.FadeOut (scddisasm/src/DA Garden/Main.asm:312-331)
        phase = Phase.FADE_OUT;
        counter = 0;
        return true;
    }

    /** FadeToBlack (scddisasm/src/Title Screen/Main.asm:929-942): 22 times VSync then a fade step; the program returns after the last. */
    private void fadeOut() {
        fadeToBlack(0, 64);
        if (++counter < FADE_FRAMES) return;
        // moveq #0,d1; move.b exitFlag,d1; bmi .NegFlag (Main.asm:326-336).
        exitCode = (exitFlag & 0x80) != 0 ? 0 : exitFlag;
        phase = Phase.DONE;
    }

    /** RenderClouds (scddisasm/src/Title Screen/Main.asm:685-711): one sub-CPU graphics command; GetCloudsImage takes its image. */
    private void renderClouds() {
        rendered = clouds.render(cloudsControl ? p2Held : 0);
    }

    /** The V-blank routine of the previous VSync (scddisasm/src/Title Screen/Main.asm:354-467). */
    private void vblank(int p1, int p2) {
        if (vintRoutine != 4) {                                  // VInt_Nothing does not read the pads
            p1Tap = p1 & ~p1Held;                                // ReadController (Main.asm:582-606)
            p1Held = p1;
            p2Held = p2;
        }
        if (timer != 0) timer--;                                 // VInt_Finish (scddisasm/src/Title Screen/Main.asm:446-450)
    }

    /** VInt_CopyClouds*: the cloud image halves this V-blank copies into the buffer tiles (Main.asm:393-422). */
    private void copyClouds() {
        if (vintRoutine > 3) return;
        int buffer = vintRoutine >> 1, part = vintRoutine & 1;
        int half = CdTitleClouds.IMAGE_BYTES / 2;
        int dest = 0x20 + buffer * CdTitleClouds.IMAGE_BYTES + part * half;
        System.arraycopy(cloudsImage, part * half, vram, dest, half);
        dirtyTiles.set(dest >> 5, (dest + half) >> 5);
    }

    /** ScrollBgBuf1/ScrollBgBuf2 (scddisasm/src/Title Screen/Main.asm:501-567): cloud buffer select and the water lines. */
    private void scrollBackground(int cloudScroll) {
        for (int line = 0; line < CLOUD_LINES; line++) scrollB[line] = cloudScroll;
        int speed = 0x4000;
        for (int i = 0; i < WATER_LINES; i++) {
            scrollBuf[i] += speed;
            speed += 0x1000;
            scrollB[WATER_TOP + i] = scrollBuf[i] >>> 16;
        }
    }

    /** PaletteCycle (scddisasm/src/Title Screen/Main.asm:609-643): line 0 colours 5-8 every fourth call. */
    private void paletteCycle() {
        palCycleDelay += 0x40;
        if (palCycleDelay < 0x100) return;
        palCycleDelay &= 0xFF;
        palCycleFrame = palCycleFrame + 1 >= 3 ? 0 : palCycleFrame + 1;
        System.arraycopy(assets.waterPalCycle, palCycleFrame * 4, palette, 5, 4);
    }

    /** DrawTilemaps (scddisasm/src/Title Screen/Main.asm:1732-1827). */
    private void drawTilemaps() {
        // Emblem: plane A at $C206 (column 3, row 4), 26x19; nonzero words + $C000|($6D80/$20).
        for (int y = 0; y < 19; y++) for (int x = 0; x < 26; x++) {
            int word = assets.mapEmblem[y * 26 + x];
            planeA[(4 + y) * 64 + 3 + x] = word == 0 ? 0 : (word + (0xC000 | (0x6D80 >> 5))) & 0xFFFF;
        }
        // Water at $EA00 and $EA40 (row 20, columns 0 and 32), mountains at $E580 (row 11): $300|byte.
        background(assets.mapWater, 20, 0, 8);
        background(assets.mapWater, 20, 32, 8);
        background(assets.mapMountains, 11, 0, 9);
    }

    private void background(byte[] map, int row, int column, int rows) {
        for (int y = 0; y < rows; y++) for (int x = 0; x < 32; x++) {
            int b = map[y * 32 + x] & 0xFF;
            planeB[(row + y) * 64 + column + x] = b == 0 ? 0 : 0x300 | b;
        }
    }

    /** DrawCloudsMap (scddisasm/src/Title Screen/Main.asm:646-683): buffer 1 at $E000 from tile $8001, buffer 2 at $E040 from $8181. */
    private void drawCloudsMap() {
        for (int buffer = 0; buffer < 2; buffer++) {
            int first = buffer == 0 ? 0x8001 : 0x8181;
            for (int row = 0; row < 12; row++) for (int col = 0; col < 32; col++) {
                planeB[row * 64 + buffer * 32 + col] = first + col * 12 + row;
            }
        }
    }

    private void loadArt(int address, byte[] art) {
        System.arraycopy(art, 0, vram, address, Math.min(art.length, vram.length - address));
        dirtyTiles.set(address >> 5, Math.min(0x800, (address + art.length + 31) >> 5));
    }

    // ---- palette fades (Main.asm:929-1255) ----

    private void fadeFromBlack(int start, int count) {
        for (int i = start; i < start + count; i++) {
            int target = fadePalette[i], c = palette[i];
            if (c == target) continue;
            if (c + 0x200 <= target) palette[i] = c + 0x200;
            else if (c + 0x20 <= target) palette[i] = c + 0x20;
            else palette[i] = c + 2;
        }
    }

    private void fadeFromWhite(int start, int count) {
        for (int i = start; i < start + count; i++) {
            int target = fadePalette[i], c = palette[i];
            if (c == target) continue;
            if (c >= 0x200 && c - 0x200 >= target) palette[i] = c - 0x200;
            else if (c >= 0x20 && c - 0x20 >= target) palette[i] = c - 0x20;
            else palette[i] = (c - 2) & 0xFFFF;
        }
    }

    private void fadeToBlack(int start, int count) {
        for (int i = start; i < start + count; i++) {
            int c = palette[i];
            if (c == 0) continue;
            if ((c & 0xE) != 0) palette[i] = c - 2;
            else if ((c & 0xE0) != 0) palette[i] = c - 0x20;
            else if ((c & 0xE00) != 0) palette[i] = c - 0x200;
        }
    }

    // ---- objects (Main.asm:1464-1720) ----

    /** ClearSprites then RunObjects: run every active slot, then draw from the last slot to the first. */
    private void runObjects() {
        sprites.clear();
        for (TitleObject object : slots) if (object != null) object.run();
        for (int i = OBJECTS - 1; i >= 0; i--) {
            TitleObject object = slots[i];
            if (object != null && object.visible) object.draw();
        }
    }

    private TitleObject spawn(TitleObject object) {
        for (int i = 0; i < OBJECTS; i++) {
            if (slots[i] == null) {
                slots[i] = object;
                return object;
            }
        }
        return null;                                             // SpawnObject .NotFound: nothing spawns
    }

    private abstract class TitleObject {
        List<List<Piece>> map;
        int tile, frame, x, y;
        boolean visible;
        abstract void run();

        /** DrawObject (scddisasm/src/Title Screen/Main.asm:1655-1718); H32 allows 64 sprites. */
        void draw() {
            for (Piece piece : map.get(frame)) {
                if (sprites.size() >= 63) return;              // cmpi.b #64,spriteCount, counted from 1
                int t = (piece.tile() + tile) & 0xFFFF;
                if ((t & 0x7FF) == (0xBC20 >> 5)) t -= 0x2000;   // the solid-colour tiles use the line below
                int sx = (x + 128 + piece.x()) & 0x1FF;
                if (sx == 0) sx = 1;                             // keep away from the masking X
                sprites.add(new Sprite(sx - 128, y + piece.y(), piece.size(), t));
            }
        }
    }

    /** ObjSonic (Sonic/Main.asm:12-108): six timed frames, the turn flag, the arm and the banner. */
    private final class Sonic extends TitleObject {
        private int step = -1, delay;

        @Override void run() {
            if (step < 0) {
                map = assets.sprSonic;
                tile = 0xE000 | (0x6D00 >> 5);
                visible = true;
                x = 91;
                y = 15;
                delay = 2;
                step = 0;
                return;
            }
            if (step > 5 || --delay != 0) return;
            switch (step) {
                case 0 -> { frame++; delay = 3; }
                case 1 -> { loadArt(0x6000, assets.artMountains); frame++; delay = 2; }
                case 2 -> { loadArt(0x6B00, assets.artWater); frame++; delay = 1; }
                case 3 -> {
                    titleMode |= 0x80;
                    spawn(new SonicArm());
                    spawn(new Banner());
                    frame++;
                    delay = 0x14;
                }
                case 4 -> { frame++; delay = 4; }
                case 5 -> frame = 4;
                default -> { }
            }
            step++;
        }
    }

    /** ObjSonicArm (scddisasm/src/Title Screen/Objects/Sonic/Main.asm:128-170): waits for its delay counter to carry, then waves once. */
    private final class SonicArm extends TitleObject {
        private boolean started, animating;
        private int delay, armFrame;

        @Override void run() {
            if (!started) {
                map = assets.sprSonic;
                tile = 0xE000 | (0x6D00 >> 5);
                visible = true;
                x = 140;
                y = 104;
                frame = 9;
                started = true;
                return;
            }
            if (!animating) {
                delay += 0x12;
                if (delay < 0x100) return;
                delay &= 0xFF;
                animating = true;
            }
            if (armFrame >= assets.armFrames.length) return;
            frame = assets.armFrames[armFrame] & 0xFF;
            armFrame++;
        }
    }

    /** ObjBanner (Banner/Main.asm:7-19). */
    private final class Banner extends TitleObject {
        @Override void run() {
            map = assets.sprBanner;
            tile = 0xA000 | (0xF000 >> 5);
            visible = true;
            x = 127;
            y = 127;
        }
    }

    /** ObjPlanet (Planet/Main.asm:15-66): loads its art on its second run, then hovers. */
    private final class Planet extends TitleObject {
        private int runs, offset, delay;

        @Override void run() {
            if (runs++ == 0) return;                             // bsr.w BookmarkObject first
            if (runs == 2) {
                loadArt(0x8040, assets.artPlanet);
                map = assets.sprPlanet;
                tile = 0x6000 | (0x8040 >> 5);
                visible = true;
                x = 226;
                y = 24;
            }
            delay += 0x40;
            if (delay < 0x100) return;
            delay &= 0xFF;
            y += assets.planetOffsets[offset];
            offset = (offset + 1) & 0x1F;
        }
    }

    /** ObjCopyright, USA (Copyright/Main.asm:7-26). */
    private final class Copyright extends TitleObject {
        @Override void run() {
            map = assets.sprCopyright;
            tile = 0xE000 | (0xDE00 >> 5);
            visible = true;
            y = 208;
            x = 80;
            frame = 1;
        }
    }

    /** ObjTM, USA (Copyright/Main.asm:42-58). */
    private final class Tm extends TitleObject {
        @Override void run() {
            map = assets.sprTm;
            tile = 0xE000 | (0xDFC0 >> 5);
            visible = true;
            x = 194;
            y = 131;
        }
    }

    private enum MenuState { INIT, PRESS_START, MOVE_RIGHT_WAIT, MOVE_RIGHT_OUT, MOVE_RIGHT_SET, MOVE_RIGHT_IN,
        MOVE_RIGHT_IN_CHECK, MOVE_LEFT_WAIT, MOVE_LEFT_OUT, MOVE_LEFT_SET, MOVE_LEFT_IN, WAIT_SELECTION, DONE }

    /** ObjMenu (Menu/Main.asm:23-511): PRESS START, the cheats and the option carousel. */
    private final class Menu extends TitleObject {
        private MenuState state = MenuState.INIT;
        private int delay, option, allowSel, cloudsIdx, soundTestIdx, stageSelIdx, bestTimesIdx;

        @Override void run() {
            switch (state) {
                case INIT -> {
                    map = assets.sprMenu;
                    tile = 0xA000 | (0xD800 >> 5);
                    visible = true;
                    x = 83;
                    y = 180;
                    timer = MENU_TIMER;                          // REGION=USA
                    pressStart();
                }
                case PRESS_START -> pressStart();
                case MOVE_RIGHT_WAIT -> {
                    delay += 0x80;
                    if (delay < 0x100) { moveRight(); return; }
                    delay &= 0xFF;
                    x += 0x20;
                    state = MenuState.MOVE_RIGHT_OUT;
                }
                case MOVE_RIGHT_OUT -> {
                    if ((x & 0xFFFF) < 0x100) { x += 0x20; return; }
                    state = MenuState.MOVE_RIGHT_SET;
                }
                case MOVE_RIGHT_SET -> {
                    setOption();
                    x = -0x2D;
                    state = MenuState.MOVE_RIGHT_IN;
                }
                case MOVE_RIGHT_IN -> {
                    x += 0x10;
                    state = MenuState.MOVE_RIGHT_IN_CHECK;
                }
                case MOVE_RIGHT_IN_CHECK -> {
                    if (x < 0 || x < 0x53) { x += 0x10; return; }
                    arrived();
                }
                case MOVE_LEFT_WAIT -> {
                    delay += 0x80;
                    if (delay < 0x100) { moveLeft(); return; }
                    delay &= 0xFF;
                    x -= 0x20;
                    state = MenuState.MOVE_LEFT_OUT;
                }
                case MOVE_LEFT_OUT -> {
                    if (x >= 0 || (x & 0xFFFF) >= 0xFFCB) { x -= 0x20; return; }
                    state = MenuState.MOVE_LEFT_SET;
                }
                case MOVE_LEFT_SET -> {
                    setOption();
                    x = 0xD3;
                    state = MenuState.MOVE_LEFT_IN;
                }
                case MOVE_LEFT_IN -> {
                    x -= 0x10;
                    if ((x & 0xFFFF) >= 0x53) return;
                    arrived();
                }
                case WAIT_SELECTION -> checkButtons();
                case DONE -> { }
            }
        }

        /** ObjMenu_PressStart (Menu/Main.asm:37-67). */
        private void pressStart() {
            state = MenuState.PRESS_START;
            delay += 0x10;
            if (delay >= 0x100) { delay &= 0xFF; frame ^= 1; }
            if ((p1Tap & 0x80) != 0) { prepareMenu(); return; }
            cloudsCheat();
            if (cloudsControl && p2Held != 0) timer = MENU_TIMER_RESET;   // ObjMenu_ChkCloudCtrl
            int exit = cheat(soundTestIdx, SOUND_TEST, 7, i -> soundTestIdx = i);
            if (exit == 0) exit = cheat(stageSelIdx, STAGE_SELECT, 8, i -> stageSelIdx = i);
            if (exit == 0) exit = cheat(bestTimesIdx, BEST_TIMES, 9, i -> bestTimesIdx = i);
            if (exit != 0) { exitFlag = exit; state = MenuState.DONE; return; }
            if (timer == 0) { exitFlag = 0xFF; state = MenuState.DONE; }  // ObjMenu_TimeOut
        }

        /** .PrepareMenu (Menu/Main.asm:69-117), then ObjMenu_MoveRight's first pass. */
        private void prepareMenu() {
            p1Held = 0;
            p1Tap = 0;
            frame = 1;
            timer = MENU_TIMER_RESET;
            int n = 0;
            menuOptions[n++] = 0xFF;
            menuOptions[n++] = 2;
            int selection = 1;
            if ((titleFlags & 0x40) != 0) { selection = 2; menuOptions[n++] = 3; }
            menuSel = selection;
            if ((titleFlags & 0x20) != 0) menuOptions[n++] = 4;
            if ((titleFlags & 0x10) != 0) menuOptions[n++] = 5;
            if ((titleFlags & 0x08) != 0) menuOptions[n++] = 6;
            if ((titleFlags & 0x04) != 0) menuOptions[n++] = 7;
            menuOptions[n] = 0xFF;
            spawn(new Arrow(this, false));
            spawn(new Arrow(this, true));
            moveRight();
        }

        private void moveRight() {
            tile = 0xC000 | (0xD800 >> 5);                       // unhighlight
            allowSel = 0;
            state = MenuState.MOVE_RIGHT_WAIT;
        }

        private void moveLeft() {
            tile = 0xC000 | (0xD800 >> 5);
            allowSel = 0;
            state = MenuState.MOVE_LEFT_WAIT;
        }

        /** ObjMenu_SetOption (Menu/Main.asm:482-496): frame and text art of the selected option. */
        private void setOption() {
            option = menuOptions[menuSel];
            frame = option;
            loadArt(0xD800, assets.menuText[option]);
        }

        private void arrived() {
            x = 0x53;
            tile = 0xA000 | (0xD800 >> 5);
            allowSel = 3;                                        // ObjMenu_WaitSelection
            state = MenuState.WAIT_SELECTION;
        }

        /** ObjMenu_WaitSelection .CheckButtons (Menu/Main.asm:208-225) and its branches. */
        private void checkButtons() {
            if ((p1Held & 0x04) != 0) {                          // ObjMenu_SelectLeft
                timer = MENU_TIMER_RESET;
                if (menuOptions[menuSel - 1] == 0xFF) return;
                menuSel--;
                moveRight();
                return;
            }
            if ((p1Held & 0x08) != 0) {                          // ObjMenu_SelectRight
                timer = MENU_TIMER_RESET;
                if (menuOptions[menuSel + 1] == 0xFF) return;
                menuSel++;
                moveLeft();
                return;
            }
            if ((p1Tap & 0xF0) != 0) {                           // ObjMenu_SelectOption
                int id = menuOptions[menuSel] - 1;
                exitFlag = id < 0 ? 1 : id;
                state = MenuState.DONE;
                return;
            }
            if (timer == 0) { exitFlag = 0xFF; state = MenuState.DONE; }
        }

        /** ObjMenu_CloudsCheat (Menu/Main.asm:300-339): A held through the sequence. */
        private void cloudsCheat() {
            int d0 = cloudsIdx;
            if ((p1Held & 0x40) != 0) {
                d0 = p1Tap;
                if (d0 == CLOUDS[cloudsIdx]) {
                    if (++cloudsIdx == CLOUDS.length) {
                        sounds.add(SoundRequest.RING);
                        cloudsControl = true;
                    }
                    return;
                }
            }
            if (d0 != 0) cloudsIdx = 0;                          // .Failed: tst.b d0
        }

        /** The three exit cheats share one shape (Menu/Main.asm:356-473). */
        private int cheat(int index, int[] sequence, int exit, java.util.function.IntConsumer store) {
            if (p1Tap == sequence[index]) {
                if (index + 1 == sequence.length) {
                    store.accept(index + 1);
                    sounds.add(SoundRequest.RING);
                    return exit;
                }
                store.accept(index + 1);
                return 0;
            }
            if (p1Tap != 0) store.accept(0);
            return 0;
        }
    }

    /** {@code .Cheat} tables (Menu/Main.asm:336, 388, 428, 468): pad bits 0 Up, 1 Down, 2 Left, 3 Right, 4 B, 5 C, 6 A. */
    private static final int[] CLOUDS = {1, 2, 2, 2, 2, 1};
    private static final int[] SOUND_TEST = {2, 2, 2, 4, 8, 0x40};
    private static final int[] STAGE_SELECT = {1, 2, 2, 4, 8, 0x10};
    private static final int[] BEST_TIMES = {8, 8, 1, 1, 2, 0x20};

    /** ObjMenuArrow (Menu/Main.asm:530-632). */
    private final class Arrow extends TitleObject {
        private final Menu parent;
        private final boolean right;
        private boolean started;
        private int delay, arrowFrame;

        Arrow(Menu parent, boolean right) {
            this.parent = parent;
            this.right = right;
        }

        @Override void run() {
            if (!started) {
                map = assets.sprMenuArrow;
                tile = 0xA000 | (0xDC00 >> 5);
                visible = true;
                y = 181;
                x = right ? 168 : 72;
                started = true;
            }
            int neighbour = menuOptions[menuSel + (right ? 1 : -1)];
            if (parent.allowSel == 0 || neighbour == 0xFF) {
                frame = 0;
                delay = 0;
                arrowFrame = 0;
                return;
            }
            frame = (right ? 4 : 1) + arrowFrame;                // .Frames 4,5,6 / 1,2,3
            delay += 0x10;
            if (delay < 0x100) return;
            delay &= 0xFF;
            if (++arrowFrame >= 3) arrowFrame = 0;
        }
    }

    // ---- view ----
    /** The screen this frame shows, for {@link com.openggf.game.soniccd.program.CdProgramScreenRenderer}. */
    public com.openggf.game.soniccd.program.CdProgramScreenRenderer.Frame screen() {
        return new com.openggf.game.soniccd.program.CdProgramScreenRenderer.Frame(vram, dirtyTiles, planeA, planeB,
                palette, line -> 0, line -> scrollB[line], sprites, 256, displayEnabled);
    }
    public Phase phase() { return phase; }
    public boolean displayEnabled() { return displayEnabled; }
    public boolean done() { return phase == Phase.DONE; }
    /** The exit code {@code d1} the program returns to IPX, or -1 while it runs. */
    public int exitCode() { return exitCode; }
    public int exitFlag() { return exitFlag; }
    public int timer() { return timer; }
    /** Main-loop halves completed: the clock that phases the clouds, water and palette cycle. */
    public int halves() { return halves; }
    public int cloudRenders() { return clouds.renders(); }
    public long frames() { return frames; }
    /** True once the menu object checks PRESS START and the cheats each frame. */
    public boolean acceptsMenuInput() {
        for (TitleObject object : slots) if (object instanceof Menu m) return m.state != MenuState.INIT;
        return false;
    }
    public List<SoundRequest> drainSounds() {
        List<SoundRequest> out = List.copyOf(sounds);
        sounds.clear();
        return out;
    }
}
