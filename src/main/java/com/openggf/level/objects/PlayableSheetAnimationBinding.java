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
        final Map<String, Integer> nativeFrames;
        CanonicalProfile(Map<CanonicalAnimation, Integer> strides) { this(strides, Map.of()); }
        CanonicalProfile(Map<CanonicalAnimation, Integer> strides, Map<String, Integer> nativeFrames) {
            this.strides = Map.copyOf(strides); this.nativeFrames = Map.copyOf(nativeFrames);
        }
        // ROM-free standalone modules have no native animation vocabulary. Retain
        // their authored WAIT cycle until they supply their own runtime profile.
        @Override public Integer resolveAnimationId(AbstractPlayableSprite sprite, int clock, int count) {
            return CanonicalAnimation.WAIT.ordinal();
        }
        @Override public int resolveFrame(AbstractPlayableSprite sprite, int clock, int count) { return 0; }
    }

    /** Whether the source sheet explicitly supplies normal native-frame aliases. */
    public static boolean hasNativeFrameAliases(SpriteArtSet art) {
        return art != null && art.animationProfile() instanceof CanonicalProfile profile && !profile.nativeFrames.isEmpty();
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
            int param = script.endParam();
            if (script.endAction() == SpriteAnimationEndAction.SWITCH) {
                var nextCanonical = resolve(CanonicalAnimation.values()[param], art.animationSet(), host);
                param = host.resolveNativeId(host.getAnimationFallbacks().getOrDefault(nextCanonical, nextCanonical));
                if (param < 0) param = host.resolveNativeId(CanonicalAnimation.WAIT);
            }
            scripts.addScript(id, new SpriteAnimationScript(script.delay(), script.frames(), script.endAction(), param));
        }
        // Append-only canonical keys map into a private contiguous strike namespace.
        for (int offset = 0; offset <= 5; offset++) {
            var strike = art.animationSet().getScript(CanonicalAnimation.HOMING_DIVE.ordinal() + offset);
            if (strike != null) scripts.addScript(HOMING_DIVE_ID + offset,
                    new SpriteAnimationScript(strike.delay(), strike.frames(), strike.endAction(), strike.endAction() == SpriteAnimationEndAction.SWITCH
                            ? host.resolveNativeId(resolve(CanonicalAnimation.values()[strike.endParam()], art.animationSet(), host))
                            : strike.endParam()));
        }
        // Native movement keeps the Walk byte even at running speed. A sheet
        // with RUN but no WALK still needs that dispatch to reach its run tier.
        if (art.animationSet().getScript(CanonicalAnimation.WALK.ordinal()) == null
                && art.animationSet().getScript(CanonicalAnimation.RUN.ordinal()) != null) {
            int walkId = host.resolveNativeId(CanonicalAnimation.WALK);
            var fallback = scripts.getScript(walkId);
            scripts.addScript(walkId, new SpriteAnimationScript(0xFF, fallback.frames(), fallback.endAction(), fallback.endParam()));
        }
        var profile = AnimationTranslator.translate(host, nativeProfile, scripts);
        // Canonical sheets use the host's full movement/animation dispatch. Keep
        // these settings local: applying them in AnimationTranslator also changes
        // native cross-game donation (notably animation-change push clearing).
        profile.setPushUsesWalkSpecialHandler(nativeProfile.isPushUsesWalkSpecialHandler());
        profile.setPushDelayShift(nativeProfile.getPushDelayShift());
        profile.setDuckReleasePublishesWalk(nativeProfile.isDuckReleasePublishesWalk());
        profile.setAirborneSlidePreservesPublishedAnimation(nativeProfile.isAirborneSlidePreservesPublishedAnimation());
        // Native deep-wait interruption requires an authored finite exit script.
        // A looping WAIT fallback cannot finish Blink/GetUp and would lock input.
        profile.setBlinkAnimId(art.animationSet().getScript(CanonicalAnimation.BLINK.ordinal()) != null
                ? nativeProfile.getBlinkAnimId() : -1);
        profile.setGetUpAnimId(art.animationSet().getScript(CanonicalAnimation.GET_UP.ordinal()) != null
                ? nativeProfile.getGetUpAnimId() : -1);
        profile.setTumbleFrameBase(ScriptedVelocityAnimationProfile.NO_TUMBLE_FRAMES);
        profile.setSuperAlternateFrameStep(0, 0, 0);
        profile.setHighSpeedWalkRunAnimId(-1);
        if (art.animationSet().getScript(CanonicalAnimation.ROLL2.ordinal()) == null)
            profile.setRoll2AnimId(-1);
        profile.setWalkSlopeFrameStride(canonicalProfile.strides.getOrDefault(resolve(CanonicalAnimation.WALK, art.animationSet(), host), -1));
        profile.setRunSlopeFrameStride(canonicalProfile.strides.getOrDefault(resolve(CanonicalAnimation.RUN, art.animationSet(), host), -1));
        var nativePoses = host.getNativeFramePoses();
        if (!nativePoses.isEmpty() && !canonicalProfile.nativeFrames.isEmpty()) {
            // Keep object-owned bytes and authored indices in disjoint namespaces.
            // Scripts (including slope bases) move together; stock art never enters this path.
            var mappings = new java.util.ArrayList<com.openggf.level.render.SpriteMappingFrame>();
            var dplcs = new java.util.ArrayList<com.openggf.level.render.SpriteDplcFrame>();
            int idle = art.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames().getFirst();
            for (int frame = 0; frame < 256; frame++) {
                var pose = nativePoses.get(frame);
                int target = pose == null ? idle : canonicalProfile.nativeFrames.getOrDefault(pose.key(),
                        art.animationSet().getScript(resolve(pose.fallback(), art.animationSet(), host).ordinal()).frames().getFirst());
                mappings.add(frame == 0 ? new com.openggf.level.render.SpriteMappingFrame(java.util.List.of()) : art.mappingFrames().get(target));
                dplcs.add(frame == 0 ? new com.openggf.level.render.SpriteDplcFrame(java.util.List.of()) : art.dplcFrames().get(target));
            }
            mappings.addAll(art.mappingFrames()); dplcs.addAll(art.dplcFrames());
            var relocated = new SpriteAnimationSet();
            scripts.getAllScripts().forEach((id, script) -> relocated.addScript(id,
                    new SpriteAnimationScript(script.delay(), script.frames().stream().map(f -> f + 256).toList(), script.endAction(), script.endParam())));
            return new SpriteArtSet(art.artTiles(), mappings, dplcs, art.paletteIndex(), art.basePatternIndex(),
                    art.frameDelay(), art.bankSize(), profile, relocated);
        }
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
