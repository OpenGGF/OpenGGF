package micaandtalon;

import com.openggf.game.*;
import com.openggf.level.objects.PlayableSheetMaterializer;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.*;

import java.util.Set;

/**
 * CharacterDefinition and AbstractPlayableSprite for TALON; binds Tails physics, integrated wings,
 * flight and divebomb.
 */
public final class TalonCharacter extends AbstractPlayableSprite {
    // Private mod animation IDs, outside native and homing namespaces.
    private static final int FLIGHT = 0x200;
    private SpriteAnimationSet authoredAnimations;
    private int authoredMappingCount;
    private StrikeTrailStyle trail;

    public TalonCharacter(String code, int x, int y) {
        super(code, (short) x, (short) y, new CharacterPhysicsSpec(PhysicsProfile.SONIC_2_TAILS));
    }

    static CharacterDefinition definition(
            String owner,
            PlayableSheetMaterializer.MaterializedArt materialized,
            CharacterPresentation presentation,
            SuperFormProfile solar,
            StrikeTrailStyle trail) {
        return new CharacterDefinition(
                CharacterKey.mod(owner, "talon"),
                "TALON",
                (code, x, y) -> {
                    var talon = new TalonCharacter(code, x, y);
                    talon.authoredAnimations = materialized.art().animationSet();
                    talon.authoredMappingCount = materialized.art().mappingFrames().size();
                    talon.trail = trail;
                    return talon;
                },
                SidekickRespawnStrategy::tails,
                PlayerCharacter.TAILS_ALONE,
                SecondaryAbility.FLY,
                true,
                ignored -> materialized.art(),
                ignored -> materialized.palette(),
                Set.of(
                        CharacterDefinition.MovementAbility.SPINDASH,
                        CharacterDefinition.MovementAbility.CROSS_GAME_FLIGHT,
                        CharacterDefinition.MovementAbility.SUPER_FLIGHT_LIFT),
                null,
                new CharacterDefinition.PresentationSupplier() {
                    public CharacterPresentation load(String code) {
                        return load(code, GameId.S3K);
                    }

                    public CharacterPresentation load(String code, GameId host) {
                        return new CharacterPresentation(
                                "TALON",
                                "TALON",
                                presentation.art(),
                                presentation.supplementaryGlyphs(),
                                presentation.stages(),
                                presentation.endings());
                    }
                },
                ignored -> solar);
    }

    @Override
    public SecondaryAbility getSecondaryAbility() {
        return SecondaryAbility.FLY;
    }

    @Override
    protected void onMovementTick() {
        HomingAttack.requestReticle(this, getAir() && isDownPressed());
        if (!hasFlightArt()) return;
        int id = installFlightArt(selectedFlightPose());
        super.setAnimationId(id);
        if (getForcedAnimationId() >= 0) super.setForcedAnimationId(id);
    }

    private boolean hasFlightArt() {
        return authoredAnimations != null
                && getAnimationSet() != null
                && getTailsFlightController() != null
                && getTailsFlightController().isActive();
    }

    private CanonicalAnimation selectedFlightPose() {
        return getTailsFlightController()
                .selectAnimation(
                        getTailsCarryController() != null
                                && getTailsCarryController().isCarryingMainCharacter());
    }

    // The movement hook runs before native flight. Redirect its later pose write
    // locally too, retaining the native selector and this sheet's private clock.
    @Override
    public void setAnimationId(int id) {
        super.setAnimationId(flightPresentationId(id));
    }

    @Override
    public void setForcedAnimationId(int id) {
        super.setForcedAnimationId(flightPresentationId(id));
    }

    private int flightPresentationId(int id) {
        if (id < 0 || id >= FLIGHT || !hasFlightArt()) return id;
        var pose = selectedFlightPose();
        return id == resolveAnimationId(pose) ? installFlightArt(pose) : id;
    }

    private int installFlightArt(CanonicalAnimation pose) {
        // A transformation retains its forced wind-up even if flight was active.
        if (getSuperStateController() != null
                && getSuperStateController().getState() == SuperState.TRANSFORMING
                && getForcedAnimationId() >= 0) return getForcedAnimationId();
        // Powered binding has already relocated these canonical scripts into the
        // shared mapping bank. Normal private wing cycles still use source indices.
        boolean powered = isSuperSonic();
        var source = powered ? getAnimationSet().getScript(PoweredPlayableSheet.animationId(pose)) : null;
        boolean boundPowered = source != null;
        if (!boundPowered) source = authoredAnimations.getScript(pose.ordinal());
        if (source == null)
            source = authoredAnimations.getScript(CanonicalAnimation.TAILS_FLY.ordinal());
        int relocation = !boundPowered && getAnimationFrameCount() >= authoredMappingCount + 256 ? 256 : 0;
        var frames = source.frames().stream().map(frame -> frame + relocation).toList();
        int id = FLIGHT + pose.ordinal();
        getAnimationSet()
                .addScript(
                        id,
                        new SpriteAnimationScript(
                                source.delay(), frames, source.endAction(), source.endParam()));
        return id;
    }

    @Override
    protected boolean onAbilityActivate(boolean up, boolean down, boolean left, boolean right) {
        // Native transformation owns an eligible air press before the character ability.
        if (getSuperStateController() != null && getSuperStateController().activateFromAirAbility())
            return true;
        // Ground duck/spindash and native flight descent retain their own input paths.
        // Solar divebombs are ordinary strikes; Instagib belongs to Hyper MICA alone.
        return getAir() && down && HomingAttack.divebomb(this, HomingAttack.Mode.HOMING);
    }

    /** Slate normal trail; Solar supplies its own heat trail through the effects table. */
    @Override
    public StrikeTrailStyle strikeTrailStyle() {
        return trail;
    }

    @Override
    public void draw() {
        drawStandardArt();
    }
}
