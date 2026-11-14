package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Tamabboh's missile, object $22 with subtype 1 ({@code ObjTamabbohMissile}, Palmtree Panic/Objects/
 * Tamabboh/Main.asm:213-282). Tamabboh creates two ({@code ObjTamabboh_InitMissile}, scddisasm/src/Level/Palmtree Panic/Objects/Tamabboh/Main.asm:189-198):
 * one lobbed right at $20000 and one left at -$20000, both up at -$40000, with gravity $2000 per frame
 * (16.16). Collision $AD (hurt, size $2D). Deleted when off screen or when {@code ObjGetFloorDist}
 * reports it inside the ground. The tile word and priority come from the parent (parent priority + 1).
 *
 * <p>Routines: 0 init, 2 move. Initialisation sets {@code oVar32} (horizontal acceleration) to 0.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTamabbohMissile extends CdObject implements TouchResponseProvider,
        ObjectVisualFacing.NonDirectional {
    private static final ToIntFunction<PpzObjectCatalogue.Entry> ANIMATIONS = e -> e.tamabbohMissileAnimations();

    private final boolean flyLeft;
    private int routine;
    private int xSub;
    private int ySub;
    private int xVelocity;
    private int yVelocity;
    private int gravity;
    private int colType;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private int mapFrame;
    private boolean initialised;

    public ObjTamabbohMissile(int x, int y, int tileWord, int priority, boolean flyLeft) {
        super(new ObjectSpawn(x, y, 0x22, 1, 0, false, 0), "Tamabboh missile");
        this.tileWord = tileWord;
        this.priority = priority;
        this.flyLeft = flyLeft;
    }

    /** A layout entry with subtype 1 (not placed by the shipped layouts): oVar3F clear, no inherited tile. */
    public ObjTamabbohMissile(ObjectSpawn spawn) {
        super(spawn, "Tamabboh missile");
        this.flyLeft = false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            init();
            return;
        }
        if (!renderFlagOnScreen()) {                    // tst.b oSprFlags; bmi.s .Action; DeleteObject
            setDestroyed(true);
            return;
        }
        if (floorDistance() < 0) {                      // .Action: tst.w d1; bpl.s .MoveAnim; DeleteObject
            setDestroyed(true);
            return;
        }
        addTo(true, xVelocity);
        addTo(false, yVelocity);
        yVelocity += gravity;                           // oVar32 (0) is added to oVar2A first
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** ObjTamabbohMissile_Init (Tamabboh/Main.asm:226-251). */
    private void init() {
        routine = 2;
        colType = 0xAD;
        width = 8;
        yRadius = 8;
        xVelocity = flyLeft ? -0x20000 : 0x20000;
        yVelocity = -0x40000;
        gravity = 0x2000;
        animator = PpzObjectArt.animator(artProvider(), ANIMATIONS, 0);
        initialised = true;
    }


    private int floorDistance() {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    private void addTo(boolean horizontal, int velocity) {
        if (horizontal) {
            int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
            x = position >>> 16;
            xSub = position & 0xFFFF;
        } else {
            int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
            y = position >>> 16;
            ySub = position & 0xFFFF;
        }
    }

    @Override public int getCollisionFlags() { return initialised ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_MISSILE, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }
}
