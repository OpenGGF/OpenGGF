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
    public sealed interface Binding permits Owner, Screen { }
    /** Screen geometry has no world anchor or owner identity. */
    public enum Screen implements Binding { INSTANCE }
    public record Owner(Key key, int x, int y, Anchoring anchoring) implements Binding {
        public Owner { Objects.requireNonNull(key); Objects.requireNonNull(anchoring); }
    }
    private final int cameraX, cameraY;
    private final Map<Key, Owner> owners;
    private final IdentityHashMap<SpritePresentation.Tile, Binding> tiles;

    MotionPacket(int cameraX, int cameraY, IdentityHashMap<SpritePresentation.Tile, ? extends Binding> collected) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        var owners = new java.util.HashMap<Key, Owner>();
        tiles = new IdentityHashMap<>();
        collected.forEach((tile, owner) -> {
            if (owner instanceof Owner anchor) owners.put(anchor.key(), anchor);
            tiles.put(tile, owner);
        });
        this.owners = Map.copyOf(owners);
    }
    private MotionPacket(int cameraX, int cameraY, Map<Key, Owner> owners) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.owners = owners;
        tiles = new IdentityHashMap<>();
    }
    /** Retire old tile/subject/art references once only anchors are needed for pairing. */
    public MotionPacket anchorsOnly() { return new MotionPacket(cameraX, cameraY, owners); }
    public int cameraX() { return cameraX; }
    public int cameraY() { return cameraY; }
    public Map<Key, Owner> owners() { return owners; }
    public boolean screen(SpritePresentation.Tile tile) { return tiles.get(tile) == Screen.INSTANCE; }
    public Owner owner(SpritePresentation.Tile tile) {
        return tiles.get(tile) instanceof Owner owner ? owner : null;
    }
}
