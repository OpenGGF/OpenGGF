package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

/** Read-only projection of human bodies and their fixed tail owner, in native slot order. */
public final class Sonic2CompetitionPlayerDisplay {
    private Sonic2CompetitionPlayerDisplay() { }
    /** Obj01_Init (s2.asm:36179-36201), Obj02_Init (s2.asm:38899-38931). */
    public static ObjectSlot body(RomByteReader rom, AbstractPlayableSprite player, int slot) {
        if (!player.shouldRefreshRenderFlagThisFrame()) return null;
        int flags = 4 | (player.getRenderHFlip() ? 1 : 0) | (player.getRenderVFlip() ? 2 : 0);
        int mapping = slot == 0 ? Sonic2Constants.MAP_UNC_SONIC_ADDR : Sonic2Constants.MAP_UNC_TAILS_ADDR;
        int art = slot == 0 ? Sonic2Constants.ART_TILE_SONIC : Sonic2Constants.ART_TILE_TAILS;
        return new ObjectSlot(slot + 1, flags, player.getCentreX() & 65535, player.getCentreY() & 65535,
                24, slot == 0 ? 19 : 15, new Mapping(Sonic2RomAddresses.address(rom, mapping), player.getMappingFrame()),
                Sonic2CompetitionSpriteMapper.nativeArtWord(art) | (player.isHighPriority() ? 0x8000 : 0), null);
    }
    /** Obj05_Init/Main (s2.asm:41713-41736); Tails_Tails is native slot128. */
    public static ObjectSlot tail(RomByteReader rom, AbstractPlayableSprite tails) {
        var controller = tails.getTailsTailsController();
        var pose = controller == null ? null : controller.presentation();
        if (pose == null) return null;
        return new ObjectSlot(5, 4 | (pose.flipX() ? 1 : 0) | (pose.flipY() ? 2 : 0),
                tails.getCentreX() & 65535, tails.getCentreY() & 65535, 24, 0,
                new Mapping(Sonic2RomAddresses.address(rom, Sonic2Constants.MAP_UNC_TAILS_ADDR), pose.mappingFrame()),
                Sonic2CompetitionSpriteMapper.nativeArtWord(Sonic2Constants.ART_TILE_TAILS_TAILS)
                        | (tails.isHighPriority() ? 0x8000 : 0), null);
    }
}
