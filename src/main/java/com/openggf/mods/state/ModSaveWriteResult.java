package com.openggf.mods.state;

/** Checked publication outcome. A failed required backup prevents primary publication. */
@com.openggf.game.ModApi
public enum ModSaveWriteResult {
    SAVED, REPLACEMENT_REQUIRED, TOO_LARGE, BACKUP_FAILED, PRIMARY_FAILED;

    public boolean saved() { return this == SAVED; }
}
