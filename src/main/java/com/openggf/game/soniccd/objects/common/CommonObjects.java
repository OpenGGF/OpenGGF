package com.openggf.game.soniccd.objects.common;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * Objects common to every stage. IDs follow the Level-dialect object index
 * (Palmtree Panic/Object Index (Act 1 Present).asm:8-71). The power-up ($03) is the shared
 * shield and trail-star owner with CD art; a zone fragment that registers
 * the same ID overrides the common entry for its stages. Spikes are placed only at the ID the
 * stage's ObjectIndex gives ObjSpikes (object catalogue column {@code spikesId}).
 */
public final class CommonObjects implements CdObjectRegistration {
    @Override
    public void register(Registrar registrar) {
        registrar.object(0x0A, "Spring", (spawn, registry) -> new CdSpring(spawn));
        registrar.object(0x10, "Ring", (spawn, registry) -> new CdRing(spawn));
        registrar.object(0x18, "Explosion", (spawn, registry) -> new CdExplosion(spawn));
        // --- tt ---
        // ObjMonitorTimePost: subtypes 0-7 are monitors, 8 and up time posts and the HUD time icon
        // (ObjTimePostTimeIcon, Monitor and Time Post/Main.asm:64-71).
        registrar.object(0x19, "Monitor", (spawn, registry) -> spawn.subtype() < CdTimePost.PAST_SUBTYPE
                ? new CdMonitor(spawn) : CdTimePost.create(spawn));
        // --- tt ---
        registrar.object(0x1A, "Monitor item", (spawn, registry) -> new CdMonitorItem(spawn));
        registrar.object(0x1C, "HUD/Points", (spawn, registry) -> new CdPoints(spawn));
        registrar.object(0x3C, "Title card", (spawn, registry) -> CdTitleCard.parent());
        // --- common2 ---
        // These routines' IDs come from each stage's ObjectIndex (catalogue *Id columns): some zones
        // place them elsewhere (the robot generator) or use their IDs for other objects.
        registrar.stageObject("testBadnikId", "Test badnik", (spawn, registry) -> new CdTestBadnik(spawn));
        registrar.stageObject("floatBlockId", "Floating block", (spawn, registry) -> new CdFloatBlock(spawn));
        registrar.stageObject("checkpointId", "Checkpoint", (spawn, registry) -> new CdCheckpoint(spawn));
        registrar.stageObject("bigRingId", "Big ring", (spawn, registry) -> new CdBigRing(spawn));
        registrar.stageObject("goalPostId", "Goal post", (spawn, registry) -> new CdGoalPost(spawn));
        registrar.stageObject("signpostId", "Signpost", (spawn, registry) -> new CdSignpost(spawn));
        registrar.stageObject("boulderId", "Boulder", (spawn, registry) -> new CdBoulder(spawn));
        registrar.stageObject("flowerId", "Flower", (spawn, registry) -> new CdFlower(spawn));
        registrar.stageObject("animalId", "Animal", (spawn, registry) -> new CdAnimal(spawn));
        registrar.stageObject("robotGeneratorId", "Robot generator", (spawn, registry) -> new CdRobotGenerator(spawn));
        registrar.stageObject("resultsId", "Results", (spawn, registry) -> new CdResults(spawn));
        registrar.stageObject("gameOverId", "Game over", (spawn, registry) -> new CdGameOver(spawn));
        // Level-dialect ObjSpikes is $26 only where the stage's ObjectIndex starts it (Palmtree Panic); $26 is
        // the door in Collision Chaos, the elevator in Tidal Tempest and the jump spike ball in Stardust Speedway.
        registrar.stageObject("spikesId", "Spikes", (spawn, registry) -> new CdSpikes(spawn));
        // --- end common2 ---
    }

    @Override
    public void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException {
        CommonObjectArt.register(art);
    }
}
