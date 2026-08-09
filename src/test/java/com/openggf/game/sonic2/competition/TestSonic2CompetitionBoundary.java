package com.openggf.game.sonic2.competition;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionBoundary {

    private static final String REV01_SHA1 =
            "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9";
    private static final int LEVEL_ORDER_ADDRESS = 0x008E52;

    @Test
    void rev01CompetitionLevelSelectUsesEhzMczCnzAndSpecialStage()
            throws Exception {
        Rom rom = GameServices.rom().getRom();
        byte[] romBytes = rom.readAllBytes();
        String actualSha1 = HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-1").digest(romBytes));

        assertEquals(REV01_SHA1, actualSha1,
                "competition evidence requires Sonic 2 World REV01");

        RomByteReader reader = new RomByteReader(romBytes);
        List<Integer> actualOrder = List.of(
                reader.readU16BE(LEVEL_ORDER_ADDRESS),
                reader.readU16BE(LEVEL_ORDER_ADDRESS + 2),
                reader.readU16BE(LEVEL_ORDER_ADDRESS + 4),
                reader.readU16BE(LEVEL_ORDER_ADDRESS + 6));

        assertEquals(List.of(0x0000, 0x0B00, 0x0C00, 0xFFFF), actualOrder,
                "$008E52 must order EHZ1, MCZ1, CNZ1, then $FFFF as the "
                        + "special-stage entry (not a fourth normal level)");
    }

    @Test
    void ordinarySonicAndTailsBootstrapKeepsP2CpuControlled() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        SpriteManager sprites = new SpriteManager(config);
        GameplayTeamBootstrap.BootstrappedTeam team =
                GameplayTeamBootstrap.registerActiveTeam(
                        new Sonic2GameModule(), sprites, config);

        assertEquals(2, sprites.getAllSprites().size());
        assertEquals(1, team.sidekicks().size());

        AbstractPlayableSprite main = team.mainSprite();
        AbstractPlayableSprite secondary = team.sidekicks().getFirst();
        assertFalse(main.isCpuControlled(), "ordinary P1 must remain human-controlled");
        assertNull(main.getCpuController(), "ordinary P1 must not have a sidekick controller");
        assertTrue(secondary.isCpuControlled(),
                "ordinary P2 is a CPU sidekick, not a human competition slot");
        assertNotNull(secondary.getCpuController());
        assertInstanceOf(SidekickCpuController.class, secondary.getCpuController());
    }
}
