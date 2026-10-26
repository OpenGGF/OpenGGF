package com.openggf.game.soniccd;

import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.DynamicArtDmaServiceModel;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PaletteWhiteFadeModel;
import com.openggf.game.rules.PresentationOnlyRules;

/** Only consulted presentation rules are available; there is no fabricated CD player profile. */
final class SonicCdStaticRules implements PhysicsProvider {
    private final GameRules rules = PresentationOnlyRules.create(
            // No following/look, water shimmer or wrap simulation in the frozen camera view.
            new CameraRules((short) 0, false, 0, false, false, false),
            null,
            // The coordinator requires a policy at attachment. HOLD submits/claims no work,
            // and no player art exists to audit. This is not a CD DMA timing model.
            DynamicArtDmaServiceModel.EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT,
            // Session attachment consults this; static admission resets fades and never starts one.
            PaletteWhiteFadeModel.SCREEN_OVERLAY);

    @Override public GameRules getRules() { return rules; }
    @Override public PhysicsProfile getProfile(String characterType) {
        throw SonicCd.unsupported("player physics for " + characterType);
    }
    @Override public PhysicsProfile getInitProfile(String characterType) {
        throw SonicCd.unsupported("player initialization physics for " + characterType);
    }
    @Override public PhysicsModifiers getModifiers() { throw SonicCd.unsupported("player physics modifiers"); }
}
