package com.openggf.game.sonic2.kis2;

import com.openggf.data.Game;
import com.openggf.game.GameDataSource;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.level.Pattern;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.logging.Logger;

/**
 * Sonic 2 decorated by the Knuckles in Sonic 2 lock-on (tier one). Overrides
 * only what the lock-on program changes: physics, the game data (layouts, art,
 * palette) and the life-icon art; everything else delegates to stock S2.
 */
final class Kis2GameModule extends DelegatingGameModule {

    private static final Logger LOGGER = Logger.getLogger(Kis2GameModule.class.getName());

    private final PatchContext context;
    private PhysicsProvider physicsProvider;
    private ObjectArtProvider objectArtProvider;
    private RomByteReader sk;
    private Kis2Game game;

    Kis2GameModule(GameModule base, PatchContext context) {
        super(base, Kis2Constants.PATCH_ID);
        this.context = context;
    }

    @Override
    public Game createGame(Rom rom) {
        // Run the base module's side effects (PLC service, active ROM) and
        // replace only the game data object.
        base().createGame(rom);
        game = new Kis2Game(rom, skReader());
        return game;
    }

    @Override
    public Game createGame(GameDataSource source) {
        return createGame(source.rom().orElseThrow(() ->
                new IllegalStateException("Knuckles in Sonic 2 requires a ROM data source")));
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Kis2PhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic2ObjectArtProvider(this::loadLifeIcon);
        }
        return objectArtProvider;
    }

    private Pattern[] loadLifeIcon() {
        try {
            return new Kis2PlayerArt(skReader()).loadLifeIcon();
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("KiS2 life icon unavailable, keeping the stock Sonic 2 icon: " + e.getMessage());
            return null;
        }
    }

    private RomByteReader skReader() {
        if (sk == null) {
            try {
                sk = context.openLogicalRom(LogicalRom.SK);
            } catch (IOException e) {
                throw new UncheckedIOException("Logical S&K ROM unavailable for Knuckles in Sonic 2", e);
            }
        }
        return sk;
    }
}
