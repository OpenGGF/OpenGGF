package com.openggf.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Semantic phase correspondence. Script clocks and control flow belong to ROM loaders. */
public final class AnimationFamilies {
    private AnimationFamilies() { }

    @ModApi
    public record Binding(CanonicalAnimation animation, CanonicalAnimation family, List<Integer> phases,
                          int offsetX, int offsetY) {
        public Binding { phases = List.copyOf(phases); }
        public Binding(CanonicalAnimation animation, CanonicalAnimation family, List<Integer> phases) {
            this(animation, family, phases, 0, 0);
        }
        public String phaseKey(int step) { return "family-" + family.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "/" + phases.get(step); }
    }

    /** Loads a module-owned correspondence profile, never art or animation bytecode. */
    public static Map<Integer, Binding> load(String resource) {
        try (var input = AnimationFamilies.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("Missing animation family profile: " + resource);
            return Map.copyOf(new ObjectMapper().readValue(input, new TypeReference<Map<Integer, Binding>>() { }));
        } catch (IOException e) {
            throw new IllegalStateException("Invalid animation family profile: " + resource, e);
        }
    }

    /** A missing script can borrow an existing script of the same semantic family. */
    public static int resolve(Map<Integer, Binding> bindings, CanonicalAnimation animation) {
        for (var entry : new java.util.TreeMap<>(bindings).entrySet())
            if (entry.getValue().animation() == animation) return entry.getKey();
        CanonicalAnimation family = familyOf(animation);
        var ordered = new java.util.TreeMap<>(bindings);
        for (var entry : ordered.entrySet())
            if (entry.getValue().animation() == family) return entry.getKey();
        for (var entry : ordered.entrySet())
            if (entry.getValue().family() == family) return entry.getKey();
        return -1;
    }

    /** Materializes host scripts using donor phases without replacing donor movement handlers. */
    public static Map<CanonicalAnimation, com.openggf.sprites.animation.SpriteAnimationScript> donate(
            DonorCapabilities host, com.openggf.sprites.animation.SpriteAnimationSet hostScripts,
            DonorCapabilities donor, com.openggf.sprites.animation.SpriteAnimationSet donorScripts) {
        var result = new java.util.EnumMap<CanonicalAnimation, com.openggf.sprites.animation.SpriteAnimationScript>(CanonicalAnimation.class);
        for (var entry : host.getAnimationFamilies().entrySet()) {
            var requested = entry.getValue();
            if (requested.animation() == null || java.util.Set.of(CanonicalAnimation.WALK, CanonicalAnimation.RUN,
                    CanonicalAnimation.ROLL, CanonicalAnimation.PUSH,
                    CanonicalAnimation.SPINDASH, CanonicalAnimation.DUCK, CanonicalAnimation.LOOK_UP,
                    CanonicalAnimation.SPRING, CanonicalAnimation.DEATH,
                    CanonicalAnimation.DROWN).contains(requested.family())) continue;
            int donorId = resolve(donor.getAnimationFamilies(), requested.family());
            var supplied = donor.getAnimationFamilies().get(donorId);
            var original = hostScripts.getScript(entry.getKey());
            var donorScript = donorScripts.getScript(donorId);
            if (supplied == null || donorScript == null || original == null) continue;
            var frames = new java.util.ArrayList<Integer>();
            for (int phase : requested.phases()) {
                int step = supplied.phases().indexOf(phase);
                // An unavailable drawing phase may reuse this family's first pose;
                // it must not discard the host's clock or finite terminator.
                if (step < 0) step = 0;
                frames.add(donorScript.frames().get(step));
            }
            if (frames.isEmpty()) continue;
            int parameter = original.endParam();
            if (original.endAction() == com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH) {
                var next = host.getAnimationFamilies().get(parameter);
                if (next == null || next.animation() == null) continue;
                parameter = donor.resolveNativeId(next.animation());
                if (parameter < 0) continue;
            }
            result.put(requested.animation(), new com.openggf.sprites.animation.SpriteAnimationScript(
                    original.delay(), frames, original.endAction(), parameter));
        }
        return Map.copyOf(result);
    }

    public static CanonicalAnimation familyOf(CanonicalAnimation animation) {
        return switch (animation) {
            case ROLL2 -> CanonicalAnimation.ROLL;
            case STOP -> CanonicalAnimation.SKID;
            case FLOAT, FLOAT3, FLOAT4 -> CanonicalAnimation.FLOAT2;
            case LEAP2 -> CanonicalAnimation.LEAP1;
            case GET_AIR -> CanonicalAnimation.BUBBLE;
            case WATER_SLIDE, SLIDE -> CanonicalAnimation.HURT_FALL;
            case HURT2 -> CanonicalAnimation.HURT;
            case NULL_ANIM -> CanonicalAnimation.BLANK;
            default -> animation;
        };
    }

    /** Family equivalence precedes approximate compatibility fallbacks. */
    public static Map<CanonicalAnimation, CanonicalAnimation> fallbacks(
            Map<Integer, Binding> bindings, Map<CanonicalAnimation, CanonicalAnimation> compatibility) {
        var result = new java.util.EnumMap<CanonicalAnimation, CanonicalAnimation>(CanonicalAnimation.class);
        result.putAll(compatibility);
        for (var animation : CanonicalAnimation.values()) {
            int id = resolve(bindings, animation);
            if (id >= 0 && bindings.get(id).animation() != null)
                result.put(animation, bindings.get(id).animation());
        }
        return Map.copyOf(result);
    }
}
