package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code ChildObjDat_7AB80} -&gt; {@code loc_7AB8E} (sonic3k.asm:163458-163509): the pair of
 * shots the Metropolis recreation fires during its laser pass.
 *
 * <p>Two children, at {@code (-$C,-4)} and {@code (-$18,-4)}, both running the same code with
 * different subtypes. {@code ObjDat3_7ABFA} gives them {@code Map_SSZMTZOrbs} on
 * {@code make_art_tile(ArtTile_SSZMTZOrbs,1,1)}, priority {@code $280}, {@code $28} by {@code 8},
 * frame {@code $D} and collision {@code $9C}. Subtype 1 then overrides three of those: it waits
 * eight frames instead of none, draws frame {@code $C} and takes priority {@code $100}, so the
 * second shot trails the first and sits in front of it.
 *
 * <p>The wait is a held draw, not a hidden one — {@code loc_7ABC2} runs
 * {@code Child_Draw_Sprite} every frame it counts down, which is why the muzzle sits on the ship's
 * nose before it moves. When {@code $2E(a0)} goes negative {@code loc_7ABCE} installs
 * {@code loc_7ABEE}, sets {@code x_vel = -$400} negated by the X-flip, plays {@code sfx_Laser}
 * once, and from then on the shot is {@code MoveSprite2} plus
 * {@code Sprite_CheckDeleteTouch} — so it leaves on the screen edge with no off-screen routine of
 * its own.
 *
 * <p>The parent is read once, by {@code Refresh_ChildPositionAdjusted} at setup, and never again;
 * this object therefore keeps no reference to the ship and nothing to relink on a rewind restore.
 */
public final class SszMtzBossLaserChild extends AbstractObjectInstance
        implements RewindRecreatable, com.openggf.level.objects.TouchResponseProvider {

    /** {@code ChildObjDat_7AB80}: {@code dc.w 2-1}. */
    public static final int LASER_PAIR = 2;
    /** {@code dc.b -$C,-4} and {@code dc.b -$18,-4}. */
    public static final int CHILD_DX_0 = -0x0C;
    public static final int CHILD_DX_1 = -0x18;
    public static final int CHILD_DY = -4;
    /** {@code ObjDat3_7ABFA}: {@code dc.w $280} / {@code dc.b $28,8,$D,$9C}. */
    public static final int PRIORITY_LEAD = 0x280;
    public static final int PRIORITY_TRAIL = 0x100;
    private static final int HALF_WIDTH = 0x28;
    private static final int HALF_HEIGHT = 8;
    public static final int FRAME_LEAD = 0x0D;
    public static final int FRAME_TRAIL = 0x0C;
    public static final int COLLISION_FLAGS = 0x9C;
    /** {@code moveq #0,d0} / {@code moveq #8,d0}: how long each shot is held on the nose. */
    public static final int DELAY_LEAD = 0;
    public static final int DELAY_TRAIL = 8;
    /** {@code move.w #-$400,d0}, negated when the ship is X-flipped. */
    public static final int SHOT_X_VEL = -0x400;
    /** {@code make_art_tile(ArtTile_SSZMTZOrbs,1,1)}: palette line 1, not the orbs' line 0. */
    private static final int PALETTE_LINE = 1;

    private boolean trailing;
    private int xPos;
    private int yPos;
    private int xVel;
    private int delay;
    private boolean firing;
    private boolean renderFlipped;

    public SszMtzBossLaserChild(ObjectSpawn spawn, boolean parentFlipped) {
        super(spawn, "SSZMTZBossLaser");
        this.trailing = spawn.subtype() != 0;
        this.xPos = spawn.x() << 16;
        this.yPos = spawn.y() << 16;
        this.renderFlipped = parentFlipped;
        this.delay = trailing ? DELAY_TRAIL : DELAY_LEAD;
    }

    /** Probe/rewind constructor: the spawn alone carries the subtype, and the flip with it. */
    public SszMtzBossLaserChild(ObjectSpawn spawn) {
        this(spawn, spawn.renderFlags() != 0);
    }

    @Override
    public SszMtzBossLaserChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszMtzBossLaserChild(ctx.spawn());
    }

    private record RewindExtra(int xPos, int yPos, int xVel, int delay, boolean firing,
                               boolean renderFlipped, boolean trailing)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(xPos, yPos, xVel, delay, firing,
                        renderFlipped, trailing));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            xPos = extra.xPos();
            yPos = extra.yPos();
            xVel = extra.xVel();
            delay = extra.delay();
            firing = extra.firing();
            renderFlipped = extra.renderFlipped();
            // Both come off the ObjectSpawn at construction and never change, but the coverage
            // guard is right that a recreate-then-restore must not depend on that: the subtype
            // and the Y are carried rather than re-derived.
            trailing = extra.trailing();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!firing) {
            // loc_7ABC2: subq.w #1,$2E(a0) / bmi.s loc_7ABCE, with a draw on every frame it holds.
            if (--delay >= 0) {
                return;
            }
            firing = true;
            xVel = renderFlipped ? -SHOT_X_VEL : SHOT_X_VEL;
            services().playSfx(Sonic3kSfx.LASER.id);
            // loc_7ABCE falls straight into loc_7ABEE, so the shot moves on this same frame.
        }
        xPos += xVel << 8;
        // Sprite_CheckDeleteTouch: off the camera's span and the shot is gone.
        if (isOffCamera()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    private boolean isOffCamera() {
        var camera = services().camera();
        int left = (camera.getX() & 0xFFFF) - HALF_WIDTH * 2;
        int right = (camera.getX() & 0xFFFF) + camera.getWidth() + HALF_WIDTH * 2;
        int x = getX();
        return x < left || x > right;
    }

    /** {@code ObjDat3_7ABFA}'s last byte: a {@code $80} category, i.e. plain harm. */
    @Override public int getCollisionFlags() { return firing ? COLLISION_FLAGS : 0; }

    /** A shot is not something that takes hits. */
    @Override public int getCollisionProperty() { return 0; }

    @Override public int getX() { return (xPos >> 16) & 0xFFFF; }

    @Override public int getY() { return (yPos >> 16) & 0xFFFF; }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(trailing ? PRIORITY_TRAIL : PRIORITY_LEAD);
    }

    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }

    @Override public int getOnScreenHalfHeight() { return HALF_HEIGHT; }

    public boolean isFiringForTest() { return firing; }

    public int delayForTest() { return delay; }

    public int xVelForTest() { return xVel; }

    public boolean isTrailingForTest() { return trailing; }

    /** {@code ObjDat3_7ABFA}'s frame $D, or the {@code move.b #$C} subtype 1 overrides it with. */
    public int mappingFrameForTest() { return trailing ? FRAME_TRAIL : FRAME_LEAD; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_MTZ_ORBS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(trailing ? FRAME_TRAIL : FRAME_LEAD, getX(), getY(),
                    renderFlipped, false, PALETTE_LINE);
        }
    }
}
