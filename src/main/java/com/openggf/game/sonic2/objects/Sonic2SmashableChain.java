package com.openggf.game.sonic2.objects;

import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.level.objects.*;

/** Obj2F / Obj32 share the badnik word, including the save across SolidObject. */
final class Sonic2SmashableChain {
    private Sonic2SmashableChain() { }
    static BadnikChainPolicy policy(ObjectServices services) {
        var host = java.util.Objects.requireNonNull(services.gameModule(), "S2 smashable host");
        return java.util.Objects.requireNonNull(host.getGameService(BadnikChainPolicy.class), "S2 shared chain");
    }
    static java.util.List<com.openggf.game.PlayableEntity> players(ObjectServices services) {
        return services.playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED, 0, 0);
    }
    static void release(ObjectServices services, com.openggf.sprites.playable.AbstractPlayableSprite player, boolean roll, boolean bounce) {
        // Obj32_SetCharacterOffBlock (s2.asm:49402-49416) / loc_2343E (s2.asm:49241-49260).
        if (roll) {
            short y = player.getCentreY();
            player.setRolling(true);
            com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, y);
            if (bounce) player.setYSpeed((short)-0x300);
        }
        player.setAir(true);
        player.setOnObject(false);
        services.objectManager().clearRidingObject(player);
    }
    static void award(ObjectServices services, int x, int y) {
        var manager = java.util.Objects.requireNonNull(services.objectManager(), "S2 smashable allocator");
        // SmashableObject_LoadPoints (s2.asm:49465-49487): failed AllocateObject
        // returns BEFORE changing the shared chain or awarding score.
        // KiS2 SmashableObject_LoadPoints (docs/kis2disasm/s2.asm:52696-52723) matches.
        int slot = manager.allocateDynamicSlot();
        if (slot < 0) return;
        int points = policy(services).incrementAndAward();
        var popup = ObjectConstructionContext.with(services, slot, () -> new PointsObjectInstance(
                new ObjectSpawn(x,y,0x29,0,0,false,0), services, points));
        ObjectLifetimeOps.addDynamicAtReservedSlot(manager, popup, slot);
        // This routine calls AddPoints, not AddPoints2, even for a P2 smash.
        var roster = com.openggf.game.competition.NativeCompetitionRoster.from(services.worldSession());
        (roster != null ? roster.gameState(0) : services.gameState()).addScore(points);
    }
}
