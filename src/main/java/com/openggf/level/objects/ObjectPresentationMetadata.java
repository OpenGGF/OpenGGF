package com.openggf.level.objects;

/** Native producer identity for picture-only transforms; never a collision classification. */
@com.openggf.game.ModApi
public record ObjectPresentationMetadata(Role role, int anchorX, int anchorY) {
    public static final ObjectPresentationMetadata NONE = new ObjectPresentationMetadata(Role.NONE, 0, 0);
    public ObjectPresentationMetadata { java.util.Objects.requireNonNull(role, "role"); }
    @com.openggf.game.ModApi public enum Role { NONE, BADNIK, PICKUP }
}
