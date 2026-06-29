package com.openggf.game.solid;

public record PreContactState(
        short xSpeed,
        short ySpeed,
        boolean rolling,
        boolean air,
        int animationId) {

    public PreContactState(short xSpeed, short ySpeed, boolean rolling, int animationId) {
        this(xSpeed, ySpeed, rolling, false, animationId);
    }

    public static final PreContactState ZERO =
            new PreContactState((short) 0, (short) 0, false, false, 0);
}
