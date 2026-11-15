package com.openggf.game.soniccd.objects.common;

import com.openggf.game.CheckpointState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Checkpoint, object $13 ({@code ObjCheckpoint}, Level/_Objects/Checkpoint/Main.asm:50-189): a
 * post (oWidth 8, oYRadius $18, priority 4, tile $6CB) and its ball, a second $13 made with
 * FindObjSlot 32 pixels above. A post whose subtype is above {@code checkpoint} takes special
 * collision $C0|$23; once touched it records its subtype in {@code checkpoint}, sets
 * {@code spawnMode} and runs ObjCheckpoint_SaveData. The ball then spins once around a point 24
 * pixels above the post (angle +8 a frame, radius 8) and animates.
 *
 * <p>ObjCheckpoint_SaveData (Checkpoint/Main.asm:17-44) saves the player's position (not the
 * post's), the camera and background positions, the bottom bound, the water state and the
 * time, capped at 5:00. Through the shared checkpoint state: position, cameras, background
 * scroll, water, and the capped time; the shared state also banks rings, which CD's routine does
 * not save (the restart flow that reads it is not hosted here). {@code miniSonic} is not hosted.
 */
public final class CdCheckpoint extends CdObject implements TouchResponseProvider, TouchResponseListener,
        ObjectVisualFacing.NonDirectional {
    /** .Unactivated: move.b #$C0|$23,oColType(a0) (Checkpoint/Main.asm:89). */
    private static final int COLLISION_FLAGS = 0xC0 | 0x23;
    /** ObjCheckpoint_SaveData: cmpi.l #(5<<16)|(0<<8)|0,d0 (Checkpoint/Main.asm:35-38). */
    private static final long TIME_CAP_FRAMES = 5 * 3600L;

    private int routine;
    private boolean active;
    private boolean touched;
    private Ball ball;

    public CdCheckpoint(ObjectSpawn spawn) {
        super(spawn, "Checkpoint");
        // ObjCheckpoint_Init: tile $6CB, oWidth 8, oYRadius $18, priority 4 (Checkpoint/Main.asm:74-79).
        this.tileWord = CommonObjectArt.CHECKPOINT_TILE;
        this.width = 8;
        this.yRadius = 0x18;
        this.priority = 4;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            init();
            return;
        }
        // ObjCheckpoint_Main (Checkpoint/Main.asm:126-143).
        if (active || !touched) return;
        active = true;
        if (player instanceof AbstractPlayableSprite sprite) saveData(sprite);
    }

    /** ObjCheckpoint_Init (Checkpoint/Main.asm:71-117). */
    private void init() {
        routine = 2;
        // move.b checkpoint,d0; cmp.b oSubtype(a0),d0; bcs .Unactivated (:81-86).
        // checkpoint is 0 until a post is touched; the shared state reports none as negative.
        int checkpoint = checkpointState() == null ? 0 : Math.max(0, checkpointState().getLastCheckpointIndex());
        active = (checkpoint & 0xFF) >= (spawn.subtype() & 0xFF);
        var objects = services().objectManager();
        if (objects == null) return;
        ball = new Ball(this, active);
        objects.addDynamicObject(ball);                       // jsr FindObjSlot (:92)
    }

    /** ObjCheckpoint_Main's activation and ObjCheckpoint_SaveData (Checkpoint/Main.asm:17-44, 132-137). */
    private void saveData(AbstractPlayableSprite player) {
        CheckpointState state = checkpointState();
        if (state == null) return;
        // move.b oSubtype(a0),checkpoint; move.b #1,spawnMode; savedX/savedY from the player.
        state.saveCheckpoint(spawn.subtype() & 0xFF, player.getCentreX(), player.getCentreY(), false);
        if (services().parallaxManager() != null && services().levelManager() != null) {
            com.openggf.game.CheckpointBackgroundScrollAccess.save(state,
                    com.openggf.level.ParallaxCheckpointAccess.capture(services().parallaxManager(),
                            services().levelManager().getCurrentZone()));
        }
        var water = services().waterSystem();
        int zone = services().featureZoneId();
        int act = services().featureActId();
        if (water != null && water.hasWater(zone, act)) {
            var features = services().zoneFeatureProvider();
            state.saveWaterState(water.getWaterLevelY(zone, act), features != null ? features.getWaterRoutine() : 0);
        }
        var level = services().levelGamestate();
        if (level != null) state.saveActTimer(Math.min(level.getTimerFrames(), TIME_CAP_FRAMES));
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        if (stage != null) stage.saveCheckpointMiniPlayer(player);            // move.b shrunk_player,respawn_shrunk
    }

    private CheckpointState checkpointState() {
        return services().checkpointState() instanceof CheckpointState state ? state : null;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (!active) touched = true;                         // oColStatus set by the touch pass
    }

    @Override public int getCollisionFlags() { return routine == 2 && !active ? COLLISION_FLAGS : 0; }
    /**
     * A {@code $C0|size} type branches to {@code Player_TouchSpecial} (Palmtree Panic/Player Object
     * Collision.asm:83-86, 302-318), where type $23 goes to {@code .FlagCollision}: it only sets {@code oColStatus},
     * no hurt and no bounce, so the touch is
     * listener-only.
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    boolean activated() { return active; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(CommonObjectArt.CHECKPOINT, 0, false, false);
    }

    /**
     * The ball: routine 4 waits for the post, then spins; routine 6 animates (Checkpoint/Main.asm:92-117,
     * 149-189). It runs ObjCheckpoint, so it draws and despawns by CheckObjDespawn too.
     */
    public static final class Ball extends CdObject implements ObjectVisualFacing.NonDirectional {
        private final CdCheckpoint parent;
        private final int centreX;
        private final int centreY;
        private boolean active;
        private boolean spun;
        private int angle;
        private CdAnimator animator;
        private int mapFrame = 1;

        Ball(CdCheckpoint parent, boolean parentActive) {
            super(new ObjectSpawn(parent.x, parent.y - 32, 0x13, parent.spawn.subtype(), 0, false, 0), "CheckpointBall");
            this.parent = parent;
            // .Unactivated2: tile $6CB, oWidth 8, oYRadius 8, priority 3, frame 1; Y - 32;
            // centre Y - (32 - 8) (Checkpoint/Main.asm:100-116).
            this.tileWord = CommonObjectArt.CHECKPOINT_TILE;
            this.width = 8;
            this.yRadius = 8;
            this.priority = 3;
            this.centreX = parent.x;
            this.centreY = parent.y - (32 - 8);
            this.spun = parentActive;                        // addq.b #2,oRoutine(a1): animate only (:96-98)
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (spun) {
                // ObjCheckpoint_Animate: lea Ani_Checkpoint,a1; AnimateObject (Checkpoint/Main.asm:187-189).
                if (animator == null) animator = animator(e -> e.offset("checkpointAnimations"), 0);
                if (animator != null) {
                    animator.step();
                    mapFrame = animator.mapFrame();
                }
                return;
            }
            // ObjCheckpoint_Ball (Checkpoint/Main.asm:149-181).
            if (!active) {
                if (!parent.activated()) return;
                active = true;
            }
            angle = (angle + 8) & 0xFF;
            // muls.w #8 / #-8 then lsr.l #8: the low word is the arithmetic shift's.
            x = centreX + (short) ((TrigLookupTable.sinHex(angle) * 8) >> 8);
            y = centreY + (short) ((TrigLookupTable.cosHex(angle) * -8) >> 8);
            if (angle == 0) spun = true;
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawFrame(CommonObjectArt.CHECKPOINT, mapFrame, false, false);
        }
    }
}
