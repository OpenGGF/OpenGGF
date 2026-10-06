package slaytherobotnik.content;

import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.DamageType;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Powers;

/**
 * Potions, presented as item monitors from Sonic 3 &amp; Knuckles that the player breaks open
 * during a fight. {@code {P}} in the text is the potency.
 */
public final class Potions {
    public static final String EXTRA_LIFE = "potion:extra_life";

    private Potions() {
    }

    public static void register(Catalog c) {
        // Block Potion.
        c.addPotion(new PotionDef("potion:blue_shield", "Blue Shield Monitor", CardRarity.COMMON, CardTarget.SELF, 12,
                "Gain {P} Block.", (combat, target, p) -> combat.gainBlock(combat.player(), p, null), null));
        // Explosive Potion.
        c.addPotion(new PotionDef("potion:fire_shield", "Fire Shield Monitor", CardRarity.COMMON,
                CardTarget.ALL_ENEMIES, 10, "Deal {P} damage to ALL enemies.", (combat, target, p) -> {
                    for (Enemy e : combat.activeEnemies()) {
                        combat.dealDamage(combat.player(), e, p, DamageType.THORNS);
                    }
                }, null));
        // Fire Potion.
        c.addPotion(new PotionDef("potion:lightning_shield", "Lightning Shield Monitor", CardRarity.COMMON,
                CardTarget.ENEMY, 20, "Deal {P} damage.",
                (combat, target, p) -> combat.dealDamage(combat.player(), target, p, DamageType.THORNS), null));
        // Energy Potion.
        c.addPotion(new PotionDef("potion:speed_shoes", "Speed Shoes Monitor", CardRarity.COMMON, CardTarget.SELF, 2,
                "Gain {P} Energy.", (combat, target, p) -> combat.gainEnergy(p), null));
        // Swift Potion.
        c.addPotion(new PotionDef("potion:ring_burst", "Ring Burst Monitor", CardRarity.COMMON, CardTarget.SELF, 3,
                "Draw {P} cards.", (combat, target, p) -> combat.draw(p), null));
        // Strength Potion.
        c.addPotion(new PotionDef("potion:power_monitor", "Power Monitor", CardRarity.COMMON, CardTarget.SELF, 2,
                "Gain {P} Strength.",
                (combat, target, p) -> combat.applyPower(combat.player(), combat.player(), Powers.strength(p)), null));
        // Dexterity Potion.
        c.addPotion(new PotionDef("potion:glove_monitor", "Glove Monitor", CardRarity.COMMON, CardTarget.SELF, 2,
                "Gain {P} Dexterity.",
                (combat, target, p) -> combat.applyPower(combat.player(), combat.player(), Powers.dexterity(p)), null));
        // Focus Potion.
        c.addPotion(new PotionDef("potion:focus_monitor", "Focus Monitor", CardRarity.COMMON, CardTarget.SELF, 1,
                "Gain {P} Focus.",
                (combat, target, p) -> combat.applyPower(combat.player(), combat.player(), Powers.focus(p)), null));
        // Fear Potion.
        c.addPotion(new PotionDef("potion:robotnik_monitor", "Robotnik Monitor", CardRarity.COMMON, CardTarget.ENEMY, 3,
                "Apply {P} Vulnerable. (Point Robotnik's face at THEM.)",
                (combat, target, p) -> combat.applyPower(combat.player(), target, Powers.vulnerable(p)), null));
        // Weak Potion.
        c.addPotion(new PotionDef("potion:static_monitor", "Static Monitor", CardRarity.COMMON, CardTarget.ENEMY, 3,
                "Apply {P} Weak.",
                (combat, target, p) -> combat.applyPower(combat.player(), target, Powers.weak(p)), null));
        // Blood Potion.
        c.addPotion(new PotionDef("potion:health_monitor", "Health Monitor", CardRarity.UNCOMMON, CardTarget.SELF, 20,
                "Heal {P}% of your Max HP.",
                (combat, target, p) -> combat.healPlayer(combat.player().maxHp() * p / 100),
                (run, p) -> run.heal(run.maxHp() * p / 100)));
        // Fruit Juice.
        c.addPotion(new PotionDef("potion:energy_capsule", "Energy Capsule Monitor", CardRarity.UNCOMMON,
                CardTarget.SELF, 5, "Gain {P} Max HP.", (combat, target, p) -> combat.gainMaxHp(p),
                (run, p) -> run.gainMaxHp(p)));
        // Ancient Potion.
        c.addPotion(new PotionDef("potion:super_ring", "Super Ring Monitor", CardRarity.UNCOMMON, CardTarget.SELF, 1,
                "Gain {P} Artifact.",
                (combat, target, p) -> combat.applyPower(combat.player(), combat.player(), Powers.artifact(p)), null));
        // Ghost in a Jar.
        c.addPotion(new PotionDef("potion:invincibility", "Invincibility Monitor", CardRarity.RARE, CardTarget.SELF, 1,
                "Gain {P} Intangible.",
                (combat, target, p) -> combat.applyPower(combat.player(), combat.player(), Powers.intangible(p)), null));
        // Fairy in a Bottle.
        c.addPotion(new PotionDef(EXTRA_LIFE, "1-Up Monitor", CardRarity.RARE, PotionDef.AUTO, 30,
                "When you would die, heal to {P}% of your Max HP instead and discard this.",
                (combat, target, p) -> combat.revivePlayer(combat.player().maxHp() * p / 100), null));
        // Blessing of the Forge.
        c.addPotion(new PotionDef("potion:super_monitor", "Super Monitor", CardRarity.RARE, CardTarget.SELF, 0,
                "Upgrade ALL cards in your hand for the rest of combat.", (combat, target, p) -> {
                    for (var card : java.util.List.copyOf(combat.player().hand())) {
                        combat.upgradeForCombat(card);
                    }
                }, null));
    }
}
