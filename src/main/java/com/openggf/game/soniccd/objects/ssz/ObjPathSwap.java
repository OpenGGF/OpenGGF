package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Path swap, object $1D in acts 1 and 2 ({@code PathSwapObject}, objects/r7/path_swap.asm:3-123): a
 * $30-pixel square that toggles {@code stage_layer} (the layer the player's block fetch reads, see
 * {@link SszStageLayer}) when the player's box overlaps it, then idles for $3C frames. The sprite shows the
 * current layer: tile $34D with the priority bit set while the layer is clear (:30-34) and a priority of 4
 * (:114). Subtype nonzero flips it vertically (:23-25). When the layer is changed by another path swap the idle
 * object plays its flash animation too ({@code loc_20B8E8}, :83-104).
 *
 * <p>Routines (:14-18): 0 init, 2 wait, 4 flash ({@code PathSwapAnims}, ending in {@code $FC}), 6 settle: the
 * tile priority and the object's priority follow the layer, the script index is reset and the routine returns
 * to 2. The routine ends in {@code CheckObjectDespawn} (:9-10). The player is the single main player
 * ({@code lea player_object,a6}); the overlap is the box test of :50-73 with the radii of both objects.
 *
 * <p>Layer, collision mapping: the shared engine's plane switching (the {@code PlaneSwitcher} objects of the
 * other games) is not used. Sonic CD's layer is one global byte that the zone's {@code GetBlock}
 * (Collision Floor.asm:565-583) and {@code PlayerCheckBlock} read, so this object writes {@link SszStageLayer}
 * and the player lane maps it onto the zone's special-chunk profile.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPathSwap extends SszObject implements ObjectVisualFacing.NonDirectional {
    /** {@code move.b #$3C,obj.var_2a(a0)} (:74, :100). */
    private static final int COOLDOWN = 0x3C;
    /** {@code move.b #$18,obj.width/width_2/height(a0)} (:37-39). */
    private static final int RADIUS = 0x18;

    private int cooldown;
    private SszAnimator animator;

    public ObjPathSwap(ObjectSpawn spawn) {
        super(spawn, "Path swap");
    }

    @Override protected String sheetKind() { return SszObjectArt.PATH_SWAP; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity entity) {
        if (routine == 0) {
            if (artProvider() == null) return;
            init();
        }
        switch (routine) {
            case 2 -> wait(entity);
            case 4 -> flash();
            case 6 -> settle();
            default -> { }
        }
    }

    /** PathSwapObject_0_Routine0 (:22-39). */
    private void init() {
        if (spawn.subtype() != 0) spriteFlip |= 2;           // bset #1,obj.sprite_flags(a0)
        routine += 2;
        tileWord = SszObjectArt.PATH_SWAP_TILE;
        if (!SszStageLayer.of(cdLevel()).isSet()) tileWord |= 0x8000;     // ori.b #$80,obj.sprite_tile(a0)
        width = RADIUS;
        yRadius = RADIUS;
        animator = SszObjectArt.animator(artProvider(), SszObjectCatalogue.Entry::pathSwapAnimations, 0);
        initialised = true;
    }

    /** PathSwapObject_0_Routine2 (:41-102). */
    private void wait(PlayableEntity entity) {
        if (cooldown != 0) {                                 // loc_20B894's guard (:42-45)
            cooldown--;
            return;
        }
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        SszStageLayer layer = SszStageLayer.of(cdLevel());
        if (overlaps(player)) {
            cooldown = COOLDOWN;
            routine += 2;
            layer.toggle();                                  // eori.b #1,stage_layer
            return;
        }
        // loc_20B8E8 (:83-102): flash when the sprite's priority bit and the layer disagree.
        boolean highTile = (tileWord & 0x8000) != 0;
        if (highTile == layer.isSet()) {
            cooldown = COOLDOWN;
            routine += 2;
        }
    }

    /** The box test of :50-73: the player's radii plus this object's, on each axis (a word compare). */
    private boolean overlaps(AbstractPlayableSprite player) {
        int d1 = (byte) player.getXRadius() + RADIUS;
        int d0 = ((x - (player.getCentreX() & 0xFFFF)) + d1) & 0xFFFF;
        if ((short) d0 < 0 || d0 >= ((d1 * 2) & 0xFFFF)) return false;
        d1 = (byte) player.getYRadius() + RADIUS;
        d0 = ((y - (player.getCentreY() & 0xFFFF)) + d1) & 0xFFFF;
        return (short) d0 >= 0 && d0 < ((d1 * 2) & 0xFFFF);
    }

    /** PathSwapObject_0_Routine4 (:106-108). */
    private void flash() {
        if (animator.step() == 2) routine += 2;
        mapFrame = animator.mapFrame();
        if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    /** PathSwapObject_0_Routine6 (:112-123). */
    private void settle() {
        tileWord |= 0x8000;
        priority = 4;
        if (SszStageLayer.of(cdLevel()).isSet()) {
            tileWord &= 0x7FFF;
            priority = 0;
        }
        animator.setAnimFrame(0);                            // move.b #0,obj.anim_index(a0)
        routine -= 4;
    }

    public int cooldownFrames() { return cooldown; }
}
