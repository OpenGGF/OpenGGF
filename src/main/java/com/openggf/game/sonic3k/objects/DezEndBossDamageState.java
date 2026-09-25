package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** sub_7FB4E/sub_7FB92: only a released enemy publishes the root's damage latch. */
final class DezEndBossDamageState implements RewindStateful<DezEndBossDamageState.Value> {
    record Value(int health, int flash, boolean pending, boolean defeated) { }
    enum Contact { NONE, CONSUME, HIT }
    private int health = 8;
    private int flash;
    private boolean pending;
    private boolean defeated;

    /** Called in the enemy's slot, before the next root update consumes the latch. */
    Contact enemyContact(int rootX, int rootY, int enemyX, int enemyY, boolean flipY, int yVelocity) {
        if (pending || defeated) return Contact.NONE;
        // Check_InMyRange is centred on a0 (the enemy), testing a1 (the root),
        // with signed CMP.W/BLT/BGE. Thus enemy offsets -$28 are excluded, +$28 included.
        if ((short) rootX < (short) (enemyX - 0x28) || (short) rootX >= (short) (enemyX + 0x28)
                || (short) rootY < (short) (enemyY - 0x28) || (short) rootY >= (short) (enemyY + 0x28)) {
            return Contact.NONE;
        }
        boolean matching = flipY ^ ((short) yVelocity < 0);
        if (!matching) return Contact.CONSUME;
        pending = true;
        return Contact.HIT;
    }

    /** Returns true only on the root pass which consumes the eighth hit. */
    boolean update(ObjectServices services) {
        if (!pending || defeated) return false;
        if (flash == 0) {
            if (--health == 0) {
                defeated = true;
                return true;
            }
            flash = 0x20;
            services.playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        flashPalette(services, flash);
        if (--flash == 0) pending = false;
        return false;
    }
    private static void flashPalette(ObjectServices services, int counter) {
        try {
            var rom = services.rom();
            int source = Sonic3kConstants.PAL_DEZ_END_BOSS_HIT_FLASH_ADDR + ((counter & 1) == 0 ? 12 : 0);
            // word_7FC1A: six non-contiguous CRAM destinations, all on line 1.
            for (int i = 0; i < 6; i++) {
                int destination = rom.read16BitAddr(0x7FC1A + 2 * i);
                int color = (destination - 0xFC00) / 2;
                S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                        services.currentLevel(), services.graphicsManager(), S3kPaletteOwners.DEZ_END_BOSS,
                        S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, color / 16, color % 16,
                        rom.readBytes(source + i * 2, 2));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
    int health() { return health; }
    boolean invulnerable() { return pending; }
    boolean defeated() { return defeated; }
    @Override public Value captureRewindStateValue() { return new Value(health, flash, pending, defeated); }
    @Override public void restoreRewindStateValue(Value value) {
        health = value.health(); flash = value.flash(); pending = value.pending(); defeated = value.defeated();
    }
}
