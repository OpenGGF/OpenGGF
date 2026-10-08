package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Pattern;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;

/** loc_593EC's retained RAM, palette and queued VRAM tail after Load_Level/LoadSolids. */
record DezActTransitionHandoff(Sonic3kDEZEvents access, S3kDezZoneRuntimeState previous,
                               byte[][] palettes, byte[] blocks, byte[] art)
        implements SeamlessTransitionResourceHandoff {
    @Override public DeferredLevelResourceManifest deferredResources() { return DeferredLevelResourceManifest.EMPTY; }
    @Override public void transferAfterTargetInit() {
        var state = S3kRuntimeStates.currentDez(access.zoneRuntimeRegistry()).orElseThrow();
        if (state.actIndex() != 1) throw new IllegalStateException("DEZ transition did not install Act 2");
        state.setForegroundRoutine(0); state.setBackgroundRoutine(0); state.setBossFlag(false);
        state.setCameraStoredMinX(previous.cameraStoredMinX()); state.setCameraStoredMaxX(previous.cameraStoredMaxX());
        state.setCameraStoredMinY(previous.cameraStoredMinY()); state.setCameraStoredMaxY(previous.cameraStoredMaxY());
        state.setPanelBits(previous.panelBits());
        state.transitionPlane().copyFrom(previous.transitionPlane());
        var level = access.levelManager().getCurrentLevel();
        try {
            for (int line = 0; line < 4; line++) {
                byte[] data = line < 2 ? palettes[line] : access.rom().readBytes(0xA991C + (line - 1) * 32, 32);
                S3kPaletteWriteSupport.applyLine(access.paletteRegistryOrNull(), level, access.graphics(),
                        "s3k.dez.seamlessPalette", S3kPaletteOwners.PRIORITY_ZONE_EVENT, line, data, true);
            }
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        access.zoneLayoutMutationPipeline().queue(context -> {
            for (int offset = 0; offset < blocks.length; offset += 8) {
                int index = 0x2BC + offset / 8;
                var chunk = level.getChunk(index);
                int[] values = new int[6];
                for (int i = 0; i < 4; i++) values[i] = ((blocks[offset + i * 2] & 255) << 8) | (blocks[offset + i * 2 + 1] & 255);
                values[4] = chunk.getSolidTileIndex(); values[5] = chunk.getSolidTileAltIndex();
                context.surface().restoreChunkState(index, values);
            }
            for (int offset = 0; offset < art.length; offset += 32) {
                var pattern = new Pattern();
                pattern.fromSegaFormat(java.util.Arrays.copyOfRange(art, offset, offset + 32));
                context.surface().setPattern(0x292 + offset / 32, pattern);
            }
            Sonic3kPlcLoader.refreshAffectedRenderers(java.util.List.of(new Sonic3kPlcLoader.TileRange(0x292, art.length / 32)), access.levelManager());
            return MutationEffects.redrawAllTilemaps();
        });
    }
}
