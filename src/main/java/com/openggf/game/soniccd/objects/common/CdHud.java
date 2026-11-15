package com.openggf.game.soniccd.objects.common;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.Pattern;
import com.openggf.level.objects.FixedRuntimeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * HUD, object $1C with a non-negative subtype ({@code ObjHUD}, HUD and Points/Main.asm:67-140),
 * in its reserved slots: score and time ({@code objHUDScoreSlot}), lives ({@code objHUDLivesSlot},
 * subtype 1) and rings ({@code objHUDRingsSlot}, secondary subtype 1), loaded by LevelStart
 * (Level/Main.asm:262-266). The sprites are screen-positioned mappings over tile $8568; the
 * digits are tiles rewritten by {@code UpdateHUD} (HUD and Points/Main.asm:186-339), which the
 * score object performs here into the HUD sheet's own tiles once per frame.
 *
 * <p>Differences from Sonic 1: the time reads minutes, seconds and centiseconds
 * ({@code mulu #100; divu #60}, HUD and Points/Main.asm:292-305); the HUD is three objects, not
 * Sonic 1's single HUD sprite. The debug position tracker (frame 2) is not hosted.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class CdHud extends CdObject implements FixedRuntimeObjectInstance, ObjectVisualFacing.NonDirectional {
    public enum Kind {
        /** Frame 0 at (16+128, 8+128): SCORE, TIME and the ring digits (HUD and Points/Main.asm:91-92,134). */
        SCORE_TIME(2, 0, 8),
        /** Frame 1 at (16+128, 200+128) (HUD and Points/Main.asm:105-108). */
        LIVES(3, 1, 200),
        /** Frame 3, the RINGS label (HUD and Points/Main.asm:94-97). */
        RINGS(5, 3, 8);

        final int slot;
        final int frame;
        final int screenY;

        Kind(int slot, int frame, int screenY) {
            this.slot = slot;
            this.frame = frame;
            this.screenY = screenY;
        }

        public int slot() { return slot; }
    }

    /** ObjHUD_Init: move.w #16+128,oX(a0) (HUD and Points/Main.asm:91). */
    private static final int SCREEN_X = 16;
    /** VRAM destinations of UpdateHUD's VDP commands, as tiles past the HUD base $568. */
    private static final int SCORE_DIGITS = 0xB060 / 32 - 0x568;     // HUD and Points/Main.asm:227
    private static final int MINUTE_DIGIT = 0xB220 / 32 - 0x568;     // HUD and Points/Main.asm:282
    private static final int SECOND_DIGITS = 0xB260 / 32 - 0x568;    // HUD and Points/Main.asm:287
    private static final int CENTISECOND_DIGITS = 0xB2E0 / 32 - 0x568; // HUD and Points/Main.asm:292
    private static final int RING_DIGITS = 0xB360 / 32 - 0x568;      // HUD and Points/Main.asm:239
    private static final int LIVES_DIGIT = 0xB4A0 / 32 - 0x568;      // HUD and Points/Main.asm:640
    /** Art_HUDNumbers: $40 bytes (two tiles, one column) per digit (lsl.w #6, HUD and Points/Main.asm:555). */
    private static final int DIGIT_BYTES = 0x40;

    private final Kind kind;
    private int lastScore = -1;
    private int lastRings = -1;
    private long lastTime = -1;
    private int lastLives = -1;

    public CdHud(Kind kind) {
        super(new ObjectSpawn(0, 0, 0x1C, kind == Kind.LIVES ? 1 : 0, 0, false, 0), "HUD");
        this.kind = kind;
        this.tileWord = CdObjectArtProvider.HUD_TILE;
        this.priority = 0;
    }

    public Kind kind() { return kind; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (kind == Kind.SCORE_TIME) updateDigits();
        if (kind == Kind.RINGS) updateRingsFlash(vIntRunCount);
    }

    /**
     * ObjHUD_Main .Rings: with rings, bclr #5 of the tile word's high byte keeps palette 0; with
     * none, eori.b #$20 whenever levelVIntCounter's low nibble is 0 (HUD and Points/Main.asm:120-131).
     */
    private void updateRingsFlash(int vIntRunCount) {
        var state = services().levelGamestate();
        if (state != null && state.getRings() != 0) {
            tileWord &= ~0x2000;
        } else if ((vIntRunCount & 0xF) == 0) {
            tileWord ^= 0x2000;
        }
    }

    /** UpdateHUD's counters, rewritten only when their value changes (HUD and Points/Main.asm:219-311). */
    private void updateDigits() {
        var sheet = services().renderManager() == null ? null : services().renderManager().getSheet(CdObjectArtProvider.HUD);
        var provider = services().renderManager() == null ? null
                : services().renderManager().getArtProvider() instanceof CdObjectArtProvider p ? p : null;
        if (sheet == null || provider == null || provider.image() == null) return;
        Pattern[] tiles = sheet.getPatterns();
        int numbers = provider.entry().hudNumbersArt();
        var state = services().levelGamestate();
        var game = services().gameState();
        int score = game != null ? game.getScore() : 0;
        int rings = state != null ? Math.min(state.getRings(), 999) : 0;
        int lives = game != null ? Math.min(game.getLives(), 9) : 0;
        long time = state != null ? state.getTimerFrames() : 0;
        boolean changed = false;
        if (score != lastScore) {
            // HUD_ResetScore writes six blanks and a 0 (HUD and Points/Main.asm:369-418), then HUD_DrawScore.
            if (lastScore < 0) writeCells(tiles, numbers, SCORE_DIGITS, new int[] {-1, -1, -1, -1, -1, -1, 0});
            drawCounter(tiles, numbers, SCORE_DIGITS, score, 6);
            lastScore = score;
            changed = true;
        }
        if (rings != lastRings) {
            // HUD_ResetRings: blank, blank, 0 (HUD and Points/Main.asm:359-363,417-418); HUD_DrawRings: 3 digits, leading zeros kept blank.
            writeCells(tiles, numbers, RING_DIGITS, new int[] {-1, -1, 0});
            drawCounter(tiles, numbers, RING_DIGITS, rings, 3);
            lastRings = rings;
            changed = true;
        }
        if (time != lastTime) {
            long minutes = Math.min(9, time / 3600);
            int seconds = (int) (time / 60 % 60);
            int frames = (int) (time % 60);
            int centiseconds = minutes >= 9 && seconds == 59 && frames == 59 ? 99 : frames * 100 / 60;
            drawNumber(tiles, numbers, MINUTE_DIGIT, (int) minutes, 1);
            drawNumber(tiles, numbers, SECOND_DIGITS, seconds, 2);
            drawNumber(tiles, numbers, CENTISECOND_DIGITS, centiseconds, 2);
            lastTime = time;
            changed = true;
        }
        if (lives != lastLives) {
            drawNumber(tiles, numbers, LIVES_DIGIT, lives, 1);   // HUD_DrawLives (HUD and Points/Main.asm:639-651)
            lastLives = lives;
            changed = true;
        }
        if (changed) {
            PatternSpriteRenderer renderer = art(CdObjectArtProvider.HUD);
            if (renderer != null) renderer.updatePatternRange(services().graphicsManager(), 0, tiles.length);
        }
    }

    /** HUD_DrawCounter (HUD and Points/Main.asm:530-565): leading zeros leave their cells unwritten. */
    private void drawCounter(Pattern[] tiles, int numbers, int cell, int value, int digits) {
        boolean leading = false;
        for (int d = digits - 1; d >= 0; d--, cell += 2) {
            int digit = (int) (value / pow10(d) % 10);
            if (digit != 0) leading = true;
            if (leading) writeDigit(tiles, numbers, cell, digit);
        }
    }

    /** HUD_DrawNum (HUD and Points/Main.asm:686-718): every digit is written, zeros included. */
    private void drawNumber(Pattern[] tiles, int numbers, int cell, int value, int digits) {
        for (int d = digits - 1; d >= 0; d--, cell += 2) writeDigit(tiles, numbers, cell, (int) (value / pow10(d) % 10));
    }

    /** HUD_ResetNumber (HUD and Points/Main.asm:385-408): a negative entry is a blank cell. */
    private void writeCells(Pattern[] tiles, int numbers, int cell, int[] digits) {
        for (int digit : digits) {
            writeDigit(tiles, numbers, cell, digit);
            cell += 2;
        }
    }

    private void writeDigit(Pattern[] tiles, int numbers, int cell, int digit) {
        if (cell + 1 >= tiles.length) return;
        var provider = (CdObjectArtProvider) services().renderManager().getArtProvider();
        byte[] bytes = digit < 0 ? new byte[DIGIT_BYTES] : provider.image().slice(numbers + digit * DIGIT_BYTES, DIGIT_BYTES);
        tiles[cell].fromSegaFormat(Arrays.copyOfRange(bytes, 0, 32));
        tiles[cell + 1].fromSegaFormat(Arrays.copyOfRange(bytes, 32, 64));
    }

    private static long pow10(int d) {
        long p = 1;
        for (int i = 0; i < d; i++) p *= 10;
        return p;
    }

    /**
     * Presentation only: a capture that leaves the game HUD out ({@code debug.traceRender.showGameHud} false, the
     * switch the shared HUD obeys in trace replay and capture) draws neither the HUD objects nor the HUD's time
     * icon ({@link CdTimeIcon}); their state still updates.
     */
    static boolean shown(com.openggf.level.objects.ObjectServices services) {
        var configuration = services.configuration();
        return configuration == null || configuration.getBoolean(SonicConfiguration.TRACE_SHOW_GAME_HUD);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!shown(services())) return;
        PatternSpriteRenderer renderer = art(CdObjectArtProvider.HUD);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        int palette = CdObjectArtProvider.tilePalette(tileWord);
        renderer.drawFrameIndex(kind.frame, camera.getX() + SCREEN_X, camera.getY() + kind.screenY, false, false, palette);
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(priority); }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
