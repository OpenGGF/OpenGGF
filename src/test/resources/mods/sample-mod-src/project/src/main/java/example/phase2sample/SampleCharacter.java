package example.phase2sample;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

/** Minimal Phase-3 character stub. Add art and movement hooks as the mod grows. */
public final class SampleCharacter extends AbstractPlayableSprite {
    public SampleCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y);
        setWidth(20);
        setHeight(40);
    }

    public static CharacterDefinition definition(String ownerModId) {
        CharacterKey key = CharacterKey.mod(ownerModId, "sample-character");
        return new CharacterDefinition(key, "Sample Character", SampleCharacter::new, null,
                PlayerCharacter.SONIC_AND_TAILS, SecondaryAbility.NONE, false,
                ignored -> SpriteArtSet.EMPTY);
    }

    @Override protected void defineSpeeds() {
        // Physics profiles are registered by later customization; stock defaults apply meanwhile.
    }

    @Override protected void createSensorLines() {
        // Add the character's six terrain sensors before enabling it for gameplay.
    }

    @Override public void draw() {
        // Install baked playable art before enabling this stub for gameplay.
    }
}
