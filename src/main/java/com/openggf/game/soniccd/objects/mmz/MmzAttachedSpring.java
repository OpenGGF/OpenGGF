package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The upward spring riding another object (a moving spring, $F0; a spike trap block, $E8): {@code ObjSpring} with a
 * parent ({@code oVar34}) and an offset ({@code oVar38}, {@code oVar39}), {@code src/objects/spring.asm}
 * (MoveSpringObject, :3-170, whose tail follows the parent) and its upward routine. The common {@code CdSpring}
 * has no parent link, so this class carries the upward spring's launch and the follow. Sideways and diagonal springs on a moving spring are common springs placed without a parent
 * (unverified: no fixture reaches one).
 *
 * <p>The follow runs after the draw: position = parent position plus the offset. Despawn is the spring's own chunk
 * check on its initial X.
 */
final class MmzAttachedSpring extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** ObjSpring_Speeds: $F000 red, $F600 yellow (Spring/Main.asm:243-244). */
    private static final int[] SPEEDS = {-0x1000, -0x0A00};

    private final com.openggf.level.objects.ObjectInstance parent;
    private final int offsetX;
    private final int offsetY;
    private final int speed;
    private final int initialX;
    private boolean animating;
    private boolean resetPending;
    private CdAnimator animator;
    private int mapFrame;

    MmzAttachedSpring(ObjectSpawn spawn, com.openggf.level.objects.ObjectInstance parent, int offsetX, int offsetY) {
        super(spawn, "MoveSpringTop");
        this.parent = parent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.initialX = spawn.x();
        this.priority = 4;
        this.width = 0x10;
        this.yRadius = 8;
        this.tileWord = CdObjectArtProvider.SPRING_TILE;
        if ((spawn.subtype() & 2) != 0) tileWord |= 0x2000;
        this.speed = SPEEDS[(spawn.subtype() >> 1) & 1];
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (renderFlagOnScreen()) {
            if (resetPending) {
                resetPending = false;
                animating = false;
                mapFrame = 0;
                if (animator != null) animator.restart();
            } else if (animating) {
                if (animator == null) animator = animator(e -> e.springAnimations(), 0);
                if (animator != null) {
                    if (animator.step() == 2) resetPending = true; else mapFrame = animator.mapFrame();
                }
            } else if (player instanceof AbstractPlayableSprite sprite && solidObject(Entry.SOLID)) {
                launch(sprite);
            }
        }
        x = (parent.getX() + offsetX) & 0xFFFF;               // oVar34: copy the parent's position, then oVar38/oVar39
        y = (parent.getY() + offsetY) & 0xFFFF;
    }

    /** ObjSpring_Main_Up .Action (Spring/Main.asm:267-276). */
    private void launch(AbstractPlayableSprite player) {
        NativePositionOps.addYPosPreserveSubpixel(player, 8);
        player.setYSpeed((short) speed);
        player.setAir(true);
        player.setOnObject(false);
        player.setAnimationId(CdSonicAnimation.SPRING);
        clearStoodOn();
        animating = true;
        if (animator != null) animator.restart();
        services().playSfx(com.openggf.audio.GameSound.SPRING);
    }

    @Override public Role solidRole() { return Role.SPRING; }
    @Override public boolean solidSpringFacesUp() { return true; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawFrame(CdObjectArtProvider.SPRING_UP, mapFrame, false, false);
    }
}
