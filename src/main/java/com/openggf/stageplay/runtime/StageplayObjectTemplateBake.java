package com.openggf.stageplay.runtime;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.graphics.RenderContext;
import com.openggf.level.objects.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Constructor-only visual preparation in a disposable source world, before the scene world opens.
 */
final class StageplayObjectTemplateBake {
    private StageplayObjectTemplateBake() {}

    static StageplayObjectDirection.VisualTemplate prepare(
            SceneRomLibrary library, ObjectActor actor, ObjectKey anchor) {
        return prepare(library, actor, anchor, null);
    }

    static StageplayObjectDirection.VisualTemplate prepare(
            SceneRomLibrary library,
            ObjectActor actor,
            ObjectKey anchor,
            ObjectSpawn originalPlacement) {
        return prepareSource(library, actor, anchor, originalPlacement, true, false).picture();
    }

    record Prepared(StageplayObjectDirection.VisualTemplate picture, int initialPlacementFlags) { }

    static Prepared prepareSource(SceneRomLibrary library, ObjectActor actor, ObjectKey anchor,
                                  ObjectSpawn originalPlacement, boolean picture, boolean freeFacing) {
        if (SessionManager.getCurrentWorldSession() != null)
            throw new IllegalStateException(
                    "Object visual preparation requires its own unoccupied offline world");
        var services = EngineServices.current();
        var config = services.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var rom =
                Objects.requireNonNull(
                        library.sourceRom(actor.gameId()),
                        "Supply object source ROM " + actor.gameId());
        GameModule module =
                new StageplayWorldModule(
                        GameModuleRegistry.createStockModule(GameId.fromCode(actor.gameId())),
                        null, java.util.Set.of(), true);
        // The disposable source may load music or construct objects that request SFX. Keep
        // those requests silent through retirement; the final scene installs its own audio profile.
        try (var ignored =
                services.audio()
                        .beginRewindReplay(0, 0, com.openggf.audio.rewind.AudioReplayReason.SEEK)) {
            GameplayModeContext temporary = null;
            try {
                services.roms().setRom(rom);
                RenderContext.reset();
                services.graphics().clearPaletteTextures();
                temporary =
                        SessionManager.openGameplaySession(
                                module, module, StockGameDataSources.pinned(rom, module), null);
                GameplaySessionFactory.attachManagers(temporary, services);
                services.graphics()
                        .bindRuntimeManagedReferences(
                                temporary.getCamera(), temporary.getFadeManager());
                GameplayTeamBootstrap.registerActiveTeam(
                        module, temporary.getSpriteManager(), config);
                temporary.getLevelManager().loadZoneAndAct(actor.zone(), actor.act());
                temporary.getLevelManager().skipPendingInitialTitleCardPresentation();
                var objects = temporary.getLevelManager().getObjectManager();
                var direction = ObjectDirectionAccess.forManager(objects);
                // Source-level load assembly owns its fixed native occupants in this disposable
                // world.
                // Construct the requested root only afterward: no LevelFrameStep or root/child
                // update is admitted to bootstrap its appearance. Its constructor may prepare
                // art/children.
                var root =
                        originalPlacement == null
                                ? create(
                                        objects,
                                        ObjectDirectionAccess.registry(objects),
                                        actor,
                                        anchor)
                                : ObjectDirectionAccess.createNative(objects, originalPlacement);
                if (root == null || root instanceof PlaceholderObjectInstance)
                    throw new IllegalArgumentException(
                            "The source registry has no concrete constructor for " + actor.id());
                // Logic-only load intentionally skips GPU publication. Publish the decoded level's
                // current native palette owners after constructor-time palette writes, without a
                // draw
                // or gameplay tick; capture consumes their raw ROM colors rather than a displayed
                // fade.
                temporary.getLevelManager().reloadLevelPalettes();
                var snapshot = picture ? direction.captureDynamic(root) : null;
                // Free retains native initialization/AI ownership. Only an explicitly declared
                // constructor mapping may translate entry facing into source placement flags;
                // reading a not-yet-initialized visual owner here would prevent Free lead-ins.
                int initialFlags = freeFacing && root instanceof ObjectVisualFacing.PlacementSource
                        ? ObjectVisualFacing.initialPlacementFlags(root, StageplayObjectCast.nativeFacing(anchor.facing()), 0) : 0;
                return new Prepared(snapshot, initialFlags);
            } catch (IOException failure) {
                throw new UncheckedIOException(
                        "Cannot prepare object source set for " + actor.id(), failure);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException(
                        "Cannot prepare directed object "
                                + actor.id()
                                + ": "
                                + failure.getMessage()
                                + ". Start as Free in its native set, then switch once appearance"
                                + " initializes",
                        failure);
            } finally {
                if (temporary != null && SessionManager.getCurrentGameplayMode() == temporary) {
                    try {
                        var objects = temporary.getLevelManager().getObjectManager();
                        if (objects != null) ObjectDirectionAccess.forManager(objects).close();
                    } finally {
                        try {
                            module.resetModuleScopedState();
                        } finally {
                            SessionManager.closeGameplaySession();
                        }
                    }
                }
            }
        }
    }

    static ObjectInstance create(
            ObjectManager objects, ObjectRegistry registry, ObjectActor actor, ObjectKey anchor) {
        return create(objects, registry, actor, anchor, 0);
    }

    static ObjectInstance create(ObjectManager objects, ObjectRegistry registry, ObjectActor actor,
                                 ObjectKey anchor, int initialFlags) {
        var source = spawn(actor, anchor);
        var spawn = new ObjectSpawn(source.x(), source.y(), source.objectId(), source.subtype(), initialFlags,
                source.respawnTracked(), source.rawYWord(), source.layoutIndex());
        if (actor.sourceKind() == ObjectSourceKind.RELEASED_ANIMAL) {
            var factory =
                    ObjectRegistryCatalog.releasedAnimalFactory(
                                    registry,
                                    new ObjectRegistryCatalog.Context(actor.zone(), actor.act()))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "This source set has no native released-animal"
                                                        + " provider"));
            return ObjectDirectionAccess.createAuxiliary(objects, spawn, factory.factory());
        }
        return ObjectDirectionAccess.createNative(objects, spawn);
    }

    static ObjectSpawn spawn(ObjectActor actor, ObjectKey anchor) {
        int x = nativeCoordinate(anchor.x()), y = nativeCoordinate(anchor.y());
        // A deliberate native source placement, independent of the requested picture facing.
        // Baking must not apply the director's transform once here and again while drawing.
        return new ObjectSpawn(
                x, y, actor.objectId(), actor.subtype(), 0, false, y, -1);
    }

    private static int nativeCoordinate(double value) {
        long rounded = Math.round(value);
        if (rounded < 0 || rounded > 0xffff)
            throw new IllegalArgumentException("Object anchor exceeds native coordinate range");
        return (int) rounded;
    }
}
