package example.phase3character;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.playable.*;

public final class SampleCharacter extends AbstractPlayableSprite {
    public SampleCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y);
        setWidth(18); setHeight(38);
    }

    static CharacterDefinition definition(String owner,
            PlayableSheetMaterializer.MaterializedArt materialized) {
        CharacterKey key = CharacterKey.mod(owner, "runner");
        return new CharacterDefinition(key, "Phase Runner", SampleCharacter::new, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                ignored -> materialized.art(), ignored -> materialized.palette());
    }

    @Override protected void defineSpeeds() {
        max = 0x500; runAccel = 0x10; runDecel = 0x80; friction = 0x10;
        jump = 0x640; slopeRunning = 0x20; slopeRollingUp = 0x14; slopeRollingDown = 0x50;
    }
    @Override protected void createSensorLines() { }
    @Override public CharacterKey characterKey() {
        return CharacterKey.mod("phase3-character", "runner");
    }
    @Override public SecondaryAbility getSecondaryAbility() { return SecondaryAbility.NONE; }
    @Override public void draw() { }
}
