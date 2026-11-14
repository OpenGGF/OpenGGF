package com.openggf.game.sonic3k.bluespheres;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The perfect-clear showcase, BlueSpheresResults' loc_4DC1A
 * (sonic3k.asm:101715-101833): the difficulty counts up to the stage's
 * target, each step adding a character or prop, and a button press after it
 * arrives skips ten levels ahead.
 */
final class S3kBlueSpheresPerfect {
    private enum Step { FADE_OUT, FADE_IN, RUN }

    static final int SFX_LAUNCH_READY = 0xA7;
    private static final int SLOT_LEVEL = 1;
    private static final int SLOT_DIFFICULTY = 13;
    private static final int FIRST_DYNAMIC = 3;
    private static final int ART_TEXT = 0x8000 | S3kBlueSpheresAssets.TILE_MISC;

    private final S3kBlueSpheresAssets assets;
    private final S3kBlueSpheresSession session;
    private final AudioManager audio;
    private final S3kBlueSpheresPalette palette;
    private final S3kBlueSpheresObject[] slots = new S3kBlueSpheresObject[96];
    private final int[] planeA = new int[S3kBlueSpheresAssets.PLANE_WIDTH * S3kBlueSpheresAssets.PLANE_HEIGHT];
    private final int[] planeB = new int[planeA.length];
    private final int[] hScrollB = new int[224];
    private final List<SpriteMappingFrame> textMap, numberMap;
    private final S3kBlueSpheresShowcase showcase;
    private Pattern[] vram = S3kBlueSpheresAssets.emptyVram();
    private Step step = Step.FADE_OUT;
    private int stepFrames;
    private int vScrollBG;
    private int pressed;
    private S3kBlueSpheresScene lastScene;

    S3kBlueSpheresPerfect(S3kBlueSpheresAssets assets, S3kBlueSpheresSession session, AudioManager audio,
                          S3kBlueSpheresPalette palette) throws IOException {
        this.assets = Objects.requireNonNull(assets);
        this.session = Objects.requireNonNull(session);
        this.audio = Objects.requireNonNull(audio);
        this.palette = Objects.requireNonNull(palette);
        for (int i = 0; i < slots.length; i++) slots[i] = new S3kBlueSpheresObject();
        textMap = assets.mappings(S3kBlueSpheresAssets.MAP_TEXT);
        numberMap = assets.mappings(S3kBlueSpheresAssets.MAP_NUMBERS);
        showcase = new S3kBlueSpheresShowcase(assets, session, palette);
        audio.fadeOutMusic();
    }

    /** One frame; true once the loop has ended and the levels should advance. */
    boolean update(int pressedButtons) {
        stepFrames++;
        switch (step) {
            case FADE_OUT -> {
                palette.stepToBlack();
                if (stepFrames >= S3kBlueSpheresPalette.FADE_FRAMES) setUp();
            }
            case FADE_IN -> {
                palette.stepFromBlack();
                if (stepFrames >= S3kBlueSpheresPalette.FADE_FRAMES) {
                    step = Step.RUN;
                    stepFrames = 0;
                }
            }
            case RUN -> {
                processSprites(pressedButtons);
                showcase.cyclePalette();
                if (difficulty() == targetDifficulty() && (pressedButtons & 0xFF) != 0) return true;
            }
        }
        return false;
    }

    /** loc_4DDAA: ten levels ahead, unless progress is disabled. */
    void advanceLevels() {
        if (session.progressDisabled()) return;
        for (int i = 0; i < 10; i++) session.advance();
    }

    S3kBlueSpheresScene scene(S3kBlueSpheresPalette current) {
        if (step == Step.FADE_OUT) {
            return lastScene == null ? null : withCram(lastScene, current);
        }
        List<SpriteSatEntry> sat = S3kBlueSpheresObject.render(slots);
        return new S3kBlueSpheresScene(vram, planeA, planeB, null, hScrollB, 0, vScrollBG, sat, current.cram());
    }

    S3kBlueSpheresShowcase showcase() {
        return showcase;
    }

    void showFrom(S3kBlueSpheresScene results) {
        lastScene = results;
    }

    private static S3kBlueSpheresScene withCram(S3kBlueSpheresScene scene, S3kBlueSpheresPalette palette) {
        return new S3kBlueSpheresScene(scene.vram(), scene.planeA(), scene.planeB(), scene.hScrollA(),
                scene.hScrollB(), scene.vScrollA(), scene.vScrollB(), scene.sat(), palette.cram());
    }

