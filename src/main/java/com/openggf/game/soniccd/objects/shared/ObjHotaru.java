package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Stardust Speedway's Hotaru spawner ({@code HotaruObject} with a secondary subtype that is neither $FF nor
 * $FE, objects/r7/hotaru.asm:3-143): an invisible object that, once the player is within $F0 pixels on both
 * axes, spawns two fireflies ({@link ObjHotaruFirefly}) at plus and minus an offset from the table
 * {@code word_20E33A} (row {@code subtype & $C}), and then watches them: a firefly that dies sets its bit
 * in the object's state byte; when both are gone the spawner deletes itself. Metallic Madness's Hotaru
 * (objects/r8/hotaru.asm) is another routine. The sparks between two fireflies are {@link ObjHotaruSpark}.
 *
 * <p>Not modelled: {@code object_states} (the state byte persisting across the spawner's unload); a firefly
 * killed on an earlier visit comes back after the spawner has left the range.
 */
public final class ObjHotaru extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    /** The state byte bits 0 and 1: the plus and minus firefly were destroyed (:70-71, 83-84, 114-125). */
    private int state;
    private int childSubtype;
    private int childTile;
    private int offsetX;
    private int offsetY;
    ObjHotaruFirefly plus;
    ObjHotaruFirefly minus;
    private boolean checkedThisFrame;

    public ObjHotaru(ObjectSpawn spawn) {
        super(spawn, "Hotaru");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        checkedThisFrame = false;
        if (routine == 0 && !init()) return;
        switch (routine) {
            case 2 -> {                                                       // HotaruObject_2_Routine2 (:52-63)
                if (player instanceof AbstractPlayableSprite sprite && near(sprite, x, y, -0xF0, 0x1E0, -0xF0, 0x1E0)) {
                    routine = 4;
                }
                checkedThisFrame = true;                                      // jmp CheckObjectDespawn
            }
            case 4 -> spawnChildren();
            case 6 -> watchChildren();
            default -> { }
        }
    }

    /** HotaruObject_2_Routine0 (:23-50); it falls into Routine2 on the same frame. */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        var payload = level.payload();
        routine = 2;
        int row = SharedObjectCatalogue.offset(payload, "hotaruOffsets") + (getSpawn().subtype() & 0xC);
        offsetX = (short) provider.image().u16(row);
        offsetY = (short) provider.image().u16(row + 2);
        childSubtype = (getSpawn().subtype() & 1) != 0 ? 0xFF : 0;           // btst #0,obj.subtype(a0); sne d0 (:41-43)
        childTile = SharedObjectCatalogue.offset(payload, act() == 2 ? "hotaruTileAct3" : "hotaruTile");
        tileWord = childTile;
        return true;
    }

    /** HotaruObject_2_Routine4 (:67-93): the plus child, then the minus child, unless their bits are set. */
    private void spawnChildren() {
        routine = 6;
        var objects = services().objectManager();
        if (objects == null) return;
        if ((state & 1) == 0) {
            plus = new ObjHotaruFirefly(new ObjectSpawn(x + offsetX, y + offsetY, getSpawn().objectId(), childSubtype, 0, false, 0),
                    this, childTile);
            objects.addDynamicObjectAfterCurrent(plus);
        }
        if ((state & 2) == 0) {
            minus = new ObjHotaruFirefly(new ObjectSpawn(x - offsetX, y - offsetY, getSpawn().objectId(), childSubtype, 0, false, 0),
                    this, childTile);
            objects.addDynamicObjectAfterCurrent(minus);
        }
    }

    /**
     * HotaruObject_2_Routine6 (:113-143): a child whose slot no longer holds object $23 while the pointer is set was
     * destroyed and sets its bit; a child that despawned by range cleared the pointer itself (loc_20E0AA, :417-425).
     * Both bits set ({@code cmpi.b #$83,(a6)}) deletes the spawner with its loaded bit kept (HotaruObject_1_Routine4,
     * DeleteObject); no pointer left despawns it (loc_20E1F6, DespawnObject), so its entry may spawn again.
     */
    private void watchChildren() {
        if (plus != null && (plus.isDestroyed() || plus.gone())) {
            if (plus.killed()) state |= 1;
            plus = null;
        }
        if (minus != null && (minus.isDestroyed() || minus.gone())) {
            if (minus.killed()) state |= 2;
            minus = null;
        }
        if (state == 3) {
            setDestroyed(true);
        } else if (plus == null && minus == null) {
            com.openggf.level.objects.ObjectLifetimeOps.destroyRespawnableOffscreen(this);
        }
    }

    /**
     * sub_20E202 (:560-572): the player's centre inside a box, each axis by {@code sub.w d1; sub.w d2; bcc}
     * (an unsigned compare of the offset against the extent).
     */
    static boolean near(AbstractPlayableSprite player, int ox, int oy, int d1, int d2, int d3, int d4) {
        int dy = (player.getCentreY() - oy - d1) & 0xFFFF;
        if (dy >= (d2 & 0xFFFF)) return false;
        int dx = (player.getCentreX() - ox - d3) & 0xFFFF;
        return dx < (d4 & 0xFFFF);
    }

    /** The spawner is never drawn, so its on-screen bit stays clear (no DrawObject in Routine0 to Routine6). */
    @Override protected boolean renderFlagOnScreen() { return false; }

    /** CheckObjectDespawn runs only from Routine2 (:62-63). */
    @Override protected boolean despawnsByCheckObjDespawn() { return checkedThisFrame; }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    public int routine() { return routine; }
}
