package com.openggf.game.session;

import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;

import java.util.List;

/**
 * Engine-owned game patches installed in every {@link EngineContext}. Kept
 * outside the published Mod API surface: creators receive patches through
 * the resolution service, never by enumerating this list.
 */
public final class BuiltInPatches {

    private BuiltInPatches() {
    }

    /** Built-in registrations in registration order. */
    public static List<RegisteredPatch> registrations() {
        return List.of(new RegisteredPatch(
                new PatchOwner.BuiltIn(Kis2GamePatch.ID), Kis2GamePatch.ID, new Kis2GamePatch(), 0));
    }
}
