package com.openggf.game.session;

import com.openggf.architecture.CompositionRoot;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.launch.GameSupportNotices;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Engine-owned game patches installed in every {@link EngineContext}. Kept
 * outside the published Mod API surface: creators receive patches through
 * the resolution service, never by enumerating this list. Like
 * {@code GameModuleRegistry}, it is the composition root that names the
 * concrete game-package patches the engine ships.
 */
@CompositionRoot
public final class BuiltInPatches {

    private static final Kis2GamePatch KIS2 = new Kis2GamePatch();

    private BuiltInPatches() {
    }

    /** Built-in registrations in registration order. */
    public static List<RegisteredPatch> registrations() {
        return List.of(new RegisteredPatch(
                new PatchOwner.BuiltIn(Kis2GamePatch.ID), Kis2GamePatch.ID, KIS2, 0));
    }

    /** Launch notices for built-in patches, evaluated against the hub's ROM catalogue. */
    public static Optional<GameSupportNotices.Stage> launchNotice(GameplayLaunchRequest request,
            Supplier<RomImageCatalogue> catalogue) {
        return KIS2.usesTierOne(request, catalogue)
                ? Optional.of(GameSupportNotices.Stage.KIS2_TIER_ONE) : Optional.empty();
    }
}
