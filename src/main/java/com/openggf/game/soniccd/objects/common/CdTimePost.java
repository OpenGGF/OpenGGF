package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Time post, object $19 with subtype 8 (past) or 9 (future) ({@code ObjTimePost},
 * Level/_Objects/Monitor and Time Post/Main.asm:64-197). Touching it ({@code $C0|$1F}: the
 * player's special-touch {@code .FlagCollision}, Palmtree Panic/Player Object
 * Collision.asm:302-306, 383-385) sets the campaign's warp direction and plays the voice
 * clip, spins for 60 frames on {@code Ani_Monitor} and then shows the activated frame.
 * Subtype $A is the HUD time icon ({@link CdTimeIcon}); in Time Attack both delete
 * themselves ({@code ObjTimePostTimeIcon}, Monitor and Time Post/Main.asm:64-71), which
 * is not hosted (Time Attack mode reads clear).
 *
 * <p>On a stage whose objects test layers (Metallic Madness) a layer-1 post draws in front
 * (Monitor and Time Post/Main.asm:105-110) and a touch counts only for a player on the post's
 * layer (:143-153; the flag is cleared either way). The activated bit is kept per stage through the shared respawn record; native keeps it per
 * time zone across warps ({@code ObjMonitor_GetSavedFlags}, Monitor and Time
 * Post/Main.asm:207-236), which needs a cross-stage saved-flags owner.
 */
public final class CdTimePost extends CdObject
        implements TouchResponseProvider, TouchResponseListener, ObjectVisualFacing.NonDirectional {
    /** {@code cmpi.b #8,oSubtype(a0)}: subtype 8 is the past post (Monitor and Time Post/Main.asm:120,164). */
    public static final int PAST_SUBTYPE = 8;
    /** {@code cmpi.b #$A,oSubtype(a0)}: the HUD time icon (Monitor and Time Post/Main.asm:70). */
    public static final int TIME_ICON_SUBTYPE = 0xA;
    /**
     * {@code move.b #$C0|$1F,oColType(a0)} (.StillActive, Monitor and Time Post/Main.asm:132). CD's
     * player sends every $C0 flag to {@code Player_TouchSpecial} ({@code cmpi.b #$C0,d1; beq},
     * Palmtree Panic/Player Object Collision.asm:85-86), whose size $1F only counts the touch
     * ({@code .FlagCollision}, Player Object Collision.asm:305-306, 383-385). The shared touch
     * owner's listener-only category is the $40 class, so the post reports size $1F in that class;
     * the overlap test uses the same size entry.
     */
    static final int NATIVE_COLLISION_TYPE = 0xC0 | 0x1F;
    static final int COLLISION_FLAGS = 0x40 | (NATIVE_COLLISION_TYPE & 0x3F);
    /** {@code move.b #60,oTimePostSpin(a0)} (.Touched, Monitor and Time Post/Main.asm:156). */
    static final int SPIN_FRAMES = 60;
    /** {@code move.b #$A,oMapFrame(a0)}; +2 for the future post (Monitor and Time Post/Main.asm:119-122). */
    static final int PAST_FRAME = 0xA;

    private enum Routine { INIT, MAIN, SPIN, DONE }

    private Routine routine = Routine.INIT;
    private int spin;
    private int mapFrame;
    private boolean touched;
    private CdAnimator animator;

    public CdTimePost(ObjectSpawn spawn) {
        super(spawn, "Time post");
        // ObjTimePost_Init: oYRadius $20, oXRadius $E, tile $5A8, priority 3, oWidth $F
        // (Monitor and Time Post/Main.asm:96-113).
        this.yRadius = 0x20;
        this.width = 0xF;
        this.priority = 3;
        this.tileWord = CdObjectArtProvider.MONITOR_TILE;
    }

    /** {@code ObjTimePostTimeIcon}: the time icon for subtype $A, a time post otherwise. */
    public static CdObject create(ObjectSpawn spawn) {
        return spawn.subtype() == TIME_ICON_SUBTYPE ? new CdTimeIcon(spawn) : new CdTimePost(spawn);
    }

    boolean past() {
        return spawn.subtype() == PAST_SUBTYPE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> init();
            case MAIN -> main();
            case SPIN -> spin();
            case DONE -> { }
        }
    }

    /** ObjTimePost_Init (Monitor and Time Post/Main.asm:96-132), falling into the main routine. */
    private void init() {
        routine = Routine.MAIN;
        if (frontLayerOfLayeredStage()) {                                  // Monitor and Time Post/Main.asm:105-110
            priority = 0;
            tileWord |= 0x8000;
        }
        animator = animator(e -> e.monitorAnimations(), spawn.subtype());   // move.b oSubtype(a0),oAnim(a0)
        mapFrame = past() ? PAST_FRAME : PAST_FRAME + 2;
        // btst #0,2(a2,d0.w): an activated post shows its done frame (Monitor and Time Post/Main.asm:124-129).
        if (isRemembered()) {
            mapFrame++;
            routine = Routine.DONE;
            return;
        }
        main();
    }

    /** ObjTimePost_Main (Monitor and Time Post/Main.asm:138-173). */
    private void main() {
        if (!touched) return;                                              // tst.b oColStatus(a0)
        touched = false;
        if (!onPlayerLayer()) return;                                      // Metallic Madness layers (:143-153)
        spin = SPIN_FRAMES;
        routine = Routine.SPIN;
        ObjectLifetimeOps.markSpawnRemembered(services().objectManager(), spawn);   // bset #0,2(a2,d0.w)
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        if (tt != null) tt.touchTimePost(past());
    }

    /** ObjTimePost_Spin (Monitor and Time Post/Main.asm:179-190). */
    private void spin() {
        if (--spin == 0) {
            routine = Routine.DONE;
            mapFrame = past() ? PAST_FRAME + 1 : PAST_FRAME + 3;           // move.b #$B,oMapFrame; addq.b #2
            return;
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    private boolean isRemembered() {
        var objects = services().objectManager();
        return objects != null && objects.isRemembered(spawn);
    }

    @Override public boolean shouldStayActiveWhenRemembered() { return true; }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (routine != Routine.MAIN || result.category() != TouchCategory.SPECIAL
                || !(playerEntity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) {
            return;
        }
        touched = true;                                                    // addq.b #1,oColStatus(a1)
    }

    @Override public int getCollisionFlags() { return routine == Routine.MAIN ? COLLISION_FLAGS : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    public int mapFrame() { return mapFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == Routine.INIT) return;
        drawFrame(CdObjectArtProvider.MONITOR, mapFrame, false, false);
    }
}
