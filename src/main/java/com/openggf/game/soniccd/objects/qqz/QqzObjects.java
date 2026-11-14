package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Quartz Quadrant object registration fragment; owned by the Quartz Quadrant object lane. IDs follow the zone's
 * object indexes (r5/object_index_1_2.asm: the ID is the table position plus one; Act 3 links
 * r5/object_index_3.asm, where $20-$23 are the boss's and the other IDs below are {@code ObjNull}, so these
 * objects stand down or delete themselves there). Objects the zone shares with others (platform, collapse
 * floor, swing, tunnel path, block, roll tunnel, doors, projector, spikes) belong to the shared lane; the
 * boss and its parts to the boss lane.
 */
public final class QqzObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x2C, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        // --- qqz ---
        registrar.badnik(0x20, "Noro Noro", (spawn, r) -> new ObjNoroNoro(spawn));
        registrar.badnik(0x21, "Sasori", (spawn, r) -> new ObjSasori(spawn));
        registrar.badnik(0x22, "Kemusi", (spawn, r) -> new ObjKemusi(spawn));
        registrar.badnik(0x23, "Kumo Kumo", (spawn, r) -> new ObjKumoKumo(spawn));
        registrar.object(0x25, "Stalactite", (spawn, r) -> new ObjStalactite(spawn));
        registrar.object(0x27, "Circle platform", (spawn, r) -> new ObjCirclePlatform(spawn));
        // $2A is the swing in the act 1 and 2 index (r5/object_index_1_2.asm) and a null object in act 3.
        registrar.object(0x2A, "Swing", p -> QqzObjectCatalogue.offset(p, "swingMappings") >= 0, (spawn, r) -> new ObjSwing(spawn));
        registrar.object(0x29, "Sine platform", (spawn, r) -> new ObjSinePlatform(spawn));
        registrar.object(0x2B, "Large break wall", (spawn, r) -> new ObjLargeBreakWall(spawn));
        registrar.object(0x2F, "Bridge", (spawn, r) -> new ObjBridge(spawn));
        registrar.object(0x31, "Conveyor switch", (spawn, r) -> new ObjConveyorSwitch(spawn));
        registrar.object(0x32, "Break wall", (spawn, r) -> new ObjBreakWall(spawn));
        // --- end qqz ---
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        QqzObjectArt.register(art);
    }
}
