package com.openggf.level.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Engine-internal acquired-player identities. Each native owner supplies its own release routine. */
public final class PlayerControlOwners {
    private final List<AbstractPlayableSprite> players = new ArrayList<>();

    public void add(AbstractPlayableSprite player) {
        Objects.requireNonNull(player, "player");
        if (players.stream().noneMatch(owned -> owned == player)) players.add(player);
    }

    public void remove(AbstractPlayableSprite player) { players.removeIf(owned -> owned == player); }
    public boolean isEmpty() { return players.isEmpty(); }
    public List<AbstractPlayableSprite> players() { return List.copyOf(players); }
    public void clear() { players.clear(); }

    public List<PlayerRefId> capture(RewindCaptureContext context) {
        if (players.isEmpty()) return List.of();
        var identities = context.requireIdentityTable();
        var refs = new ArrayList<PlayerRefId>();
        for (var player : players) {
            var ref = identities.encodePlayer(player);
            if (ref == null || ref.isNull()) throw new IllegalStateException("Owned player has no registered rewind identity");
            refs.add(ref);
        }
        refs.sort(Comparator.comparingInt(PlayerRefId::encoded));
        return List.copyOf(refs);
    }

    public void restore(List<PlayerRefId> refs, RewindCaptureContext context) {
        var restored = new ArrayList<AbstractPlayableSprite>();
        if (!refs.isEmpty()) {
            var identities = context.requireIdentityTable();
            for (var ref : refs) {
                var player = identities.resolvePlayer(ref, true);
                if (!(player instanceof AbstractPlayableSprite sprite))
                    throw new IllegalStateException("Owned rewind player is not a playable sprite: " + ref);
                restored.add(sprite);
            }
        }
        players.clear();
        for (var player : restored) add(player);
    }
}
