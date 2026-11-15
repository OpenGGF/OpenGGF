package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Stardust Speedway object registration fragment; owned by the Stardust Speedway object lane. An ID is the
 * zone's {@code ObjectIndex} slot plus one (the trace's {@code object_type}; the layout loader and the object
 * code's {@code move.b #id,obj.id} both count from one: the Kabasira tests for $22 at objects/r7/kabasira.asm:14
 * and sits in slot $21 of r7/object_index_1_2.asm). Acts 1 and 2 use r7/object_index_1_2.asm and act 3
 * r7/object_index_3.asm; the two agree on every ID registered here, and a class notes where one index leaves
 * its ID empty.
 */
public final class SszObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x27, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        // --- sszobja ---
        registrar.object(0x04, "Mesh", (spawn, r) -> new ObjMesh(spawn));
        registrar.object(0x08, "Booster", (spawn, r) -> new ObjBooster(spawn));
        registrar.object(0x09, "Sparks", (spawn, r) -> new ObjSparks(spawn));
        registrar.object(0x1D, "Path swap", (spawn, r) -> new ObjPathSwap(spawn));
        registrar.object(0x1E, "Spring launcher", (spawn, r) -> new ObjSpringLauncher(spawn));
        registrar.badnik(0x21, "Kanabun", (spawn, r) -> new ObjKanabun(spawn));
        registrar.badnik(0x22, "Kabasira", (spawn, r) -> new ObjKabasira(spawn));
        // Hotaru ($23) is the shared owner's (objects/shared/ObjHotaru), registered per stage there.
        registrar.object(0x26, "Jump spike ball", (spawn, r) -> new ObjJumpSpikeBall(spawn));
        registrar.object(0x35, "Swap platform", (spawn, r) -> new ObjSwapPlatform(spawn));
        registrar.object(0x36, "Lamp", (spawn, r) -> new ObjLamp(spawn));
        registrar.object(0x38, "Searchlight", (spawn, r) -> new ObjSearchlight(spawn));
        // --- end sszobja ---
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        SszObjectArt.register(art);
    }
}
