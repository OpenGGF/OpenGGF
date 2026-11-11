package micaandtalon;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;

/**
 * Tutorial part 2: MICA with Sonic's physics and route, drawn from her own sheet.
 * No presentation, ability or powered form yet; later parts add them.
 */
public final class MicaCharacter extends AbstractPlayableSprite {
    public MicaCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
    }

    static CharacterDefinition definition(
            String owner, PlayableSheetMaterializer.MaterializedArt sheet) {
        return new CharacterDefinition(
                CharacterKey.mod(owner, "mica"),
                "MICA",
                MicaCharacter::new,
                null, // the standard Sonic respawn strategy
                PlayerCharacter.SONIC_AND_TAILS, // Sonic's route; solo launches use SONIC_ALONE
                SecondaryAbility.NONE,
                false, // no powered form yet
                ignored -> sheet.art(),
                ignored -> sheet.palette());
    }

    static CharacterPhysicsSpec physicsSpec() {
        // Every host's single-player Sonic constants.
        return new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_SONIC);
    }

    @Override
    public SecondaryAbility getSecondaryAbility() {
        return SecondaryAbility.NONE;
    }

    @Override
    public void draw() {
        drawStandardArt();
    }
}
