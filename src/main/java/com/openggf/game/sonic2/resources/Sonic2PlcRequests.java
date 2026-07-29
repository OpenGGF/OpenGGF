package com.openggf.game.sonic2.resources;

import com.openggf.level.objects.ObjectServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;

import java.io.IOException;

/** Object-owner bridge for ROM PLC submissions. */
public final class Sonic2PlcRequests {
    private Sonic2PlcRequests() {
    }

    /**
     * Appends the supplied ROM PLCs in the caller's native order.
     *
     * <p>Object construction tests intentionally omit a game module.  In that
     * presentation-only environment there is no logical hardware queue to own
     * a submission, so this is a no-op; a live session always resolves the
     * game-owned service through injected object services.
     */
    public static void append(ObjectServices services, int... plcIds) {
        if (services == null || services.gameModule() == null) {
            return;
        }
        Sonic2PlcService plcService = services.gameModule().getGameService(Sonic2PlcService.class);
        ObjectArtProvider provider = services.gameModule().getObjectArtProvider();
        if (plcService == null || !(provider instanceof Sonic2ObjectArtProvider sonic2Provider)) {
            return;
        }
        try {
            if (services.levelManager() == null) return;
            Sonic2RuntimePlcPublisher.append(sonic2Provider, plcService,
                    services.levelManager()::refreshObjectArtPatterns, plcIds);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to submit Sonic 2 ROM PLC " + plcIds[0], e);
        }
    }
}
