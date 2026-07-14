package example.platformer;

import com.openggf.audio.StreamedMusicPort;
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
 * {@code SampleStandaloneModule}'s profile field). This shape clones {@code SampleCharacter}'s
 * ctor/{@code characterKey()}/{@code draw()}/{@code defineSpeeds()}/{@code createSensorLines()}
 * structure from the standalone sample and adds a double-jump secondary ability via
 * {@link #onAbilityActivate(boolean, boolean, boolean, boolean)}.
 */
public final class BoltCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;

    /**
     * Double-jump latch. Non-final so it round-trips through rewind capture/restore --
     * a rewind seek across an in-air double jump must land back on the correct latch
     * state rather than silently re-granting (or permanently denying) the ability.
     * Reset to {@code false} on landing in {@link #draw()} (see that method's javadoc
     * for why the per-frame {@code draw()} hook is the landing-reset seam).
     */
    private boolean doubleJumpUsed;

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

    /**
     * Fires once per airborne stretch. {@code AbstractPlayableSprite} only invokes this
     * hook for a valid airborne ability-button activation (see its javadoc), so no
     * additional {@code getAir()} gate is needed here -- just the one-shot latch.
     */
    @Override protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        if (doubleJumpUsed) {
            return false;
        }
        doubleJumpUsed = true;
        setYSpeed((short) -0x600);
        setJumping(false);
        key.ownerModId().ifPresent(owner ->
                currentAudioManager().playNamespacedSfx(new StreamedMusicPort.SfxRef(owner, "jump2")));
        return true;
    }

    /**
     * {@code AbstractPlayableSprite} has no dedicated landing hook, so the per-frame
     * {@code draw()} override (already required for rendering) doubles as the landing-reset
     * seam: once {@code getAir()} reports grounded, the double-jump latch is cleared and
     * ready for the next airborne stretch.
     */
    @Override public void draw() {
        if (!getAir()) {
            doubleJumpUsed = false;
        }
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
