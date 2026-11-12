package com.openggf.game.sonic3k.bluespheres;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.competition.NativeMenuRasterRenderer;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * One Blue Spheres visit: the title and level-code menu, its stages and the
 * results, in the {@link GameMode#BLUE_SPHERES} host mode. The screen owns
 * its palette RAM and runs the ROM's blocking fades itself. It stays open
 * while its stage runs, so the stage's entry fade-to-white covers its last
 * frame and the stage hands control back to it.
 */
public final class S3kBlueSpheresScreen implements NativeMenuScreen {
    private enum Phase {
        /** BlueSpheresTitle's Pal_FadeToBlack over whatever was shown. */
        TITLE_FADE_OUT,
        /** Pal_FadeFromBlack to the title palette. */
        TITLE_FADE_IN,
        TITLE,
        /** The stage runs; this screen only supplies the entry fade's picture. */
        STAGE,
        /** BlueSpheresResults' Pal_FadeFromWhite. */
        RESULTS_FADE_IN,
        RESULTS,
        /** The PERFECT showcase (loc_4DC1A). */
        PERFECT
    }

    private final Rom rom;
    private final S3kBlueSpheresAssets assets;
    private final Sonic3kSpecialStageProvider stages;
    private final AudioManager audio;
    private final GraphicsManager graphics;
    private final S3kBlueSpheresSession session = new S3kBlueSpheresSession();
    private final S3kBlueSpheresPalette palette = new S3kBlueSpheresPalette();
    private NativeMenuRasterRenderer renderer;
    private Phase phase = Phase.TITLE_FADE_OUT;
    private int phaseFrames;
    private S3kBlueSpheresTitle title;
    private S3kBlueSpheresResults results;
    private S3kBlueSpheresPerfect perfect;
    private S3kBlueSpheresScene scene;
    private Pattern[] vram = S3kBlueSpheresAssets.emptyVram();
    private boolean stageRequested;
    private boolean resultsLeft;
    private int lastHeld;
    private Exit exit = Exit.NONE;

    S3kBlueSpheresScreen(Rom rom, S3kBlueSpheresAssets assets, Sonic3kSpecialStageProvider stages,
                         AudioManager audio, GraphicsManager graphics) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.stages = Objects.requireNonNull(stages, "stages");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    /** The current phase's name, for diagnostics and round-trip tests. */
    public String phaseName() {
        return phase.name();
    }

    /** {@code Blue_spheres_current_level}. */
    public int level() {
        return session.level;
    }

    @Override
    public GameMode mode() {
        return GameMode.BLUE_SPHERES;
    }

    @Override
    public void initialize() {
        // BlueSpheresStartup: palette RAM starts black.
        palette.fillBlack();
        beginTitle();
    }

    @Override
    public void update(InputHandler input) {
        PlayerInputState player = input.logical().player1();
        int held = controller(player.heldMask(), player.actionHeldMask(), player.startHeld());
        // Ctrl_1_pressed is the newly held bits; the logical snapshot's own
        // pressed mask may also carry auto-repeat, which the ROM never sees.
        int pressed = held & ~lastHeld;
        lastHeld = held;
        step(held, pressed);
    }

    void step(int held, int pressed) {
        phaseFrames++;
        switch (phase) {
            case TITLE_FADE_OUT -> {
                palette.stepToBlack();
                if (phaseFrames >= S3kBlueSpheresPalette.FADE_FRAMES) setUpTitle();
            }
            case TITLE_FADE_IN -> {
                palette.stepFromBlack();
                if (phaseFrames >= S3kBlueSpheresPalette.FADE_FRAMES) enter(Phase.TITLE);
            }
            case TITLE -> {
                title.update(held, pressed);
                if (title.consumeTitlePaletteChange()) {
                    int[] words = title.titlePalette();
                    palette.setNormal(0, words);
                    palette.setTarget(0, words);
                }
                scene = title.scene(vram, palette);
                if (stageRequested) startStage();
            }
            case STAGE -> { }
            case RESULTS_FADE_IN -> {
                palette.stepFromWhite();
                if (phaseFrames >= S3kBlueSpheresPalette.FADE_FRAMES) enter(Phase.RESULTS);
            }
            case RESULTS -> {
                results.update(pressed);
                scene = results.scene(palette);
                if (resultsLeft) leaveResults();
            }
            case PERFECT -> {
                if (perfect.update(pressed)) {
                    perfect.advanceLevels();
                    perfect = null;
                    beginTitle();
                } else {
                    scene = perfect.scene(palette);
                }
            }
        }
        if (phase == Phase.TITLE_FADE_OUT || phase == Phase.TITLE_FADE_IN || phase == Phase.RESULTS_FADE_IN) {
            refreshSceneCram();
        }
    }

    private void enter(Phase next) {
        phase = next;
        phaseFrames = 0;
    }

    /** BlueSpheresTitle: cmd_FadeOut and Pal_FadeToBlack over the current picture. */
    private void beginTitle() {
        audio.fadeOutMusic();
        stageRequested = false;
        enter(Phase.TITLE_FADE_OUT);
    }

    private void setUpTitle() {
        try {
            vram = titleVram();
            title = new S3kBlueSpheresTitle(assets, session, new S3kBlueSpheresTitle.Events() {
                @Override public void playSfx(int sfx) { audio.playSfx(sfx); }
                @Override public void startStage() { stageRequested = true; }
            });
            results = null;
            title.setUp(palette);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the Blue Spheres title", e);
        }
        scene = title.scene(vram, palette);
        audio.playMusic(Sonic3kMusic.CONTINUE.id);
        palette.fillBlack();
        enter(Phase.TITLE_FADE_IN);
    }

    /**
     * Obj_SpheresTitle_4CEAA's Start (loc_4D008): build the stage from the
     * current seed and hand over to the special stage.
     */
    private void startStage() {
        stageRequested = false;
        try {
            var loader = new Sonic3kSpecialStageDataLoader(rom);
            S3kBlueSpheresStage stage = S3kBlueSpheresStage.build(rom, loader, session.seed);
            session.difficultyWord = stage.difficultyWord();
            stages.armBlueSpheres(stage, session.character(), this::stageFinished);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot build the Blue Spheres stage", e);
        }
        // SpecialStage opens with cmd_Stop (sonic3k.asm:10621-10623) before its fade.
        audio.stopMusic();
        enter(Phase.STAGE);
        exit = new Exit(Destination.SPECIAL_STAGE, null, false);
    }

    /** The stage's exit fade left the palette white; cleared stages go to the results. */
    private void stageFinished(boolean cleared, int ringsLeft) {
        session.ringsLeft = ringsLeft;
        palette.fillWhite();
        if (!cleared) {
            // loc_978E: a failed Blue Spheres stage writes Game_mode $2C.
            beginTitle();
            return;
        }
        audio.fadeOutMusic();
        try {
            vram = resultsVram();
            resultsLeft = false;
            results = new S3kBlueSpheresResults(assets, session, new S3kBlueSpheresResults.Events() {
                @Override public void playSfx(int sfx) { audio.playSfx(sfx); }
                @Override public void leave() { resultsLeft = true; }
            }, vram);
            results.setUp(palette);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load the Blue Spheres results", e);
        }
        scene = results.scene(palette);
        audio.playMusic(Sonic3kMusic.ACT_CLEAR.id);
        palette.fillWhite();
        enter(Phase.RESULTS_FADE_IN);
    }

    /**
     * The results loop's tail: a perfect clear opens the showcase; otherwise
     * the full game advances one level (loc_4DBFC) and returns to the title.
     */
    private void leaveResults() {
        if (session.ringsLeft == 0) {
            try {
                perfect = new S3kBlueSpheresPerfect(assets, session, audio, palette);
                perfect.showFrom(scene);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load the Blue Spheres perfect screen", e);
            }
            enter(Phase.PERFECT);
            return;
        }
        if (!session.progressDisabled()) session.advance();
        beginTitle();
    }

    private void refreshSceneCram() {
        if (scene != null) {
            scene = new S3kBlueSpheresScene(scene.vram(), scene.planeA(), scene.planeB(), scene.hScrollA(),
                    scene.hScrollB(), scene.vScrollA(), scene.vScrollB(), scene.sat(), palette.cram());
        }
    }

    /** The VRAM BlueSpheresTitle loads (sonic3k.asm:100289-100313). */
    private Pattern[] titleVram() throws IOException {
        Pattern[] bank = S3kBlueSpheresAssets.emptyVram();
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_TITLE_TEXT,
                assets.nemesis(S3kBlueSpheresAssets.ART_NEM_CREDITS_TEXT));
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_BG,
                assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SK_LOGO));
        // Both Kosinski archives decompress back to back and DMA to tile 0.
        byte[] characters = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_CHAR);
        byte[] sprites = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_CHAR_SPRITE);
        byte[] joined = new byte[characters.length + sprites.length];
        System.arraycopy(characters, 0, joined, 0, characters.length);
        System.arraycopy(sprites, 0, joined, characters.length, sprites.length);
        S3kBlueSpheresAssets.load(bank, 0, joined);
        loadNormalText(bank);
        return bank;
    }

    /** The VRAM BlueSpheresResults loads (sonic3k.asm:101627-101652). */
    private Pattern[] resultsVram() throws IOException {
        Pattern[] bank = S3kBlueSpheresAssets.emptyVram();
        byte[] general = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_RESULTS_GENERAL);
        byte[] icons = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_RESULTS_TK_ICONS);
        byte[] joined = new byte[general.length + icons.length];
        System.arraycopy(general, 0, joined, 0, general.length);
        System.arraycopy(icons, 0, joined, general.length, icons.length);
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_EMERALDS, joined);
        loadNormalText(bank);
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_TITLE_TEXT,
                assets.nemesis(S3kBlueSpheresAssets.ART_NEM_CREDITS_TEXT));
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_BG,
                assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SK_LOGO));
        return bank;
    }

    /** {@code ArtKos_BlueSphereNormalText}: a {@code $6D0}-word DMA to ArtTile_BlueSphere_Misc. */
    private void loadNormalText(Pattern[] bank) throws IOException {
        byte[] text = assets.kosinski(S3kBlueSpheresAssets.ART_KOS_NORMAL_TEXT);
        int length = Math.min(text.length, 0x6D0 * 2);
        byte[] dma = new byte[length];
        System.arraycopy(text, 0, dma, 0, length);
        S3kBlueSpheresAssets.load(bank, S3kBlueSpheresAssets.TILE_MISC, dma);
    }

    /** Ctrl_1 layout from the logical pad: directions, B $10, C $20, A $40, Start $80. */
    static int controller(int directions, int actions, boolean start) {
        int value = directions & 0x0F;
        if ((actions & InputActionMasks.ACTION_B) != 0) value |= S3kBlueSpheresTitle.B;
        if ((actions & InputActionMasks.ACTION_C) != 0) value |= S3kBlueSpheresTitle.C;
        if ((actions & InputActionMasks.ACTION_A) != 0) value |= S3kBlueSpheresTitle.A;
        if (start) value |= S3kBlueSpheresTitle.START;
        return value;
    }

    @Override
    public void draw() {
        if (scene == null) return;
        if (renderer == null) renderer = new NativeMenuRasterRenderer(graphics);
        renderer.draw(scene.rasterize(), scene.cram());
    }

    @Override
    public void setClearColor() {
        glClearColor(0, 0, 0, 1);
    }

    @Override
    public void reset() {
        stages.disarmBlueSpheres();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        scene = null;
        title = null;
        results = null;
        perfect = null;
    }

    @Override
    public Exit consumeExit() {
        Exit value = exit;
        exit = Exit.NONE;
        return value;
    }
}
