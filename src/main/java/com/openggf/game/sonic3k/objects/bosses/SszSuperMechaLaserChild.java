package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;

/** {@code loc_7C744}/{@code loc_7C764}: Super Mecha Sonic's attached then aimed laser. */
public final class SszSuperMechaLaserChild extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider {
    private static final int DX = -7;
    private static final int DY = -8;
    private static final int SPEED = 0x400;

    private final SszMechaSonicObjectInstance parent;
    private final int subtype;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;
    private int posX;
    private int posY;
    private int xVel;
    private int yVel;
    private boolean launched;

    private record RewindExtra(int posX, int posY, int xVel, int yVel, boolean launched,
                               int animFrame, int animFrameTimer, int mappingFrame)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSuperMechaLaserChild(ObjectSpawn spawn, SszMechaSonicObjectInstance parent) {
        super(spawn, "SSZSuperMechaLaser");
        this.parent = parent;
        subtype = spawn.subtype() & 0xFF;
        posX = spawn.x() << 16;
        posY = spawn.y() << 16;
        animation.script = script();
    }

    public SszSuperMechaLaserChild(ObjectSpawn spawn) { this(spawn, null); }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszSuperMechaLaserChild(context.spawn());
    }

    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                posX, posY, xVel, yVel, launched, animation.animFrame,
                animation.animFrameTimer, animation.mappingFrame));
    }

    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                              RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            posX = extra.posX(); posY = extra.posY(); xVel = extra.xVel(); yVel = extra.yVel();
            launched = extra.launched(); animation.animFrame = extra.animFrame();
            animation.animFrameTimer = extra.animFrameTimer();
            animation.mappingFrame = extra.mappingFrame();
        }
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (launched) {
            posX += xVel << 8;
            posY += yVel << 8;
            var camera = services().camera();
            int x = getX(), y = getY();
            if (x < (camera.getX() & 0xFFFF) - 0x40
                    || x > (camera.getX() & 0xFFFF) + camera.getWidth() + 0x40
                    || y < (camera.getY() & 0xFFFF) - 0x40
                    || y > (camera.getY() & 0xFFFF) + camera.getHeight() + 0x40) {
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
            return;
        }
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        boolean flipped = parent.renderFlippedForTest();
        posX = ((parent.getX() + (flipped ? -DX : DX)) & 0xFFFF) << 16;
        posY = ((parent.getY() + DY) & 0xFFFF) << 16;
        animate(player);
    }

    private void animate(PlayableEntity player) {
        if (animator == null) {
            try {
                animator = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_ADDR,
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_SIZE);
            } catch (IOException | RuntimeException ignored) { return; }
        }
        animator.animateNoSst(animation, script(), () -> {
            if (subtype == 8) {
                ObjectLifetimeOps.expireDynamic(this);
            } else {
                launchAt(player);
            }
        });
    }

    private int script() {
        return subtype == 8 ? Sonic3kConstants.SSZ_MECHA_LASER_FINISH_ANIM_ADDR
                : Sonic3kConstants.SSZ_MECHA_LASER_LAUNCH_ANIM_ADDR;
    }

    /** {@code sub_861D0}, d5=2: the dominant axis is exactly {@code $400}. */
    private void launchAt(PlayableEntity player) {
        if (player == null) player = services().spriteManager().getMainPlayable();
        if (player == null && services().camera() != null) {
            player = services().camera().getFocusedSprite();
        }
        if (player == null) return;
        int dx = (short) ((player.getCentreX() & 0xFFFF) - getX());
        int dy = (short) ((player.getCentreY() & 0xFFFF) - getY());
        int ax = Math.abs(dx), ay = Math.abs(dy);
        if (ax == 0 && ay == 0) { yVel = SPEED; }
        else if (ax >= ay) {
            xVel = dx < 0 ? -SPEED : SPEED;
            yVel = ax == 0 ? 0 : (dy * SPEED) / ax;
        } else {
            yVel = dy < 0 ? -SPEED : SPEED;
            xVel = (dx * SPEED) / ay;
        }
        launched = true;
        services().playSfx(Sonic3kSfx.BOSS_LASER.id);
    }

    @Override public int getCollisionFlags() { return 0x86; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getX() { return (posX >> 16) & 0xFFFF; }
    @Override public int getY() { return (posY >> 16) & 0xFFFF; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x200); }
    public boolean launchedForTest() { return launched; }
    public int xVelForTest() { return xVel; }
    public int yVelForTest() { return yVel; }
    public int mappingFrameForTest() { return animation.mappingFrame; }
    public int animationFrameForTest() { return animation.animFrame; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_EXTRA);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(),
                    !launched && parent != null && parent.renderFlippedForTest(), false, 1);
        }
    }
}
