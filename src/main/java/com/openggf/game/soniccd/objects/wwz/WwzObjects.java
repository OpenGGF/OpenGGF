package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Object registration fragment of Wacky Workbench. IDs follow the zone's object indexes
 * (Level/Wacky Workbench/Object Index (Act 1 Present).asm:8-71; r6/object_index_1.asm to
 * object_index_3.asm for the other stages). Acts 2 and 3 leave some of these IDs to
 * {@code NullObject}: each class stands down there. The shared objects (platform, switch, tunnel path, door, crusher,
 * Poh-Bee, seesaw, tube door, spike chain, roll tunnel, horizontal door, tunnel splash, projector) and the boss
 * parts belong to other lanes.
 */
public final class WwzObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x2F, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        registrar.object(0x04, "Launcher", (spawn, r) -> new ObjLauncher(spawn));
        registrar.object(0x05, "Freezer", (spawn, r) -> new ObjFreezer(spawn));
        registrar.object(0x08, "Goddess statue", (spawn, r) -> new ObjGoddessStatue(spawn));
        registrar.object(0x20, "Piston", (spawn, r) -> new ObjPiston(spawn));
        registrar.object(0x21, "Electric beams", (spawn, r) -> new ObjElectricBeams(spawn));
        registrar.object(0x23, "Bouncing platform", (spawn, r) -> new ObjBouncePlatform(spawn));
        registrar.object(0x28, "Spinning platform", (spawn, r) -> new ObjSpinPlatform(spawn));
        registrar.object(0x2A, "Snake blocks", (spawn, r) -> new ObjSnakeBlocks(spawn));
        registrar.object(0x2B, "Eggman statue", (spawn, r) -> new ObjEggmanStatue(spawn));
        registrar.badnik(0x30, "Bata-pyon", (spawn, r) -> new ObjBataPyon(spawn));
        registrar.badnik(0x32, "Semi", (spawn, r) -> new ObjSemi(spawn));
        registrar.badnik(0x33, "Minomusi", (spawn, r) -> new ObjMinomusi(spawn));
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        WwzObjectArt.register(art);
    }
}
