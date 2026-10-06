package slaytherobotnik.core;

/**
 * Optional behaviour a card has while it sits in a pile rather than when it is played:
 * Burn hurting you at the end of the turn, a card that does something when discarded,
 * Drop Dash growing while it is Retained, and so on. Unused hooks stay {@code null}.
 *
 * <pre>{@code
 * CardHooks.builder().onEndOfTurnInHand((combat, card) -> combat.damagePlayer(2)).build()
 * }</pre>
 */
public record CardHooks(
        CardCallback onDraw,
        CardCallback onEndOfTurnInHand,
        CardCallback onManualDiscard,
        CardCallback onExhaust,
        CardCallback onRetain,
        PlayedCallback onOtherCardPlayed,
        CostAdjuster costAdjuster) {

    public static Builder builder() {
        return new Builder();
    }

    /** Called with the combat and the card the hook belongs to. */
    @FunctionalInterface
    public interface CardCallback {
        void run(Combat combat, Card self);
    }

    /** Called while this card is in hand and another card is played. */
    @FunctionalInterface
    public interface PlayedCallback {
        void run(Combat combat, Card self, Card played);
    }

    /** Adjusts the card's cost each time it is evaluated (for example "costs 1 less per discard this turn"). */
    @FunctionalInterface
    public interface CostAdjuster {
        int adjust(Combat combat, Card self, int cost);
    }

    public static final class Builder {
        private CardCallback onDraw;
        private CardCallback onEndOfTurnInHand;
        private CardCallback onManualDiscard;
        private CardCallback onExhaust;
        private CardCallback onRetain;
        private PlayedCallback onOtherCardPlayed;
        private CostAdjuster costAdjuster;

        public Builder onDraw(CardCallback c) { onDraw = c; return this; }
        public Builder onEndOfTurnInHand(CardCallback c) { onEndOfTurnInHand = c; return this; }
        /** Only discards caused by card effects ("Discard a card"), not the end-of-turn discard. */
        public Builder onManualDiscard(CardCallback c) { onManualDiscard = c; return this; }
        public Builder onExhaust(CardCallback c) { onExhaust = c; return this; }
        public Builder onRetain(CardCallback c) { onRetain = c; return this; }
        public Builder onOtherCardPlayed(PlayedCallback c) { onOtherCardPlayed = c; return this; }
        public Builder costAdjuster(CostAdjuster c) { costAdjuster = c; return this; }

        public CardHooks build() {
            return new CardHooks(onDraw, onEndOfTurnInHand, onManualDiscard, onExhaust, onRetain,
                    onOtherCardPlayed, costAdjuster);
        }
    }
}
