package micaandtalon;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;

/** CharacterDefinition and AbstractPlayableSprite for MICA; binds Sonic physics, spindash, homing and Gilded form. */
public final class MicaCharacter extends AbstractPlayableSprite {
    public MicaCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
    }

    static CharacterDefinition definition(String owner,
            PlayableSheetMaterializer.MaterializedArt materialized, CharacterPresentation presentation, SuperFormProfile gilded) {
        CharacterKey key = CharacterKey.mod(owner, "mica");
        return new CharacterDefinition(key, "MICA", MicaCharacter::new, null,
                PlayerCharacter.SONIC_AND_TAILS, SecondaryAbility.NONE, true,
                ignored -> materialized.art(), ignored -> materialized.palette(),
                java.util.Set.of(CharacterDefinition.MovementAbility.SPINDASH), null, ignored -> presentation,
                ignored -> gilded, CharacterKey.mod(owner, "talon"));
    }

    static CharacterPhysicsSpec physicsSpec() {
        // Sonic1/2/3kPhysicsProvider all use this profile for single-player Sonic.
        // S3K's Character_Speeds init table belongs to Competition mode only.
        return new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_SONIC);
    }
    @Override protected void onMovementTick() {
        // The world runtime gates free air movement and the spent-air-action latch,
        // and retains the selected target while a homing strike is in progress.
        HomingAttack.requestReticle(this, getAir());
    }
    @Override protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        // The S3K form gate owns an eligible transformation press before her strike.
        if (getSuperStateController() != null && getSuperStateController().activateFromAirAbility()) return true;
        // Homing is MICA's own move: an empty target set still gives the forward dash.
        return HomingAttack.start(this, isSuperSonic()
                ? HomingAttack.Mode.INSTAGIB : HomingAttack.Mode.HOMING);
    }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public void draw() {
        drawStandardArt();
    }
}
