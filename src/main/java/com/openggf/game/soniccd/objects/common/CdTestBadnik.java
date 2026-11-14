package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * Test badnik, object $06 ({@code ObjTestBadnik}, Level/_Objects/Test Badnik/Main.asm:11-57): an
 * unused enemy drawn with the power-up mappings at tile $541, enemy collision 6, animated with
 * Ani_Powerup animation 0. It deletes itself (no on-screen exception) once its initial X chunk is
 * out of range, and on init when {@code oFlags} bit 7 is set.
 */
public final class CdTestBadnik extends CdObject implements TouchResponseProvider, TouchResponseAttackable,
        ObjectVisualFacing.NonDirectional {
    /** ObjTestBadnik_Init: move.b #6,oColType(a0) (Test Badnik/Main.asm:38). */
    private static final int COLLISION_FLAGS = 0x06;

    private final int initialX;
    private boolean initialised;
    private CdAnimator animator;
    private int mapFrame;

    public CdTestBadnik(ObjectSpawn spawn) {
        super(spawn, "TestBadnik");
        this.initialX = spawn.x();
        // ObjTestBadnik_Init: priority 1, tile $541 (Test Badnik/Main.asm:33-36).
        this.priority = 1;
        this.tileWord = CommonObjectArt.TEST_BADNIK_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        initialised = true;
        // ObjTestBadnik_Main: lea Ani_Powerup,a1; bsr AnimateObject (Test Badnik/Main.asm:55-56).
        if (animator == null) animator = animator(e -> e.offset("powerupAnimations"), 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        // oRoutine2 is never written, so oExplodeBadnik is clear; oLayer is the layout's secondary subtype.
        if (player != null && !isDestroyed()) killAsEnemy(player, subtype2(), true);
    }

    @Override public int getCollisionFlags() { return initialised ? COLLISION_FLAGS : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    /** cmpi.w #$80+(320+$40)+$80,d0; bhi DeleteObject on oUnusedBadX (Test Badnik/Main.asm:45-53). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideDespawnRange(initialX, cameraX, false);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(CommonObjectArt.TEST_BADNIK, mapFrame, hFlip(), vFlip());
    }
}
