package example.platformer;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.objects.PlayableSheetMaterializer.MaterializedArt;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

/**
 * "Bolt" -- a round robot with a distinct, floatier tuning than the stock Sonic 2 profile
 * (see {@link #defineSpeeds()} and {@link PlatformerModule}'s matching {@code PhysicsProvider}
 * literal -- author classes may not hold non-primitive static state, so the design-constants
 * values are duplicated in both places rather than shared through a static field, mirroring
 * how {@code SampleCharacter}'s {@code defineSpeeds()} duplicates
 * {@code SampleStandaloneModule}'s profile field). Double-jump ({@code onAbilityActivate}) is
 * added in a later task; this shape clones {@code SampleCharacter}'s ctor/
 * {@code characterKey()}/{@code draw()}/{@code defineSpeeds()}/{@code createSensorLines()}
 * structure from the standalone sample.
 */
public final class BoltCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;

    public BoltCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y);
        key = CharacterKey.parsePersisted(code.replaceFirst("_p\\d+$", ""));
        setWidth(20);
        setHeight(runHeight);
    }

    public static CharacterDefinition definition(String owner, MaterializedArt materialized) {
        CharacterKey key = CharacterKey.mod(owner, "bolt");
        return new CharacterDefinition(key, "Bolt", BoltCharacter::new, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                ignored -> materialized.art(), ignored -> materialized.palette());
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

    @Override protected void defineSpeeds() {
        runAccel = 0x20; runDecel = 0x80; friction = 0x20; max = 0x480; jump = 0x780;
        slopeRunning = 0x20; slopeRollingUp = 0x14; slopeRollingDown = 0x50;
        rollDecel = 0x20; minStartRollSpeed = 0x80; minRollSpeed = 0x80; maxRoll = 0x1000;
        rollHeight = 28; runHeight = 38; standXRadius = 9; standYRadius = 19;
        rollXRadius = 7; rollYRadius = 14;
    }

    @Override protected void createSensorLines() {
        groundSensors = new Sensor[] {
                new GroundSensor(this, Direction.DOWN, (byte) -9, (byte) 19, true),
                new GroundSensor(this, Direction.DOWN, (byte) 9, (byte) 19, true) };
        ceilingSensors = new Sensor[] {
                new GroundSensor(this, Direction.UP, (byte) -9, (byte) -19, false),
                new GroundSensor(this, Direction.UP, (byte) 9, (byte) -19, false) };
        pushSensors = new Sensor[] {
                new GroundSensor(this, Direction.LEFT, (byte) -10, (byte) 0, false),
                new GroundSensor(this, Direction.RIGHT, (byte) 10, (byte) 0, false) };
    }
}
