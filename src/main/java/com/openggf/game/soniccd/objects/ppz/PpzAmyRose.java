package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/** Hosts the Time Attack palette write and retains the campaign placeholder until Amy's interaction is hosted. */
final class PpzAmyRose extends PlaceholderObjectInstance {
    PpzAmyRose(ObjectSpawn spawn) {
        super(spawn, "Amy Rose");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (CdSceneCoordinator.isTimeAttack(services().gameModule())
                && services().currentLevel() instanceof CdLevel level) {
            // ObjAmyRose (Level/Palmtree Panic/Objects/Amy Rose/Main.asm:7-25).
            // Pal_Level (Level/Palmtree Panic/Palette Data (Act 1 Present).asm:48-60) shares Pal_LevelEnd data.
            var art = services().gameModule().getGameService(CdObjectArtProvider.class);
            var image = art.image();
            int table = CdStageCatalogue.entry(level.payload()).paletteTable();
            services().updatePalette(1, image.paletteEntryFirstLine(table, 5));
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!CdSceneCoordinator.isTimeAttack(services().gameModule())) super.appendRenderCommands(commands);
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        // The Time Attack branch returns before CheckObjDespawn.
        return CdSceneCoordinator.isTimeAttack(services().gameModule());
    }

    @Override
    public PpzAmyRose recreateForRewind(RewindRecreateContext context) {
        return new PpzAmyRose(context.spawn());
    }
}
