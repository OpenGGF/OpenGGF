package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.CdObjectRegistration;
import com.openggf.game.soniccd.objects.common.CdNullObject;

/**
 * Palmtree Panic object registration fragment; owned by the lane that implements these objects.
 * IDs follow the zone's object indexes (Palmtree Panic/Object Index (Act 1 Present).asm:8-71; the other
 * periods and acts differ only where noted on each class).
 */
public final class PpzObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Metal Sonic's projector (ObjProjector), in the stages whose index starts it (acts 1 and 2; the stage's own code is the test).
        registrar.object(0x2E, "Projector", com.openggf.game.soniccd.objects.projector.ProjectorObjectCatalogue::indexedIn,
                (spawn, r) -> new com.openggf.game.soniccd.objects.projector.CdProjector(spawn));
        // --- ppzobja ---
        registrar.object(0x04, "Waterfall", (spawn, r) -> new ObjWaterfall(spawn));
        // ObjBoulder is $1B in every PPZ index; acts 1/2 load its art through section PLCs.
        registrar.object(0x1B, "Boulder", (spawn, r) -> new PpzBoulder(spawn));
        // $22 is Tamabboh with its missiles as subtype 1 (Tamabboh/Main.asm:8-9).
        registrar.badnik(0x22, "Tamabboh", (spawn, r) -> spawn.subtype() == 1
                ? new ObjTamabbohMissile(spawn) : new ObjTamabboh(spawn));
        registrar.object(0x2A, "3D ramp", (spawn, r) -> new Obj3DRamp(spawn));
        registrar.object(0x2C, "3D plant", (spawn, r) -> new Obj3DPlant(spawn));
        registrar.object(0x32, "Scenery", (spawn, r) -> new ObjScenery(spawn));
        registrar.object(0x33, "Log shadow", (spawn, r) -> new ObjLogShadow(spawn));
        registrar.badnik(0x3D, "Mosqui", (spawn, r) -> new ObjMosqui(spawn));
        registrar.badnik(0x3E, "Pata-Bata", (spawn, r) -> new ObjPataBata(spawn));
        registrar.badnik(0x3F, "Anton", (spawn, r) -> new ObjAnton(spawn));
        registrar.badnik(0x40, "Taga-Taga", (spawn, r) -> new ObjTagaTaga(spawn));
        // --- end ppzobja ---
        // --- ppzobjb ---
        // $05, $08 and $23 are bare rts routines in every Palmtree Panic index (Effects/Main.asm:64-72, Missile/Main.asm:7-10).
        registrar.object(0x05, "Earthquake", (spawn, r) -> new PpzBlankObject(spawn, true));
        registrar.object(0x08, "Earthquake setup", (spawn, r) -> new PpzBlankObject(spawn, true));
        registrar.object(0x07, "Tunnel path", (spawn, r) -> new PpzTunnelPath(spawn));
        registrar.object(0x09, "Spinning disc", (spawn, r) -> new ObjSpinningDisc(spawn));
        registrar.object(0x21, "Platform", (spawn, r) -> new PpzPlatform(spawn));
        registrar.object(0x20, "Collapsing platform", (spawn, r) -> new ObjCollapsePlatform(spawn));
        registrar.object(0x23, "Missile", (spawn, r) -> new PpzBlankObject(spawn, false));
        registrar.object(0x28, "Springboard", (spawn, r) -> new ObjSpringBoard(spawn));
        registrar.object(0x31, "Sonic hole", (spawn, r) -> new ObjSonicHole(spawn));
        registrar.object(0x35, "Vertical tunnel door", p -> PpzObjectCatalogue.tunnelDoorMappings(p) >= 0,
                (spawn, r) -> new PpzTunnelDoor(spawn));
        registrar.object(0x36, "Breakable wall", (spawn, r) -> new ObjBreakableWall(spawn));
        // --- end ppzobjb ---
        // --- ppz: routes A2 ---
        // $29 is ObjSwing in the act 2 index (Palmtree Panic/Object Index (Act 2 Present).asm:48); the other indexes do not list it.
        registrar.object(0x29, "Swing", p -> PpzObjectCatalogue.entry(p).swingMappings() >= 0,
                (spawn, r) -> new PpzSwing(spawn));
        // --- end ppz: routes A2 ---
        // $13 is the checkpoint in Present and ObjNull in the other periods.
        // ObjectIndex (Level/Palmtree Panic/Object Index (Act 1 Past).asm:26).
        registrar.object(0x13, "Null", p -> com.openggf.game.soniccd.objects.art.CdObjectCatalogue.entry(p).offset("checkpointId") != 0x13,
                (spawn, r) -> new CdNullObject(spawn));
        // ObjectIndex (Level/Palmtree Panic/Object Index (Act 1 Past).asm:54) uses ObjNull at $2F.
        // ObjNull (Level/Palmtree Panic/Object Index (Act 1 Past).asm:77-79) clears the slot.
        // In Act 3 the boss spawns its own shoulder at this ID (ObjectIndex,
        // Level/Palmtree Panic/Object Index (Act 3).asm:54).
        registrar.object(0x2F, "Amy Rose", (spawn, r) ->
                GameServices.level().getCurrentLevel() instanceof CdLevel level && level.payload() == CdStagePayload.R11A
                        ? new PpzAmyRose(spawn)
                        : new CdNullObject(spawn));
        // ObjectIndex (Level/Palmtree Panic/Object Index (Act 1 Present).asm:54-55).
        registrar.object(0x30, "Amy heart", p -> PpzObjectCatalogue.entry(p).amyMappings() >= 0,
                (spawn, r) -> new PpzAmyRose.Heart(spawn));
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        PpzObjectArt.register(art);
    }
}
