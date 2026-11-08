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
                PlayerCharacter.SONIC_AND_TAILS, SecondaryAbility.NONE, false,
                ignored -> materialized.art(), ignored -> materialized.palette());
    }

    static CharacterPhysicsSpec physicsSpec() {
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(0x10, 0x80, 0x10, 0x500, 0x640)
                .rolling(0x20, 0x80, 0x80, 0xE00).build());
    }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public void draw() {
        drawStandardArt();
    }
}
