package mica;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;
import java.util.Set;

/** TALON engine skeleton. Body and Solar colours temporarily reuse MICA's assets. */
public final class TalonCharacter extends AbstractPlayableSprite {
    public TalonCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y,
                new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_TAILS));
    }

    static CharacterDefinition definition(String owner, PlayableSheetMaterializer.MaterializedArt placeholder) {
        return new CharacterDefinition(CharacterKey.mod(owner, "talon"), "TALON", TalonCharacter::new,
                SidekickRespawnStrategy::tails, PlayerCharacter.TAILS_ALONE, SecondaryAbility.FLY, true,
                ignored -> placeholder.art(), ignored -> placeholder.palette(),
                Set.of(CharacterDefinition.MovementAbility.SPINDASH,
                        CharacterDefinition.MovementAbility.CROSS_GAME_FLIGHT,
                        CharacterDefinition.MovementAbility.SUPER_FLIGHT_LIFT), null,
                ignored -> new CharacterPresentation("TALON", "TAL", java.util.Map.of(), java.util.Map.of()),
                ignored -> solarProfile());
    }

    private static SuperFormProfile solarProfile() {
        var hosts = new java.util.EnumMap<GameId, java.util.Map<SuperFormProfile.Tier, SuperFormProfile.Form>>(GameId.class);
        MicaGilded.profile().games().forEach((game, forms) -> {
            var placeholder = forms.get(SuperFormProfile.Tier.SUPER);
            var solar = new SuperFormProfile.Form(placeholder.fade(), placeholder.dryCycle(),
                    placeholder.underwaterCycle(), placeholder.cycleTicks(), placeholder.fadeTicks(),
                    PhysicsProfile.SONIC_3K_SUPER_TAILS, CanonicalAnimation.WAIT, java.util.Map.of(), false, false);
            hosts.put(game, java.util.Map.of(SuperFormProfile.Tier.SUPER, solar));
        });
        return new SuperFormProfile(hosts);
    }

    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.FLY; }
    @Override protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        // TODO phase 2 follow-up: down-held requestReticle from the character tick hook;
        // down+jump targeted divebomb (Instagib in Solar). Until then every jump flaps.
        return getSuperStateController() != null && getSuperStateController().activateFromAirAbility();
    }
    @Override public void draw() { drawStandardArt(); }
}
