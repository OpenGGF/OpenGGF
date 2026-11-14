package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The machine's spikes, object $36 ({@code BossSpikesObject}, objects/r6/boss.asm:715-800), allocated by Eggman: $40
 * pixels above Eggman, drawn every frame. Eggman's {@code var_34} drives it: 0 still, 1 (landed) starts a spin at a
 * 20-frame period that shortens by 3 each frame step down to 1 (and bumps Eggman's value to 2), negative (the defeat)
 * deletes it. The frame steps 0-3; {@code BossSpikesAnim} supplies only the end marker's sign.
 */
final class WwzBossSpikes extends WwzBossPiece {
    static final int ID = 0x36;
    // var_2e: Eggman.
    WwzBossEggman eggman;
    /** oVar1F: the current step period. */
    private int period;

    WwzBossSpikes(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzBossSpikes");
        this.eggman = eggman;
    }

    private WwzBossSpikes(ObjectSpawn spawn) {
        super(spawn, "WwzBossSpikes");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // BossSpikesInit (objects/r6/boss.asm:753-762)
            routine = 2;
            priority = 5;
            width = 0x10;
            yRadius = 0x10;
            tileWord = WwzBossArt.PART_TILE;
            return;
        }
        if (eggman == null) return;                          // BossSpikesFollowParent (:765-776)
        setXWord(eggman.xWord());
        setYWord(eggman.yWord() - 0x40);
        int state = eggman.spikesState & 0xFF;
        if (state == 0) return;                              // BossSpikesHiddenReturn
        if ((state & 0x80) != 0) {                           // BossSpikesDelete: addq.l #4,sp; jmp DeleteObject
            deleteObject();
            return;
        }
        if (state == 1) {                                    // BossSpikesHit (:820-825)
            period = 0x14;
            animTime = 1;
            eggman.spikesState = 2;
            return;
        }
        animTime = (animTime - 1) & 0xFF;                    // BossSpikesAnimate (:790-794)
        if (animTime != 0) return;
        mapFrame = (mapFrame + 1) & 0xFF;                    // BossSpikesAdvanceFrame (:797-817)
        var image = stageImage();
        int table = WwzBossCatalogue.offset(payload(), "spikesAnimation");
        if (image != null && table >= 0 && (byte) image.u8(table + mapFrame) < 0) mapFrame = 0;
        int next = (byte) (period - 3);
        if (next < 1) next = 1;
        period = next & 0xFF;
        animTime = period;
    }

    private com.openggf.game.soniccd.CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    @Override String sheet() { return WwzBossArt.SPIKES; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzBossSpikes(spawn);
    }
}
