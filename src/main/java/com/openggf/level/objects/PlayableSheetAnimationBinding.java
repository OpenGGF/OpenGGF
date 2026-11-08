package com.openggf.level.objects;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.DonorCapabilities;
import com.openggf.sprites.animation.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Map;

/** Host installation of canonical creator art; no game context leaks into art suppliers. */
public final class PlayableSheetAnimationBinding {
    private PlayableSheetAnimationBinding() { }

    /** Outside every stock host animation byte; private to installed creator sheets. */
    public static final int HOMING_DIVE_ID = 0x100;

    static final class CanonicalProfile implements SpriteAnimationProfile {
        final Map<CanonicalAnimation, Integer> strides;
        CanonicalProfile(Map<CanonicalAnimation, Integer> strides) { this.strides = Map.copyOf(strides); }
        // ROM-free standalone modules have no native animation vocabulary. Retain
        // their authored WAIT cycle until they supply their own runtime profile.
        @Override public Integer resolveAnimationId(AbstractPlayableSprite sprite, int clock, int count) {
            return CanonicalAnimation.WAIT.ordinal();
        }
        @Override public int resolveFrame(AbstractPlayableSprite sprite, int clock, int count) { return 0; }
    }

    public static boolean requiresBinding(SpriteArtSet art) {
        return art != null && art.animationProfile() instanceof CanonicalProfile;
    }

    public static SpriteArtSet bind(SpriteArtSet art, DonorCapabilities host,
                                    ScriptedVelocityAnimationProfile nativeProfile) {
        if (!(art.animationProfile() instanceof CanonicalProfile canonicalProfile)) return art;
        SpriteAnimationSet scripts = new SpriteAnimationSet();
        for (var canonical : CanonicalAnimation.values()) {
            int id = host.resolveNativeId(canonical);
            if (id < 0) continue;
            var selected = resolve(canonical, art.animationSet(), host);
            var script = art.animationSet().getScript(selected.ordinal());
            var nextCanonical = resolve(CanonicalAnimation.values()[script.endParam()], art.animationSet(), host);
            int next = host.resolveNativeId(host.getAnimationFallbacks().getOrDefault(nextCanonical, nextCanonical));
            if (next < 0) next = host.resolveNativeId(CanonicalAnimation.WAIT);
            scripts.addScript(id, new SpriteAnimationScript(script.delay(), script.frames(), script.endAction(), next));
        }
        var strike = art.animationSet().getScript(CanonicalAnimation.HOMING_DIVE.ordinal());
        if (strike != null) scripts.addScript(HOMING_DIVE_ID,
                new SpriteAnimationScript(strike.delay(), strike.frames(), SpriteAnimationEndAction.LOOP, 0));
        // Native movement keeps the Walk byte even at running speed. A sheet
        // with RUN but no WALK still needs that dispatch to reach its run tier.
        if (art.animationSet().getScript(CanonicalAnimation.WALK.ordinal()) == null
                && art.animationSet().getScript(CanonicalAnimation.RUN.ordinal()) != null) {
            int walkId = host.resolveNativeId(CanonicalAnimation.WALK);
            var fallback = scripts.getScript(walkId);
            scripts.addScript(walkId, new SpriteAnimationScript(0xFF, fallback.frames(), fallback.endAction(), fallback.endParam()));
        }
        var profile = AnimationTranslator.translate(host, nativeProfile, scripts);
        profile.setTumbleFrameBase(ScriptedVelocityAnimationProfile.NO_TUMBLE_FRAMES);
        profile.setSuperAlternateFrameStep(0, 0, 0);
        profile.setHighSpeedWalkRunAnimId(-1);
        if (art.animationSet().getScript(CanonicalAnimation.ROLL2.ordinal()) == null)
            profile.setRoll2AnimId(-1);
        profile.setWalkSlopeFrameStride(canonicalProfile.strides.getOrDefault(resolve(CanonicalAnimation.WALK, art.animationSet(), host), -1));
        profile.setRunSlopeFrameStride(canonicalProfile.strides.getOrDefault(resolve(CanonicalAnimation.RUN, art.animationSet(), host), -1));
        return new SpriteArtSet(art.artTiles(), art.mappingFrames(), art.dplcFrames(), art.paletteIndex(),
                art.basePatternIndex(), art.frameDelay(), art.bankSize(), profile, scripts);
    }

    private static CanonicalAnimation resolve(CanonicalAnimation requested, SpriteAnimationSet scripts,
                                               DonorCapabilities host) {
        var seen = java.util.EnumSet.noneOf(CanonicalAnimation.class);
        while (seen.add(requested)) {
            if (scripts.getScript(requested.ordinal()) != null) return requested;
            // STOP/SKID and other vocabulary bridges share the host's native
            // destination. An authored equivalent can serve that destination.
            for (var equivalent : CanonicalAnimation.values()) {
                if (host.getAnimationFallbacks().get(equivalent) == requested
                        && scripts.getScript(equivalent.ordinal()) != null) return equivalent;
            }
            requested = host.getAnimationFallbacks().getOrDefault(requested, CanonicalAnimation.WAIT);
        }
        if (scripts.getScript(CanonicalAnimation.WAIT.ordinal()) == null)
            throw new IllegalArgumentException("Canonical playable art requires WAIT");
        return CanonicalAnimation.WAIT;
    }
}
