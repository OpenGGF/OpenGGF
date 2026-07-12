package com.openggf;

import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;

import java.util.Objects;
import java.util.logging.Logger;

/** Composition-root adapter from neutral team-bootstrap diagnostics to the mod manager. */
final class ModCharacterFallbackFindings {
    private static final Logger LOG = Logger.getLogger(ModCharacterFallbackFindings.class.getName());

    private ModCharacterFallbackFindings() { }

    static GameplayTeamBootstrap.CharacterFindingSink sink(ModRuntimeFindingStore findings) {
        Objects.requireNonNull(findings, "findings");
        return finding -> {
            String owner;
            try {
                owner = CharacterKey.parsePersisted(finding.requestedCode()).ownerModId().orElse(null);
            } catch (IllegalArgumentException invalidCode) {
                owner = null;
            }
            String code = finding.reason() == PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER
                    ? "MOD_CHARACTER_DISABLED_FALLBACK" : "MOD_CHARACTER_UNKNOWN_FALLBACK";
            String message = "Playable character " + finding.requestedCode()
                    + " fell back to " + finding.fallbackCode();
            if (owner == null) {
                LOG.warning(message);
                return;
            }
            findings.upsertOwnerFinding(owner, new ModFinding(ModFindingSeverity.WARNING,
                    code, message, finding.requestedCode()));
        };
    }
}
