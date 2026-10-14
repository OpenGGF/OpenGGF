package com.openggf.sprites.managers;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.resources.PlayerArtTransferProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reversible host-ROM resources for a script that publishes literal native
 * player mappings. It borrows presentation only; player physics, animation
 * bytes and powered/controller RAM remain owned by the existing participant.
 */
final class NativePlayerArtBindings {
    private enum Phase { ACTIVE, RETURN }
    private record Value(Phase phase, TailsTailsController.RewindState tail) { }
    private final SpriteManager sprites;
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private Map<String, Value> pending;

    NativePlayerArtBindings(SpriteManager sprites) { this.sprites = sprites; }

    void borrow(AbstractPlayableSprite player) {
        Binding binding = bindings.get(player.getCode());
        if (binding == null || binding.player != player) {
            binding = new Binding(player);
            bindings.put(player.getCode(), binding);
        }
        binding.bind(Phase.ACTIVE);
    }

    void release(AbstractPlayableSprite player) {
        Binding binding = bindings.get(player.getCode());
        if (binding != null && binding.player == player && binding.phase != null) {
            // P2 releases after P1 Animate. Keep the last native mapping drawable
            // until P1's next ordinary animation publication; do not advance it.
            binding.bind(Phase.RETURN);
        }
    }

    void ordinaryMappingPublished(AbstractPlayableSprite player) {
        Binding binding = bindings.get(player.getCode());
        if (binding != null && binding.player == player && binding.phase == Phase.RETURN) {
            binding.bind(null);
            bindings.remove(player.getCode());
        }
    }

    void forget(AbstractPlayableSprite player) {
        Binding binding = bindings.remove(player.getCode());
        if (binding != null) binding.bind(null);
    }

    void clear() {
        for (Binding binding : bindings.values()) binding.bind(null);
        bindings.clear();
        pending = null;
    }

    boolean isBorrowed(AbstractPlayableSprite player) {
        Binding binding = bindings.get(player.getCode());
        return binding != null && binding.player == player && binding.ownsResources();
    }

    RewindSnapshottable<Map<String, Value>> adapter() {
        return new RewindSnapshottable<>() {
            @Override public String key() { return "native-player-art-bindings"; }
            @Override public Map<String, Value> capture() {
                var values = new LinkedHashMap<String, Value>();
                for (var entry : bindings.entrySet()) {
                    var binding = entry.getValue();
                    if (binding.ownsResources()) values.put(entry.getKey(), new Value(binding.phase,
                            binding.player.getTailsTailsController() == null ? null
                                    : binding.player.getTailsTailsController().captureRewindState()));
                }
                return Map.copyOf(values);
            }
            @Override public void restore(Map<String, Value> value) { pending = value; }
            @Override public void resetForMissingSnapshot() { pending = Map.of(); }
        };
    }

