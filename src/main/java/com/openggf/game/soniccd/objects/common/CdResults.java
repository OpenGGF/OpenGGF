package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.audio.CdSoundRequests;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * End-of-level results, object $3A ({@code ObjResults}, Level/_Objects/Results/Main.asm:42-359). After its
 * spawn delay it queues PLC $10 and waits for the queue and for the player to be 336 pixels right
 * of the camera (Stardust Speedway act 3 does not wait for the player). It then becomes the first of
 * three lines from ObjResults_InitData (the others by FindObjSlot), each with a 480-frame timer,
 * tile $83C4 and the good- or bad-future mappings by {@code goodFuture}. Lines slide 8 pixels a frame
 * to their destination and draw once the timer is below 472; the first line then tallies: each frame
 * 100 off the time bonus and 100 off the ring bonus, 10 points (100 score) for each; when both are
 * empty its timer becomes at least 45 and counts down to the next-level routine.
 *
 * <p>The next-level routine reports the cleared act to the program flow
 * ({@link com.openggf.game.soniccd.program.CdStageExit#actCleared}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class CdResults extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** USA: move.w #480,oResultsTimer (Results/Main.asm:96-98, 107-109). */
    private static final int LINE_TIMER = 480;
    /** USA: cmpi.w #472,oResultsTimer; bcc .End (Results/Main.asm:171-176). */
    private static final int DRAW_BELOW = 472;
    /** Three RESOBJ entries (Results/Main.asm:355-359), counted by .LoadResults' moveq into d6 (Results/Main.asm:90-92). */
    private static final int LINES = 3;
    /** ObjResults_Init: moveq #$10,d0; jsr LoadPLC (Results/Main.asm:66-68). */
    private static final int RESULTS_PLC = 0x10;
    private static final int FM_TALLY = 0xBD;
    private static final int FM_SSWARP = 0xC8;

    /** {@code timeBonus.w} and {@code ringBonus.w}, shared by every line. */
    static final class Bonuses implements com.openggf.game.rewind.RewindStateful<Bonuses.State> {
        int time;
        int ring;
        boolean nextLevel;

        record State(int time, int ring, boolean nextLevel) { }

        @Override public State captureRewindStateValue() { return new State(time, ring, nextLevel); }
        @Override public void restoreRewindStateValue(State state) {
            time = state.time();
            ring = state.ring();
            nextLevel = state.nextLevel();
        }
    }

    private final Bonuses bonuses;
    private int routine;
    private int timer;
    private int screenY;
    private int destX;
    private int frame;
    private int plcDrain;
    // Derived sheet/upload state belongs to the provider, not to the gameplay snapshot.
    @com.openggf.game.rewind.RewindTransient(reason = "Provider sheet/upload cache; invalidated after restore")
    private int drawnScore = -1, drawnTime = -1, drawnRings = -1;

    @Override
    protected void afterGenericRewindStateRestored(com.openggf.game.rewind.schema.RewindCaptureContext context) {
        super.afterGenericRewindStateRestored(context);
        // The shared sheets and GPU upload can still contain a later tally's digits.
        drawnScore = drawnTime = drawnRings = -1;
    }

    /**
     * StartResults: LevelEndSpawn $3A with oResultsTimer = delay (Level End/Main.asm:838-842). Public for the
     * other StartResults caller, the flower capsule ({@code objects/boss/CdCapsule}).
     */
    public CdResults(int spawnDelay, int timeBonus, int ringBonus) {
        this(new Bonuses(), 0);
        bonuses.time = timeBonus;
        bonuses.ring = ringBonus;
        timer = spawnDelay;
    }

    private CdResults(Bonuses bonuses, int routine) {
        super(new ObjectSpawn(0, 0, 0x3A, 0, 0, false, 0), "Results");
        this.bonuses = bonuses;
        this.routine = routine;
    }

    public CdResults(ObjectSpawn spawn) {
        this(new Bonuses(), 0);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // ObjResults_Init: subq.b #1,oResultsTimer; beq .LoadPLC (Results/Main.asm:61-69).
                timer = (timer - 1) & 0xFF;
                if (timer != 0) return;
                queuePlc();
                routine = 2;
                // Falls into ObjResults_WaitPLC, which sees the cue just queued (Results/Main.asm:66-77).
                if (plcDrain == 0) waitPlc(player);
            }
            case 2 -> waitPlc(player);
            case 4 -> move();
            case 6 -> bonus();
            default -> {
                // ObjResults_NextLevel (Results/Main.asm:277-345): levelRestart 2, read once by LevelStart.
                if (bonuses.nextLevel) return;
                bonuses.nextLevel = true;
                com.openggf.game.soniccd.program.CdStageExit.actCleared(services());
            }
        }
    }

    private void queuePlc() {
        var level = cdLevel();
        CdObjectArtProvider provider = artProvider();
        int batch = flags().scrollLock ? CdPlcQueue.FAST_BATCH : CdPlcQueue.SLOW_BATCH;
        plcDrain = level == null || provider == null ? 0
                : CdPlcQueue.framesToDrain(provider.image(), level.payload(), RESULTS_PLC, batch);
    }

    /** ObjResults_WaitPLC (Results/Main.asm:75-152). */
    private void waitPlc(PlayableEntity player) {
        if (plcDrain > 0 && --plcDrain > 0) return;
        boolean ssz3 = zone() == 5 && act() == 2;
        var camera = services().camera();
        if (!ssz3) {
            if (player == null || camera == null) return;
            if (((camera.getX() + 336) & 0xFFFF) >= (player.getCentreX() & 0xFFFF)) return;
        }
        CdObjectArtProvider provider = artProvider();
        int data = provider == null ? -1 : provider.entry().offset("resultsInitData");
        if (data < 0) return;
        var objects = services().objectManager();
        for (int line = 0; line < LINES; line++) {
            CdResults target = line == 0 ? this : new CdResults(bonuses, 4);
            int at = data + line * 8;
            target.timer = LINE_TIMER;
            target.routine = 4;
            // move.w #$83C4,oTile(a1); in Stardust Speedway act 3 #$82F2 and that act's mappings (Results/Main.asm:114-124).
            target.tileWord = ssz3 ? CommonObjectArt.RESULTS_TILE_SSZ3 : CommonObjectArt.RESULTS_TILE;
            target.screenY = provider.image().u16(at);
            target.x = provider.image().u16(at + 2);
            target.destX = provider.image().u16(at + 4);
            target.frame = provider.image().u8(at + 7) + (line == 2 ? act() : 0);
            if (line != 0 && objects != null) objects.addDynamicObject(target);   // ResultsFindObjSlot
        }
    }

    /** ObjResults_Move (Results/Main.asm:155-189). */
    private void move() {
        if (timer != 0) timer--;
        if (destX == x) {
            if (frame == 0) routine = 6;
        } else {
            x += destX > x ? 8 : -8;
        }
    }

    /** ObjResults_Bonus (Results/Main.asm:193-276). */
    private void bonus() {
        int points = 0;
        if (bonuses.time != 0) {
            points += 10;                                    // .TimeBonus
            bonuses.time = (bonuses.time - 100) & 0xFFFF;
            if (bonuses.ring != 0) {
                points += 10;                                // falls into .RingBonus
                bonuses.ring = (bonuses.ring - 100) & 0xFFFF;
            }
        } else if (bonuses.ring != 0) {
            points += 10;                                    // .RingBonus
            bonuses.ring = (bonuses.ring - 100) & 0xFFFF;
        } else {
            timer = (timer - 1) & 0xFFFF;                    // subq.w #1; bpl .ChkWarpSound
            if ((timer & 0x8000) != 0) routine = 8;
            // ObjResults_Bonus .ChkWarpSound (Level/_Objects/Results/Main.asm:211-219).
            if (timer == 30 && flags().specialStage) services().playSfx(FM_SSWARP);
            return;
        }
        if (bonuses.time != 0 || bonuses.ring != 0) {
            if (timer != 0) timer--;
            // ObjResults_Bonus .PlayTallySound tests bit 0 of the timer's high byte
            // (Level/_Objects/Results/Main.asm:258-267).
            if ((timer & 0x100) == 0) services().playSfx(FM_TALLY);
        } else {
            CdSoundRequests.resultsChime(services());
            timer = Math.max(timer, 45);
        }
        var game = services().gameState();
        if (game != null) game.addScore(points * 10);        // ResultsAddPoints, units of ten
    }

    int routine() { return routine; }
    int lineFrame() { return frame; }
    int timer() { return timer; }
    int screenY() { return screenY; }
    Bonuses bonuses() { return bonuses; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine < 4 || (routine == 4 && timer >= DRAW_BELOW)) return;
        boolean ssz3 = tileWord == CommonObjectArt.RESULTS_TILE_SSZ3;
        String key = flags().goodFuture ? (ssz3 ? CommonObjectArt.RESULTS_GOOD_SSZ3 : CommonObjectArt.RESULTS_GOOD)
                : (ssz3 ? CommonObjectArt.RESULTS_BAD_SSZ3 : CommonObjectArt.RESULTS_BAD);
        if (frame == 1) updateDigitArt(ssz3);
        PatternSpriteRenderer renderer = art(key);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        renderer.drawFrameIndex(frame, camera.getX() + CdScreenPosition.centredOrigin(services()) + x - 128, camera.getY() + screenY - 128, false, false,
                CdObjectArtProvider.tilePalette(tileWord));
    }

    /**
     * UpdateHUD writes bonus digits into the results tiles, and its score tiles remain visible there.
     * Our sheets own copies, so update both mapping variants explicitly.
     * UpdateHUD (Level/_Objects/HUD and Points/Main.asm:313-336);
     * HUD_DrawBonus (Level/_Objects/HUD and Points/Main.asm:444-490).
     */
    private void updateDigitArt(boolean ssz3) {
        var provider = artProvider();
        if (provider == null) return;
        int score = services().gameState() == null ? 0 : services().gameState().getScore();
        if (score == drawnScore && bonuses.time == drawnTime && bonuses.ring == drawnRings) return;
        int base = tileWord & 0x7FF;
        for (String key : ssz3 ? new String[]{CommonObjectArt.RESULTS_BAD_SSZ3, CommonObjectArt.RESULTS_GOOD_SSZ3}
                : new String[]{CommonObjectArt.RESULTS_BAD, CommonObjectArt.RESULTS_GOOD}) {
            var sheet = provider.getSheet(key);
            if (sheet == null) continue;
            writeDigits(provider, sheet.getPatterns(), 0xB060 / 32 - base, score, 7);
            writeDigits(provider, sheet.getPatterns(), (ssz3 ? 0x6D40 : 0x8780) / 32 - base, bonuses.time, 5);
            writeDigits(provider, sheet.getPatterns(), (ssz3 ? 0x6E80 : 0x88C0) / 32 - base, bonuses.ring, 5);
            var renderer = art(key);
            if (renderer != null) renderer.updatePatternRange(services().graphicsManager(), 0, sheet.getPatterns().length);
        }
        drawnScore = score;
        drawnTime = bonuses.time;
        drawnRings = bonuses.ring;
    }

    static void writeDigits(CdObjectArtProvider provider, com.openggf.level.Pattern[] tiles, int cell,
                            int value, int digits) {
        int divisor = 1;
        for (int i = 1; i < digits; i++) divisor *= 10;
        boolean leading = true;
        for (int i = 0; i < digits; i++, cell += 2, divisor /= 10) {
            int digit = value / divisor % 10;
            if (digit != 0 || i == digits - 1) leading = false;
            byte[] bytes = leading ? new byte[64]
                    : provider.image().slice(provider.entry().hudNumbersArt() + digit * 64, 64);
            if (cell >= 0 && cell + 1 < tiles.length) {
                tiles[cell].fromSegaFormat(java.util.Arrays.copyOfRange(bytes, 0, 32));
                tiles[cell + 1].fromSegaFormat(java.util.Arrays.copyOfRange(bytes, 32, 64));
            }
        }
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(0); }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
