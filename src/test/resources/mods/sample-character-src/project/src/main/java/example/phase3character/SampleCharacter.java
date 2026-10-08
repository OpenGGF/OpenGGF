package example.phase3character;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;

public final class SampleCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;
    public SampleCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
        key = CharacterKey.parsePersisted(code.replaceFirst("_p\\d+$", ""));
    }

    static CharacterDefinition definition(String owner,
            PlayableSheetMaterializer.MaterializedArt materialized) {
        CharacterKey key = CharacterKey.mod(owner, "runner");
        return new CharacterDefinition(key, "Phase Runner", SampleCharacter::new, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                ignored -> materialized.art(), ignored -> materialized.palette());
    }

    static CharacterPhysicsSpec physicsSpec() {
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(0x10, 0x80, 0x10, 0x500, 0x640)
                .rolling(0x20, 0x80, 0x80, 0xE00).build());
    }
    @Override public CharacterKey characterKey() { return key; }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public void draw() {
        if (!isHidden() && getSpriteRenderer() != null) {
            getSpriteRenderer().drawFrame(getMappingFrame(), getRenderCentreX(), getRenderCentreY(),
                    getRenderHFlip(), getRenderVFlip());
        }
    }
}
