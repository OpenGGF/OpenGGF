package com.openggf.game.sonic2;

/** ROM wind-tunnel coordinate and control branches. */
public record Sonic2WindTunnelProfile(int firstTunnelMinimumY,
        boolean clearsAirAbility, boolean clampsUpwardMovement) {
    public static final Sonic2WindTunnelProfile STOCK = new Sonic2WindTunnelProfile(0x400, false, false);
}
