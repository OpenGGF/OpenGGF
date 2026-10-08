package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

/** sub_5A79E: four uncompressed tiles from (laser frame + 2)*4 into VRAM tile $208. */
public final class DezFinalLaserArt {
    private DezFinalLaserArt() { }

    public static void update(ObjectServices services, DezFinalBossZoneRuntimeState state) {
        int frame = state.laserOffset();
        if (frame == state.uploadedLaserOffset()) return;
        final byte[] pixels;
        try {
            // addq.w / lsl.w precede the long source-address add; preserve word wrap.
            int offset = ((frame + 2) << 2) & 0xFFFF;
            pixels = services.rom().readBytes(0x15A674 + offset, 0x80);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        state.uploadedLaserOffset(frame);
        services.zoneLayoutMutationPipeline().queue(context -> {
            var dirtyPatterns = new java.util.BitSet();
            for (int tile = 0; tile < 4; tile++) {
                var pattern = new Pattern();
                pattern.fromSegaFormat(Arrays.copyOfRange(pixels, tile * 32, (tile + 1) * 32));
                dirtyPatterns.or(context.surface().setPattern(0x208 + tile, pattern).dirtyPatterns());
            }
            Sonic3kPlcLoader.refreshAffectedRenderers(
                    List.of(new Sonic3kPlcLoader.TileRange(0x208, 4)), services.levelManager());
            return new MutationEffects(dirtyPatterns, false, false, false, true, false, false);
        });
    }
}
