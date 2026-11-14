package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.FixedRuntimeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Title card, object $3C ({@code ObjTitleCard}, Level/_Objects/Title Card/Main.asm:23-191).
 * the level start puts the parent in {@code objTtlCardSlot} (Level/Main.asm:271-275); its routine 0
 * places eight pieces with {@code FindObjSlot} from the stage's {@code ObjTitleCard_Data}, so the
 * pieces take the first free dynamic slots after the spawner's first pass. Pieces slide in by 8,
 * hold for their timer, slide out by $10 and delete; the parent slides vertically the same way,
 * then waits for the PLC queue and clears {@code ctrlLocked} and {@code scrollLock}.
 *
 * <p>Hosted here: slots, motion, art, the HUD timer hold (UpdateHUD ticks only while
 * {@code ctrlLocked} is clear, HUD and Points/Main.asm:263-264), the {@code ctrlLocked} and
 * {@code scrollLock} flags ({@link CdLevelFlags}) and the PLC 2 wait, timed by {@link CdPlcQueue}.
 * The player and camera owners do not read the two flags yet.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class CdTitleCard extends CdObject implements FixedRuntimeObjectInstance, ObjectVisualFacing.NonDirectional {
    /** objTtlCardSlot (Level/_Definitions.inc:135). */
    public static final int PARENT_SLOT = 4;
    private static final int PIECES = 8;          // moveq #7,d6 (Title Card/Main.asm:50)
    private static final int ACT_PIECE = 5;       // cmpi.b #5,d1 (Title Card/Main.asm:66)

    private enum Routine { INIT, SLIDE_IN_VERT, SLIDE_IN_HORIZ, SLIDE_OUT_VERT, SLIDE_OUT_HORIZ, WAIT_PLC }

    /** moveq #2,d0; jmp LoadPLC (Title Card/Main.asm:147-148). */
    private static final int TITLE_CARD_PLC = 2;

    private Routine routine;
    private int plcDrain;
    private int screenY;
    private int frame;
    private int animTime;
    private int slideInTarget;
    private int slideOutTarget;

    /** The parent, as LevelStart writes it ($3C into objTtlCardSlot). */
    public static CdTitleCard parent() {
        return new CdTitleCard(Routine.INIT, 0, 0, 0, 0, 0, 0);
    }

    private CdTitleCard(Routine routine, int x, int screenY, int frame, int animTime, int slideInTarget,
                        int slideOutTarget) {
        super(new ObjectSpawn(x, 0, 0x3C, 0, 0, false, 0), "TitleCard");
        this.routine = routine;
        this.x = x;
        this.screenY = screenY;
        this.frame = frame;
        this.animTime = animTime;
        this.slideInTarget = slideInTarget;
        this.slideOutTarget = slideOutTarget;
        this.tileWord = CdObjectArtProvider.TITLE_CARD_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> init();
            case SLIDE_IN_VERT -> {
                // ObjTitleCard_SlideInVert: step 8 toward oVar2E (Title Card/Main.asm:80-96).
                if (screenY == slideInTarget) routine = Routine.SLIDE_OUT_VERT;
                else screenY += screenY < slideInTarget ? 8 : -8;
            }
            case SLIDE_IN_HORIZ -> {
                // ObjTitleCard_SlideInHoriz: step 8 toward oVar2A (Title Card/Main.asm:101-117).
                if (x == slideInTarget) routine = Routine.SLIDE_OUT_HORIZ;
                else x += x < slideInTarget ? 8 : -8;
            }
            case SLIDE_OUT_VERT -> {
                // ObjTitleCard_SlideOutVert: hold oAnimTime, then step $10 toward oVar30 (Title Card/Main.asm:122-148).
                if (animTime != 0) animTime--;
                else if (screenY == slideOutTarget) queueTitleCardPlc();
                else screenY += screenY < slideOutTarget ? 0x10 : -0x10;
            }
            case SLIDE_OUT_HORIZ -> {
                // ObjTitleCard_SlideOutHoriz: hold, then step $10 toward oVar2C, then delete (Title Card/Main.asm:153-176).
                if (animTime != 0) animTime--;
                else if (x == slideOutTarget) setDestroyed(true);
                else x += x < slideOutTarget ? 0x10 : -0x10;
            }
            case WAIT_PLC -> {
                // ObjTitleCard_WaitPLC: tst.l plcBuffer; bne .End; clr.b scrollLock; clr.b ctrlLocked;
                // delete (Title Card/Main.asm:181-191).
                if (--plcDrain > 0) return;
                CdLevelFlags flags = flags();
                flags.scrollLock = false;
                flags.ctrlLocked = false;
                var state = services().levelGamestate();
                if (state != null) state.resumeTimer();
                setDestroyed(true);
            }
        }
    }

    /**
     * .DidSlide: addq.b #4,oRoutine; move.b #1,scrollLock; moveq #2,d0; jmp LoadPLC (Title Card/Main.asm:144-148).
     * With scrollLock set the queue drains 18 tiles a level frame ({@link CdPlcQueue}).
     */
    private void queueTitleCardPlc() {
        routine = Routine.WAIT_PLC;
        flags().scrollLock = true;
        var level = cdLevel();
        CdObjectArtProvider provider = artProvider();
        plcDrain = level == null || provider == null ? 0
                : CdPlcQueue.framesToDrain(provider.image(), level.payload(), TITLE_CARD_PLC, CdPlcQueue.FAST_BATCH);
    }

    /** ObjTitleCard_Init (Title Card/Main.asm:39-75). */
    private void init() {
        routine = Routine.SLIDE_IN_VERT;
        x = 0x118;
        screenY = 0x30;
        slideOutTarget = 0x30;
        slideInTarget = 0xF0;
        animTime = 0x5A;
        priority = 4;
        var provider = services().renderManager() != null
                && services().renderManager().getArtProvider() instanceof CdObjectArtProvider p ? p : null;
        var objects = services().objectManager();
        if (provider == null || provider.image() == null || objects == null) return;
        var image = provider.image();
        int data = provider.entry().titleCardData();
        int act = actNumber();
        for (int i = 0; i < PIECES; i++) {
            int at = data + i * 8;
            int pieceFrame = image.u8(at + 6) + (i == ACT_PIECE ? act : 0);
            CdTitleCard piece = new CdTitleCard(Routine.SLIDE_IN_HORIZ, image.u16(at + 2), image.u16(at),
                    pieceFrame, image.u8(at + 7), image.u16(at + 4), image.u16(at + 2));
            piece.priority = 0;
            // TitleCardFindObjSlot = FindObjSlot (Title Card/Main.asm:18,54).
            objects.addDynamicObject(piece);
        }
    }

    /** The native {@code act} byte: 0, 1, 2 for acts 1-3 (CdStageKey act projection). */
    private int actNumber() {
        var level = services().levelManager() != null ? services().levelManager().getCurrentLevel() : null;
        return level instanceof com.openggf.game.soniccd.level.CdLevel cd ? cd.payload().stage().actNumber() - 1 : 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // ObjTitleCard_WaitPLC never calls TitleCardDrawObject (Title Card/Main.asm:181-191).
        if (routine == Routine.INIT || routine == Routine.WAIT_PLC) return;
        PatternSpriteRenderer renderer = art(CdObjectArtProvider.TITLE_CARD);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        // oSprFlags bits 2-3 clear: screen coordinates, 128 = left/top edge (DrawObjects).
        renderer.drawFrameIndex(frame, camera.getX() + x - 128, camera.getY() + screenY - 128, false, false,
                CdObjectArtProvider.tilePalette(tileWord));
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(priority); }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
