package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * Mesh, object $03 ({@code MeshObject}, objects/r7/mesh.asm:3-32): a scenery sprite (tile $42F8, frame 0)
 * whose draw priority follows {@code stage_layer}: 4 on the first layer, 0 on the second
 * ({@code MeshObject_0_Routine2}, :25-32), so the path swap moves it behind or in front of the player.
 * The routine ends in {@code CheckObjectDespawn} (:9). Its sprite flags set only bit 2, so there is no
 * on-screen width ({@code oWidth} stays 0) and the despawn range is the chunk test alone.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjMesh extends SszObject implements ObjectVisualFacing.NonDirectional {
    public ObjMesh(ObjectSpawn spawn) {
        super(spawn, "Mesh");
    }

    @Override protected String sheetKind() { return SszObjectArt.MESH; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // MeshObject_0_Routine0 (:19-23)
            routine = 2;
            tileWord = SszObjectArt.MESH_TILE;
            initialised = true;
        }
        // MeshObject_0_Routine2 (:25-32): move.b #4,oPriority; tst.b stage_layer; beq; move.b #0,oPriority.
        priority = SszStageLayer.of(cdLevel()).isSet() ? 0 : 4;
    }
}