    private void setUp() {
        try {
            palette.clearTarget();
            palette.setTarget(0x00, assets.paletteWords(S3kBlueSpheresAssets.PAL_SONIC_TAILS, 16));
            palette.setTarget(0x10, assets.paletteWords(S3kBlueSpheresAssets.PAL_KNUCKLES, 16));
            palette.setTarget(0x20, assets.paletteWords(S3kBlueSpheresAssets.PAL_RESULTS_3, 16));
            palette.setTarget(0x30, assets.paletteWords(S3kBlueSpheresAssets.PAL_ENDING_SK_LOGO, 16));
            // loc_4DC1A reloads only some art. VRAM keeps the results screen's tiles,
            // including ArtKos_BlueSphereNormalText at ArtTile_BlueSphere_Misc that the
            // LEVEL and difficulty sprites draw from; sub_4C8E4's Clear_DisplayData
            // fills only VRAM $0000-$003F.
            vram = lastScene != null ? lastScene.vram().clone() : S3kBlueSpheresAssets.emptyVram();
            vram[0] = new Pattern();
            vram[1] = new Pattern();
            S3kBlueSpheresAssets.load(vram, 0, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SK_LOGO));
            showcase.loadArt(vram);
            Arrays.fill(planeA, 0);
            Arrays.fill(planeB, 0);
            int[] backdrop = assets.enigma(S3kBlueSpheresAssets.MAP_ENI_TITLE_BG, 2 << 13);
            for (int i = 0; i < 14; i++) {
                int command = assets.word(S3kBlueSpheresAssets.RESULTS_BACKDROP_TARGETS + i * 2);
                S3kBlueSpheresPlanes.copy(planeB, ((command & 0x3FFF) | 0xC000) - 0xE000, backdrop, 12, 8);
            }
            vScrollBG = 0x10;
            for (int line = 0; line < 0xE0; line++) hScrollB[line] -= 0x10;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Cannot load the Blue Spheres perfect screen", e);
        }
        for (S3kBlueSpheresObject slot : slots) slot.clear();
        S3kBlueSpheresObject level = slots[SLOT_LEVEL];
        level.routine = this::levelInit;
        level.setY(0xC0);
        slots[SLOT_DIFFICULTY].routine = this::difficultyInit;
        processSprites(0);
        showcase.resetSuper();
        audio.playMusic(Sonic3kMusic.INVINCIBILITY.id);
        palette.fillBlack();
        step = Step.FADE_IN;
        stepFrames = 0;
    }

    private void processSprites(int pressedButtons) {
        pressed = pressedButtons & 0xFF;
        for (S3kBlueSpheresObject slot : slots) slot.drawn = false;
        for (S3kBlueSpheresObject slot : slots) if (slot.routine != null) slot.routine.run(slot);
    }

    private int difficulty() {
        return (session.difficultyWord >>> 8) & 0xFF;
    }

    private int targetDifficulty() {
        return session.difficultyWord & 0xFF;
    }

    // ---- Obj_SpheresTitle_4D03E in Player_2: the level number ----

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

    // ---- Obj_SphereResults_4E0A4 (sonic3k.asm:102046-102114): the difficulty counter ----

    private void difficultyInit(S3kBlueSpheresObject self) {
        self.mappings = textMap;
        self.artTile = ART_TEXT;
        self.setX(0x120);
        self.setY(0x140);
        self.priority = 0x80;
        self.frame = 4;
        for (int i = 1; i <= 4; i++) {
            S3kBlueSpheresObject digit = slots[SLOT_DIFFICULTY + i];
            digit.clear();
            digit.mappings = numberMap;
            digit.artTile = ART_TEXT;
            digit.priority = 0x80;
            digit.routine = d -> { if (difficulty() != 12) d.draw(); };
            digit.setX(0x114 + (i - 1) * 8);
            digit.setY(0x150);
        }
        slots[SLOT_DIFFICULTY + 1].frame = 0x0A;
        slots[SLOT_DIFFICULTY + 4].frame = 0x0B;
        self.scratch = 0x1D;
        self.routine = this::difficultyCounter;
        difficultyCounter(self);
    }

    /** loc_4E116: every 30 frames the shown difficulty rises one step towards the target. */
    private void difficultyCounter(S3kBlueSpheresObject self) {
        twoDigits(SLOT_DIFFICULTY, (difficulty() + 1) & 0xFF);
        if (--self.scratch < 0) {
            self.scratch = 0x1D;
            if (difficulty() != targetDifficulty()) {
                session.difficultyWord = (session.difficultyWord + 0x100) & 0xFFFF;
                showcase.spawn(slots, FIRST_DYNAMIC, difficulty());
                audio.playSfx(SFX_LAUNCH_READY);
            }
        }
        self.frame = difficulty() == 12 ? 5 : 4;
        self.draw();
    }

    /** sub_4D1AA (sonic3k.asm:100923-100962): two digits, a leading zero blank, centred. */
    private void twoDigits(int parent, int value) {
        int tens = (value & 0xFF) / 10;
        int ones = (value & 0xFF) % 10;
        int significant = tens != 0 ? 2 : ones != 0 ? 1 : 0;
        slots[parent + 2].frame = significant >= 2 ? tens : S3kBlueSpheresDigits.BLANK;
        slots[parent + 3].frame = significant >= 1 ? ones : S3kBlueSpheresDigits.BLANK;
        int blanks = 2 - significant;
        int x = 0x113 + blanks * 4;
        int child = parent + 1;
        for (int i = 0; i <= blanks; i++) slots[child++].setX(x);
        for (int i = 0; i <= significant; i++) {
            x += 8;
            slots[child++].setX(x);
        }
    }
}
