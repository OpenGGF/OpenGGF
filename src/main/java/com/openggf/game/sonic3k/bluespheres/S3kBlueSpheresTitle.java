package com.openggf.game.sonic3k.bluespheres;

import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.render.SpriteMappingFrame;

import java.util.List;
import java.util.Objects;

/**
 * The Blue Spheres title and level-code menu: BlueSpheresTitle
 * (sonic3k.asm:100284-100378) and its Obj_SpheresTitle_* objects
 * (sonic3k.asm:100558-101619), for the full game a Sonic 1 lock-on unlocks.
 *
 * <p>Slots follow the ROM's object RAM: Player_1 is 0, Player_2 1,
 * Reserved_object_3 2 and {@code Dynamic_object_RAM+object_size*n} is
 * {@code 3+n}. Objects run in slot order, and an object created ahead of the
 * running slot runs later in the same pass, as Process_Sprites does.
 */
final class S3kBlueSpheresTitle {
    /** Sounds and mode changes the title requests. */
    interface Events {
        void playSfx(int sfx);
        /** {@code move.b #$34,(Game_mode).w}: start the Blue Spheres stage. */
        void startStage();
    }

    // Controller bits (Ctrl_1 layout).
    static final int UP = 0x01, DOWN = 0x02, LEFT = 0x04, RIGHT = 0x08;
    static final int B = 0x10, C = 0x20, A = 0x40, START = 0x80;
    static final int ABC = A | B | C;

    static final int SFX_STARPOST = 0x63;
    static final int SFX_WEATHER_MACHINE = 0x89;
    static final int SFX_ERROR = 0xB2;

    private static final int SLOT_CONTROL = 0;
    private static final int SLOT_COPYRIGHT = 1;
    private static final int SLOT_CHARACTERS = 2;
    private static final int SLOT_LEVEL = 6;
    private static final int SLOT_CODE_EDIT = 18;
    private static final int SLOT_CODE_SHOW = 31;
    private static final int SLOT_START = 45;
    private static final int SLOT_ICON_LEFT = 46;
    private static final int SLOT_ICON_RIGHT = 47;

    private static final int ART_TEXT = 0x8000 | S3kBlueSpheresAssets.TILE_MISC;
    private static final int ART_TEXT_SELECTED = 0x8000 | (S3kBlueSpheresAssets.TILE_MISC + 0x14);
    private static final int ART_ICON_SONIC = 0x8000 | (3 << 13) | (S3kBlueSpheresAssets.TILE_MISC + 0x4A);
    private static final int ART_ICON_KNUCKLES = 0x8000 | (S3kBlueSpheresAssets.TILE_MISC + 0x4A);

    private static final String GET_BLUE_SPHERES = "GET   BLUE   SPHERES !                            ";
    private static final String NO_WAY = "NO WAY!    NO WAY!    NO WAY!    NO WAY?   ";

    private final S3kBlueSpheresAssets assets;
    private final S3kBlueSpheresSession session;
    private final Events events;
    private final S3kBlueSpheresObject[] slots = new S3kBlueSpheresObject[96];
    private final List<SpriteMappingFrame> textMap, numberMap, iconMap, charMap, copyrightMap;
    private final int[] planeA = new int[S3kBlueSpheresAssets.PLANE_WIDTH * S3kBlueSpheresAssets.PLANE_HEIGHT];
    private final int[] planeB = new int[planeA.length];
    private final int[] hScrollA = new int[224];
    private final int[] codeDigitX = new int[12];
    private final int[][] iconTargets = new int[2][6];
    private final int[][] charPositions = new int[4][2];
    private final int[][] charAnimations = new int[4][];
    /** The ROM byte read by Obj_SpheresTitle_4CEB6's {@code tst.b 0}. */
    private final int vectorByteZero;
    private int vScrollFG;
    private int vScrollBG;
    private int pressed;
    private int held;

