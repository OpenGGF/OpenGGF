package com.openggf.game.sonic2.resources;

import com.openggf.game.sonic2.Sonic2ObjectArtProvider;

import java.io.IOException;

/** Commits one ROM PLC's logical FIFO work and eager art from a shared preflight. */
public final class Sonic2RuntimePlcPublisher {
    private Sonic2RuntimePlcPublisher() {
    }

    public static boolean append(Sonic2ObjectArtProvider artProvider, Sonic2PlcService plcService,
                                 Runnable refreshRendererCache, int... plcIds) throws IOException {
        Sonic2ObjectArtProvider.PreparedPlc prepared = artProvider.preparePlcs(plcIds);
        artProvider.preflightPreparedPlc(prepared);
        Sonic2PlcService.PreparedAppendBatch logical = plcService.prepareAppendBatch(plcIds);
        plcService.appendPrepared(logical);
        boolean published = artProvider.publishPreparedPlc(prepared);
        if (published) {
            if (refreshRendererCache == null) {
                throw new IllegalStateException("Runtime PLC renderer publication requires a cache refresh owner");
            }
            refreshRendererCache.run();
        }
        return published;
    }
}
