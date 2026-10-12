package com.openggf.mods.code;

import com.openggf.mods.scene.ModSceneFactory;

/** A master-title entry as registered by a mod, before ownership is attached. Engine-internal. */
record ModTitleEntry(String label, ModSceneFactory factory) {
}
