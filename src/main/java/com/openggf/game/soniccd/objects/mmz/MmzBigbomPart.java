package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The parts a {@link MmzBigbom} controller makes ({@code BigbomObject} subtypes 4, 6 and 8) from BigbomObject_0_Routine4 (objects/r8/bigbom.asm:454-637):
 * the fuse that burns above the body and then lets the controller go on (subtype 4), the explosion that plays for $28
 * calls (subtype 6) and the seven debris pieces that fly out and hurt (subtype 8, collision $A7, off on
 * {@code stage_layer} 1 for an {@code oSubtype2} 2 bomb).
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
final class MmzBigbomPart extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    enum Kind { FUSE, EXPLOSION, DEBRIS }

    private final Kind kind;
    private final MmzBigbom controller;          // oVar3E
    private final int sub2;
    private int routine;
    private int timer;                           // oVar2A of the fuse and explosion
    private int speedX;                          // oVar2A of the debris (16.16)
    private int speedY;                          // oVar2E of the debris
    private int colType;
    private int mapFrame;
    private int spriteFlip;
    private boolean initialised;
    private CdAnimator animator;

    MmzBigbomPart(ObjectSpawn spawn, Kind kind, MmzBigbom controller, int sub2, int flip, int speedX, int speedY) {
        super(spawn, "BigbomPart");
        this.kind = kind;
        this.controller = controller;
        this.sub2 = sub2;
        this.spriteFlip = flip;
        this.speedX = speedX;
        this.speedY = speedY;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (kind) {
            case FUSE -> fuse();
            case EXPLOSION -> explosion();
            case DEBRIS -> debris(player);
        }
    }

    /** BigbomObject_3_Routine0..6 (objects/r8/bigbom.asm:471-514). */
    private void fuse() {
        switch (routine) {
            case 0 -> {                                                    // Routine0 falls into Routine2
                routine = 2;
                initialised = true;
                priority = 2;
                tileWord = MmzObjectArt.BIGBOM_TILE;
                width = 4;
                yRadius = 0xC;
                timer = 0x3C;
                fuseWait();
            }
            case 2 -> fuseWait();
            case 4 -> {
                addToY(0x5000);
                timer = (timer - 1) & 0xFFFF;
                if ((timer & 0x8000) != 0) routine += 2;
                animate("bigbomFuseAnimations");
            }
            default -> {                                                   // Routine6
                if (!controller.isDestroyed()) controller.advanceRoutine();
                setDestroyed(true);
            }
        }
    }

    private void fuseWait() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            timer = 0x4C;
            routine += 2;
        }
        animate("bigbomFuseAnimations");
    }

    /** BigbomObject_4_Routine0/2 (objects/r8/bigbom.asm:541-556). */
    private void explosion() {
        if (routine == 0) {
            routine = 2;
            initialised = true;
            priority = 1;
            tileWord = MmzObjectArt.BIGBOM_EXPLODE_TILE;
            timer = 0x28;
        }
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            setDestroyed(true);
            return;
        }
        animate("bigbomExplodeAnimations");
    }

    /** BigbomObject_5_Routine0/2 (objects/r8/bigbom.asm:587-627). */
    private void debris(PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            initialised = true;
            priority = 1;
            width = 8;
            yRadius = 8;
            tileWord = MmzObjectArt.BIGBOM_TILE;
            colType = 0xA7;
        }
        if (sub2 == 2) colType = stageLayer() != 0 ? 0 : 0xA7;
        addToX(speedX);
        addToY(speedY);
        speedY += 0x2000;                                                  // oVar36 = $2000, oVar32 = 0
        if (player instanceof AbstractPlayableSprite sprite && (short) (y - sprite.getCentreY()) >= 0xE0) {
            setDestroyed(true);                                            // cmpi.w #$E0,d0; blt
            return;
        }
        animate("bigbomDebrisAnimations");
    }

    private void animate(String column) {
        if (animator == null) animator = mmzAnimator(column, 0);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    @Override public int getCollisionFlags() { return kind == Kind.DEBRIS ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        String key = switch (kind) {
            case FUSE -> MmzObjectArt.BIGBOM_FUSE;
            case EXPLOSION -> MmzObjectArt.BIGBOM_EXPLODE;
            case DEBRIS -> MmzObjectArt.BIGBOM_DEBRIS;
        };
        drawSheet(key, mapFrame, (spriteFlip & 1) != 0, false);
    }
}
