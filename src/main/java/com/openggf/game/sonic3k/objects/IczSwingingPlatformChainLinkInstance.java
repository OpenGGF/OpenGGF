package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Detached ICZ chain link: ROM loc_8AF3E initializes, then loc_8AF9E uses MoveChkDel. */
public final class IczSwingingPlatformChainLinkInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private boolean initialized;
    private boolean visible;
    private boolean deleteNextFrame;

    IczSwingingPlatformChainLinkInstance(int x, int y, int ordinal, boolean parentXFlip) {
        super(new ObjectSpawn(x, y, 0xFF, ordinal * 2, 0, false, 0), "ICZSwingingChainLink");
        this.x = x;
        this.y = y;
        // word_8AF86 gives $80..$400; parent render bit0 selects negative X.
        int velocity = (ordinal + 1) * 0x80;
        this.xVel = parentXFlip ? -velocity : velocity;
        this.yVel = -velocity;
    }

    @Override
    public IczSwingingPlatformChainLinkInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        return new IczSwingingPlatformChainLinkInstance(spawn.x(), spawn.y(), spawn.subtype() / 2, false);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) return;
        if (deleteNextFrame) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // loc_8AF3E returns after initialization. Movement starts next dispatch.
        if (!initialized) {
            initialized = true;
            return;
        }
        // MoveSprite adds $38 to y_vel after using the old word for movement
        // (sonic3k.asm:36072-36083), unlike the no-gravity MoveSprite2.
        SubpixelMotion.State motion = new SubpixelMotion.State(x, y, xSub, ySub, xVel, yVel);
        SubpixelMotion.objectFallXY(motion, SubpixelMotion.S3K_GRAVITY);
        x = motion.x;
        y = motion.y;
        xSub = motion.xSub;
        ySub = motion.ySub;
        yVel = (short) motion.yVel;
        visible = true;
        int cameraX = services().camera() == null ? cameraLeft() : services().camera().getX();
        int cameraY = services().camera() == null ? cameraTop() : services().camera().getY();
        // Sprite_CheckDeleteXY checks aligned X against coarse-back+$280,
        // and the unsigned Y interval [-$80,+$180], both inclusive. Its
        // Go_Delete_Sprite installs deletion for the next object dispatch.
        deleteNextFrame = isCoarseXOutOfRange(x, cameraX, 0x280)
                || ((y - cameraY + 0x80) & 0xFFFF) > 0x200;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || deleteNextFrame || !visible) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ICZ_PLATFORMS);
        if (renderer != null) {
            // CreateChild6_Simple does not copy the chain owner's render flips.
            renderer.drawFrameIndex(8, x, y, false, false, 2);
        }
    }
}
