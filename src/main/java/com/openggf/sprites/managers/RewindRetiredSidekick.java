package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Derived bindings for a script-retired roster; gameplay values remain in sprite snapshots. */
record RewindRetiredSidekick(AbstractPlayableSprite sprite, String characterName, boolean temporary) { }
