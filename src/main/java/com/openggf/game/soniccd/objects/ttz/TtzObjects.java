package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Tidal Tempest object registration fragment. IDs are the ObjectIndex position plus one
 * (RunObjects: {@code movea.l -4(a1,d0.w),a1}, Level/Object Functions.asm:21-24) of the stage's
 * {@code r4/object_index_*.asm}; every Tidal Tempest routine keeps one ID across the stages that link it.
 */
public final class TtzObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x52, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        registrar.object(0x04, "Water surface", (spawn, r) -> new ObjWaterSurface(spawn));
        for (int id = 0x0B; id <= 0x0E; id++) {
            registrar.object(id, "Splash", (spawn, r) -> new ObjSplash(spawn));
        }
        // $20: a layout entry with subtype $80 or more is a vent; smaller subtypes are the bubbles it makes.
        registrar.object(0x20, "Bubble spawner", (spawn, r) -> (spawn.subtype() & 0x80) != 0
                ? new ObjBubbleSpawner(spawn) : new ObjBubble(spawn, false, 0));
        registrar.object(0x26, "Elevator", (spawn, r) -> new ObjElevator(spawn));
        registrar.object(0x25, "Big door", (spawn, r) -> new ObjBigDoor(spawn));
        registrar.object(0x2A, "Geyser", (spawn, r) -> new ObjGeyser(spawn));
        registrar.object(0x2C, "Amenbo", (spawn, r) -> new ObjAmenbo(spawn));
        registrar.object(0x2B, "Tonbo", (spawn, r) -> new ObjTonbo(spawn));
        registrar.object(0x2F, "Animal", (spawn, r) -> new TtzAnimal(spawn));
        registrar.object(0x2D, "Taga-Taga", (spawn, r) -> new ObjTagaTaga(spawn));
        registrar.object(0x2E, "Yago", (spawn, r) -> new ObjYago(spawn));
        registrar.object(0x32, "Fall spikes", (spawn, r) -> new ObjFallSpikes(spawn));
        registrar.object(0x33, "Crush blocks", (spawn, r) -> new ObjCrushBlocks(spawn));
        registrar.object(0x35, "Fan", (spawn, r) -> new ObjFan(spawn));
        registrar.object(0x36, "Mace", (spawn, r) -> new ObjMace(spawn));
        registrar.object(0x38, "Circle spike ball", (spawn, r) -> new ObjCircleSpikeBall(spawn));
        registrar.object(0x3E, "Platform chain", (spawn, r) -> new ObjPlatformChain(spawn));
        registrar.object(0x3D, "Rotate block", (spawn, r) -> new ObjRotateBlock(spawn));
        registrar.object(0x48, "Twin move block", (spawn, r) -> new ObjTwinMoveBlock(spawn));
        registrar.object(0x49, "Pressure elevator", (spawn, r) -> new ObjPressureElevator(spawn));
        registrar.object(0x42, "Shooter", (spawn, r) -> new ObjShooter(spawn));
        // Switch and door: $27 and $28 of the act 1 and 2 indexes (r4/object_index_1a.asm:35-36, _2a, _2c_2d); the act 3 index
        // (object_index_3.asm) nulls both, and the b/c/d past and future indexes null the door.
        registrar.object(0x27, "Switch", p -> p.stage().actNumber() != 3 && TtzObjectCatalogue.offset(p, "switchMappings") >= 0,
                (spawn, r) -> new TtzSwitch(spawn));
        // $3F is the prize switch in the R41C and R41D index (r4/object_index_1c_1d.asm); $40 and $41 are the prizes it makes.
        registrar.object(0x3F, "Prize switch", p -> TtzObjectCatalogue.offset(p, "prizeSwitchMappings") >= 0,
                (spawn, r) -> new ObjPrizeSwitch(spawn));
        // $22 is the spikes in the act 1 and 2 indexes (r4/object_index_1a.asm and the others); the act 3 index nulls it.
        registrar.object(0x22, "Spikes", p -> p.stage().actNumber() != 3 && TtzObjectCatalogue.offset(p, "spikesMappings") >= 0,
                (spawn, r) -> new ObjSpikes(spawn));
        // $44 is the wood block in the R42A index only (r4/object_index_2a.asm); the other indexes null it.
        registrar.object(0x44, "Wood block", p -> TtzObjectCatalogue.offset(p, "woodBlockMappings") >= 0,
                (spawn, r) -> new ObjWoodBlock(spawn));
        registrar.object(0x28, "Door", p -> p.stage().actNumber() != 3 && TtzObjectCatalogue.offset(p, "doorMappings") >= 0,
                (spawn, r) -> new TtzDoor(spawn));
        // $24 and $39: the r4 horizontal move blocks and collapse floor, where the stage image holds their code.
        registrar.object(0x24, "Horizontal move block", p -> TtzObjectCatalogue.offset(p, "hMoveMappings1") >= 0
                || TtzObjectCatalogue.offset(p, "hMoveMappings2") >= 0 || TtzObjectCatalogue.offset(p, "hMoveMappings3") >= 0,
                (spawn, r) -> new TtzHMoveBlock(spawn));
        registrar.object(0x39, "Collapse floor", p -> TtzObjectCatalogue.offset(p, "collapseFloorMappings") >= 0,
                (spawn, r) -> new TtzCollapseFloor(spawn));
        registrar.object(0x45, "Move wall", (spawn, r) -> new ObjMoveWall(spawn));
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        TtzObjectArt.register(art);
    }
}
