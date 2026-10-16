package com.openggf.game.sonic3k.sidekick;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;

/**
 * SpawnLevelMainSprites loc_68C2/loc_68D8 and Obj_Tails loc_13A10: one
 * native carry-in participant for CNZ1 and locked-on MHZ1. Configured
 * participants retain their identity, donor resources and ordinary chain.
 */
public final class Sonic3kIntroCarryParticipants {
    private static final String CODE = "tails_cnz_carry";
    private Sonic3kIntroCarryParticipants() { }

    static AbstractPlayableSprite storyCarrier(SpriteManager sprites) {
        var players = sprites.getRegisteredSidekicks();
        var configuredP2 = players.stream()
                .filter(player -> !SpriteManagerInternalAccess.usesNativeScriptedArt(sprites, player))
                .findFirst().orElse(null);
        if (configuredP2 != null && configuredP2.getSecondaryAbility() == SecondaryAbility.FLY)
            return configuredP2;
        return players.stream()
                .filter(player -> SpriteManagerInternalAccess.usesNativeScriptedArt(sprites, player)
                        && player.getSecondaryAbility() == SecondaryAbility.FLY
                        && player.getCpuController() != null
                        && player.getCpuController().isTransientCarrySidekick())
                .findFirst().orElse(null);
    }

    public static void installIfNeeded(GameModule module, PlayerCharacter mode, int zone, int act) {
        var trigger = module == null ? null : module.getSidekickCarryTrigger();
        if (trigger == null || !trigger.shouldEnterCarry(zone, act, mode)) return;
        var sprites = GameServices.spritesOrNull();
        var level = GameServices.levelOrNull();
        if (sprites == null || level == null || sprites.getMainPlayable() == null
                || level.getTransitions().isLastStarPostHitSet()
                || storyCarrier(sprites) != null) return;
        // Native solo Sonic receives Obj_Tails at physical Player_2. For an
        // extension roster whose first follower lacks that carry contract,
        // retain its configured P2 and append the same intrinsic script role.
        // This is an extension authority decision, not ROM arbitrary-team proof.
        var carrier = build(module, sprites);
        SpriteManagerInternalAccess.addNativeScriptedTemporarySidekick(
                sprites, carrier, "tails", () -> recreate(module));
        level.refreshPlayableSpriteArt();
    }

    private static AbstractPlayableSprite build(GameModule module, SpriteManager sprites) {
        var leader = sprites.getMainPlayable();
        var carrier = new Tails(CODE, (short) Math.max(0, leader.getX() - 0x20),
                (short) (leader.getY() + 4));
        carrier.setCpuControlled(true);
        carrier.setAir(true);
        var controller = new SidekickCpuController(carrier, leader);
        controller.setCarryTrigger(module.getSidekickCarryTrigger());
        controller.setTransientCarrySidekick(true);
        carrier.setCpuController(controller);
        return carrier;
    }

    private static AbstractPlayableSprite recreate(GameModule module) {
        var sprites = GameServices.spritesOrNull();
        if (sprites == null || sprites.getMainPlayable() == null) return null;
        var carrier = build(module, sprites);
        SpriteManagerInternalAccess.addNativeScriptedTemporarySidekick(sprites, carrier, "tails", null);
        GameServices.level().refreshPlayableSpriteArt();
        return carrier;
    }
}