    S3kBlueSpheresTitle(S3kBlueSpheresAssets assets, S3kBlueSpheresSession session, Events events) {
        this.assets = Objects.requireNonNull(assets);
        this.session = Objects.requireNonNull(session);
        this.events = Objects.requireNonNull(events);
        for (int i = 0; i < slots.length; i++) slots[i] = new S3kBlueSpheresObject();
        textMap = assets.mappings(S3kBlueSpheresAssets.MAP_TEXT);
        numberMap = assets.mappings(S3kBlueSpheresAssets.MAP_NUMBERS);
        iconMap = assets.mappings(S3kBlueSpheresAssets.MAP_ICON);
        charMap = assets.mappings(S3kBlueSpheresAssets.MAP_CHAR_SPRITE);
        copyrightMap = assets.mappings(S3kBlueSpheresAssets.MAP_COPYRIGHT);
        for (int i = 0; i < 12; i++) codeDigitX[i] = assets.word(S3kBlueSpheresAssets.CODE_DIGIT_X + i * 2);
        for (int i = 0; i < 6; i++) {
            iconTargets[0][i] = assets.word(S3kBlueSpheresAssets.ICON_TARGETS_LEFT + i * 2);
            iconTargets[1][i] = assets.word(S3kBlueSpheresAssets.ICON_TARGETS_RIGHT + i * 2);
        }
        for (int i = 0; i < 4; i++) {
            charPositions[i][0] = assets.word(S3kBlueSpheresAssets.CHAR_POSITIONS + i * 4);
            charPositions[i][1] = assets.word(S3kBlueSpheresAssets.CHAR_POSITIONS + i * 4 + 2);
            charAnimations[i] = animationScript(S3kBlueSpheresAssets.ANI_CHAR_SPRITE, i);
        }
        vectorByteZero = assets.u8(0);
    }

    /**
     * BlueSpheresTitle's setup between its two fades: sub_4CCA6, plane maps,
     * the banner text and the first Process_Sprites/Render_Sprites pass.
     */
    void setUp(S3kBlueSpheresPalette palette) throws java.io.IOException {
        java.util.Arrays.fill(planeA, 0);
        java.util.Arrays.fill(planeB, 0);
        java.util.Arrays.fill(hScrollA, 0);
        vScrollFG = vScrollBG = 0;
        for (S3kBlueSpheresObject slot : slots) slot.clear();
        session.startFullGame();
        palette.clearTarget();
        palette.setTarget(0, titlePalette());

        int[] fg = assets.enigma(S3kBlueSpheresAssets.MAP_ENI_TITLE_FG, 1 << 13);
        S3kBlueSpheresPlanes.copy(planeA, 0x380, fg, 40, 16);
        int[] bg = assets.enigma(S3kBlueSpheresAssets.MAP_ENI_TITLE_BG, (2 << 13) | S3kBlueSpheresAssets.TILE_BG);
        S3kBlueSpheresPlanes.copy(planeB, 0x09C, bg, 12, 8);

        String text = GET_BLUE_SPHERES;
        if (session.menuFlag == 0) {
            text = NO_WAY;
            session.playerOption = 1;
            vScrollFG = -16;
            vScrollBG = -8;
        }
        S3kBlueSpheresPlanes.writeText(assets, planeA, 0x100, text, S3kBlueSpheresAssets.TILE_TITLE_TEXT);

        slots[SLOT_CONTROL].routine = this::controlInit;
        slots[SLOT_COPYRIGHT].routine = this::copyrightInit;
        slots[SLOT_CHARACTERS].routine = this::charactersInit;
        processSprites(0, 0);
    }

    /** One loc_4CAE8 iteration after Wait_VSync: sub_4CC96, then Process_Sprites. */
    void update(int heldButtons, int pressedButtons) {
        for (int line = 0; line < 0x40; line++) hScrollA[line] -= 2;
        processSprites(heldButtons, pressedButtons);
    }

    S3kBlueSpheresScene scene(com.openggf.level.Pattern[] vram, S3kBlueSpheresPalette palette) {
        List<SpriteSatEntry> sat = S3kBlueSpheresObject.render(slots);
        return new S3kBlueSpheresScene(vram, planeA, planeB, hScrollA, null, vScrollFG, vScrollBG,
                sat, palette.cram());
    }

