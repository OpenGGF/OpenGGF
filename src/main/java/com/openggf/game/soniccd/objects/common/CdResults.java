package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
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
 * ({@link com.openggf.game.soniccd.program.CdStageExit#actCleared}). Not hosted: Stardust Speedway act 3's
 * own mappings and tile, and the sounds.
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

    /** {@code timeBonus.w} and {@code ringBonus.w}, shared by every line. */
    static final class Bonuses {
        int time;
        int ring;
        boolean nextLevel;
    }

    private final Bonuses bonuses;
    private int routine;
    private int timer;
    private int screenY;
    private int destX;
    private int frame;
    private int plcDrain;

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
            return;
        }
        if (bonuses.time != 0 || bonuses.ring != 0) {
            if (timer != 0) timer--;                         // .HaveBonus
        } else if (timer < 45) {
            timer = 45;                                      // cmpi.w #45; bcc .AddPoints; move.w #45 (USA)
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
        PatternSpriteRenderer renderer = art(key);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        renderer.drawFrameIndex(frame, camera.getX() + x - 128, camera.getY() + screenY - 128, false, false,
                CdObjectArtProvider.tilePalette(tileWord));
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(0); }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
