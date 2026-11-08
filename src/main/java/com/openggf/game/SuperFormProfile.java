package com.openggf.game;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable authored powered forms. Palette rows contain sixteen Mega Drive colour words. */
@ModApi
public record SuperFormProfile(Map<GameId, Map<Tier, Form>> games) {
    public SuperFormProfile {
        var copy = new java.util.EnumMap<GameId, Map<Tier, Form>>(GameId.class);
        games.forEach((game, forms) -> copy.put(game, Map.copyOf(forms)));
        games = Map.copyOf(copy);
        if (games.containsKey(GameId.STANDALONE)) throw new IllegalArgumentException("Native host required");
        games.forEach((game, forms) -> {
            if (!forms.containsKey(Tier.SUPER) || game != GameId.S3K && forms.containsKey(Tier.HYPER))
                throw new IllegalArgumentException("Hyper requires S3K and Super");
        });
    }
    @ModApi public enum Tier { SUPER, HYPER }
    /** Missing animation overrides preserve the canonical normal sheet, including transformation. */
    @ModApi public record Form(List<List<Integer>> fade, List<List<Integer>> dryCycle,
            List<List<Integer>> underwaterCycle, int cycleTicks, int fadeTicks,
            PhysicsProfile physics, CanonicalAnimation transformationAnimation,
            Map<CanonicalAnimation, com.openggf.sprites.animation.SpriteAnimationScript> animations,
            boolean afterImages, boolean stars) {
        public Form {
            fade = rows(fade); dryCycle = rows(dryCycle); underwaterCycle = rows(underwaterCycle);
            if (fade.isEmpty() || dryCycle.isEmpty() || cycleTicks < 1 || cycleTicks > 256
                    || fadeTicks < 1 || fadeTicks > 256 || !underwaterCycle.isEmpty()
                    && underwaterCycle.size() != dryCycle.size()) throw new IllegalArgumentException("Invalid form cycle");
            Objects.requireNonNull(physics); Objects.requireNonNull(transformationAnimation);
            var safe = new java.util.EnumMap<CanonicalAnimation, com.openggf.sprites.animation.SpriteAnimationScript>(CanonicalAnimation.class);
            animations.forEach((key, script) -> {
                if (script.frames().isEmpty() || script.frames().size() > 65536
                        || script.frames().stream().anyMatch(frame -> frame == null || frame < 0)
                        || script.endAction() == com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH
                        && (script.endParam() < 0 || script.endParam() >= CanonicalAnimation.values().length))
                    throw new IllegalArgumentException("Invalid canonical form animation");
                safe.put(key, new com.openggf.sprites.animation.SpriteAnimationScript(script.delay(),
                        List.copyOf(script.frames()), Objects.requireNonNull(script.endAction()), script.endParam()));
            });
            animations = Map.copyOf(safe);
        }
        private static List<List<Integer>> rows(List<List<Integer>> rows) {
            if (rows.size() > 256) throw new IllegalArgumentException("Too many palette frames");
            return rows.stream().map(row -> {
                if (row.size() != 16 || row.stream().anyMatch(v -> v == null || (v & ~0xEEE) != 0))
                    throw new IllegalArgumentException("Expected sixteen MD colour words");
                return List.copyOf(row);
            }).toList();
        }
    }
}
