package com.openggf.game.sonic2.kis2;

import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;
import com.openggf.game.titlescreen.SegaPaletteFade;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import java.util.List;

/** Chip-backed KiS2 title. Retains the engine's S2 boot logo, then runs KiS2 Obj0E without the S2 credit prelude. */
public final class Kis2TitleScreen implements TitleScreenProvider {
    private static final int BASE = PatternAtlasRange.RESULTS_SCREENS.base();
    private final LockOnAddressSpace rom;
    private final TitleScreenProvider boot;
    private Kis2TitleData data;
    private Kis2TitleAnimation animation;
    private State state = State.INACTIVE;
    private final Kis2TitleInput titleInput = new Kis2TitleInput();
    private TitleScreenAction exitAction = TitleScreenAction.ONE_PLAYER;
    private PatternSpriteRenderer knuckles, emblem, banner, text, stars;
    private boolean cached, music;
    private int starIndex, starTimer, starFrame, fadeFrames;
    private static final int[] STAR_ANIMATION = {0,1,2,1,0};

    public Kis2TitleScreen(LockOnAddressSpace rom) { this(rom, new TitleScreenManager()); }
    public Kis2TitleScreen(LockOnAddressSpace rom, TitleScreenProvider boot) {
        if (!rom.hasChip()) throw new IllegalArgumentException("KiS2 title requires the chip");
        this.rom = rom; this.boot = boot;
    }
    @Override public void initialize() {
        titleInput.resetSequence();
        exitAction = TitleScreenAction.ONE_PLAYER;
        data = new Kis2TitleData(rom);
        animation = new Kis2TitleAnimation(); cached = false; music = false;
        starIndex = 0; starTimer = 0; starFrame = 0; fadeFrames = 0;
        boot.initialize(); state = State.SEGA_LOGO;
    }
    @Override public void update(InputHandler input) {
        if (state == State.INACTIVE || state == State.EXITING) return;
        if (state == State.SEGA_LOGO) {
            boot.update(input);
            if (boot.getState() != State.SEGA_LOGO) {
                boot.reset(); animation.tick(false); state = State.FADE_IN;
                GameServices.audio().playSfx(Sonic2Sfx.SPARKLE.id);
            }
            return;
        }
        // Pal_FadeFromBlack is blocking: title object clocks remain frozen during its 22 VBlanks.
        if (state == State.FADE_IN) {
            if (++fadeFrames >= 22) state = State.ACTIVE;
            return;
        }
        boolean unlocked = titleInput.update(input.logical().player1());
        if (unlocked) GameServices.audio().playSfx(Sonic2Sfx.RING_RIGHT.id);
        // TitleScreen_Loop and Obj0E test Start, not the A/B/C action buttons.
        boolean confirm = input.logical().player1().startPressed() || input.logical().player2().startPressed();
        boolean wasComplete = animation.complete;
        animation.tick(confirm);
        if (!music && (animation.frame >= 56 || animation.complete)) {
            music = true; GameServices.audio().playMusic(Sonic2Music.TITLE.id);
        }
        if (wasComplete && confirm) {
            exitAction = titleInput.requestsLevelSelect(input.logical().player1())
                    ? TitleScreenAction.LEVEL_SELECT : TitleScreenAction.ONE_PLAYER;
            state = State.EXITING;
        }
        if (starIndex < data.starPositions.length) {
            starTimer++;
            if (starTimer <= 10) starFrame = STAR_ANIMATION[(starTimer - 1) / 2];
            else if (starTimer >= (starIndex == 0 ? 15 : 23)) {
                starTimer = 0; starIndex++;
                if (starIndex < data.starPositions.length) GameServices.audio().playSfx(Sonic2Sfx.SPARKLE.id);
            }
        }
    }
    @Override public void draw() {
        if (state == State.INACTIVE || state == State.EXITING) return;
        if (state == State.SEGA_LOGO) { boot.draw(); return; }
        GraphicsManager gm = GameServices.graphics();
        if (gm.isHeadlessMode()) return;
        if (!cached) {
            for (int i = 0; i < data.patterns.length; i++) gm.cachePatternTexture(data.patterns[i], BASE + i);
            knuckles = renderer(data.knuckles, 0); emblem = renderer(data.emblem, -1);
            banner = renderer(data.banner, -1); text = renderer(data.text, 3); stars = renderer(data.stars, 1);
            cached = true;
        }
        for (int line = 0; line < 4; line++) gm.cachePaletteTexture(palette(line), line);
        int width = gm.getProjectionWidth() > 0 ? gm.getProjectionWidth() : 320;
        int offset = (width - 320) / 2;
        gm.beginPatternBatch();
        drawBackground(gm, width);
        // The character is a high-priority sprite; the emblem-top object masks its lower edge.
        plane(gm, data.foreground, 40, offset, -animation.scroll, 40);
        if (animation.frame >= 128 || animation.complete) knuckles.drawFrameIndex(animation.bodyFrame, offset + animation.x, animation.y);
        emblem.drawFrameIndex(0, offset + 160, 104 - animation.scroll);
        if (animation.handVisible()) knuckles.drawFrameIndex(animation.handFrame, offset + animation.x, animation.handY);
        text.drawFrameIndex(1, offset + 224, 200);
        if (animation.complete) text.drawFrameIndex(0, offset + 160, 184);
        if (animation.bannerVisible) banner.drawFrameIndex(0, offset + 160, animation.bannerY);
        if (starIndex < data.starPositions.length && starTimer <= 10)
            stars.drawFrameIndex(starFrame, offset + data.starPositions[starIndex][0], data.starPositions[starIndex][1]);
        int falling = animation.frame - ("PAL".equalsIgnoreCase(GameServices.configuration().getString(SonicConfiguration.REGION)) ? 400 : 464); // Obj0E_Sonic_SpawnFallingStar, NTSC branch.
        if (falling >= 0 && falling <= 140) stars.drawFrameIndex((falling / 4 & 1) == 0 ? 0 : 3, offset + 240 - falling * 2, falling);
        gm.flushPatternBatch();
    }
    private PatternSpriteRenderer renderer(List<SpriteMappingFrame> frames, int palette) {
        PatternSpriteRenderer result = new PatternSpriteRenderer(new ObjectSpriteSheet(data.patterns, frames, palette, 1));
        result.ensurePatternsCached(GameServices.graphics(), BASE); return result;
    }
    private Palette palette(int line) {
        if (animation.complete) {
            if (line == 2 && !animation.skipped && animation.frame >= 288 && animation.frame < 328)
                return fromWhite(data.palettes[line], Math.min(7, (animation.frame - 288) / 5));
            if (line == 2) {
                Palette result = data.palettes[line].deepCopy();
                int entry = (animation.frame / 8) % 6 * 2;
                byte[] bytes = new byte[32];
                bytes[10] = data.starCycle[entry]; bytes[11] = data.starCycle[entry + 1];
                Palette cycle = new Palette(); cycle.fromSegaFormat(bytes);
                result.colors[5].r = cycle.colors[5].r;
                result.colors[5].g = cycle.colors[5].g;
                result.colors[5].b = cycle.colors[5].b;
                return result;
            }
            return data.palettes[line];
        }
        int steps = switch (line) {
            case 0 -> animation.frame >= 128 ? 21 : 0;
            case 1 -> Math.min(21, fadeFrames);
            case 2 -> 0;
            case 3 -> Math.max(0, Math.min(21, (animation.frame - 56) / 3));
            default -> 0;
        };
        return SegaPaletteFade.apply(data.palettes[line], SegaPaletteFade.Mode.FROM_BLACK, steps);
    }
    private static Palette fromWhite(Palette target, int steps) {
        Palette result = target.deepCopy();
        int floor = 255 - steps * 255 / 7;
        for (Palette.Color color : result.colors) {
            color.r = (byte) Math.max(Byte.toUnsignedInt(color.r), floor);
            color.g = (byte) Math.max(Byte.toUnsignedInt(color.g), floor);
            color.b = (byte) Math.max(Byte.toUnsignedInt(color.b), floor);
        }
        return result;
    }
    private void drawBackground(GraphicsManager gm, int width) {
        PatternDesc desc = new PatternDesc();
        int scroll = -(-0x280 + animation.frame) >> 2;
        for (int y = 0; y < 28; y++) {
            if (y == 24 || y == 25) continue;
            int shift = y < 20 ? 0 : scroll;
            for (int x = -1; x <= (width + 7) / 8; x++) {
                int mapX = Math.floorMod(x + (shift >> 3), 64);
                desc.set(data.background[y * 64 + mapX]);
                gm.renderPatternWithId(BASE + desc.getPatternIndex(), desc, x * 8 - (shift & 7), y * 8);
            }
        }
        // SwScrl_Title reads a separate ripple displacement for each water scanline.
        gm.flushPatternBatch();
        gm.flushScreenSpace();
        float scale = (float) gm.getViewportHeight() / 224;
        for (int line = 0; line < 16; line++) {
            int screenY = 192 + line;
            int shift = scroll + data.ripple[((-(animation.frame / 8) & 31) + line) & 63];
            gm.enableScissor(gm.getViewportX(), gm.getViewportY() + (int) ((223 - screenY) * scale),
                    gm.getViewportWidth(), Math.max(1, (int) Math.ceil(scale)));
            gm.beginPatternBatch();
            for (int x = -1; x <= (width + 7) / 8; x++) {
                int mapX = Math.floorMod(x + (shift >> 3), 64);
                desc.set(data.background[(screenY >> 3) * 64 + mapX]);
                gm.renderPatternWithId(BASE + desc.getPatternIndex(), desc, x * 8 - (shift & 7), (screenY >> 3) * 8);
            }
            gm.flushPatternBatch();
            gm.flushScreenSpace();
        }
        gm.disableScissor();
        gm.beginPatternBatch();
    }
    private void plane(GraphicsManager gm, int[] map, int stride, int xOffset, int yOffset, int cols) {
        PatternDesc desc = new PatternDesc();
        for (int y = 0; y < 28; y++) for (int x = 0; x < cols; x++) {
            desc.set(map[y * stride + x]);
            if (desc.getPatternIndex() != 0) gm.renderPatternWithId(BASE + desc.getPatternIndex(), desc, xOffset + x * 8, yOffset + y * 8);
        }
    }
    @Override public void setClearColor() {
        if (state == State.SEGA_LOGO) boot.setClearColor();
        else if (data != null && !GameServices.graphics().isHeadlessMode()) {
            Palette.Color backdrop = palette(2).colors[0];
            org.lwjgl.opengl.GL11.glClearColor(Byte.toUnsignedInt(backdrop.r) / 255f,
                    Byte.toUnsignedInt(backdrop.g) / 255f, Byte.toUnsignedInt(backdrop.b) / 255f, 1);
        }
    }
    @Override public void reset() { boot.reset(); state = State.INACTIVE; cached = false; }
    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }
    @Override public TitleScreenAction consumeExitAction() { return isExiting() ? exitAction : TitleScreenAction.OTHER; }
    public Kis2TitleAnimation animation() { return animation; }
}
