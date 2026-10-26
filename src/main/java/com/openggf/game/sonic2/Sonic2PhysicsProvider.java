package com.openggf.game.sonic2;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rules.GameRules;

/**
 * Physics provider for Sonic 2.
 * Returns Sonic or Tails profile based on character type.
 * Spindash is enabled with standard speed table.
 */
public class Sonic2PhysicsProvider implements PhysicsProvider,
        com.openggf.game.internal.NativePlayerSpeedPolicy {

    @Override
    public boolean supports(com.openggf.sprites.playable.AbstractPlayableSprite player,
                            com.openggf.game.session.WorldSession ownerWorld) {
        if (getClass() != Sonic2PhysicsProvider.class
                || com.openggf.game.CrossGameFeatureProvider.isActive()) return false;
        if (!stockOwner(player, ownerWorld)) return false;
        var profile = player.getPhysicsProfile();
        return player.getClass() == com.openggf.sprites.playable.Sonic.class
                && com.openggf.game.CharacterKey.SONIC.equals(player.characterKey())
                && (profile == PhysicsProfile.SONIC_2_SONIC || profile == PhysicsProfile.SONIC_2_SUPER_SONIC)
                || player.getClass() == com.openggf.sprites.playable.Tails.class
                && com.openggf.game.CharacterKey.TAILS.equals(player.characterKey())
                && profile == PhysicsProfile.SONIC_2_TAILS;
    }

    private static boolean stockOwner(com.openggf.sprites.playable.AbstractPlayableSprite player,
                                      com.openggf.game.session.WorldSession world) {
        if (world != null) {
            var port = com.openggf.game.session.WorldSessionPolicyAccess.getService(world,
                    com.openggf.game.sonic2.competition.Sonic2CompetitionObjectHost.class);
            if (port instanceof com.openggf.game.sonic2.competition.Sonic2NativeCompetitionHost host) {
                var roster = com.openggf.game.competition.NativeCompetitionRoster.from(world);
                return host.context().getWorldSession() == world
                        && world.rootGameModule().getClass() == Sonic2GameModule.class
                        && roster != null && roster.slotOf(player) >= 0;
            }
        }
        return world != null && world.rootGameModule().getClass() == Sonic2GameModule.class
                && world.getGameModule().getClass() == Sonic2GameModule.class
                && !com.openggf.game.session.WorldSessionPolicyAccess.hasPolicies(world);
    }

    private static void write(com.openggf.sprites.playable.AbstractPlayableSprite player,
                              int maximum, int acceleration, int deceleration) {
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.publishNativeSpeedWords(
                player, maximum, acceleration, deceleration);
    }

    private static boolean sonicSuper(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        return player.getClass() == com.openggf.sprites.playable.Sonic.class && player.isSuperSonic();
    }

    @Override
    public void initialize(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // Obj01_Init (s2.asm:36187-36189), Obj02_Init (s2.asm:38907-38909).
        write(player, 0x600, 0xC, 0x80);
    }

    @Override
    public void waterEdge(com.openggf.sprites.playable.AbstractPlayableSprite player, boolean underwater) {
        // Obj01_InWater/OutWater (s2.asm:36387-36394,36411-36418);
        // Obj02_InWater/OutWater (s2.asm:39555-39557,39573-39575).
        if (sonicSuper(player)) {
            write(player, underwater ? 0x500 : 0xA00, underwater ? 0x18 : 0x30, underwater ? 0x80 : 0x100);
        } else {
            write(player, underwater ? 0x300 : 0x600, underwater ? 6 : 0xC, underwater ? 0x40 : 0x80);
        }
    }

    @Override
    public void giveSpeedShoes(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // super_shoes (s2.asm:25926-25944): unconditional, including underwater and Super.
        write(player, 0xC00, 0x18, 0x80);
    }

    @Override
    public void expireSpeedShoes(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        // Obj01_ChkShoes (s2.asm:36314-36321), Obj02_ChkShoes (s2.asm:39050-39052): no water test.
        if (sonicSuper(player)) write(player, 0xA00, 0x30, 0x100);
        else write(player, 0x600, 0xC, 0x80);
    }

    @Override
    public short neutralGroundFriction(com.openggf.sprites.playable.AbstractPlayableSprite player, short acceleration) {
        // Obj01_UpdateSpeedOnGround (s2.asm:36772-36776). Obj02 has no Super override.
        return sonicSuper(player) ? (short) 0xC : acceleration;
    }

    @Override
    public boolean ownsProfileInstallation(com.openggf.sprites.playable.SuperStateController controller) {
        return controller.getClass() == Sonic2SuperStateController.class;
    }

    @Override
    public PhysicsProfile getProfile(String characterType) {
        if ("tails".equalsIgnoreCase(characterType)) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    public PhysicsModifiers getModifiers() {
        return PhysicsModifiers.STANDARD;
    }

    @Override
    public GameRules getRules() {
        return GameRules.SONIC_2;
    }
}
