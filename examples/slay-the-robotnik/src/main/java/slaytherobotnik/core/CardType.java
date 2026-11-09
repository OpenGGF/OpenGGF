package slaytherobotnik.core;

/** Immutable card kinds. Display labels retain the original example's text. */
public enum CardType {
    ATTACK("Attack"), SKILL("Skill"), POWER("Power"), STATUS("Status"), CURSE("Curse");

    private final String label;
    CardType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
