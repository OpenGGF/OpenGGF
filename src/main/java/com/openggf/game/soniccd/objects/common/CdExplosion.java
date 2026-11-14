package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Explosion, object $18 ({@code ObjExplosion}, Level/_Objects/Explosion/Main.asm:14-106).
 * Routine 0 sets priority 1 and the stage's explosion tile word, spawns a points object when the
 * explosion came from a badnik, and picks animation 1 for a non-zero subtype; routine 2 runs
 * Ani_Explosion until its $FC; routine 4 deletes, or turns a badnik's explosion into a flower
 * ($1F, owned by the flower's lane: deleted until it is hosted).
 *
 * <p>The tile word is stage data: $8680 with layer 2 ({@code oLayer}) clearing priority, or $0680
 * with no layer test in Tidal Tempest's copy (src/objects/explosion.asm:68-77), read from the
 * object catalogue rather than tested by zone.
 */
public final class CdExplosion extends CdObject implements ObjectVisualFacing.NonDirectional,
        com.openggf.level.objects.RewindRecreatable {
    // Not final: a rewind restore rebuilds the object from its spawn and the capture puts these back.
    private boolean fromBadnik;
    private int pointsFrame;
    private int layer;
    private int routine;
    private CdAnimator animator;
    /** The animator's script bytes and animation, kept as scalars so a restore rebuilds it exactly. */
    private int animId;
    private int[] animState = {-1, 0, 0, 0, 0};

    @Override
    public com.openggf.level.objects.AbstractObjectInstance recreateForRewind(com.openggf.level.objects.RewindRecreateContext ctx) {
        return new CdExplosion(ctx.spawn());
    }

    /**
     * @param fromBadnik  {@code oExplodeBadnik} clear (Explosion/Main.asm:7,15)
     * @param pointsFrame {@code oExplodePoints} (the points sprite before {@code lsr.b #1}, Explosion/Main.asm:19-20)
     * @param layer       {@code oLayer}, copied from the spawner (Monitor and Time Post/Main.asm:403)
     */
    public CdExplosion(int x, int y, int subtype, boolean fromBadnik, int pointsFrame, int layer) {
        super(new ObjectSpawn(x, y, 0x18, subtype, 0, false, 0), "Explosion");
        this.fromBadnik = fromBadnik;
        this.pointsFrame = pointsFrame;
        this.layer = layer;
    }

    public CdExplosion(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), true, 0, 0);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        if (animator == null && routine != 0) {                       // after a rewind restore
            animator = animator(e -> e.explosionAnimations(), animId);
            if (animator != null) animator.restoreScriptState(animState);
        }
        if (routine == 2 && animator != null) {
            routine += animator.step();
            animState = animator.scriptState();
            return;
        }
        if (routine == 4) {
            // ObjExplosion_Done: delete; a badnik's explosion becomes ObjFlower in its slot (scddisasm/src/Level/_Objects/Explosion/Main.asm:98-106).
            var objects = services().objectManager();
            if (!fromBadnik || objects == null) {
                setDestroyed(true);                          // DeleteObject: the slot is freed
                return;
            }
            int slot = com.openggf.level.objects.ObjectLifetimeOps.detachSlotForTransfer(this);
            setDestroyed(true);
            com.openggf.level.objects.ObjectLifetimeOps.addReplacementAtTransferredSlot(objects,
                    CdFlower.fromBadnikExplosion(x, y, spawn.subtype(), layer), slot);
        }
    }

    private void init() {
        routine = 2;
        priority = 1;
        CdObjectArtProvider provider = artProvider();
        tileWord = provider != null ? provider.entry().explosionTile() : 0x8680;
        // tst.b oLayer; beq .HighPriority; andi.b #$7F,oTile (Explosion/Main.asm:68-70), the $8680 copy only.
        if (CdObjectArtProvider.tilePriority(tileWord) && layer != 0) tileWord &= 0x7FFF;
        if (fromBadnik) makePoints();
        // tst.b oSubtype; beq; move.w #$100,oAnim (Explosion/Main.asm:81-83).
        animId = spawn.subtype() != 0 ? 1 : 0;
        animator = animator(e -> e.explosionAnimations(), animId);
    }

    /** ObjExplosion_MakePoints (scddisasm/src/Level/_Objects/Explosion/Main.asm:14-39): FindObjSlot, $1C with subtype $80|frame. */
    private void makePoints() {
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObject(new CdPoints(new ObjectSpawn(x, y, 0x1C, 0x80 | ((pointsFrame >> 1) & 0x7F), 0,
                false, 0)));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null) return;
        drawFrame(CdObjectArtProvider.EXPLOSION, animator.mapFrame(), false, false);
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
