package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomGame;
import com.openggf.data.RomManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit catalogue setup for the PR223 post-smoke fixtures (2026-10-24).
 * Only declared in-memory base keys change; no ROM is opened, copied or closed.
 * Restoration preserves resolved Object values, not private map key-presence metadata.
 */
public final class RomCatalogueFixture implements AutoCloseable {
    private final SonicConfigurationService configuration;
    private final RomManager manager;
    private final EnumMap<RomGame, Path> images = new EnumMap<>(RomGame.class);
    private final EnumMap<SonicConfiguration, Object> originalValues =
            new EnumMap<>(SonicConfiguration.class);
    private boolean closed;

    private RomCatalogueFixture(SonicConfigurationService configuration, RomManager manager,
            Map<RomGame, Path> declaredImages) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.manager = Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(declaredImages, "declaredImages");
        for (var entry : declaredImages.entrySet()) {
            RomGame game = Objects.requireNonNull(entry.getKey(), "declared game");
            Path path = Objects.requireNonNull(entry.getValue(), "declared path")
                    .toAbsolutePath().normalize();
            images.put(game, path);
        }
        validateBindings();
        for (RomGame game : images.keySet()) {
            originalValues.put(game.configurationKey(),
                    configuration.getConfigValue(game.configurationKey()));
        }
        installBindings();
    }

    public static RomCatalogueFixture bind(SonicConfigurationService configuration,
            RomManager manager, Map<RomGame, Path> declaredImages) {
        return new RomCatalogueFixture(configuration, manager, declaredImages);
    }

    /** Reapply only at an explicit fixture reset; keep the first restoration snapshot. */
    public void reapply() {
        if (closed) throw new IllegalStateException("Catalogue fixture is closed");
        validateBindings();
        installBindings();
    }

    private void validateBindings() {
        for (var entry : images.entrySet()) {
            if (!Files.isRegularFile(entry.getValue()) || !Files.isReadable(entry.getValue())) {
                throw new IllegalArgumentException("Fixture ROM must be a readable regular file: "
                        + entry.getValue());
            }
            if (configuration.hasSessionOverride(entry.getKey().configurationKey())) {
                throw new IllegalStateException("Fixture cannot replace a session-owned ROM key: "
                        + entry.getKey().configurationKey());
            }
        }
    }

    private void installBindings() {
        for (var entry : images.entrySet()) {
            configuration.setConfigValue(entry.getKey().configurationKey(), entry.getValue().toString());
        }
        manager.reloadCatalogue();
    }

    /** Restore base values even if a new session owner now overlays one of those keys. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        originalValues.forEach(configuration::setConfigValue);
        manager.reloadCatalogue();
    }
}
