package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Objects several zones assemble, but not every zone. Each routine's object ID differs by stage:
 * {@code stageObject} names the shared catalogue column ({@code <routine>Id}) that gives, per
 * stage, the ObjectIndex slot starting that routine. The registry resolves an ID as the stage's
 * zone fragment, then this fragment, then the common fragment, so a zone object at the same ID
 * wins and a stage that does not index the routine never sees it.
 */
public final class SharedObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        // Roll tunnel; Palmtree Panic's and Wacky Workbench's spin tunnel tag is the same body.
        registrar.stageObject("rollTunnelId", "Roll tunnel", (spawn, r) -> new ObjRollTunnel(spawn));
        registrar.stageObject("blockId", "Block", (spawn, r) -> new ObjBlock(spawn));
        // Quartz Quadrant's block: another routine under the same name; its pieces share the ID.
        registrar.stageObject("fallingBlockId", "Falling block", (spawn, r) -> new ObjFallingBlock(spawn));
        // Metallic Madness's crusher; Wacky Workbench's is another routine under the same name.
        registrar.stageObject("crusherId", "Crusher", (spawn, r) -> new ObjCrusher(spawn));
        // Quartz Quadrant's collapse floor; Tidal Tempest's and Metallic Madness's differ in instructions.
        registrar.stageObject("collapseFloorId", "Collapse floor", (spawn, r) -> new ObjCollapseFloor(spawn));
        // Quartz Quadrant's spikes: another routine than the Level dialect's ObjSpikes (common $26).
        registrar.stageObject("qqzSpikesId", "Spikes", (spawn, r) -> new ObjQqzSpikes(spawn));
        // Switch: Wacky Workbench's form and Metallic Madness's act-selected tile form are one body.
        registrar.stageObject("switchId", "Switch", (spawn, r) -> new ObjSwitch(spawn));
        registrar.stageObject("switchByActId", "Switch", (spawn, r) -> new ObjSwitch(spawn));
        registrar.stageObject("wwzDoorId", "Door", (spawn, r) -> new ObjWwzDoor(spawn));
        registrar.stageObject("wwzPlatformId", "Platform", (spawn, r) -> new ObjWwzPlatform(spawn));
        // Wacky Workbench's Poh-Bee; Metallic Madness's is another routine under the same name.
        registrar.stageObject("wwzPohBeeId", "Poh-Bee", (spawn, r) -> new ObjWwzPohBee(spawn));
        // Horizontal door, its splash and the tunnel splash: one body in every zone but Tidal Tempest.
        registrar.stageObject("hDoorId", "H door", (spawn, r) -> new ObjHDoor(spawn));
        registrar.stageObject("hDoorSplashId", "H door splash", (spawn, r) -> new ObjHDoorSplash(spawn));
        registrar.stageObject("tunnelSplashId", "Tunnel splash", (spawn, r) -> new ObjTunnelSplash(spawn));
        // Wacky Workbench's spike chain; Collision Chaos's is another routine under the same name.
        registrar.stageObject("spikeChainId", "Spike chain", (spawn, r) -> new ObjSpikeChain(spawn));
        // Wacky Workbench's tube door; Metallic Madness's is another routine under the same name.
        registrar.stageObject("wwzTubeDoorId", "Tube door", (spawn, r) -> new ObjWwzTubeDoor(spawn));
        // Moving spring: one body in every zone but Metallic Madness (MoveSpringObjectR8 is another routine).
        registrar.stageObject("movingSpringId", "Moving spring", (spawn, r) -> new ObjMovingSpring(spawn));
        // Stardust Speedway's swing; Palmtree Panic's, Tidal Tempest's and Quartz Quadrant's are other routines.
        registrar.stageObject("swingId", "Swing", (spawn, r) -> new ObjSwing(spawn));
        // Stardust Speedway's Hotaru spawner; its fireflies and sparks are made by it. Metallic Madness's is another routine.
        registrar.stageObject("hotaruId", "Hotaru", (spawn, r) -> new ObjHotaru(spawn));
        // Tunnel path: Quartz Quadrant's and Wacky Workbench's are two routines under one name (Palmtree Panic's and
        // Metallic Madness's are others again).
        registrar.stageObject("qqzTunnelPathId", "Tunnel path", (spawn, r) -> new ObjQqzTunnelPath(spawn));
        registrar.stageObject("wwzTunnelPathId", "Tunnel path", (spawn, r) -> new ObjWwzTunnelPath(spawn));
        registrar.stageObject("mmzTunnelPathId", "Tunnel path", (spawn, r) -> new ObjMmzTunnelPath(spawn));
        // Wacky Workbench's crusher (acts 2 and 3); Metallic Madness's is the crusherId routine above.
        registrar.stageObject("wwzCrusherId", "Crusher", (spawn, r) -> new ObjWwzCrusher(spawn));
        // Metallic Madness's tube door; Wacky Workbench's is the wwzTubeDoorId routine above.
        registrar.stageObject("mmzTubeDoorId", "Tube door", (spawn, r) -> new ObjMmzTubeDoor(spawn));
        // Dango: Stardust Speedway's body (Metallic Madness assembles the same one).
        registrar.stageObject("dangoId", "Dango", (spawn, r) -> new ObjDango(spawn));
        registrar.stageObject("mmzDangoId", "Dango", (spawn, r) -> new ObjDango(spawn));
        // Quartz Quadrant's platform; Wacky Workbench's is the wwzPlatformId routine above.
        registrar.stageObject("qqzPlatformId", "Platform", (spawn, r) -> new ObjQqzPlatform(spawn));
        // Wacky Workbench's seesaw; Metallic Madness's is another routine under the same name.
        registrar.stageObject("wwzSeesawId", "Seesaw", (spawn, r) -> new ObjWwzSeesaw(spawn));
        // Metallic Madness's seesaw; Wacky Workbench's is the wwzSeesawId routine above.
        registrar.stageObject("mmzSeesawId", "Seesaw", (spawn, r) -> new ObjMmzSeesaw(spawn));
        // Stardust Speedway's spike ball; Tidal Tempest's is a static one.
        registrar.stageObject("spikeBallId", "Spike ball", (spawn, r) -> new ObjSpikeBall(spawn));
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        SharedObjectArt.register(art);
    }
}
