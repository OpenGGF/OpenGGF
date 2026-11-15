package com.openggf.game.rules;

import java.util.Objects;

/**
 * Immutable game-wide behavioural facts owned by a {@link com.openggf.game.GameModule}.
 *
 * <p>This is the module's typed policy subtree, not a second composition root:
 * it holds no factories, callbacks, lifecycle hooks or session state. Consumers
 * read the narrow record they need ({@code module.getPolicies().rules().ring()})
 * rather than depending on this aggregate. Adding a component is an architecture
 * change reviewed against {@code docs/architecture/per-game-rule-placement.md}.
 *
 * <p>Cross-game donation composes policies only through
 * {@link CrossGamePolicyComposer}; there is deliberately no method here that
 * replaces a category.
 *
 * @param rules shared-runtime rule groups (movement, collision, camera, rings,
 *              objects, sidekick CPU, power-ups, drowning, presentation models)
 */
@com.openggf.game.ModApi
public record GamePolicies(GameRules rules) {

    public GamePolicies {
        Objects.requireNonNull(rules, "rules");
    }
}
