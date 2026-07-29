package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Shared SSZ/HPZ teleporter ($79). The sanctuary controller consumes its
 * readiness bit for the centre-exit gate.
 */
public final class SSZHPZTeleporterObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SlopedSolidProvider {
    private static final int HPZ_MAPPING_FRAME = 0xA;
    private static final int HPZ_PALETTE_LINE = 0;
    private static final byte[] HPZ_SLOPE = {
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0xB, 0xD, 0xF, 0x11,
            0x11, 0x11, 0x11, 0x11, 0x11, 0xF, 0xD, 0xB,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9
    };
    private static final SolidObjectParams SOLID =
            SolidObjectParams.of(0x23, 0x10, 0x10);

    private int x;
    private int y;
    private int buildTimer = 0x10;
    private boolean ready;

    private record RewindExtra(int x, int y, int buildTimer, boolean ready)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SSZHPZTeleporterObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZHPZTeleporter");
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public SSZHPZTeleporterObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SSZHPZTeleporterObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!ready && buildTimer-- <= 0) {
            ready = true;
        }
    }

    public boolean isReady() { return ready; }
    public void setReadyForTest(boolean value) { ready = value; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public int getPriorityBucket() { return 3; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public byte[] getSlopeData() { return HPZ_SLOPE; }
    @Override public boolean isSlopeFlipped() { return false; }
    int mappingFrameForTest() { return HPZ_MAPPING_FRAME; }
    int renderPaletteLineForTest() { return HPZ_PALETTE_LINE; }

    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        return super.captureRewindState().withObjectSubclassExtra(
                new RewindExtra(x, y, buildTimer, ready));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        super.restoreRewindState(snapshot);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            buildTimer = extra.buildTimer();
            ready = extra.ready();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER);
        if (renderer != null) {
            renderer.drawFrameIndex(
                    HPZ_MAPPING_FRAME, x, y, false, false, HPZ_PALETTE_LINE);
        }
    }
}
