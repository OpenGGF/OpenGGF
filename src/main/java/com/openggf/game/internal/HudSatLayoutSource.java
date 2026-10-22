package com.openggf.game.internal;

/** Engine-owned companion to ObjectArtProvider; keeps native SAT geometry outside its creator API. */
public interface HudSatLayoutSource {
    /** Returns the ROM-loaded HUD footprint, or null before the provider has loaded its art. */
    HudSatLayout getHudSatLayout();
}
