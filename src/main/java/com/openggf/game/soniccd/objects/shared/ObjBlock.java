package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Block, Tidal Tempest and Stardust Speedway ({@code BlockObject}, objects/r4/block.asm:3-26;
 * objects/r7/block.asm:3-26): a still solid block. Both zones assemble one body; the priority
 * ($1 Tidal Tempest, $3 Stardust Speedway), tile word, size table and mappings are per-stage data
 * from {@link SharedObjectCatalogue}. The subtype selects the mapping frame and a size pair
 * (oXRadius = oWidth, oYRadius) from the stage's table (objects/r4/block.asm:11-20).
 *
 * <p>Collision Chaos's block (Collision Chaos/Objects/Block/Main.asm:31-214, a moving block with a
 * hidden-block partner) and Quartz Quadrant's ({@link ObjFallingBlock}) are other routines under
 * the same name.
 */
public final class ObjBlock extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private boolean initialised;
    private int mapFrame;

    public ObjBlock(ObjectSpawn spawn) {
        super(spawn, "Block");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        // loc_20EDAC: lea player_object,a1; jsr SolidObject (objects/r4/block.asm:22-24).
        solidObject(ReevaluatingSolidObject.Entry.SOLID);
    }

    /** BlockObject's init (objects/r4/block.asm:4-20); false until the stage's tables are readable. */
    private boolean init() {
        var level = cdLevel();
        var provider = artProvider();
        if (level == null || provider == null) return false;
        var payload = level.payload();
        priority = SharedObjectCatalogue.offset(payload, "blockPriority");
        tileWord = SharedObjectCatalogue.offset(payload, "blockTile");
        int subtype = getSpawn().subtype() & 0xFF;
        mapFrame = subtype;
        // add.w d0,d0; lea byte_20EDC2(pc,d0.w),a1; move.b (a1)+,d0; move.b (a1),d1.
        int sizes = SharedObjectCatalogue.offset(payload, "blockSizes") + subtype * 2;
        width = provider.image().u8(sizes);        // obj.width (oXRadius) and obj.width_2 (oWidth)
        yRadius = provider.image().u8(sizes + 1);  // obj.height (oYRadius)
        initialised = true;
        return true;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.BLOCK, mapFrame, hFlip(), vFlip());
    }

    public int mapFrame() { return mapFrame; }
}
