package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Collision Chaos object registration fragment. IDs follow the zone's object indexes (Collision Chaos/Object Index
 * (Act 1 Present).asm:12-71; the other acts differ only where noted on each class: r3/object_index_1b_1c_1d.asm,
 * object_index_2.asm and object_index_3.asm).
 */
public final class CczObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x38, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        registrar.object(0x1D, "Bumper", (spawn, r) -> new CczBumper(spawn));
        registrar.object(0x1E, "Flipper", (spawn, r) -> new CczFlipperObject(spawn));
        registrar.object(0x20, "Spikes", (spawn, r) -> new CczSpikes(spawn));
        registrar.object(0x21, "Block", (spawn, r) -> new CczBlock(spawn));
        registrar.object(0x22, "Hidden block", (spawn, r) -> new CczHiddenBlock(spawn));
        registrar.object(0x23, "Metal platform", (spawn, r) -> new CczMetalPlatform(spawn));
        registrar.object(0x28, "Drum platform", (spawn, r) -> new CczDrumPlatform(spawn));
        // $30-$34 are the Act 1 Present intro; the other acts' indexes null or reuse them (Act 3: boss parts).
        registrar.badnik(0x24, "KamaKama", (spawn, r) -> new CczKamaKama(spawn));
        registrar.object(0x26, "Door", (spawn, r) -> new CczDoor(spawn));
        registrar.object(0x27, "Fire shooter", (spawn, r) -> CczFireShooter.create(spawn));
        registrar.object(0x29, "Glass shatter", (spawn, r) -> new CczGlassShatter(spawn));
        registrar.object(0x2A, "Bumper platform", (spawn, r) -> new CczBumperPlatform(spawn));
        registrar.object(0x2B, "Teleporter", (spawn, r) -> new CczTeleporter(spawn));
        registrar.badnik(0x2D, "Ga", (spawn, r) -> CczGa.isParent(spawn.subtype()) ? new CczGa(spawn) : new CczGa.Dust(spawn));
        registrar.badnik(0x2E, "Tentou", (spawn, r) -> CczTentou.isParent(spawn.subtype()) ? new CczTentou(spawn) : new CczTentou.Bomb(spawn));
        registrar.object(0x2F, "Pocket", (spawn, r) -> new CczPocket(spawn));
        registrar.object(0x30, "Intro spikes", (spawn, r) -> new CczIntroSpikes(spawn));
        registrar.object(0x32, "Amy Rose", (spawn, r) -> new CczAmyRose(spawn));
        registrar.object(0x33, "Amy heart", (spawn, r) -> new CczAmyRose.Heart(spawn));
        registrar.object(0x31, "Metal Sonic", (spawn, r) -> new CczMetalSonic(spawn));
        // $39 is Collision Chaos' own animal (Animal/Main.asm), not the common ObjAnimal.
        registrar.object(0x39, "Animal", (spawn, r) -> new CczAnimal(spawn));
        // $13 is the checkpoint in Act 1 Present and ObjNull in the other indexes (ObjNull, Collision Chaos/Object Index (Act 1 Variant).asm:71-73).
        registrar.object(0x13, "Null", p -> com.openggf.game.soniccd.objects.art.CdObjectCatalogue.entry(p).offset("checkpointId") != 0x13,
                (spawn, r) -> new com.openggf.game.soniccd.objects.common.CdNullObject(spawn));
        // $36 is ObjNull outside Act 3 (r3/object_index_3.asm:57); the drain stands down there.
        registrar.object(0x36, "Drain", (spawn, r) -> CczDrain.create(spawn));
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        CczObjectArt.register(art);
    }
}
