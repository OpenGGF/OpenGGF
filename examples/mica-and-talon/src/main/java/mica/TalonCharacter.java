package mica;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;
import java.util.Set;

/** TALON: native flight, down-held targeting and a Solar Instagib dive. */
public final class TalonCharacter extends AbstractPlayableSprite {
    // Private mod animation IDs, outside native and homing namespaces.
    private static final int FLIGHT = 0x200, TIRED = 0x201;
    private com.openggf.sprites.animation.SpriteAnimationSet authoredAnimations;
    public TalonCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y,
                new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_TAILS));
    }

    static CharacterDefinition definition(String owner, PlayableSheetMaterializer.MaterializedArt materialized) {
        return new CharacterDefinition(CharacterKey.mod(owner, "talon"), "TALON", (code, x, y) -> {
                    var talon = new TalonCharacter(code, x, y);
                    talon.authoredAnimations = materialized.art().animationSet();
                    return talon;
                },
                SidekickRespawnStrategy::tails, PlayerCharacter.TAILS_ALONE, SecondaryAbility.FLY, true,
                ignored -> materialized.art(), ignored -> materialized.palette(),
                Set.of(CharacterDefinition.MovementAbility.SPINDASH,
                        CharacterDefinition.MovementAbility.CROSS_GAME_FLIGHT,
                        CharacterDefinition.MovementAbility.SUPER_FLIGHT_LIFT), null,
                new CharacterDefinition.PresentationSupplier() {
                    public CharacterPresentation load(String code) { return load(code, GameId.S3K); }
                    public CharacterPresentation load(String code, GameId host) {
                        return new CharacterPresentation("TALON", host == GameId.S2 ? "TALON" : "TAL",
                                java.util.Map.of(), java.util.Map.of());
                    }
                },
                ignored -> TalonSolar.profile());
    }

    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.FLY; }
    @Override protected void onMovementTick() {
        HomingAttack.requestReticle(this, getAir() && isDownPressed());
        if (authoredAnimations == null || getAnimationSet() == null
                || getTailsFlightController() == null || !getTailsFlightController().isActive()) return;
        var flight = authoredAnimations.getScript(CanonicalAnimation.TAILS_FLY.ordinal());
        var tired = authoredAnimations.getScript(CanonicalAnimation.TAILS_FLY_TIRED.ordinal());
        // TALON has no native-frame aliases, so host binding preserves his frame indices.
        // S3K's shared glide/flight IDs can otherwise overwrite ascent with a fallback.
        for (var pose : java.util.List.of(CanonicalAnimation.TAILS_FLY, CanonicalAnimation.TAILS_FLY_ASCEND,
                CanonicalAnimation.TAILS_FLY_CARRY, CanonicalAnimation.TAILS_FLY_CARRY_ASCEND)) {
            int id = resolveAnimationId(pose);
            if (id >= 0) getAnimationSet().addScript(id, flight);
        }
        int tiredId = resolveAnimationId(CanonicalAnimation.TAILS_FLY_TIRED);
        if (tiredId >= 0) getAnimationSet().addScript(tiredId, tired);
        if (resolveAnimationId(CanonicalAnimation.TAILS_FLY) < 0) {
            // Mod-only flight in S1/S2 needs private script IDs.
            getAnimationSet().addScript(FLIGHT, flight);
            getAnimationSet().addScript(TIRED, tired);
            setAnimationId((getDoubleJumpProperty() & 0xFF) == 0 ? TIRED : FLIGHT);
        }
    }
    @Override protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        // Native transformation owns an eligible air press before the character ability.
        if (getSuperStateController() != null && getSuperStateController().activateFromAirAbility()) return true;
        // Ground duck/spindash and native flight descent retain their own input paths.
        return getAir() && down && HomingAttack.divebomb(this, isSuperSonic()
                ? HomingAttack.Mode.INSTAGIB : HomingAttack.Mode.HOMING);
    }
    @Override public void draw() { drawStandardArt(); }
}
