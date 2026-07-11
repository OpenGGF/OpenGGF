package com.openggf.game.solid;

@com.openggf.game.ModApi
public record PostContactState(
        short xSpeed,
        short ySpeed,
        boolean air,
        boolean onObject,
        boolean pushing) {

    public static final PostContactState ZERO =
            new PostContactState((short) 0, (short) 0, false, false, false);
}
