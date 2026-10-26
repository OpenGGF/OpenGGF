package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomCondition;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(RequiresRomCondition.class)
class TestS3kSlotGlassPriority {

    @Test
    void runtimePreservesEveryRomGlassDescriptorAndKeepsSlotPlayerLowPriority() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        HeadlessTestFixture.builder()
                .withZoneAndAct(0x15, 0)
                .startPosition(S3kSlotRomData.SLOT_BONUS_PLAYER_START_X,
                        S3kSlotRomData.SLOT_BONUS_PLAYER_START_Y)
                .startPositionIsCentre()
                .build();

        LevelManager level = GameServices.level();
        int[] romDescriptors = glassDescriptors(level, false);

        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime(S3kSlotRomTestData.resources());
        runtime.bootstrap();

        assertTrue(runtime.isInitialized());
        assertArrayEquals(romDescriptors, glassDescriptors(level, true),
                "Bootstrap must preserve complete ROM descriptor words, including priority and empty tiles");
        var provider = level.getZoneFeatureProvider();
        for (int pass = 0; pass < 3; pass++) {
            provider.renderAfterBackground(GameServices.camera(), level.getFrameCounter());
            assertArrayEquals(romDescriptors, glassDescriptors(level, true),
                    "Repeated ordinary provider draws must preserve every original descriptor word");
        }

        assertTrue(GameServices.sprites().getSprite("sonic") instanceof S3kSlotBonusPlayer);
        AbstractPlayableSprite slotPlayer = (AbstractPlayableSprite) GameServices.sprites().getSprite("sonic");
        assertFalse(slotPlayer.isHighPriority(),
                "ROM Obj_Sonic_RotatingSlotBonus keeps the slot player low-priority for foreground/layout layering");

        runtime.update(0);
        assertTrue(hasVisibleNonMachineCell(runtime.activeVisibleCellsForTest()),
                "The visible cage/walls should also be available through the slot layout pass");
    }

    private static boolean hasVisibleNonMachineCell(S3kSlotRenderBuffers.VisibleCells cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.cellIdAt(i) != 0x09) {
                return true;
            }
        }
        return false;
    }

    private static int[] glassDescriptors(LevelManager level, boolean liveTilemap) {
        int[] descriptors = new int[13 * 13];
        int at = 0;
        for (int y = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y - 0x30;
             y <= S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y + 0x30;
             y += 8) {
            for (int x = S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X - 0x30;
                 x <= S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X + 0x30;
                 x += 8) {
                descriptors[at++] = liveTilemap
                        ? level.getForegroundTileDescriptorFromTilemapAtWorld(x, y)
                        : level.getForegroundTileDescriptorAtWorld(x, y);
            }
        }
        return descriptors;
    }
}
