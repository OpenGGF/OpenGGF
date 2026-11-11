package com.openggf.sprites.playable;

import com.openggf.game.*;
import com.openggf.level.Palette;
import com.openggf.game.internal.ProfileSuperSettings;
import com.openggf.sprites.animation.SpriteAnimationSet;
import java.util.List;

/** Authored forms share the native gate/drain owner, never stock Sonic art or palette tables.
 * Native gates/drain: Sonic_CheckGoSuper (s2.asm:37456-37485),
 * Sonic_Super (s2.asm:37505-37525), Sonic_Transform (sonic3k.asm:23522-23533).
 * Authored tables follow the shipped FixBugs=0 final-frame skips, unlike the fixed
 * inclusive wrap: PalCycle_SuperSonic (s2.asm:3200-3217),
 * SuperHyper_PalCycle_SuperSonic (sonic3k.asm:4752-4773).
 * S1 is explicitly a creator rule, borrowing S2's trigger and drain without a donor ROM.
 */
public final class ProfileSuperStateController extends SuperStateController implements com.openggf.game.internal.RuntimeArtRefreshParticipant, com.openggf.game.internal.EndingFormController {
    private final SuperFormProfile profile;
    private final GameId host;
    private final com.openggf.game.internal.ProfileSuperHostRules rules;
    private final com.openggf.game.internal.ProfileSuperEffects effects;
    private final PhysicsProfile normalPhysics;
    private final Palette normalPalette;
    private final com.openggf.graphics.PaletteView normalWater;
    private SpriteAnimationSet normalAnimations;
    private com.openggf.sprites.animation.SpriteAnimationProfile normalArtProfile;
    private java.util.Map<SuperFormProfile.Tier, com.openggf.sprites.art.SpriteArtSet> poweredViews = java.util.Map.of();

    public void bindPoweredArt(com.openggf.level.objects.PoweredPlayableArtBinding.Result result) {
        if (result == null) return;
        normalAnimations = result.normal().animationSet();
        normalArtProfile = result.normal().animationProfile();
        poweredViews = result.powered();
    }
    private void restoreNormalArt() {
        player.setAnimationSet(normalAnimations);
        if (normalArtProfile != null) player.setAnimationProfile(normalArtProfile);
    }
    private final ProfileSuperSettings options;
    private SuperFormProfile.Tier tier = SuperFormProfile.Tier.SUPER;
    private int phase, frame, timer;
    private int displayFrame = -1;
    private boolean doomsday;
    private boolean endingScene, endingReunion;
    private SpriteAnimationSet endingAnimations;
    // Presentation-only particles: not captured, cleared on restore and reset.
    private final PoweredFormEffects particles = new PoweredFormEffects();

    public boolean isEndingSceneActive() { return endingScene; }
    public void bindEndingAnimations(SpriteAnimationSet animations) {
        endingAnimations = animations; player.setAnimationSet(animations);
    }

    public ProfileSuperStateController(AbstractPlayableSprite player, SuperFormProfile profile, GameId host) {
        super(player);
        this.profile = profile; this.host = host;
        rules = java.util.Objects.requireNonNull(player.currentGameModule().getGameService(
                com.openggf.game.internal.ProfileSuperHostRules.class), "Host profile form rules");
        effects = player.currentGameModule().getGameService(com.openggf.game.internal.ProfileSuperEffects.class);
        normalPhysics = player.getPhysicsProfile();
        normalAnimations = player.getAnimationSet();
        var context = player.getSpriteRenderer().getRenderContext();
        normalPalette = copy(context.getPalette(paletteLine()));
        normalWater = context.getUnderwaterPalette(paletteLine());
        options = settings(player);
    }

