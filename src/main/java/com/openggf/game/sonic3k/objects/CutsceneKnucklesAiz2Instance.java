package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Rival Knuckles cutscene object for the Sonic/Tails AIZ2 post-boss transition.
 *
 * <p>ROM reference: CutsceneKnux_AIZ2.
 */
public class CutsceneKnucklesAiz2Instance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, ObjectVisualFacing.Source {
    @Override public ObjectVisualFacing.Basis nativeFacing() {
        // This legacy-named field is the native cutscene X-flip bit. Set mirrors the right-facing Knuckles basis to left.
        return facingRight ? ObjectVisualFacing.Basis.LEFT : ObjectVisualFacing.Basis.RIGHT;
    }


    private static final int OBJECT_ID = 0x82;
    private static final int INIT_X = 0x4B8E;
    private static final int INIT_Y = 0x017D;
    private static final int RUN_END_X = 0x4B3C;
    private static final int WAIT_TIMER = (2 * 60) - 1;
    private static final int LAUGH_TIMER = 0x5F;
    private static final int RUN_SPEED = 2;
    private static final int FLOOR_Y = 0x017D;

    // byte_6669F / byte_666AF / byte_666B9, locked-on ROM raw scripts.
    private static final int RUN_SCRIPT = 0x6669F;
    private static final int JUMP_SCRIPT = 0x666AF;
    private static final int LAUGH_SCRIPT = 0x666B9;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation scripts;

    private enum Phase { INIT_WAIT, RUN_IN, LAUGH_1, JUMP, LAUGH_2 }

    private Phase phase = Phase.INIT_WAIT;
    private int timer = WAIT_TIMER;
    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private boolean initialized;
    private boolean bounced;

    /** ROM render_flags bit 0: true = facing right (hFlip in draw call). */
    private boolean facingRight = true;  // Starts facing right (running in from right side)

    public CutsceneKnucklesAiz2Instance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnucklesAIZ2");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
    }

    public static CutsceneKnucklesAiz2Instance createDefault() {
        return new CutsceneKnucklesAiz2Instance(
                new ObjectSpawn(INIT_X, INIT_Y, OBJECT_ID, 4, 1, false, 0));
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialized) {
            initialized = true;
            // ObjSlot_CutsceneKnux installs standing mapping $16, not frame zero.
            animation.mappingFrame = 0x16;
            AizIntroArtLoader.loadAllIntroArt(services());
            AizIntroArtLoader.applyKnucklesPalette(services());
            services().playMusic(Sonic3kMusic.KNUCKLES.id);
            Aiz2BossEndSequenceState.armCutsceneKnucklesSlot(getSlotIndex());
            Aiz2BossEndSequenceState.setActiveKnuckles(this);
            // Obj_CutsceneKnuckles installs the AIZ2 routine, then loc_61FC2
            // initializes the wait/children and returns. Routine $02 first
            // decrements Obj_Wait on the following object entry.
            return;
        }
        Aiz2BossEndSequenceState.setActiveKnuckles(this);

        switch (phase) {
            case INIT_WAIT -> updateInitWait();
            case RUN_IN -> updateRunIn();
            case LAUGH_1 -> updateLaugh(true);
            case JUMP -> updateJump();
            case LAUGH_2 -> updateLaugh(false);
        }
    }

    private void updateInitWait() {
        if (timer > 0) {
            timer--;
            return;
        }
        phase = Phase.RUN_IN;
    }

    private void updateRunIn() {
        if (currentX - RUN_SPEED >= RUN_END_X) {
            currentX -= RUN_SPEED;
            animate(RUN_SCRIPT);
            return;
        }
        currentX = RUN_END_X;
        // ROM: loc_6203C — bclr #0,render_flags → face LEFT (toward Sonic/button)
        facingRight = false;
        phase = Phase.LAUGH_1;
        timer = LAUGH_TIMER;
        setAnimation(LAUGH_SCRIPT, 0x1C);
    }

    private void updateLaugh(boolean countdown) {
        animate(animation.script);
        if (!countdown) {
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        phase = Phase.JUMP;
        xVel = -0x100;
        yVel = -0x400;
        setAnimation(JUMP_SCRIPT, 8);
    }

    private void updateJump() {
        animate(animation.script);
        // ROM: loc_620AA uses MoveSprite (moves X and Y) + gravity
        SubpixelMotion.State motion = new SubpixelMotion.State(
                currentX, currentY, xSub, ySub, xVel, yVel);
        SubpixelMotion.objectFallXY(motion, SubpixelMotion.S3K_GRAVITY);
        currentX = motion.x;
        currentY = motion.y;
        xSub = motion.xSub;
        ySub = motion.ySub;
        xVel = motion.xVel;
        yVel = motion.yVel;

        // Still going up or above floor — keep falling
        if (yVel < 0 || currentY < FLOOR_Y) {
            return;
        }

        if (!bounced) {
            // First landing — bounce back the other way
            bounced = true;
            // loc_620AA reverses velocity without correcting first-contact penetration.
            // Only loc_620D8 corrects Y on the second landing.
            xVel = -xVel;
            yVel = -yVel;
            return;
        }

        // Second landing — done jumping, start laughing again
        currentY = FLOOR_Y;
        xVel = 0;
        yVel = 0;
        phase = Phase.LAUGH_2;
        setAnimation(LAUGH_SCRIPT, 0x1C);
    }

    private void setAnimation(int script, int mappingFrame) {
        S3kRawAnimation.set(animation, script);
        animation.mappingFrame = mappingFrame;
    }

    private void animate(int script) {
        if (scripts == null) {
            try {
                scripts = S3kRawAnimation.load(services().romReader(), RUN_SCRIPT, 0x666C3 - RUN_SCRIPT);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("AIZ2 Knuckles raw animation", e);
            }
        }
        scripts.animateNoSst(animation, script, () -> {});
    }

    /**
     * Returns true once Knuckles has completed his jump/bounce sequence
     * and entered the final laugh phase.
     */
    public boolean isJumpFinished() {
        return phase == Phase.LAUGH_2;
    }

    /**
     * Returns true once Knuckles has landed at least once during the jump
     * phase (i.e. the first bounce). The ROM button is triggered by
     * SolidObjectFull2 when Knuckles physically lands on it during the
     * first arc of the jump, before he bounces back.
     */
    public boolean hasLandedOnButton() {
        return bounced || phase == Phase.LAUGH_2;
    }

    @Override
    public boolean isHighPriority() {
        // ObjSlot_CutsceneKnux art make_art_tile(ArtTile_CutsceneKnux,1,1) sets bit 15
        // (sonic3k.asm:134854).
        return true;
    }

    // ObjSlot_CutsceneKnux priority $180, written by SetUp_ObjAttributesSlotted
    // (sonic3k.asm:134857, 178977).
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getKnucklesRenderer(services());
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animation.mappingFrame, currentX, currentY, facingRight, false);
    }
}
