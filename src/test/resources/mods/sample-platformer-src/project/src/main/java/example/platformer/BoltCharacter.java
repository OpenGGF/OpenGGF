package example.platformer;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.PlayableSheetMaterializer.MaterializedArt;
import com.openggf.game.PhysicsProfile;
import com.openggf.sprites.playable.CharacterPhysicsSpec;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;

/** Bolt's character tuning is owned by each instance; the module supplies game rules and modifiers. */
public final class BoltCharacter extends AbstractPlayableSprite {
    private final CharacterKey key;

    /**
     * Double-jump latch. Rides the production rewind path via {@link #captureSubclassRewindState()}
     * / {@link #restoreSubclassRewindState(PerObjectRewindSnapshot.PlayableSubclassRewindExtra)},
     * which pack this field into a {@link BoltRewindExtra} on every keyframe capture and
     * restore it (or reset to {@code false} when the snapshot carries no payload) on every
     * rewind restore -- a rewind seek across an in-air double jump must land back on the
     * correct latch state rather than silently re-granting (or permanently denying) the
     * ability. Landing and level initialization reset it through simulation callbacks.
     */
    private boolean doubleJumpUsed;

    public BoltCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, physicsSpec());
        key = CharacterKey.parsePersisted(code.replaceFirst("_p\\d+$", ""));
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

    @Override protected void onLanded() { doubleJumpUsed = false; }
    @Override protected void onLevelReset() { doubleJumpUsed = false; }

    @Override public void draw() {
        if (!isHidden() && getSpriteRenderer() != null) {
            getSpriteRenderer().drawFrame(getMappingFrame(), getRenderCentreX(), getRenderCentreY(),
                    getRenderHFlip(), getRenderVFlip());
        }
    }

    /** Immutable payload carrying the double-jump latch through a rewind keyframe. */
    private record BoltRewindExtra(boolean doubleJumpUsed)
            implements PerObjectRewindSnapshot.PlayableSubclassRewindExtra {
    }

    @Override protected PerObjectRewindSnapshot.PlayableSubclassRewindExtra captureSubclassRewindState() {
        return new BoltRewindExtra(doubleJumpUsed);
    }

    /**
     * Tolerates {@code null} (no subclass payload in the snapshot -- e.g. a pre-Task-3
     * snapshot shape) by resetting the latch to its fresh default of {@code false} rather
     * than assuming a payload is always present, per the hook's null contract.
     */
    @Override
    protected void restoreSubclassRewindState(PerObjectRewindSnapshot.PlayableSubclassRewindExtra extra) {
        doubleJumpUsed = extra instanceof BoltRewindExtra bolt && bolt.doubleJumpUsed();
    }

    public static CharacterPhysicsSpec physicsSpec() {
        return new CharacterPhysicsSpec(PhysicsProfile.builder()
                .movement(0x20, 0x80, 0x20, 0x480, 0x780).build());
    }
}
