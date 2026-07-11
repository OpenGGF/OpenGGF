package com.openggf.mods;

import java.util.List;
import java.util.Objects;

/** Immutable manager-facing outcome of the startup eligibility freeze. */
public record ModEligibility(String modId, Status status, List<Reason> reasons) {
    public ModEligibility {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(status, "status");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (status == Status.EFFECTIVE && !reasons.isEmpty()) {
            throw new IllegalArgumentException("Effective mods cannot have blocking reasons");
        }
        if (status != Status.EFFECTIVE && reasons.isEmpty()) {
            throw new IllegalArgumentException("Non-effective mods require a reason");
        }
    }

    public enum Status {
        EFFECTIVE,
        DISABLED,
        BLOCKED
    }

    public record Reason(String code, String message, List<String> relatedModIds) {
        public Reason {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            if (!code.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*") || message.isBlank()) {
                throw new IllegalArgumentException("Eligibility reasons require a code and message");
            }
            relatedModIds = List.copyOf(Objects.requireNonNull(relatedModIds, "relatedModIds"));
        }
    }
}
