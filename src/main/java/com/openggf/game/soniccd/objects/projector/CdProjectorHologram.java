package com.openggf.game.soniccd.objects.projector;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * The projector's two hologram children, {@code ObjMetalSonicHologram} (Palmtree Panic/Objects/Projector/Main.asm:230-281):
 * the projector's own ID with a non-zero subtype, subtype $FF the projector's beam (animation 0, width/radius 8 and 4)
 * and subtype 1 the Metal Sonic figure (animation 1, width $14, radius $18). Each turn it tests its parent
 * ({@link ProjectorChild#parentAlive}) and deletes itself when the projector is gone or has been hit; otherwise it animates
 * {@code Ani_MetalSonicHologram} and draws, at priority 4 with the projector's tile.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CdProjectorHologram extends CdObject implements ObjectVisualFacing.NonDirectional, RewindRecreatable {
    private int parentSlot;
    private int routine;
    private int mapFrame;
    private int spriteFlip;
    private CdAnimator animator;
    /** The animator's script bytes and animation, kept as scalars so a rewind restore rebuilds it exactly. */
    private int animId;
    private int prevAnim = -1;
    private int animFrame;
    private int animTime;
    private int animFlip;

    CdProjectorHologram(ObjectSpawn spawn, int parentSlot) {
        super(spawn, "Projector hologram");
        this.parentSlot = parentSlot;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CdProjectorHologram(ctx.spawn(), parentSlot);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var level = cdLevel();
        ProjectorVariant variant = level == null ? null : ProjectorVariant.of(level.payload().stage().zone());
        if (variant == null || !ProjectorChild.parentAlive(services(), parentSlot, variant.parentId())) {
            setDestroyed(true);                                      // bne.w ObjProjector_Destroy
            return;
        }
        if (routine == 0) {
            routine = 2;
            priority = 4;
            tileWord = variant.tile(level.payload().stage().actNumber() - 1);
            boolean beam = (byte) spawn.subtype() < 0;               // tst.b oSubtype(a0); bmi.s .GotSize
            width = beam ? 8 : 0x14;
            yRadius = beam ? 4 : 0x18;
            int anim = beam ? 0 : 1;
            animId = anim;
            CdObjectArtProvider provider = artProvider();
            if (provider != null) {
                animator = new CdAnimator(provider.image(), ProjectorObjectCatalogue.animations(provider.payload()), anim);
            }
        }
        if (animator == null && routine != 0) {                       // after a rewind restore
            CdObjectArtProvider provider = artProvider();
            if (provider != null) {
                animator = new CdAnimator(provider.image(), ProjectorObjectCatalogue.animations(provider.payload()), animId);
                animator.restoreScriptState(new int[] {prevAnim, animFrame, animTime, mapFrame, animFlip});
            }
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
            spriteFlip = animator.flipBits() & 3;                    // oFlags is 0: AnimateObject's flip bits only
            int[] state = animator.scriptState();
            prevAnim = state[0];
            animFrame = state[1];
            animTime = state[2];
            animFlip = state[4];
        }
    }

    @Override
    public String traceDebugDetails() {
        return String.format("routine=%d parent=%d frame=%d flip=%d anim=%d", routine, parentSlot, mapFrame, spriteFlip,
                animId);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        String key = routine == 0 || animator == null ? null : ProjectorArt.key(artProvider(), services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
