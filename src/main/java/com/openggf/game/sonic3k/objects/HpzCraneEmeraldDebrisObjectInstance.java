package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Emerald debris the Hidden Palace crane scatters when it grabs the Master Emerald
 * ({@code loc_650A6}), drawn from {@code Map_HPZEmeraldMisc}.
 *
 * <p>{@code loc_6518A} (15 sparkles, palette 0, high priority) waits {@code subtype*2} frames
 * {@code $30} below the crane, jumps to a random offset within {@code $20} pixels, plays
 * {@code byte_66843}, then rises while playing {@code byte_6684A} and deletes.
 * {@code loc_65226} (5 chips, palette 2) sit on the crane at {@code byte_65262} offsets for
 * {@code word_6526C} frames, then fall with gravity; both chips and the falling phase delete
 * through {@code Sprite_CheckDeleteXY}.
 */
public final class HpzCraneEmeraldDebrisObjectInstance extends AbstractHpzCutsceneChildObjectInstance {
    /** {@code byte_6669A} scripts, read once per instance; ROM bytes, not state. */
    private transient S3kRawAnimation rawScripts;

    private S3kRawAnimation rawScripts() {
        if (rawScripts == null) {
            rawScripts = HpzKnucklesCutsceneSupport.scripts(services());
        }
        return rawScripts;
    }

    static final int KIND_SPARKLE = 0;
    static final int KIND_CHIP = 1;
    private static final int SPARKLE_SCRIPT = 0x66843;
    private static final int RISE_SCRIPT = 0x6684A;
    /** {@code byte_65262}. */
    private static final int[] CHIP_OFFSETS = {4, 0x2C, -2, 0x2A, 6, 0x22, -8, 0x24, -2, 0x1C};
    /** {@code word_6526C}. */
    private static final int[] CHIP_WAITS = {0x20, 0x30, 0x40, 0x50, 0x60};
    /** {@code RawAni_6525C}. */
    private static final int[] CHIP_FRAMES = {0x1C, 0x1B, 0x1A, 0x19, 0x18};

    private static final int PHASE_INIT = 0;
    private static final int PHASE_WAIT = 1;
    private static final int PHASE_ANIMATE = 2;
    private static final int PHASE_RISE = 3;
    private static final int PHASE_FALL = 4;
    private static final int PHASE_DELETE = 5;

    private int kind;
    private int phase;
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int yVel;
    private int timer;
    private boolean highPriority;
    private boolean visible;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();

    public HpzCraneEmeraldDebrisObjectInstance(ObjectSpawn spawn, HpzShipCraneObjectInstance crane,
                                               int kind) {
        super(new ObjectSpawn(spawn.x(), spawn.y(), 0, spawn.subtype(), kind, false, 0),
                kind == KIND_SPARKLE ? "HpzCraneEmeraldSparkle" : "HpzCraneEmeraldChip", crane);
        this.kind = kind;
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzCraneEmeraldDebrisObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzCraneEmeraldDebrisObjectInstance(ctx.spawn(), null, ctx.spawn().renderFlags());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (kind == KIND_SPARKLE) {
            updateSparkle();
        } else {
            updateChip();
        }
        updateDynamicSpawn(x, y);
    }

    private void updateSparkle() {
        int subtype = spawn.subtype() & 0xFF;
        switch (phase) {
            case PHASE_INIT -> {
                // loc_6518A: ObjDat3_66512 frame $12, y += $30, $2E = subtype*2.
                anim.mappingFrame = 0x12;
                highPriority = true;
                y = (y + 0x30) & 0xFFFF;
                timer = subtype * 2;
                phase = PHASE_WAIT;
            }
            case PHASE_WAIT -> {
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                // loc_651B4
                int random = services().rng().nextRaw();
                x = (x + ((random & 0x3F) - 0x20)) & 0xFFFF;
                y = (y + (((random >>> 16) & 0x3F) - 0x20)) & 0xFFFF;
                phase = PHASE_ANIMATE;
                animate(SPARKLE_SCRIPT);
            }
            case PHASE_ANIMATE -> animate(SPARKLE_SCRIPT);
            case PHASE_RISE -> {
                animate(RISE_SCRIPT);
                yVel = (short) (yVel - 0x80);
                SubpixelMotion.State s = new SubpixelMotion.State(x, y, xSub, ySub, 0, yVel);
                SubpixelMotion.moveSprite2(s);
                x = s.x & 0xFFFF;
                y = s.y & 0xFFFF;
                xSub = s.xSub;
                ySub = s.ySub;
            }
            default -> ObjectLifetimeOps.expireDynamic(this);
        }
    }

    private void animate(int script) {
        int before = phase;
        rawScripts().animateNoSst(anim, script, () -> {
            if (before == PHASE_ANIMATE) {
                // loc_651F2
                phase = PHASE_RISE;
                anim.animFrame = 0;
                anim.animFrameTimer = 0;
            } else {
                // Go_Delete_Sprite
                phase = PHASE_DELETE;
            }
        });
        visible = true;
    }

    private void updateChip() {
        int subtype = spawn.subtype() & 0xFF;
        switch (phase) {
            case PHASE_INIT -> {
                // loc_65226
                anim.mappingFrame = CHIP_FRAMES[subtype >> 1];
                timer = CHIP_WAITS[subtype >> 1];
                phase = PHASE_WAIT;
            }
            case PHASE_WAIT -> {
                // loc_65276: Refresh_ChildPosition / Obj_Wait / Sprite_CheckDeleteXY
                if (parent != null && !parent.isDestroyed()) {
                    x = (parent.getX() + CHIP_OFFSETS[subtype]) & 0xFFFF;
                    y = (parent.getY() + CHIP_OFFSETS[subtype + 1]) & 0xFFFF;
                }
                timer = (short) (timer - 1);
                if (timer < 0) {
                    // loc_65288
                    phase = PHASE_FALL;
                    highPriority = true;
                }
                checkDeleteXy();
            }
            default -> {
                // loc_65296
                SubpixelMotion.State s = new SubpixelMotion.State(x, y, xSub, ySub, 0, yVel);
                SubpixelMotion.moveSprite(s, SubpixelMotion.S3K_GRAVITY);
                x = s.x & 0xFFFF;
                y = s.y & 0xFFFF;
                xSub = s.xSub;
                ySub = s.ySub;
                yVel = (short) s.yVel;
                checkDeleteXy();
            }
        }
    }

    /** {@code Sprite_CheckDeleteXY}. */
    private void checkDeleteXy() {
        var camera = services().camera();
        if (isCoarseXOutOfRange(x, camera.getX() & 0xFFFF, coarseXCullRange())
                || ((y - (camera.getY() & 0xFFFF) + 0x80) & 0xFFFF) > 0x200) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        visible = true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public int getPriorityBucket() {
        return kind == KIND_SPARKLE ? 0 : RenderPriority.fromS3kWord(0x180);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anim.mappingFrame, x, y, false, false, kind == KIND_SPARKLE ? 0 : 2);
        }
    }


}
