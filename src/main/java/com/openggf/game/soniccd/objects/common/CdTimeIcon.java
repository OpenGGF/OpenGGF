package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.FixedRuntimeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The HUD time icon: object $19 subtype $A in {@code objHUDIconSlot}, placed by LevelStart
 * ({@code move.b #$19,objHUDIconSlot.w; move.b #$A,...+oSubtype.w}, Level/Main.asm:268-269;
 * {@code ObjTimeIcon}, Monitor and Time Post/Main.asm:7-54). It is drawn only while a time
 * post has set a direction: frame $12 for the past, $13 for the future, flashing on odd
 * {@code levelFrames} once the timer reaches 90.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class CdTimeIcon extends CdObject implements FixedRuntimeObjectInstance, ObjectVisualFacing.NonDirectional {
    /** objHUDIconSlot, reserved slot 31 (Level/_Definitions.inc:131-165). */
    public static final int SLOT = 31;
    /** {@code move.w #68+128,oX(a0); move.w #210+128,oYScr(a0)} (ObjTimeIcon_Init, Monitor and Time Post/Main.asm:40-41). */
    static final int SCREEN_X = 68;
    static final int SCREEN_Y = 210;
    /** {@code move.w #$85A8,oTile(a0)} (Monitor and Time Post/Main.asm:39). */
    static final int TILE = 0x85A8;
    /** {@code move.b #$12,oMapFrame(a0)}; $13 for the future (ObjTimeIcon_Main, Monitor and Time Post/Main.asm:48-51). */
    static final int PAST_FRAME = 0x12;
    /** {@code cmpi.w #90,timeWarpTimer.w} (ObjTimeIcon, Monitor and Time Post/Main.asm:15). */
    static final int FLASH_FROM = 90;

    private int mapFrame = PAST_FRAME;
    private boolean visible;

    public CdTimeIcon() {
        this(new ObjectSpawn(0, 0, 0x19, CdTimePost.TIME_ICON_SUBTYPE, 0, false, 0));
    }

    CdTimeIcon(ObjectSpawn spawn) {
        super(spawn, "Time icon");
        this.tileWord = TILE;
        this.priority = 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        int direction = tt == null ? 0 : tt.warpDirection();
        // ObjTimeIcon_Main: $12, then $13 unless timeWarpDir is negative.
        mapFrame = direction < 0 ? PAST_FRAME : PAST_FRAME + 1;
        visible = false;
        if (direction == 0) return;                                        // tst.b timeWarpDir.w; beq .End
        if (tt.warpTimer() >= FLASH_FROM && (levelFrames() & 1) != 0) return;   // btst #0,levelFrames+1
        visible = true;
    }

    private int levelFrames() {
        var levels = services().levelManager();
        return levels == null ? 0 : levels.getFrameCounter();
    }

    public int mapFrame() { return mapFrame; }
    public boolean visible() { return visible; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || !CdHud.shown(services())) return;
        PatternSpriteRenderer renderer = art(CdObjectArtProvider.MONITOR);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        renderer.drawFrameIndex(mapFrame, camera.getX() + SCREEN_X, camera.getY() + SCREEN_Y, false, false,
                CdObjectArtProvider.tilePalette(tileWord));
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(priority); }
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
