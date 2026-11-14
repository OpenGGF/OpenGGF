package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * Robot generator, object $2D ({@code ObjRobotGenerator}, Level/_Objects/Robot Generator/Main.asm:25-197).
 * Width $22, Y radius $20, priority 4; the base tile is the stage's {@code act*4 + timeZone} entry
 * and the frame {@code (goodFuture ? 0 : 2) + (past ? 1 : 0)}. Only a past generator without the
 * good future is active: it rises $10, floats (1 pixel every 8 frames, direction reversing every
 * $20), is solid and animates, with special collision $FA, which a rolling player sets
 * (Player_TouchSpecial .CheckIfRoll, Palmtree Panic/Player Object Collision.asm:319-320, 389-394). A hit
 * clears the collision, shows frame 7, sets {@code goodFuture}, adds 1500 points, runs SolidObject and,
 * on a top or bottom contact, GetOffObject; then 21 explosions follow the schedule (FindObjSlot, $18
 * with routine2 1) and, 8 frames after the last, the generator returns to its base Y in routine 2.
 *
 * <p>The draw is unconditional; CheckObjDespawn runs only in routines 0 and 2 (Main.asm:30-33).
 * The schedule ObjRobotGenerator_ExplosionLocs (scddisasm/src/Level/_Objects/Robot Generator/Main.asm:206-229) is read from the stage file.
 */
public final class CdRobotGenerator extends CdObject implements ReevaluatingSolidObject, TouchResponseProvider,
        TouchResponseListener, ObjectVisualFacing.NonDirectional {
    /** move.b #$FA,oColType(a0) (Robot Generator/Main.asm:84). */
    private static final int COLLISION_FLAGS = 0xFA;
    /** move.l #$96,d0; jsr AddPoints: 150 in units of ten (Robot Generator/Main.asm:103-104). */
    private static final int HIT_SCORE = 0x96 * 10;

    private int routine;
    private int timer;
    private int floatStep;
    private int baseY;
    private int mapFrame;
    private int collisionFlags;
    private boolean touched;
    private int scheduleIndex;
    private int[] schedule;
    private CdAnimator animator;

    public CdRobotGenerator(ObjectSpawn spawn) {
        super(spawn, "RobotGenerator");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                if (routine == 2) activePast(player);
            }
            case 2 -> activePast(player);
            case 4 -> exploding();
            default -> {
                // ObjRobotGenerator_RebuildDelay (scddisasm/src/Level/_Objects/Robot Generator/Main.asm:165-177).
                if (--timer != 0) return;
                routine = 2;
                y = baseY;
            }
        }
    }

    /** ObjRobotGenerator_Init (Robot Generator/Main.asm:49-85). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x22;
        yRadius = 0x20;
        CdObjectArtProvider provider = artProvider();
        int tiles = provider == null ? -1 : provider.entry().offset("robotGeneratorBaseTiles");
        if (tiles >= 0) tileWord = CommonObjectArt.robotGeneratorTile(provider.image(), cdLevel().payload(), tiles);
        baseY = y;
        timer = 4;
        floatStep = 1;
        boolean goodFuture = flags().goodFuture;
        mapFrame = (goodFuture ? 0 : 2) + (timeZone() == 0 ? 1 : 0);
        if (goodFuture || timeZone() != 0) return;
        collisionFlags = COLLISION_FLAGS;
        y -= 0x10;
    }

    /** ObjRobotGenerator_ActivePast (Robot Generator/Main.asm:90-125). */
    private void activePast(PlayableEntity player) {
        if (flags().goodFuture || timeZone() != 0) return;
        floatStep();
        if (touched) {
            touched = false;
            collisionFlags = 0;                              // clr.w oColType(a0): type and status
            timer = 0;
            mapFrame = 7;
            routine = 4;
            flags().goodFuture = true;
            var game = services().gameState();
            if (game != null) game.addScore(HIT_SCORE);
            if (solidObject(Entry.SOLID) && player != null) {
                ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
            }
            return;
        }
        solidObject(Entry.SOLID);
        if (animator == null) animator = animator(e -> e.offset("robotGeneratorAnimations"), 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** ObjRobotGenerator_Float (Robot Generator/Main.asm:181-197). */
    private void floatStep() {
        timer = (timer + 1) & 0xFFFF;
        if ((timer & 7) == 0) y += (short) floatStep;
        if ((timer & 0x1F) == 0) floatStep = -floatStep;
    }

    /** ObjRobotGenerator_Exploding (Robot Generator/Main.asm:129-161). */
    private void exploding() {
        if (schedule == null) schedule = readSchedule();
        if (scheduleIndex >= schedule.length || schedule[scheduleIndex] < 0) {
            routine = 6;                                     // .Finished: timer 8
            timer = 8;
            return;
        }
        timer = (timer & 0xFF00) | ((timer + 1) & 0xFF);     // addq.b #1,oRobotGeneratorTimer
        if ((schedule[scheduleIndex] & 0xFF) != (timer & 0xFF)) return;
        int dx = (byte) schedule[scheduleIndex + 1];
        int dy = (byte) schedule[scheduleIndex + 2];
        scheduleIndex += 3;
        var objects = services().objectManager();
        if (objects != null) objects.addDynamicObject(new CdExplosion(x + dx, y + dy, 0, false, 0, 0));
    }

    /**
     * ObjRobotGenerator_ExplosionLocs: three-byte records, trigger tick and signed offsets, ended by
     * $FF (Robot Generator/Main.asm:206-229). The table follows the mappings in the stage file.
     */
    private int[] readSchedule() {
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return new int[] {-1};
        int at = provider.entry().offset("robotGeneratorExplosions");
        java.util.List<Integer> out = new java.util.ArrayList<>();
        while (true) {
            int tick = provider.image().u8(at);
            if (tick == 0xFF) { out.add(-1); break; }
            out.add(tick);
            out.add(provider.image().u8(at + 1));
            out.add(provider.image().u8(at + 2));
            at += 3;
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        // .CheckIfRoll: cmpi.b #2,oAnim(a0); addq.b #1,oColStatus(a1).
        if (collisionFlags != 0 && player.getAnimationId() == com.openggf.game.soniccd.player.CdSonicAnimation.ROLL.id()) {
            touched = true;
        }
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    /**
     * A $C0-class type is the native special touch, never a boss bounce or hurt in the shared owner: $3A adds to the collision status only while rolling: Player_TouchSpecial .CheckIfRoll (Level/Palmtree Panic/Player Object Collision.asm:302, 319-320, 389-392).
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= 2; }

    int mapFrame() { return mapFrame; }
    int routine() { return routine; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(CommonObjectArt.ROBOT_GENERATOR, mapFrame, hFlip(), vFlip());
    }
}
