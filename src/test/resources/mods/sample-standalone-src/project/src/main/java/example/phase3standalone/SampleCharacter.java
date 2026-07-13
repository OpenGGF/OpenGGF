package example.phase3standalone;

import com.openggf.game.CharacterKey;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

public final class SampleCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;

    public SampleCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y);
        key = CharacterKey.parsePersisted(code.replaceFirst("_p\\d+$", ""));
        setWidth(20);
        setHeight(runHeight);
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
        runAccel = 0x18; runDecel = 0x80; friction = 0x18; max = 0x500; jump = 0x700;
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
