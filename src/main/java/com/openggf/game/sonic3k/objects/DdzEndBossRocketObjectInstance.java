package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_8249A} (sonic3k.asm:174520-174586): a phase-2 rocket ({@code ObjDat3_83232}: priority
 * {@code $300}, frames {@code $31/$32} by {@code byte_832BF}). It is invisible for
 * {@code subtype * 8} frames, then appears at the ship {@code + ($20, $20)} with
 * {@code sfx_TubeLauncher}, a {@code loc_82588} flame and a {@code loc_825BC} exhaust, slides right
 * four pixels a frame for {@code $11} frames, then flies with {@code x_vel} from {@code $400}
 * decreasing by {@code $20} and a {@code word_824D4} {@code y_vel} chosen from {@code V_int_run_count}
 * at launch. It follows the camera delta and wrap offset, hits Player 1 through {@code sub_82C28} and
 * explodes when the boss's {@code status} bit 7 is set.
 */
final class DdzEndBossRocketObjectInstance extends AbstractDdzObjectInstance {
    /** {@code word_824D4}. */
    private static final int[] Y_VELOCITIES = {
            -0x140, -0x120, 0x100, 0xE0, -0xC0, -0xA0, 0x80, 0x60,
            -0x80, -0x60, 0x40, 0x20, -0x100, -0xE0, 0xC0, 0xA0};
    private static final int PALETTE = 2;

    static final int STATE_WAIT = 0;
    static final int STATE_SLIDE = 1;
    static final int STATE_FLY = 2;
    static final int STATE_EXPLODING = 3;

    private final int subtype;
    private int xPos;
    private int yPos;
    private short xVel = 0x400;
    private short yVel;
    private int state;
    private int timer;
    private boolean initialized;
    private int priorityWord = 0x300;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzEndBossRocketObjectInstance(DdzEndBossObjectInstance boss, int subtype) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, subtype, 0, false, 0),
                "DDZEndBossRocket", boss);
        this.subtype = subtype;
        if (boss != null) {
            xPos = (boss.getX() & 0xFFFF) << 16;
            yPos = (boss.getY() & 0xFFFF) << 16;
        }
        S3kRawAnimation.set(animation, Sonic3kConstants.DDZ_ANIM_ROCKET_ADDR);
        animation.mappingFrame = 0x31;
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossRocketObjectInstance(ObjectSpawn spawn) {
        this(null, spawn.subtype());
    }

    @Override
    public DdzEndBossRocketObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossRocketObjectInstance(null, ctx.spawn().subtype());
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    boolean launched() {
        return state == STATE_SLIDE || state == STATE_FLY;
    }

    boolean flying() {
        return state == STATE_FLY;
    }

    /** {@code status} bit 7 as the rocket's children see it. */
    boolean exploding() {
        return state == STATE_EXPLODING || goingAway();
    }

    private DdzEndBossObjectInstance boss() {
        return parent instanceof DdzEndBossObjectInstance boss ? boss : null;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            yVel = (short) Y_VELOCITIES[((vIntRunCount & 0x18) + subtype) >> 1];
            timer = subtype << 3;
            xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
            return;
        }
        switch (state) {
            case STATE_WAIT -> {
                // loc_824F4
                xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                state = STATE_SLIDE;
                timer = 0x10;
                DdzEndBossObjectInstance boss = boss();
                if (boss != null) {
                    xPos = ((boss.getX() + 0x20) & 0xFFFF) << 16;
                    yPos = ((boss.getY() + 0x20) & 0xFFFF) << 16;
                }
                services().playSfx(Sonic3kSfx.TUBE_LAUNCHER.id);
                spawnChild(() -> new DdzEndBossRocketFlameObjectInstance(this, DdzEndBossRocketFlameObjectInstance.KIND_FLAME));
                spawnChild(() -> new DdzEndBossRocketFlameObjectInstance(this, DdzEndBossRocketFlameObjectInstance.KIND_EXHAUST));
            }
            case STATE_SLIDE -> {
                // loc_8253C
                timer = (short) (timer - 1);
                if (timer < 0) {
                    state = STATE_FLY;
                    priorityWord = 0x180;
                }
                xPos += 4 << 16;
                fly();
            }
            case STATE_FLY -> {
                // loc_8255C
                xVel = (short) (xVel - 0x20);
                xPos += xVel << 8;
                yPos += yVel << 8;
                fly();
            }
            case STATE_EXPLODING -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    goDeleteClearingRespawn();
                }
            }
            default -> throw new IllegalStateException("DDZ rocket state " + state);
        }
    }

    /** {@code loc_82568}. */
    private void fly() {
        DdzObjectSupport.rawAnimations(services()).animateNoSst(animation, animation.script, () -> { });
        xPos += DdzObjectSupport.cameraDelta(services()) << 16;
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        if (DdzEndBossHitSupport.hitPlayer(services(), getX(), getY())
                || (DdzObjectSupport.playerPowered(services()) && (boss() == null || boss().destroyedStatus()))) {
            state = STATE_EXPLODING;
            timer = 3;
            int x = getX();
            int y = getY();
            spawnChild(() -> new DdzCreateBossExplosionObjectInstance(x, y, 6, this));
            return;
        }
        // Sprite_CheckDelete
        if (outOfRangeX(getX())) {
            goDeleteClearingRespawn();
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(priorityWord);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || state == STATE_WAIT || !initialized) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false, PALETTE);
        }
    }


}