    void afterRestore() {
        if (pending == null) return;
        // Object/roster recreation may refresh playable art. Rebind only after
        // those owners and scalar player/tail RAM have completed restoration.
        var values = pending;
        pending = null;
        var iterator = bindings.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!values.containsKey(entry.getKey())) {
                entry.getValue().bind(null);
                iterator.remove();
            }
        }
        for (var entry : values.entrySet()) {
            var sprite = sprites.getSprite(entry.getKey());
            if (!(sprite instanceof AbstractPlayableSprite player))
                throw new IllegalStateException("Missing native-art participant: " + entry.getKey());
            borrow(player);
            var binding = bindings.get(entry.getKey());
            binding.bind(entry.getValue().phase());
            if (entry.getValue().tail() != null && player.getTailsTailsController() != null)
                player.getTailsTailsController().restoreRewindState(entry.getValue().tail());
        }
    }

    private static final class Binding {
        private final AbstractPlayableSprite player;
        private final PlayerSpriteRenderer ordinaryRenderer, nativeRenderer;
        private final SpriteAnimationProfile ordinaryProfile, nativeProfile;
        private final SpriteAnimationSet ordinaryAnimations, nativeAnimations;
        private final int ordinaryFrameCount, nativeFrameCount;
        private final DynamicArtDecisionOwner ordinaryArt, nativeArt;
        private final TailsTailsController ordinaryTail, nativeTail;
        private Phase phase;

        Binding(AbstractPlayableSprite player) {
            this.player = player;
            ordinaryRenderer = player.getSpriteRenderer();
            ordinaryProfile = player.getAnimationProfile();
            ordinaryAnimations = player.getAnimationSet();
            ordinaryFrameCount = player.getAnimationFrameCount();
            ordinaryArt = player.getAnimationManager().dynamicArtDecisionOwner();
            ordinaryTail = player.getTailsTailsController();
            var level = player.currentLevelManagerIfAvailable();
            if (level == null || !(level.getGame() instanceof PlayerSpriteArtProvider provider))
                throw new IllegalStateException("Literal player mappings require a host ROM art provider");
            try {
                var art = provider.loadPlayerSpriteArt(player.characterKey().persisted());
                if (art == null || art.mappingFrames().isEmpty())
                    throw new IllegalStateException("Missing host player mappings: " + player.getCode());
                nativeRenderer = new PlayerSpriteRenderer(art, GameServices.graphics());
                nativeProfile = art.animationProfile();
                nativeAnimations = art.animationSet();
                nativeFrameCount = art.mappingFrames().size();
                nativeArt = dynamicArtOwner(player.characterKey().persisted(), nativeRenderer);
                var module = level.getGameModule();
                if (player instanceof Tails && module.hasSeparateTailsTailArt()) {
                    var tailRenderer = new PlayerSpriteRenderer(module.loadTailsTailArt(), GameServices.graphics());
                    nativeTail = new TailsTailsController(player, tailRenderer, true,
                            dynamicArtOwner("tails-tails", tailRenderer));
                } else nativeTail = ordinaryTail;
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        private boolean ownsResources() {
            return phase != null && player.getSpriteRenderer() == nativeRenderer;
        }

        private void bind(Phase next) {
            if (next == null && phase == null) return;
            if (next != Phase.ACTIVE && phase != null && !ownsResources()) {
                // A later script has already published its own resources. Our
                // abandoned borrowing must not overwrite that owner's binding.
                phase = null;
                return;
            }
            var currentTail = player.getTailsTailsController();
            var tailRam = currentTail == null ? null : currentTail.captureRewindState();
            boolean nativeMapping = next != null;
            player.setSpriteRenderer(nativeMapping ? nativeRenderer : ordinaryRenderer);
            player.setAnimationFrameCount(nativeMapping ? nativeFrameCount : ordinaryFrameCount);
            player.setAnimationProfile(next == Phase.ACTIVE ? nativeProfile : ordinaryProfile);
            player.setAnimationSet(next == Phase.ACTIVE ? nativeAnimations : ordinaryAnimations);
            player.getAnimationManager().setDynamicArtDecisionOwner(nativeMapping ? nativeArt : ordinaryArt);
            player.setTailsTailsController(nativeMapping ? nativeTail : ordinaryTail);
            if (tailRam != null && player.getTailsTailsController() != null)
                player.getTailsTailsController().restoreRewindState(tailRam);
            if (phase != next) {
                player.getSpriteRenderer().invalidateDplcCache();
                if (player.getTailsTailsController() != null)
                    player.getTailsTailsController().getRenderer().invalidateDplcCache();
            }
            phase = next;
        }

        private static DynamicArtDecisionOwner dynamicArtOwner(String owner, PlayerSpriteRenderer renderer) {
            var module = GameServices.module();
            var lifecycle = GameServices.dynamicArtLifecycleOrNull();
            if (module == null || lifecycle == null || module.getRules() == null
                    || !module.getRules().dynamicArtDmaService().supportsPlayerDynamicArtAudit()) return null;
            var bank = module instanceof PlayerArtTransferProfile profile ? profile.convertedPlayerArtBank(owner) : null;
            if (bank == null && !owner.equals("sonic") && !owner.equals("tails") && !owner.equals("tails-tails")) return null;
            return new DynamicArtDecisionOwner(lifecycle, module.getGameId(), owner, renderer, bank);
        }
    }
}
