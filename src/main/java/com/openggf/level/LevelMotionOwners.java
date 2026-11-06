package com.openggf.level;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.graphics.MotionPacket;
import java.util.Objects;

/** Gameplay identity resolution for opaque graphics owner keys. */
public final class LevelMotionOwners {
    private LevelMotionOwners() { }
    public record ObjectKey(ObjectRefId id) {
        public ObjectKey { Objects.requireNonNull(id); }
    }
    /** The incarnation uses Java identity, so a recreated roster member cannot pair with its predecessor. */
    public record PlayerKey(PlayerRefId id, Object incarnation) {
        public PlayerKey { Objects.requireNonNull(id); Objects.requireNonNull(incarnation); }
        @Override public boolean equals(Object other) {
            return other instanceof PlayerKey key && id.equals(key.id) && incarnation == key.incarnation;
        }
        @Override public int hashCode() { return 31 * id.hashCode() + System.identityHashCode(incarnation); }
    }
    public static MotionPacket.Owner playerOwner(com.openggf.sprites.managers.SpriteManager sprites,
                                                  com.openggf.game.PlayableEntity player) {
        int index = sprites.getRegisteredSidekicks().indexOf(player);
        var id = index < 0 ? com.openggf.game.rewind.identity.PlayerRefId.mainPlayer()
                : com.openggf.game.rewind.identity.PlayerRefId.sidekick(index);
        return new MotionPacket.Owner(new PlayerKey(id, player),
                player.getCentreX(), player.getCentreY(), MotionPacket.Anchoring.FOLLOW);
    }
}
