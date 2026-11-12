package com.openggf.game.nativemenu;

import com.openggf.data.RomManager;

/**
 * A ROM-native mode a stock game offers outside its own title flow, listed
 * under the master title's Extras. It is not a game of its own: opening it
 * launches its host game and shows the extra's first screen in place of that
 * game's title.
 */
public interface NativeExtra {
    /** Stable identifier. */
    String id();

    /** The Extras list label, in the current UI language. */
    String label();

    /** {@code MasterTitleScreen.GameEntry} game id of the game that hosts the extra. */
    String hostGameId();

    /** True when every ROM the extra needs is in the user's catalogue. */
    boolean available(RomManager roms);

    /** Builds the extra's first screen once its host game has loaded. */
    NativeMenuScreen open(NativeMenuHostContext context);
}
