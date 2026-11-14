package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Horizontal door ({@code HDoorObject}, objects/r1/h_door.asm:3-93), assembled by every zone but Tidal
 * Tempest (the water-surface doors of Palmtree Panic's tunnels and the like). It is a one-way floor
 * ({@code TopSolidObject}) until a player moving upward is inside its box; then it plays its opening
 * animation (the door is not solid while it runs) and, for subtype 0, throws a splash
 * ({@link ObjHDoorSplash}, object $B) and plays sound $A4. The sprite tile is {@code SetObjectSpriteTile}
 * index $C, picked by the layout's secondary subtype.
 */
public final class ObjHDoor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #$A4,d0; jmp PlayFmSound (objects/r1/h_door.asm:35-36). */
    private static final int SFX_OPEN = 0xA4;

    private int routine;
    private boolean initialised;
    private CdAnimator animator;
    private int mapFrame;

    public ObjHDoor(ObjectSpawn spawn) {
        super(spawn, "H door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        switch (routine) {
            case 2 -> idle(player);
            case 4 -> opening();
            case 6 -> {                                             // HDoorObject_0_Routine6 (:89-93)
                animator = null;                                    // move.b #1,obj.prev_anim_id(a0): restarts
                mapFrame = 0;
                routine -= 4;
            }
            default -> { }
        }
    }

    /** HDoorObject_0_Routine0 (:52-73), falling into Routine2. */
    private boolean init() {
        var provider = artProvider();
        if (provider == null) return false;
        routine = 2;
        priority = 1;
        width = getSpawn().subtype() == 2 ? 0x18 : 0x2C;            // obj.width_2 (:57-68)
        yRadius = 8;
        tileWord = provider.objectTileId(0xC, subtype2());
        initialised = true;
        return true;
    }

    /** HDoorObject_0_Routine2 and sub_205E3A (:75-79, 21-48). */
    private void idle(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite && sprite.getYSpeed() < 0 && inBox(sprite)) {
            routine = 4;
            if (getSpawn().subtype() != 0) return;
            var objects = services().objectManager();
            if (objects == null) return;
            objects.addDynamicObject(new ObjHDoorSplash(x, y - 4, getSpawn().objectId()));
            services().playSfx(SFX_OPEN);
            return;
        }
        solidObject(ReevaluatingSolidObject.Entry.TOP);              // loc_205E7C: TopSolidObject
    }

    /** sub_205EF0 (:101-127): the player's centre inside width_2 by height, with unsigned compares. */
    private boolean inBox(AbstractPlayableSprite player) {
        int dx = (player.getCentreX() - x + width) & 0xFFFF;
        if ((dx & 0x8000) != 0 || dx >= width * 2) return false;
        int dy = (player.getCentreY() - y + yRadius) & 0xFFFF;
        return (dy & 0x8000) == 0 && dy < yRadius * 2;
    }

    /** HDoorObject_0_Routine4 (:83-85): AnimateObject; $FC advances to Routine6. */
    private void opening() {
        if (animator == null) {
            animator = new CdAnimator(artProvider().image(),
                    SharedObjectCatalogue.offset(cdLevel().payload(), "hDoorAnims"), 0);
        }
        int result = animator.step();
        mapFrame = animator.mapFrame();
        if (result == 2) routine += 2;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.H_DOOR + tileWord, mapFrame, hFlip(), vFlip());
    }

    public int routine() { return routine; }
}