    /** sub_4CB1A: {@code Pal_SpheresTitle_2} when progress is disabled, else _1. */
    int[] titlePalette() {
        return assets.paletteWords(session.progressFlag != 0
                ? S3kBlueSpheresAssets.PAL_TITLE_2 : S3kBlueSpheresAssets.PAL_TITLE_1, 64);
    }

    private void processSprites(int heldButtons, int pressedButtons) {
        held = heldButtons & 0xFF;
        pressed = pressedButtons & 0xFF;
        for (S3kBlueSpheresObject slot : slots) slot.drawn = false;
        for (S3kBlueSpheresObject slot : slots) {
            if (slot.routine != null) slot.routine.run(slot);
        }
    }

    private S3kBlueSpheresObject slot(int index) {
        return slots[index];
    }

    // ---- Player_1: Obj_SpheresTitle_4CEAA (sonic3k.asm:100627-100754) ----
    // The cartridge's own A+B+C test is replaced by any button; see waitForAbc.

    private void controlInit(S3kBlueSpheresObject self) {
        if (session.menuFlag != 0) {
            openMenu(self);
            return;
        }
        self.routine = this::waitForAbc;
        waitForAbc(self);
    }

    private void waitForAbc(S3kBlueSpheresObject self) {
        // FixBugs=0: loc_4CEB6 tests the ROM byte at address 0 ("tst.b 0"). The
        // S&K vector table makes it zero, so the branch never skips the ABC check.
        // The fixed build removes the test; both builds behave the same here.
        if (vectorByteZero != 0) return;
        // Intentional discrepancy (docs/S3K_KNOWN_DISCREPANCIES.md, "Blue Spheres
        // NO WAY! Accepts Any Button"): the ROM waits for Ctrl_1 == A+B+C exactly;
        // any newly pressed button opens the menu here.
        if (pressed == 0) return;
        pressed = 0;
        session.spheresLeft = -1;
        session.ringsLeft = -1;
        events.playSfx(SFX_STARPOST);
        S3kBlueSpheresPlanes.writeText(assets, planeA, 0x100, GET_BLUE_SPHERES,
                S3kBlueSpheresAssets.TILE_TITLE_TEXT);
        openMenu(self);
    }

    /** loc_4CEF8: the menu objects; the full game also adds code entry and START. */
    private void openMenu(S3kBlueSpheresObject self) {
        self.routine = this::control;
        S3kBlueSpheresObject level = slot(SLOT_LEVEL);
        level.clear();
        level.routine = this::levelInit;
        level.setY(0xC0);
        session.menuFlag = 1;
        session.option = 0;
        slot(SLOT_ICON_LEFT).clear();
        slot(SLOT_ICON_LEFT).routine = this::iconInit;
        slot(SLOT_ICON_RIGHT).clear();
        slot(SLOT_ICON_RIGHT).routine = this::iconInit;
        slot(SLOT_ICON_RIGHT).xFlip = true;
        level.setY(0xB0);
        S3kBlueSpheresObject edit = slot(SLOT_CODE_EDIT);
        edit.clear();
        edit.routine = this::codeEditInit;
        edit.setY(0x144);
        S3kBlueSpheresObject show = slot(SLOT_CODE_SHOW);
        show.clear();
        show.routine = this::codeShowInit;
        show.setY(0x144);
        slot(SLOT_START).clear();
        slot(SLOT_START).routine = this::startInit;
        session.option = 1;
    }

