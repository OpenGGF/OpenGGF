package com.openggf.game.sonic2.options;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.titlescreen.SegaPaletteFade;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;

import java.io.IOException;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.glClearColor;

/** ROM-native options product; the host consumes typed exits and owns mode transitions. */
public final class Sonic2OptionsScreen implements NativeMenuScreen {
    // Menus are mutually exclusive. Reserve a subrange so retained level-select cache flags are safe.
    static final int PATTERN_BASE = PatternAtlasRange.MENU_AND_DATA_SELECT.base() + 0x1000;
    private final Sonic2OptionsState options;
    private final boolean overseas;
    private final Supplier<GraphicsManager> graphics;
    private final Runnable startMusic;
    private final IntConsumer playSound;
    private final PatternDesc descriptor = new PatternDesc();
    private Sonic2OptionsData data;
    private Exit exit = Exit.NONE;
    private boolean active;
    private boolean artCached;
    private int fadeFrames;
    private int animationTick;
    private int cachedBackgroundSource = -1;

    Sonic2OptionsScreen(Sonic2OptionsState options) {
        this(options, true);
    }

    Sonic2OptionsScreen(Sonic2OptionsState options, boolean overseas) {
        this(options, overseas, null, GameServices::graphics,
                () -> GameServices.audio().playMusic(Sonic2Music.OPTIONS.id),
                id -> GameServices.audio().playMusicMailboxNativeRequest(id));
    }

    Sonic2OptionsScreen(Sonic2OptionsState options, boolean overseas, Sonic2OptionsData data,
            Supplier<GraphicsManager> graphics, Runnable startMusic, IntConsumer playSound) {
        this.options = Objects.requireNonNull(options);
        this.overseas = overseas;
        this.data = data;
        this.graphics = graphics;
        this.startMusic = startMusic;
        this.playSound = playSound;
    }

    @Override
    public void initialize() {
        if (data == null) {
            try {
                data = Sonic2OptionsData.load(GameServices.rom().getRom(), overseas);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load Sonic 2 options from the active ROM", e);
            }
        }
        options.enter();
        exit = Exit.NONE;
        active = true;
        artCached = false;
        fadeFrames = 0;
        animationTick = 0;
        cachedBackgroundSource = -1;
        startMusic.run();
    }

    @Override
    public void update(InputHandler input) {
        if (!active || exit.destination() != Destination.NONE) {
            return;
        }
        // Pal_FadeFromBlack is a blocking 22-V-int loop; menu controls start afterwards.
        if (fadeFrames < SegaPaletteFade.ROM_FADE_FRAMES) {
            fadeFrames++;
            return;
        }
        exit = options.update(input.logical(), playSound);
        animationTick++;
    }

    @Override
    public Exit consumeExit() {
        Exit result = exit;
        if (result.destination() != Destination.NONE) {
            active = false;
            exit = Exit.NONE;
        }
        return result;
    }

    @Override
    public void reset() {
        active = false;
        exit = Exit.NONE;
        artCached = false;
        // Player_option, Two_player_items and Sound_test_sound survive a menu reset.
    }

    @Override
    public void draw() {
        if (!active || data == null) {
            return;
        }
        GraphicsManager gm = graphics.get();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }
        if (!artCached) {
            for (int i = 0; i < data.patterns.length; i++) {
                gm.cachePatternTexture(data.patterns[i], PATTERN_BASE + i);
            }
            artCached = true;
        }
        for (int line = 0; line < data.palettes.length; line++) {
            gm.cachePaletteTexture(visiblePalette(line), line);
        }
        int source = data.backgroundSource(animationTick);
        if (source != cachedBackgroundSource) {
            for (int tile = 0; tile < 10; tile++) {
                gm.updatePatternTexture(data.backgroundArt[source + tile], PATTERN_BASE + 1 + tile);
            }
            cachedBackgroundSource = source;
        }
        int viewportWidth = Math.max(320, gm.getProjectionWidth());
        int offset = (viewportWidth - 320) / 2;
        gm.beginPatternBatch();
        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < (viewportWidth + 7) / 8; x++) {
                tile(gm, data.backgroundMap[y * 40 + x % 40], x * 8, y * 8);
            }
        }
        for (int row = 0; row < 3; row++) {
            int[] box = data.box(row, options);
            for (int y = 0; y < Sonic2OptionsData.BOX_HEIGHT; y++) {
                for (int x = 0; x < Sonic2OptionsData.BOX_WIDTH; x++) {
                    tile(gm, box[y * Sonic2OptionsData.BOX_WIDTH + x],
                            offset + (9 + x) * 8, (3 + row * 8 + y) * 8);
                }
            }
        }
        gm.flushPatternBatch();
    }

    private void tile(GraphicsManager gm, int word, int x, int y) {
        descriptor.set(word);
        gm.renderPatternWithId(PATTERN_BASE + (word & 0x7FF), descriptor, x, y);
    }

    private Palette visiblePalette(int line) {
        return SegaPaletteFade.fromBlack(data.palettes[line], fadeFrames);
    }

    @Override
    public void setClearColor() {
        if (data == null) {
            glClearColor(0, 0, 0, 1);
            return;
        }
        Palette.Color color = visiblePalette(0).colors[0];
        glClearColor(Byte.toUnsignedInt(color.r) / 255f, Byte.toUnsignedInt(color.g) / 255f,
                Byte.toUnsignedInt(color.b) / 255f, 1);
    }
}
