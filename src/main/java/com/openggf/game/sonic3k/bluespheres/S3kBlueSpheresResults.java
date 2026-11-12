package com.openggf.game.sonic3k.bluespheres;

import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * BlueSpheresResults after a cleared stage (sonic3k.asm:101620-101714): the
 * CONGRATULATIONS! screen, the level, the player's pose and the circling
 * emeralds, then PERFECT when no ring was left behind.
 */
final class S3kBlueSpheresResults {
    interface Events {
        void playSfx(int sfx);
        /** {@code move.b #$2C,(Game_mode).w}: leave the results. */
        void leave();
    }

    static final int SFX_CONTINUE = 0xAC;
    private static final int SLOT_CONTROL = 0;
    private static final int SLOT_PLAYER = 1;
    private static final int SLOT_LEVEL = 6;
    private static final int SLOT_EMERALDS = 29;
    private static final int ART_TEXT = 0x8000 | S3kBlueSpheresAssets.TILE_MISC;

    private final S3kBlueSpheresAssets assets;
    private final S3kBlueSpheresSession session;
    private final Events events;
    private final S3kBlueSpheresObject[] slots = new S3kBlueSpheresObject[64];
    private final int[] planeA = new int[S3kBlueSpheresAssets.PLANE_WIDTH * S3kBlueSpheresAssets.PLANE_HEIGHT];
    private final int[] planeB = new int[planeA.length];
    private final int[] hScrollA = new int[224];
    private final int[] hScrollB = new int[224];
    private final List<SpriteMappingFrame> textMap, numberMap, emeraldMap;
    private final S3kBlueSpheresPlayerPose pose;
    private final Pattern[] vram;
    private int vScrollBG;
    private int pressed;
    private int vintCounter;
    /** {@code Stat_table}: this frame's emerald circle radius. */
    private int radius;

    S3kBlueSpheresResults(S3kBlueSpheresAssets assets, S3kBlueSpheresSession session, Events events,
                          Pattern[] vram) {
        this.assets = Objects.requireNonNull(assets);
        this.session = Objects.requireNonNull(session);
        this.events = Objects.requireNonNull(events);
        this.vram = Objects.requireNonNull(vram);
        for (int i = 0; i < slots.length; i++) slots[i] = new S3kBlueSpheresObject();
        textMap = assets.mappings(S3kBlueSpheresAssets.MAP_TEXT);
        numberMap = assets.mappings(S3kBlueSpheresAssets.MAP_NUMBERS);
        emeraldMap = assets.mappings(S3kBlueSpheresAssets.MAP_RESULTS_EMERALD);
        pose = session.playerOption == 3 ? S3kBlueSpheresPlayerPose.knuckles(assets)
                : S3kBlueSpheresPlayerPose.sonic(assets);
    }

    /** The setup between the music fade and Pal_FadeFromWhite. */
    void setUp(S3kBlueSpheresPalette palette) throws IOException {
        Arrays.fill(planeA, 0);
        Arrays.fill(planeB, 0);
        Arrays.fill(hScrollA, 0);
        Arrays.fill(hScrollB, 0);
        vScrollBG = 0;
        palette.clearTarget();
        palette.setTarget(0, assets.paletteWords(S3kBlueSpheresAssets.PAL_RESULTS_012, 64));
        S3kBlueSpheresPlanes.writeText(assets, planeA, 0x30A, "CONGRATULATIONS!",
                0x8000 | S3kBlueSpheresAssets.TILE_TITLE_TEXT);
        int[] backdrop = assets.enigma(S3kBlueSpheresAssets.MAP_ENI_TITLE_BG,
                (3 << 13) | S3kBlueSpheresAssets.TILE_BG);
        backdrop(backdrop);
        session.option = 1;
        for (S3kBlueSpheresObject slot : slots) slot.clear();
        slots[SLOT_CONTROL].routine = this::controlInit;
        slots[SLOT_PLAYER].routine = self -> playerInit(self, palette);
        S3kBlueSpheresObject level = slots[SLOT_LEVEL];
        level.routine = this::levelInit;
        level.setY(0x90);
        slots[SLOT_EMERALDS].routine = this::emeraldsInit;
        processSprites(0);
    }

    /** sub_4DEA2: the logo map tiled fourteen times, the background raised and shifted. */
    private void backdrop(int[] map) {
        for (int i = 0; i < 14; i++) {
            // vdpComm(...)>>16 keeps address bits 0-13; the low command word 3
            // supplies bits 14-15, so every target lies in plane B at $E000.
            int command = assets.word(S3kBlueSpheresAssets.RESULTS_BACKDROP_TARGETS + i * 2);
            int vramAddress = (command & 0x3FFF) | 0xC000;
            S3kBlueSpheresPlanes.copy(planeB, vramAddress - 0xE000, map, 12, 8);
        }
        vScrollBG = 0x10;
        for (int line = 0; line < 0xE0; line++) hScrollB[line] -= 0x10;
    }

    void update(int pressedButtons) {
        vintCounter++;
        processSprites(pressedButtons);
    }

    S3kBlueSpheresScene scene(S3kBlueSpheresPalette palette) {
        List<SpriteSatEntry> sat = S3kBlueSpheresObject.render(slots);
        return new S3kBlueSpheresScene(vram, planeA, planeB, hScrollA, hScrollB, 0, vScrollBG, sat, palette.cram());
    }