    /** loc_4CF82 for the full game. */
    private void control(S3kBlueSpheresObject self) {
        if (vScrollFG != 0) {
            vScrollFG++;
            if ((vScrollFG & 1) == 0) vScrollBG++;
            for (int i = 0; i < 4; i++) slot(SLOT_CHARACTERS + i).y -= 1 << 16;
        }
        if ((session.menuFlag & 0x80) == 0) {
            if ((pressed & UP) != 0 && session.option != 0) {
                session.option--;
                events.playSfx(SFX_WEATHER_MACHINE);
            }
            if ((pressed & DOWN) != 0 && session.option < 2) {
                session.option++;
                events.playSfx(SFX_WEATHER_MACHINE);
                if (session.option == 2) {
                    session.menuFlag |= 0x80;
                    pressed = 0;
                }
            }
        }
        if (session.option != 1) return;
        if (pressed != START) return;
        events.startStage();
    }

    // ---- Player_2: Obj_SpheresTitle_4DA30 (sonic3k.asm:101603-101615) ----

    private void copyrightInit(S3kBlueSpheresObject self) {
        self.mappings = copyrightMap;
        self.artTile = 0x8000 | (S3kBlueSpheresAssets.TILE_MISC + 0x64);
        self.setX(0x180);
        self.setY(0x152);
        self.priority = 0x80;
        self.routine = S3kBlueSpheresObject::draw;
        self.draw();
    }

    // ---- Reserved_object_3: Obj_SpheresTitle_4D986 (sonic3k.asm:101560-101598) ----

    private void charactersInit(S3kBlueSpheresObject self) {
        for (int i = 0; i < 4; i++) {
            S3kBlueSpheresObject sprite = slot(SLOT_CHARACTERS + i);
            int anim = 3 - i;
            sprite.mappings = charMap;
            sprite.artTile = (1 << 13) | 0x0FE;
            sprite.setX(charPositions[i][0]);
            sprite.setY(charPositions[i][1] - (session.menuFlag != 0 ? 0x10 : 0));
            sprite.priority = 0x180;
            sprite.anim = anim;
            sprite.frame = charAnimations[anim][0];
            sprite.routine = this::character;
        }
        character(self);
    }

    private void character(S3kBlueSpheresObject self) {
        animateIrregular(self, charAnimations[self.anim]);
        self.draw();
    }

    // ---- Obj_SpheresTitle_4D03E: LEVEL / STAGE number (sonic3k.asm:100757-100820) ----

    private void levelInit(S3kBlueSpheresObject self) {
        self.mappings = textMap;
        self.artTile = ART_TEXT;
        self.setX(0x120);
        self.priority = 0x80;
        int digitY = self.yPos() + 0x10;
        for (int i = 1; i <= 11; i++) {
            S3kBlueSpheresObject digit = slot(SLOT_LEVEL + i);
            digit.clear();
            digit.routine = this::digitInit;
            digit.setX(0xF7 + (i - 1) * 8);
            digit.setY(digitY);
        }
        slot(SLOT_LEVEL + 1).frame = 0x0A;
        slot(SLOT_LEVEL + 11).frame = 0x0B;
        self.routine = this::levelDisplay;
        levelDisplay(self);
    }

    /**
     * loc_4D09E: with the cursor on START or CODE the full game shows LEVEL
     * unless progress is disabled; on the LEVEL row the two swap, so the row
     * under the cursor reveals the other number.
     */
    private void levelDisplay(S3kBlueSpheresObject self) {
        boolean showLevel = (session.option != 0) == (session.progressFlag == 0);
        if (showLevel) {
            S3kBlueSpheresDigits.level(slots, SLOT_LEVEL, session.level + 1);
            self.frame = 0;
        } else {
            S3kBlueSpheresDigits.stage(slots, SLOT_LEVEL,
                    S3kBlueSpheresLevels.packedStageNumber(session.seed));
            self.frame = 1;
        }
        self.draw();
    }

    private void digitInit(S3kBlueSpheresObject self) {
        numberSprite(self);
        self.routine = S3kBlueSpheresObject::draw;
        self.draw();
    }

    /** sub_4D5C2. */
    private void numberSprite(S3kBlueSpheresObject self) {
        self.mappings = numberMap;
        self.artTile = ART_TEXT;
        self.priority = 0x80;
    }

    // ---- Obj_SpheresTitle_4D2F4 / _4D302: code entry and code display ----

