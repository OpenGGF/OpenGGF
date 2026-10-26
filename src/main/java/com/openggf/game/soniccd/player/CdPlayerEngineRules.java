package com.openggf.game.soniccd.player;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.DynamicArtDmaServiceModel;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PaletteWhiteFadeModel;
import com.openggf.game.rules.PresentationOnlyRules;
import com.openggf.sprites.playable.CharacterPhysicsSpec;

/** Engine projection for the complete native slot; shared movement never consumes this profile. */
public final class CdPlayerEngineRules implements PhysicsProvider {
    // Native Init/Main and MODE constants, not a cartridge profile or gameplay fallback.
    // scddisasm Sonic/Main.asm:135-155 (Init), 1759-1760,1848-1849 (roll radii),
    // 1809 (jump), 1496-1498 (roll cap),1729 (minimum roll). Generic slope/
    // deceleration fields are inert adapter values: native MODE owns their execution.
    public static final CharacterPhysicsSpec SPEC = new CharacterPhysicsSpec(new PhysicsProfile(
            (short) 12, (short) 128, (short) 12, (short) 0x600, (short) 0x680,
            (short) 0x20, (short) 0x14, (short) 0x50, (short) 0x20,
            (short) 0x80, (short) 0x80, (short) 0x1000,
            (short) 28, (short) 38, (short) 9, (short) 19, (short) 7, (short) 14,
            false, (short) 2));

    private static final PhysicsModifiers UNUSED_MODIFIERS = new PhysicsModifiers(
            1, 1, 1, 1, (short) 0x680, (short) 0, (short) 0, 1, 1, 1, 1);
    private static final GameRules RULES = PresentationOnlyRules.create(
            new CameraRules((short) 0, false, 0, false, false, false),
            new ObjectInteractionRules(false, false, false, false, false, false,
                    false, false, true, false, false, false, false, false, false, false,
                    ObjectInteractionRules.NO_DUCK_TOUCH_BOX,
                    ObjectInteractionRules.NO_DUCK_TOUCH_BOX, false),
            DynamicArtDmaServiceModel.EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT,
            PaletteWhiteFadeModel.SCREEN_OVERLAY);

    @Override public PhysicsProfile getProfile(String characterType) {
        if (!"sonic".equals(characterType)) throw new UnsupportedOperationException("CD terrain player is Sonic only");
        return SPEC.profile();
    }

    // Water/shoes/controller managers are bypassed by NativePlayableSlot. This is
    // an explicit inert adapter, not a claim of shared CD modifier implementation.
    @Override public PhysicsModifiers getModifiers() { return UNUSED_MODIFIERS; }
    @Override public GameRules getRules() { return RULES; }
}
