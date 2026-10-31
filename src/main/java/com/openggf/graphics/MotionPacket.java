package com.openggf.graphics;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Picture-only ownership. Native frames and rewind payloads contain none of this metadata. */
public final class MotionPacket {
    public enum Anchoring { FOLLOW, WORLD, SCREEN }
    public sealed interface Key permits ObjectKey, PlayerKey { }
    public record ObjectKey(ObjectRefId id) implements Key {
        public ObjectKey { Objects.requireNonNull(id); }
    }
    /** The incarnation uses Java identity, so a recreated roster member cannot pair with its predecessor. */
    public record PlayerKey(PlayerRefId id, Object incarnation) implements Key {
        public PlayerKey { Objects.requireNonNull(id); Objects.requireNonNull(incarnation); }
        @Override public boolean equals(Object other) {
            return other instanceof PlayerKey key && id.equals(key.id) && incarnation == key.incarnation;
        }
        @Override public int hashCode() { return 31 * id.hashCode() + System.identityHashCode(incarnation); }
    }
    public record Owner(Key key, int x, int y, Anchoring anchoring) {
        public Owner { Objects.requireNonNull(key); Objects.requireNonNull(anchoring); }
    }
    private final int cameraX, cameraY;
    private final Map<Key, Owner> owners;
    private final IdentityHashMap<SpritePresentation.Tile, Key> tiles;

    MotionPacket(int cameraX, int cameraY, IdentityHashMap<SpritePresentation.Tile, Owner> collected) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        var owners = new java.util.HashMap<Key, Owner>();
        tiles = new IdentityHashMap<>();
        collected.forEach((tile, owner) -> {
            owners.put(owner.key(), owner);
            tiles.put(tile, owner.key());
        });
        this.owners = Map.copyOf(owners);
    }
    public int cameraX() { return cameraX; }
    public int cameraY() { return cameraY; }
    public Map<Key, Owner> owners() { return owners; }
    public Owner owner(SpritePresentation.Tile tile) {
        var key = tiles.get(tile);
        return key == null ? null : owners.get(key);
    }
}
