package com.openggf.game.internal;

/**
 * Game service marker: the game's HUD is drawn by its own level objects (Sonic CD's
 * {@code ObjHUD}), so the level manager installs no shared overlay HUD.
 */
public interface ObjectDrawnHud {
}