    private void processSprites(int pressedButtons) {
        pressed = pressedButtons & 0xFF;
        for (S3kBlueSpheresObject slot : slots) slot.drawn = false;
        for (S3kBlueSpheresObject slot : slots) if (slot.routine != null) slot.routine.run(slot);
    }

    // ---- Obj_SphereResults_4DEF8 (sonic3k.asm:101900-101961) ----

    private void controlInit(S3kBlueSpheresObject self) {
        self.scratch = 5 * 60;
        self.routine = this::countdown;
        countdown(self);
    }

    private void countdown(S3kBlueSpheresObject self) {
        if (--self.scratch >= 0) {
            pressToLeave();
            return;
        }
        if (session.ringsLeft == 0) {
            S3kBlueSpheresPlanes.writeText(assets, planeA, 0x49A, "PERFECT",
                    0x8000 | S3kBlueSpheresAssets.TILE_TITLE_TEXT);
            events.playSfx(SFX_CONTINUE);
            self.scratch = 5 * 60;
        }
        self.routine = this::afterCountdown;
        pressToLeave();
    }

    /** loc_4DF3E: before PERFECT, only an imperfect clear may leave early. */
    private void pressToLeave() {
        if (session.ringsLeft != 0 && pressed != 0) events.leave();
    }

    /** loc_4DF52: leave on a press, or five seconds after PERFECT; blink line $48-$57. */
    private void afterCountdown(S3kBlueSpheresObject self) {
        boolean leave;
        if (session.ringsLeft == 0 && --self.scratch < 0) {
            leave = true;
        } else {
            leave = pressed != 0;
        }
        if (leave) events.leave();
        if ((vintCounter & 0x0F) == 0) {
            for (int line = 0x48; line < 0x58; line++) hScrollA[line] ^= 0x100;
        }
    }

    // ---- Obj_SphereResults_4DF86 (sonic3k.asm:101962-102000) ----

    private void playerInit(S3kBlueSpheresObject self, S3kBlueSpheresPalette palette) {
        self.mappings = pose.mappings();
        self.artTile = 0x8000 | S3kBlueSpheresAssets.TILE_PLAYER_1;
        self.setX(0x120);
        self.setY(0x100);
        self.priority = 0x80;
        if (session.playerOption == 3) {
            self.anim = 0x24;
            palette.setTarget(0, assets.paletteWords(S3kBlueSpheresAssets.PAL_KNUCKLES, 16));
        } else {
            self.anim = 0x13;
        }
        self.routine = this::player;
        player(self);
    }

    private void player(S3kBlueSpheresObject self) {
        pose.animate(self);
        pose.loadPlc(self, vram);
        self.draw();
    }

    // ---- Obj_SpheresTitle_4D03E reused for the level number ----

    private void levelInit(S3kBlueSpheresObject self) {
        self.mappings = textMap;
        self.artTile = ART_TEXT;
        self.setX(0x120);
        self.priority = 0x80;
        int digitY = self.yPos() + 0x10;
        for (int i = 1; i <= 11; i++) {
            S3kBlueSpheresObject digit = slots[SLOT_LEVEL + i];
            digit.clear();
            digit.mappings = numberMap;
            digit.artTile = ART_TEXT;
            digit.priority = 0x80;
            digit.routine = S3kBlueSpheresObject::draw;
            digit.setX(0xF7 + (i - 1) * 8);
            digit.setY(digitY);
        }
        slots[SLOT_LEVEL + 1].frame = 0x0A;
        slots[SLOT_LEVEL + 11].frame = 0x0B;
        self.routine = this::levelDisplay;
        levelDisplay(self);
    }

    private void levelDisplay(S3kBlueSpheresObject self) {
        if ((session.option != 0) == (session.progressFlag == 0)) {
            S3kBlueSpheresDigits.level(slots, SLOT_LEVEL, session.level + 1);
            self.frame = 0;
        } else {
            S3kBlueSpheresDigits.stage(slots, SLOT_LEVEL, S3kBlueSpheresLevels.packedStageNumber(session.seed));
            self.frame = 1;
        }
        self.draw();
    }

    // ---- Obj_Sphere_Results_4E012 (sonic3k.asm:102001-102040) ----

    private void emeraldsInit(S3kBlueSpheresObject self) {
        for (int i = 0; i < 7; i++) {
            S3kBlueSpheresObject emerald = slots[SLOT_EMERALDS + i];
            emerald.routine = this::emerald;
            emerald.mappings = emeraldMap;
            emerald.artTile = S3kBlueSpheresAssets.TILE_EMERALDS;
            emerald.priority = 0x80;
            emerald.scratch = (i * 0x24) & 0xFF;
            emerald.frame = i;
        }
        self.routine = this::emeraldRadius;
        emeraldRadius(self);
    }

    /** loc_4E05C: the radius breathes with a sine of its own counter. */
    private void emeraldRadius(S3kBlueSpheresObject self) {
        radius = (short) ((sine(self.scratch2) << 4) + 0x4000);
        self.scratch2 = (self.scratch2 + 1) & 0xFF;
        emerald(self);
    }

    /** loc_4E074. */
    private void emerald(S3kBlueSpheresObject self) {
        int sin = sine(self.scratch);
        int cos = sine(self.scratch + 0x40);
        self.setX(0x120 + ((cos * radius) >> 16));
        self.setY(0x100 + ((sin * radius) >> 16));
        self.scratch = (self.scratch + 1) & 0xFF;
        self.draw();
    }

    private int sine(int angle) {
        return assets.signedWord(S3kBlueSpheresAssets.SINE_TABLE + (angle & 0xFF) * 2);
    }
}
