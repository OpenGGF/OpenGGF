package mica;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;

public final class MicaCharacter extends AbstractPlayableSprite {
    public MicaCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
    }

    static CharacterDefinition definition(String owner,
            PlayableSheetMaterializer.MaterializedArt materialized) {
        CharacterKey key = CharacterKey.mod(owner, "mica");
        return new CharacterDefinition(key, "MICA", MicaCharacter::new, null,
                PlayerCharacter.SONIC_AND_TAILS, SecondaryAbility.NONE, true,
                ignored -> materialized.art(), ignored -> materialized.palette(), java.util.Set.of(), null, null,
                ignored -> MicaGilded.profile());
    }

    static CharacterPhysicsSpec physicsSpec() {
        // Sonic1/2/3kPhysicsProvider all use this profile for single-player Sonic.
        // S3K's Character_Speeds init table belongs to Competition mode only.
        return new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_SONIC);
    }
    @Override protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        // Homing is MICA's own move: an empty target set still gives the forward dash.
        return HomingAttack.start(this, isSuperSonic()
                ? HomingAttack.Mode.INSTAGIB : HomingAttack.Mode.HOMING);
    }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public void draw() {
        drawStandardArt();
    }
}
