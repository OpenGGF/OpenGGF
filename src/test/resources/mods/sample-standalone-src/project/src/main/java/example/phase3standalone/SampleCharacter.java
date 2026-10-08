package example.phase3standalone;

import com.openggf.game.CharacterKey;
import com.openggf.game.PhysicsProfile;
import com.openggf.sprites.playable.CharacterPhysicsSpec;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

public final class SampleCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;

    public SampleCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
        key = CharacterKey.parsePersisted(code.replaceFirst("_p\\d+$", ""));
    }

    @Override public CharacterKey characterKey() {
        return key;
    }

    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }

    @Override public void draw() {
        if (!isHidden() && getSpriteRenderer() != null) {
            getSpriteRenderer().drawFrame(getMappingFrame(), getRenderCentreX(), getRenderCentreY(),
                    getRenderHFlip(), getRenderVFlip());
        }
    }

    public static CharacterPhysicsSpec physicsSpec() {
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(0x18, 0x80, 0x18, 0x500, 0x700).build());
    }
}
