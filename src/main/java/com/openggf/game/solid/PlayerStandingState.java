package com.openggf.game.solid;

@com.openggf.game.ModApi
public record PlayerStandingState(
        ContactKind kind,
        boolean standing,
        boolean pushing) {

    public static final PlayerStandingState NONE =
            new PlayerStandingState(ContactKind.NONE, false, false);
}