    private void codeEditInit(S3kBlueSpheresObject self) {
        codeRow(self, this::codeEdit, this::codeEditDigitInit);
    }

    private void codeShowInit(S3kBlueSpheresObject self) {
        codeRow(self, this::codeShow, this::codeShowDigitInit);
    }

    /** loc_4D30E. */
    private void codeRow(S3kBlueSpheresObject self, S3kBlueSpheresObject.Routine next,
                         S3kBlueSpheresObject.Routine digitInit) {
        self.routine = next;
        self.mappings = textMap;
        self.artTile = ART_TEXT;
        self.setX(0xD4);
        self.priority = 0x80;
        self.frame = 2;
        int base = slotIndex(self);
        for (int i = 0; i < 12; i++) {
            S3kBlueSpheresObject digit = slot(base + 1 + i);
            digit.clear();
            digit.routine = digitInit;
            digit.setX(codeDigitX[i] - 0x0C);
            digit.y = self.y;
        }
        self.draw();
    }

    /** loc_4D362: the current level's code, hidden while a code is being entered. */
    private void codeShow(S3kBlueSpheresObject self) {
        if ((session.menuFlag & 0x80) != 0) return;
        long code = S3kBlueSpheresLevels.encodeCode(session.level, session.progressFlag != 0);
        int[] digits = S3kBlueSpheresLevels.codeDigits(code);
        for (int i = 0; i < 12; i++) slot(SLOT_CODE_SHOW + 1 + i).frame = digits[i];
        self.draw();
    }

    private void codeShowDigitInit(S3kBlueSpheresObject self) {
        numberSprite(self);
        self.routine = d -> { if ((session.menuFlag & 0x80) == 0) d.draw(); };
        self.routine.run(self);
    }

    private void codeEditDigitInit(S3kBlueSpheresObject self) {
        numberSprite(self);
        self.routine = d -> { if ((session.menuFlag & 0x80) != 0) d.draw(); };
        self.routine.run(self);
    }

    /** loc_4D3CE: digit entry; Start checks the code (sub_4D488). */
    private void codeEdit(S3kBlueSpheresObject self) {
        if ((session.menuFlag & 0x80) == 0) {
            self.scratch = 0;
            return;
        }
        if (pressed == START) checkCode();
        S3kBlueSpheresObject cursor = slot(SLOT_CODE_EDIT + 1 + self.scratch);
        cursor.artTile = ART_TEXT;
        if ((pressed & UP) != 0) {
            cursor.frame = cursor.frame == 0 ? 9 : cursor.frame - 1;
        }
        if ((pressed & DOWN) != 0) {
            cursor.frame = cursor.frame + 1 >= 0x0A ? 0 : cursor.frame + 1;
        }
        if ((pressed & LEFT) != 0) {
            self.scratch = self.scratch == 0 ? 0x0B : self.scratch - 1;
        }
        if ((pressed & RIGHT) != 0) {
            self.scratch = self.scratch + 1 >= 0x0C ? 0 : self.scratch + 1;
        }
        if ((session.menuFlag & 0x80) != 0) {
            slot(SLOT_CODE_EDIT + 1 + self.scratch).artTile = ART_TEXT_SELECTED;
        }
        if ((pressed & (UP | DOWN | LEFT | RIGHT)) != 0) events.playSfx(SFX_WEATHER_MACHINE);
        self.draw();
    }

    /** sub_4D488. */
    private void checkCode() {
        int[] digits = new int[12];
        for (int i = 0; i < 12; i++) digits[i] = slot(SLOT_CODE_EDIT + 1 + i).frame;
        var decoded = S3kBlueSpheresLevels.decodeCode(S3kBlueSpheresLevels.codeFromDigits(digits));
        if (decoded != null) {
            session.applyCode(decoded);
            titlePaletteChanged = true;
            events.playSfx(SFX_STARPOST);
        } else {
            events.playSfx(SFX_ERROR);
        }
        session.menuFlag &= 0x7F;
        session.option = 1;
    }

    private boolean titlePaletteChanged;