    private static ProfileSuperSettings settings(AbstractPlayableSprite player) {
        var level = player.currentLevelManagerIfAvailable();
        var source = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                level == null ? null : level.getWorldSession(), ProfileSuperSettings.Source.class);
        return source == null ? ProfileSuperSettings.DEFAULT : source.forCharacter(player.characterKey());
    }

    @Override public void rebindAfterArtRefresh() { restoreRewindState(captureRewindState()); }

    @Override public void reset() {
        super.reset();
        phase = 0; frame = 0; timer = 0; displayFrame = -1; doomsday = false; endingScene = endingReunion = false;
        // The superclass constructor resets before this class's fields exist.
        if (particles != null) particles.clear();
        if (normalPalette != null) {
            applyPalette(normalPalette, null);
            restoreNormalArt();
        }
    }

    /** S3K route script entry: keeps authored art/tier instead of installing Sonic's mappings. */
    public void startDoomsdayTransformation() {
        doomsday = true;
        if (activateFromScript()) player.addRings(50);
        else doomsday = false;
    }

    /** Native route-script writes, retaining authored presentation. */
    public void holdDoomsdayRingDrain() {
        var state = captureRewindState();
        restoreCoreRewindState(new RewindState(state.state(), 0x7FFF, state.paletteState(), state.paletteFrame(),
                state.paletteTimer(), state.transformFramesRemaining(), state.presentationTier(), -1L, -1L));
    }
    public void clearDoomsdayPalette() { phase = 0; }

    private SuperFormProfile.Form form() { return profile.games().get(host).get(tier); }
    /** The current tier's creator effects; null when the host has no authored form or none were supplied. */
    public PoweredEffects poweredEffects() {
        var forms = profile.games().get(host);
        var form = forms == null ? null : forms.get(tier);
        return form == null ? null : form.effects();
    }
    /** Creator strike colours while the authored form is active. */
    public StrikeTrailStyle activeStrikeTrail() {
        var effects = isSuper() ? poweredEffects() : null;
        return effects == null ? null : effects.trail();
    }
    /** A homing or divebomb strike landed while powered. */
    void strikeLanded() {
        if (getState() == SuperState.SUPER) particles.fire(player, poweredEffects(), PoweredEffects.Trigger.STRIKE_HIT);
    }
    private int paletteLine() { return player.getSpriteRenderer().paletteLine(); }
    @Override protected int getRingDrainInterval() { return 60; }
    @Override protected int getInitialRingDrainCounter() { return rules.initialDrainCounter(); }
    @Override protected int getMinRingsToTransform() { return options.rings(); }
    @Override protected PhysicsProfile getSuperProfile() { return form().physics(); }
    @Override protected PhysicsProfile getNormalProfile() { return normalPhysics; }
    @Override protected boolean usesAutomaticJumpTrigger() { return !rules.explicitAirTrigger(); }
    @Override protected boolean usesExplicitAirAbilityTrigger() { return rules.explicitAirTrigger(); }
    @Override protected boolean hasTransformationEmeralds() {
        var state = player.currentGameState();
        return rules.customEmeraldGate() ? state.getEmeraldCount() >= options.sonic1Emeralds()
                : rules.explicitAirTrigger() ? state.hasAllSuperEmeralds()
                    || state.hasAllEmeralds() && !state.isEmeraldsConverted() : state.hasAllEmeralds();
    }
    @Override protected boolean isTransformationEnabled() {
        return switch (rules.game()) {
            case S1 -> options.sonic1(); case S2 -> options.sonic2(); case S3K -> options.sonic3k();
            default -> false;
        };
    }
    @Override protected boolean passesGameSpecificTransformGates() {
        if (!rules.explicitAirTrigger()) return true;
        var shield = player.getShieldType();
        return shield != ShieldType.FIRE && shield != ShieldType.LIGHTNING && shield != ShieldType.BUBBLE
                && player.getInvincibleFrames() <= 0;
    }
    @Override protected int getTransformationAnimationId() {
        var selected = eligibleTier();
        var animation = profile.games().get(host).get(selected).transformationAnimation();
        var powered = poweredViews.get(selected);
        int authored = PoweredPlayableSheet.animationId(animation);
        if (powered != null && powered.animationSet().getScript(authored) != null) return authored;
        int id = player.resolveAnimationId(animation);
        return normalAnimations.getScript(id) != null || profile.games().get(host).get(selected).animations().containsKey(animation)
                ? id : player.resolveAnimationId(CanonicalAnimation.WAIT);
    }
    @Override protected void onTransformationStarted() {
        tier = eligibleTier();
        phase = 1; frame = 0; timer = 15;
        installAnimations();
        if (!rules.explicitAirTrigger()) playMusic();
    }
    private SuperFormProfile.Tier eligibleTier() {
        return rules.explicitAirTrigger() && player.currentGameState().hasAllSuperEmeralds()
                && profile.games().get(host).containsKey(SuperFormProfile.Tier.HYPER)
                ? SuperFormProfile.Tier.HYPER : SuperFormProfile.Tier.SUPER;
    }
    private void installAnimations() {
        var scripts = new SpriteAnimationSet();
        var view = poweredViews.get(tier);
        (view == null ? normalAnimations : view.animationSet()).getAllScripts().forEach(scripts::addScript);
        if (view != null) player.setAnimationProfile(view.animationProfile());
        form().animations().forEach((canonical, script) -> {
            int id = player.resolveAnimationId(canonical);
            if (id >= 0) {
                int next = script.endParam();
                if (script.endAction() == com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH)
                    next = player.resolveAnimationId(CanonicalAnimation.values()[next]);
                scripts.addScript(id, new com.openggf.sprites.animation.SpriteAnimationScript(
                        script.delay(), script.frames(), script.endAction(), next));
            }
        });
        player.setAnimationSet(scripts);
    }
    private void playMusic() {
        GameServices.audio().playMusic(rules.customEmeraldGate() ? options.sonic1Music() : com.openggf.audio.GameMusic.SUPER);
    }
    @Override protected boolean updateTransformationAnimation() {
        if (--timer < 0) {
            timer = form().fadeTicks() - 1;
            displayFrame = frame;
            apply(form().fade().get(frame++), null);
            if (frame == form().fade().size()) { phase = -1; frame = 0; }
        }
        return phase == -1;
    }
    @Override protected void onSuperActivated() {
        if (poweredViews.containsKey(tier)) installAnimations();
        // The burst marks the form's arrival, so its particles draw in the powered palette.
        particles.fire(player, poweredEffects(), PoweredEffects.Trigger.TRANSFORM);
        if (rules.explicitAirTrigger()) { timer = form().cycleTicks() - 1; if (!doomsday) playMusic(); }
        player.setShieldVisible(false);
    }
    @Override protected void updateSuperPalette() {
        if (phase != -1 || --timer >= 0) return;
        timer = form().cycleTicks() - 1;
        displayFrame = 256 + frame;
        apply(form().dryCycle().get(frame), form().underwaterCycle().isEmpty()
                ? null : form().underwaterCycle().get(frame));
        frame = (frame + 1) % form().dryCycle().size();
    }
    @Override protected void onRevertStarted() {
        phase = 2; frame = form().fade().size() - 1; timer = 3;
        restoreNormalArt(); player.setShieldVisible(true); player.setInvincibleFrames(1);
    }
    @Override protected void updatePostRevertEffects() {
        if (phase != 2 || --timer >= 0) return;
        timer = form().fadeTicks() - 1;
        if (frame >= 0) { displayFrame = frame; apply(form().fade().get(frame--), null); }
        else { displayFrame = -1; applyPalette(normalPalette, null); phase = 0; }
    }
    @Override public boolean isHyperFormActive() { return isSuper() && tier == SuperFormProfile.Tier.HYPER; }
    @Override public RewindState captureRewindState() {
        return createRewindState(phase, frame, timer, displayFrame, tier.ordinal() | (doomsday ? 2 : 0) | (endingScene ? 4 : 0) | (endingReunion ? 8 : 0));
    }
    @Override public void restoreRewindState(RewindState state) {
        particles.clear();
        restoreCoreRewindState(state); phase = state.paletteState(); frame = state.paletteFrame();
        timer = state.paletteTimer(); tier = SuperFormProfile.Tier.values()[state.presentationTier() & 1];
        doomsday = (state.presentationTier() & 2) != 0;
        endingScene = (state.presentationTier() & 4) != 0; endingReunion = (state.presentationTier() & 8) != 0;
        if (!endingScene) reconcileRewindPhysicsAndAnimationProfile(isSuper());
        if (endingScene && endingAnimations != null) player.setAnimationSet(endingAnimations);
        else if (isSuper() || getState() == SuperState.TRANSFORMING) installAnimations(); else restoreNormalArt();
        displayFrame = state.transformFramesRemaining();
        if (displayFrame >= 256) {
            int index = displayFrame - 256;
            apply(form().dryCycle().get(index), form().underwaterCycle().isEmpty()
                    ? null : form().underwaterCycle().get(index));
        } else if (displayFrame >= 0) apply(form().fade().get(displayFrame), null);
        else applyPalette(normalPalette, null);
    }
    @Override public void renderPoweredTrail() {
        if (isHyperFormActive() && form().afterImages() && effects != null) effects.renderTrail(player);
        particles.draw(player, poweredEffects(), GameServices.graphics());
    }
    @Override public void update() {
        if (endingScene) return; // AnPal owns scene palette/control release, before object dispatch.
        if (player.getDead() && isSuper()) { debugDeactivate(); return; }
        super.update();
        if (isHyperFormActive() && form().stars() && effects != null) effects.updateStars(player);
        particles.tick(player, poweredEffects(), getState() == SuperState.SUPER);
    }

    @Override public void advanceEndingPaletteAtAnPal() {
        if (!endingScene) return;
        if (getState() == SuperState.TRANSFORMING && updateTransformationAnimation()) {
            endingCore(SuperState.SUPER);
            player.setForcedAnimationId(-1);
            player.setAnimationId(0);
            ObjectControlState.none().applyTo(player);
        } else if (isSuper()) updateSuperPalette();
        else updatePostRevertEffects();
    }

    private void endingCore(SuperState state) {
        var saved = captureRewindState();
        restoreCoreRewindState(new RewindState(state, 0x7FFF, saved.paletteState(), saved.paletteFrame(),
                saved.paletteTimer(), saved.transformFramesRemaining(), saved.presentationTier(), -1L, -1L));
    }
    private void endingForm(SuperFormProfile.Tier requested) {
        endingScene = true; endingReunion = false; doomsday = false;
        tier = profile.games().get(host).containsKey(requested) ? requested : SuperFormProfile.Tier.SUPER;
        phase = -1; frame = 0; timer = 0;
        player.setSuperSonic(true); endingCore(SuperState.SUPER);
        updateSuperPalette();
    }
    /** sub_5FC8E (sonic3k.asm:126682-126692): activate without gameplay eligibility/music. */
    @Override public void initializeEndingSuperSonic() {
        endingForm(SuperFormProfile.Tier.SUPER); player.setForcedAnimationId(-1); player.setAnimationId(0);
    }
    @Override public void initializeEndingHyperSonic() { endingForm(SuperFormProfile.Tier.HYPER); }
    @Override public void initializeEndingSuperTails() { initializeEndingSuperSonic(); }
    @Override public void holdEndingTailsCompanionPaletteTimer() { }
    @Override public void holdEndingRingDrain() { endingScene = true; endingCore(getState()); }
    @Override public void beginEndingRescueRevert() {
        endingScene = true; endingReunion = false; player.setSuperSonic(false);
        player.setForcedAnimationId(-1); player.setAnimationId(0xF);
        phase = 2; frame = form().fade().size() - 1; timer = 3;
        endingCore(SuperState.NORMAL);
    }
    @Override public void completeEndingRescueRevertMappings() { }
    @Override public void clearEndingPaletteStatus() { phase = 0; }
    @Override public void startEndingReunionTransformation() {
        // Ending_Give_SuperSonic forces the highest authored tier independently of eligibility.
        endingForm(SuperFormProfile.Tier.HYPER); endingReunion = true;
        phase = 1; frame = 0; timer = 15;
        // FixBugs=0 forces the Sonic-route Hyper tier even with Chaos Emeralds only;
        // Ending_Give_SuperSonic (sonic3k.asm:181537-181571). The fixed branch repairs the trail.
        var levelState = player.currentLevelState();
        if (levelState != null) com.openggf.game.LevelRingDisplay.writeWithoutRefresh(levelState,
                (levelState.getRings()+50)&0xFFFF);
        player.applyExternalPhysicsProfile(getSuperProfile());
        player.setForcedAnimationId(0x1F); player.setAnimationId(0x1F);
        player.setInvincibleFrames(0); player.setObjectMappingFrameControl(false);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        endingCore(SuperState.TRANSFORMING);
    }

    private void apply(List<Integer> dry, List<Integer> water) { applyPalette(palette(dry), water == null ? null : palette(water)); }
    private void applyPalette(Palette dry, Palette water) {
        var context = player.getSpriteRenderer().getRenderContext();
        // Null water row deliberately retains W3's live per-zone derivation.
        context.setPalette(paletteLine(), copy(dry)); context.setUnderwaterPalette(paletteLine(), dry == normalPalette ? normalWater : water);
        var graphics = GameServices.graphics();
        if (graphics.isGlInitialized()) graphics.cachePaletteTexture(context.getPalette(paletteLine()), context.getEffectivePaletteLine(paletteLine()));
    }
    private static Palette palette(List<Integer> row) {
        var result = new Palette(); for (int i = 0; i < 16; i++) result.getColor(i).fromSegaFormat(row.get(i)); return result;
    }
    private static Palette copy(Palette source) {
        var result = new Palette();
        for (int i = 0; i < 16; i++) { var c = source.getColor(i); result.setColor(i, new Palette.Color(c.r,c.g,c.b)); }
        return result;
    }
}
