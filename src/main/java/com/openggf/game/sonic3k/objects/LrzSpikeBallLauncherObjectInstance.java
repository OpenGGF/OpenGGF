package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code Obj_LRZSpikeBallLauncher} (sonic3k.asm:89848-89933), id {@code $37} in the locked-on set
 * and nine of Lava Reef act 2's placements ({@code $50} ×2, {@code $60} ×5, {@code $70} ×2).
 * The {@code S3KL} set spends the id on {@code Obj_HCZWaterRush}, which is the name the id
 * constant carries.
 *
 * <p><b>The launcher is a solid platform that never moves.</b> Its whole behaviour is an
 * animation script read through {@code Animate_SpriteIrregularDelay}, and the script's
 * {@code $FC} command -- {@code addq.b #2,routine(a0)} at {@code loc_1AD0C}
 * (sonic3k.asm:36302-36306) -- is what fires it. {@code routine(a0)} is used here purely as a
 * one-frame flag: the object's own code pointer is {@code loc_448A8} for its whole life, and
 * {@code loc_448B6} clears the byte the frame it reads it.
 *
 * <p><b>The two {@code $FC}s do different things, and {@code anim} is what separates them.</b>
 * Script {@code 0} ({@code byte_4495E}) holds {@code mapping_frame} 3 for {@code $7F + 1} frames
 * and then runs {@code $FC}; at that instant {@code anim(a0)} is still {@code 0}, because the
 * {@code $FD} that switches to script {@code 1} does not run until the following frame. So
 * {@code tst.b anim(a0) / beq} (:89879) takes the charging branch: {@code sfx_Charging}, nothing
 * else. Script {@code 1} ({@code byte_44964}) is a forty-two entry flicker between frames 4 and 3
 * whose delays fall {@code $D, $B, 9, 7, 5, 3, 1} and then sit at {@code 0} -- every frame, for
 * the rest of the ramp -- and its {@code $FC} runs with {@code anim(a0) == 1}, which is the
 * branch at {@code loc_448D4} that plays {@code sfx_BossHit} and launches the ball.
 *
 * <p><b>Both sounds are gated on the render flag, not on the cycle</b> ({@code tst.b
 * render_flags(a0) / bpl}, :89881 and :89889): an off-screen launcher still charges and still
 * fires, silently.
 *
 * <p><b>The subtype is the launch speed and nothing else.</b> {@code lsl.w #4,d0 / neg.w d0}
 * (:89897-89900) makes {@code y_vel} of the ball {@code -(subtype << 4)}: {@code -$500},
 * {@code -$600} and {@code -$700} for Lava Reef's three placed subtypes. Gravity is
 * {@code MoveSprite}'s own {@code $38} (sonic3k.asm:36037), so the flight is a plain parabola and
 * the subtype sets its height.
 */
public final class LrzSpikeBallLauncherObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:89853). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:89851-89852). */
    private static final int HALF_EXTENT = 0x10;
    /** {@code move.w #$1B,d1 / #4,d2 / #5,d3} before {@code SolidObjectFull} (:89904-89906). */
    private static final int SOLID_HALF_WIDTH = 0x1B;
    private static final int SOLID_HEIGHT_AIR = 4;
    private static final int SOLID_HEIGHT_GROUND = 5;
    /** {@code subi.w #8,y_pos(a1)} (:89860): the ball rests eight pixels above the launcher. */
    static final int BALL_REST_OFFSET = 8;

    /**
     * {@code byte_4495E} and {@code byte_44964}, transcribed as the ROM bytes they are
     * (docs/skdisasm/Levels/LRZ/Misc Object Data/Anim - Spike Ball Launcher.asm). The engine has
     * no shared irregular-delay animator, so the four commands are modelled here, exactly as
     * {@code AizDisappearingFloorObjectInstance} models its own script.
     */
    private static final int[] ANIM_WIND_UP = {
        3, 0x7F, 0xFC, 0xFF, 0xFD, 1
    };

    private static final int[] ANIM_RAMP = {
        4, 0x0D, 3, 0x0D, 4, 0x0B, 3, 0x0B, 4, 0x09, 3, 0x09,
        4, 0x07, 3, 0x07, 4, 0x05, 3, 0x05, 4, 0x03, 3, 0x03,
        4, 0x01, 3, 0x01, 4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00,
        4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00,
        4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00,
        4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00,
        4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00, 4, 0x00, 3, 0x00,
        0xFC, 0xFF, 0xFD, 0x00
    };

    /** ROM {@code anim(a0)} and {@code prev_anim(a0)}. */
    private int anim;
    private int prevAnim;
    /** ROM {@code anim_frame(a0)} and {@code anim_frame_timer(a0)}. */
    private int animFrame;
    private int animFrameTimer;
    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /**
     * ROM {@code routine(a0)}, which this object uses only as the {@code $FC} flag: the code
     * pointer is {@code loc_448A8} for the object's whole life.
     */
    private int routineFlag;
    /** {@code move.b subtype(a0),d0 / lsl.w #4,d0 / neg.w d0} (:89894-89900). */
    private int launchVelocity;
    /** {@code jsr AllocateObjectAfterCurrent} at :89855 runs once, on the init pass. */
    private boolean initialised;

    private LrzSpikeBallLauncherBallInstance ball;

    public LrzSpikeBallLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZSpikeBallLauncher");
        int subtype = spawn == null ? 0 : spawn.subtype() & 0xFF;
        this.launchVelocity = (short) -(subtype << 4);
        this.anim = 0;
        // A cleared object slot: Animate_SpriteIrregularDelay's first pass sees anim == prev_anim
        // and falls straight into the subq, so the first entry lands on the very first frame.
        this.prevAnim = 0;
        this.animFrame = 0;
        this.animFrameTimer = 0;
        this.mappingFrame = 0;
        this.routineFlag = 0;
        this.initialised = false;
    }

    /**
     * {@code Obj_LRZSpikeBallLauncher} sits at ROM {@code $00044826} (sonic3k.lst); its whole
     * code block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzSpikeBallLauncherObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzSpikeBallLauncherObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialised) {
            initialised = true;
            allocateBall();
        }
        // lea (Ani_LRZSpikeBallLauncher).l,a1 / jsr Animate_SpriteIrregularDelay (:89876-89877).
        animate();
        // tst.b routine(a0) / beq.s loc_448FA / clr.b routine(a0) (:89878-89880).
        if (routineFlag != 0) {
            routineFlag = 0;
            if (anim == 0) {
                // loc_448B6: the charging half of the cycle only makes a sound.
                if (isWithinSolidContactBounds()) {
                    playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.CHARGING.id);
                }
            } else {
                // loc_448D4: the launch.
                if (isWithinSolidContactBounds()) {
                    playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.BOSS_HIT.id);
                }
                fireBall();
            }
        }
    }

    /** The {@code AllocateObjectAfterCurrent} block at :89855-89874. */
    private void allocateBall() {
        int x = getCentreX();
        int y = (getCentreY() - BALL_REST_OFFSET) & 0xFFFF;
        ball = spawnAfterCurrentSibling(() -> new LrzSpikeBallLauncherBallInstance(x, y));
    }

    /**
     * {@code loc_448E2} (:89892-89901): the launcher writes the ball's code pointer and its
     * {@code y_vel}, and nothing else. A ball that has been deleted -- the ROM's own
     * {@code Sprite_CheckDeleteTouch3} can remove it -- leaves the write with nowhere to go.
     */
    private void fireBall() {
        if (ball == null || ball.isDestroyed()) {
            return;
        }
        ball.launch(launchVelocity);
    }

    /**
     * {@code Animate_SpriteIrregularDelay} (sonic3k.asm:36238-36308). The delay byte is read as
     * the <em>second</em> byte of each pair and the frame as the first, and {@code subq.b #1} on
     * a timer that is already zero is the only case that advances the script -- which is why a
     * delay of {@code 0} means "every frame", not "skip".
     */
    private void animate() {
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animFrameTimer = 0;
        }
        if (animFrameTimer != 0) {
            animFrameTimer = (animFrameTimer - 1) & 0xFF;
            return;
        }
        int[] script = anim == 0 ? ANIM_WIND_UP : ANIM_RAMP;
        int index = (animFrame & 0xFF) * 2;
        int command = script[index] & 0xFF;
        if (command < 0x80) {
            commit(script, index, command);
            return;
        }
        switch (command) {
            case 0xFF -> {
                // loc_1ACDC: restart, with d1 cleared, so the delay comes from entry 0.
                animFrame = 0;
                commit(script, 0, script[0] & 0xFF);
            }
            case 0xFE -> {
                // loc_1ACEA: step back by the following byte's worth of entries.
                int back = script[index + 1] & 0xFF;
                animFrame = (animFrame - back) & 0xFF;
                int target = index - (back * 2);
                commit(script, target, script[target] & 0xFF);
            }
            case 0xFD ->
                // loc_1AD00: switch scripts. anim_frame_timer is left where the subq put it; the
                // anim change clears it on the next pass.
                anim = script[index + 1] & 0xFF;
            case 0xFC -> {
                // loc_1AD0C: addq.b #2,routine / clr.b anim_frame_timer / addq.b #1,anim_frame.
                routineFlag = (routineFlag + 2) & 0xFF;
                animFrameTimer = 0;
                animFrame = (animFrame + 1) & 0xFF;
            }
            default -> {
                // locret_1AD1E: every other negative byte is a no-op.
            }
        }
    }

    /** {@code loc_1ACBA}: take the frame, take the delay, step the script. */
    private void commit(int[] script, int index, int frame) {
        animFrameTimer = script[index + 1] & 0xFF;
        mappingFrame = frame;
        animFrame = (animFrame + 1) & 0xFF;
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception ignored) {
            // A headless fixture without an audio service still runs the cycle.
        }
    }

    /** ROM {@code anim(a0)}: 0 winding up, 1 on the flicker ramp. */
    public int anim() {
        return anim;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code anim_frame_timer(a0)}. */
    public int animFrameTimer() {
        return animFrameTimer;
    }

    /** {@code -(subtype << 4)}, the {@code y_vel} handed to the ball. */
    public int launchVelocity() {
        return launchVelocity;
    }

    /** ROM {@code $3C(a0)}. */
    public LrzSpikeBallLauncherBallInstance ball() {
        return ball;
    }

    /** ROM {@code x_pos}/{@code y_pos}: the launcher never moves. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZ2Misc,1,0) (sonic3k.asm:89850): the priority bit is clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_EXTENT;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_EXTENT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_SPIKE_BALL_LAUNCHER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }

}