    /** sub_4D488 reloads the title palette straight into Normal_palette after a good code. */
    boolean consumeTitlePaletteChange() {
        boolean changed = titlePaletteChanged;
        titlePaletteChanged = false;
        return changed;
    }

    // ---- Obj_SpheresTitle_4D610: START (sonic3k.asm:101401-101415) ----

    private void startInit(S3kBlueSpheresObject self) {
        self.mappings = textMap;
        self.artTile = ART_TEXT;
        self.setX(0x120);
        self.setY(0x128);
        self.priority = 0x80;
        self.frame = 3;
        self.routine = S3kBlueSpheresObject::draw;
        self.draw();
    }

    // ---- Obj_SpheresTitle_4D64E: cursor icons (sonic3k.asm:101416-101537) ----

    private void iconInit(S3kBlueSpheresObject self) {
        self.mappings = iconMap;
        self.artTile = session.playerOption == 3 ? ART_ICON_KNUCKLES : ART_ICON_SONIC;
        self.setX(self.xFlip ? 0x140 : 0x100);
        self.setY(0x126);
        self.scratch = self.xFlip ? 1 : 0;
        self.priority = 0;
        self.routine = this::icon;
        icon(self);
    }

    private void icon(S3kBlueSpheresObject self) {
        int[] targets = iconTargets[self.scratch];
        int targetX = targets[session.option * 2];
        int targetY = targets[session.option * 2 + 1];
        self.xVel = (short) (self.xVel + iconAccel(self.xPos(), targetX, self.xVel, 0x40, 0x80, 0x100));
        self.yVel = (short) (self.yVel + iconAccel(self.yPos(), targetY, self.yVel, 0x80, 0x100, 0x200));
        self.moveSprite2();
        if (--self.animTimer < 0) {
            self.animTimer = 2;
            self.frame = (self.frame + 1) & 7;
        }
        if ((pressed & ABC) != 0) {
            int previous = self.artTile;
            session.playerOption = 3;
            self.artTile = ART_ICON_KNUCKLES;
            if (previous == ART_ICON_KNUCKLES) {
                session.playerOption = 1;
                self.artTile = ART_ICON_SONIC;
            }
        }
        self.draw();
    }

    /**
     * loc_4D6B8-loc_4D754: at the target with a small speed the velocity stops;
     * otherwise accelerate towards it, four times harder while moving away.
     */
    static int iconAccel(int position, int target, int velocity, int step, int stopBias, int stopRange) {
        if (position == target && (velocity + stopBias & 0xFFFF) <= stopRange) {
            return -velocity;
        }
        int accel = step;
        if (target >= position) {
            if ((short) velocity < 0) accel <<= 2;
        } else {
            accel = -accel;
            if ((short) velocity >= 0) accel <<= 2;
        }
        return accel;
    }

    // ---- shared ----

    private int slotIndex(S3kBlueSpheresObject object) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == object) return i;
        throw new IllegalArgumentException("Object is not in a title slot");
    }

    /** Animate_SpriteIrregularDelay (sonic3k.asm:36295-36330), one script. */
    static void animateIrregular(S3kBlueSpheresObject self, int[] script) {
        if (self.anim != self.prevAnim) {
            self.prevAnim = self.anim;
            self.animFrame = 0;
            self.animTimer = 0;
        }
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if (self.animTimer != 0xFF) return;
        int index = self.animFrame * 2;
        int frame = script[index];
        if ((frame & 0x80) != 0) {
            if (frame == 0xFF) {
                index = 0;
                self.animFrame = 0;
                frame = script[0];
            } else {
                return;
            }
        }
        self.animTimer = script[index + 1];
        self.frame = frame;
        self.animFrame++;
    }

    private int[] animationScript(int table, int anim) {
        int start = table + assets.signedWord(table + anim * 2);
        int length = 0;
        while (assets.u8(start + length) != 0xFF) length += 2;
        int[] script = new int[length + 1];
        for (int i = 0; i <= length; i++) script[i] = assets.u8(start + i);
        return script;
    }
}
