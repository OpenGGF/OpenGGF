package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Metallic Madness object registration fragment. IDs are ObjectIndex slot + 1; the zone has three indexes
 * ({@code r8/object_index_1.asm} for R81x, {@code _2} for R82x, {@code _3} for R83x, selected by
 * {@code R8_VARIANT} in {@code Level/Metallic Madness/R8 Shared.asm:114-122}), so an ID that names different
 * routines per index is resolved against the loaded stage.
 */
public final class MmzObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // --- mmz objects ---
        registrar.object(0x38, "HV platform", (spawn, r) -> new MmzHvPlatform(spawn));
        registrar.badnik(0x24, "Mecha-Bu", (spawn, r) -> new MmzMechaBu(spawn));
        registrar.object(0x27, "Buzzsaw", (spawn, r) -> new MmzBuzzsaw(spawn));
        registrar.object(0x2A, "Spin platform", (spawn, r) -> new MmzSpinPlatform(spawn));
        registrar.object(0x0F, "Moving spring", (spawn, r) -> new MmzMoveSpring(spawn));
        registrar.object(0x36, "Spikes", (spawn, r) -> new MmzSpikes(spawn));
        registrar.object(0x37, "Trap door", (spawn, r) -> new MmzTrapDoor(spawn));
        registrar.object(0x30, "Trap door (removed)", (spawn, r) -> new MmzTrapDoor(spawn));
        registrar.object(0x28, "Propeller", (spawn, r) -> new MmzPropeller(spawn));
        registrar.object(0x3E, "Revolve door", (spawn, r) -> new MmzRevolveDoor(spawn));
        // R82x's ObjectIndex starts the laser at $1D (r8/object_index_2.asm:32); R83x's starts the bumper there
        // (r8/object_index_3.asm:32); R81x indexes neither, so $1D is scoped to the stages with laser or bumper data.
        // The bumper shares the ID (r8/object_index_3.asm:32), so one factory picks by the stage's art.
        registrar.object(0x1D, "Laser / bumper", p -> MmzLaser.indexedIn(p) || MmzBumper.indexedIn(p),
                (spawn, r) -> MmzBumper.indexedInLoadedStage() ? new MmzBumper(spawn) : new MmzLaser(spawn));
        registrar.object(0x22, "Spin disc", (spawn, r) -> new MmzSpinDisc(spawn));
        registrar.object(0x2C, "Vanish platform", (spawn, r) -> new MmzVanishPlatform(spawn));
        registrar.object(0x2B, "Spike wall", (spawn, r) -> new MmzSpikeWall(spawn));
        // Bigbom, Scarab, twin walker and spike crusher are in one index each ($23 R82x, $2F R81x, $26 R82x, $33 R82x/R83x):
        // elsewhere the ID is a null object, so they are scoped to the stages that link their art.
        registrar.object(0x23, "Bigbom", p -> MmzObjectCatalogue.offset(p, "bigbomMappings") >= 0, (spawn, r) -> new MmzBigbom(spawn));
        registrar.object(0x2F, "Scarab", p -> MmzObjectCatalogue.offset(p, "scarabMappings") >= 0, (spawn, r) -> new MmzScarab(spawn));
        registrar.object(0x26, "Twin walker", p -> MmzObjectCatalogue.offset(p, "twinWalkerMappings") >= 0, (spawn, r) -> new MmzTwinWalker(spawn));
        registrar.object(0x33, "Spike crusher", p -> MmzObjectCatalogue.offset(p, "spikeCrusherMappings") >= 0, (spawn, r) -> new MmzSpikeCrusher(spawn));
        // Collapse floor: $25 in all three indexes (r8/object_index_1.asm:40, _2, _3 likewise); the shared collapse floor is
        // Quartz Quadrant's other routine.
        registrar.object(0x25, "Collapse floor", (spawn, r) -> new MmzCollapseFloor(spawn));
        // Door: $29 in all three indexes (r8/object_index_1.asm:44, _2, _3 likewise); the boss stage reuses it.
        registrar.object(MmzDoor.ID, "Door", (spawn, r) -> new MmzDoor(spawn));
        registrar.object(0x09, "Invisible block", (spawn, r) -> new MmzInvisibleBlock(spawn));
        // $31 is NullObject in all three indexes (r8/object_index_1.asm:52; _2 and _3 likewise); R81x places two of them.
        registrar.object(0x31, "Null", (spawn, r) -> new com.openggf.game.soniccd.objects.common.CdNullObject(spawn));
        // --- end mmz objects ---
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        MmzObjectArt.register(art);
    }
}
