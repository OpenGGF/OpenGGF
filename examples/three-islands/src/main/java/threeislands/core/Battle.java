package threeislands.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A fully turn-based ("full wait") battle. Each round every living fighter is queued by speed;
 * the battle stops on each hero's turn until a {@link Command} is submitted, and nothing else
 * moves while it waits. Enemies act immediately when their turn comes. Every action appends
 * {@link BattleEvent}s that the view animates before calling {@link #advance()} again.
 *
 * <p>Dual and triple techs ({@link Skill#isTech()}) need every member alive and still waiting
 * in this round's queue: using one spends the partners' turns as well as the actor's.
 */
public final class Battle {
    public enum Phase { CHOOSE, RESOLVE, VICTORY, DEFEAT, FLED }

    public static final int SUPER_RING_COST = 50;
    public static final int SUPER_DRAIN = 10;

    private final Progress progress;
    private final Rng rng;
    public final List<Combatant> party = new ArrayList<>();
    public final List<Combatant> foes = new ArrayList<>();
    public final boolean bossBattle;
    private final Deque<Combatant> queue = new ArrayDeque<>();
    private final List<BattleEvent> events = new ArrayList<>();
    private Combatant actor;
    private Phase phase = Phase.RESOLVE;
    private int round;
    private int xpReward;
    private int ringReward;
    private final List<Item> drops = new ArrayList<>();
    private final List<String> levelUps = new ArrayList<>();

    /** A battle against {@code kinds} balanced for a party at {@code level} (the zone's level). */
    public Battle(Progress progress, List<EnemyKind> kinds, int level) {
        this.progress = progress;
        this.rng = progress.rng;
        int slot = 0;
        for (Hero hero : progress.party()) party.add(Combatant.hero(hero, slot++));
        boolean boss = false;
        int[] counts = new int[EnemyKind.values().length];
        for (EnemyKind kind : kinds) counts[kind.ordinal()]++;
        int[] seen = new int[EnemyKind.values().length];
        for (int i = 0; i < kinds.size(); i++) {
            EnemyKind kind = kinds.get(i);
            String name = kind.label;
            if (counts[kind.ordinal()] > 1) name += " " + (char) ('A' + seen[kind.ordinal()]++);
            foes.add(Combatant.enemy(kind, level, party.size(), i, name));
            boss |= kind.boss;
        }
        bossBattle = boss;
    }

    public Phase phase() { return phase; }
    public Combatant actor() { return actor; }
    public int round() { return round; }
    public List<BattleEvent> events() { return List.copyOf(events); }
    public int xpReward() { return xpReward; }
    public int ringReward() { return ringReward; }
    public List<Item> drops() { return List.copyOf(drops); }
    public List<String> levelUps() { return List.copyOf(levelUps); }

    /** The fighters still to act this round, in order (for the turn-order strip). */
    public List<Combatant> upcoming() {
        List<Combatant> out = new ArrayList<>();
        for (Combatant c : queue) if (c.alive()) out.add(c);
        return out;
    }

    public boolean canFlee() { return !bossBattle; }

    /** How many of an item the party carries. */
    public int progressCount(Item item) { return progress.count(item); }

    /** Opens the battle: announces the foes and moves to the first turn. */
    public void start() {
        events.clear();
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < foes.size(); i++) {
            if (i > 0) names.append(i == foes.size() - 1 ? " and " : ", ");
            names.append(foes.get(i).name);
        }
        events.add(BattleEvent.message(names + (foes.size() == 1 ? " attacks!" : " attack!")));
        phase = Phase.RESOLVE;
    }

    /**
     * Moves to the next turn after the previous events were shown. Stops at a hero's turn
     * ({@link Phase#CHOOSE}) or after an enemy has acted ({@link Phase#RESOLVE}), or ends the
     * battle.
     */
    public void advance() {
        // Full wait: nothing moves while a hero's command is pending.
        if (phase == Phase.CHOOSE || phase == Phase.VICTORY || phase == Phase.DEFEAT || phase == Phase.FLED) return;
        events.clear();
        if (finished()) return;
        for (int guard = 0; guard < 64; guard++) {
            Combatant next = queue.pollFirst();
            if (next == null) {
                newRound();
                continue;
            }
            if (!next.alive()) continue;
            actor = next;
            next.startTurn();
            if (next.isHero()) {
                if (next.superForm && !drainSuper(next)) {
                    phase = Phase.RESOLVE;
                    return;
                }
                phase = Phase.CHOOSE;
                return;
            }
            enemyTurn(next);
            phase = Phase.RESOLVE;
            finished();
            return;
        }
        throw new IllegalStateException("No fighter could act");
    }

    private void newRound() {
        round++;
        List<Combatant> all = new ArrayList<>();
        for (Combatant c : party) if (c.alive()) all.add(c);
        for (Combatant c : foes) if (c.alive()) all.add(c);
        List<long[]> keyed = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) keyed.add(new long[] {all.get(i).spd() * 8L + rng.nextInt(24), i});
        keyed.sort((a, b) -> Long.compare(b[0], a[0]));
        queue.clear();
        for (long[] k : keyed) queue.addLast(all.get((int) k[1]));
    }

    // ------------------------------------------------------------------ hero commands

    /** Skills the current hero may choose now (learned, affordable, partners ready). */
    public List<Skill> availableSkills() {
        List<Skill> out = new ArrayList<>();
        if (actor == null || !actor.isHero()) return out;
        for (Skill skill : Skill.values()) if (knows(skill)) out.add(skill);
        return out;
    }

    /** Whether the current hero can see this skill in its list (it may still be unusable). */
    public boolean knows(Skill skill) {
        if (actor == null || !actor.isHero() || !skill.includes(actor.hero.id)) return false;
        if (!skill.isTech()) return actor.hero.level() >= skill.learnLevel;
        for (HeroId id : HeroId.values()) if (skill.includes(id) && !progress.hasJoined(id)) return false;
        return true;
    }

    /** Null when usable now, otherwise the reason it is not. */
    public String unusableReason(Skill skill) {
        if (!knows(skill)) return "Not learned";
        for (Combatant member : members(skill)) {
            if (member == null || !member.alive()) return "A partner is down";
            if (member.ep < skill.ep) return member.name + " needs " + skill.ep + " EP";
            if (member != actor && !queue.contains(member)) return member.name + " has already acted";
        }
        if (skill.target == Kinds.FALLEN_ALLY && fallen().isEmpty()) return "Nobody needs reviving";
        return null;
    }

    public boolean canTransform() {
        return actor != null && actor.isHero() && actor.hero.id == HeroId.SONIC && !actor.superForm
                && progress.allEmeralds() && progress.rings() >= SUPER_RING_COST;
    }

    public List<Combatant> livingFoes() {
        List<Combatant> out = new ArrayList<>();
        for (Combatant c : foes) if (c.alive()) out.add(c);
        return out;
    }

    public List<Combatant> livingParty() {
        List<Combatant> out = new ArrayList<>();
        for (Combatant c : party) if (c.alive()) out.add(c);
        return out;
    }

    public List<Combatant> fallen() {
        List<Combatant> out = new ArrayList<>();
        for (Combatant c : party) if (!c.alive()) out.add(c);
        return out;
    }

    /** Performs the current hero's command. Invalid commands throw and change nothing. */
    public void submit(Command command) {
        if (phase != Phase.CHOOSE || actor == null) throw new IllegalStateException("Not waiting for a command");
        validate(command);
        events.clear();
        Combatant hero = actor;
        switch (command.type()) {
            case Command.ATTACK -> {
                events.add(new BattleEvent(BattleEvent.LUNGE, hero, command.target(), 0,
                        hero.name + " attacks " + command.target().name + "!", heroElement(hero, Element.NONE), false));
                strike(hero, command.target(), 10, heroElement(hero, Element.NONE), hero.hero.id != HeroId.SONIC);
            }
            case Command.SKILL -> useSkill(hero, command.skill(), command.target());
            case Command.ITEM -> useItem(hero, command.item(), command.target());
            case Command.GUARD -> {
                hero.guarding = true;
                events.add(new BattleEvent(BattleEvent.BUFF, hero, hero, 0, hero.name + " braces for impact.",
                        Element.NONE, false));
            }
            case Command.FLEE -> attemptFlee(hero);
            case Command.SUPER -> {
                progress.spendRings(SUPER_DRAIN);
                hero.superForm = true;
                events.add(new BattleEvent(BattleEvent.SUPER, hero, hero, 0,
                        "The Chaos Emeralds answer! Sonic becomes Super Sonic!", Element.NONE, false));
            }
            default -> throw new IllegalArgumentException("Unknown command");
        }
        if (phase == Phase.CHOOSE) phase = Phase.RESOLVE;
        finished();
    }

    private void validate(Command command) {
        switch (command.type()) {
            case Command.ATTACK -> requireFoe(command.target());
            case Command.SKILL -> {
                Skill skill = command.skill();
                if (skill == null) throw new IllegalArgumentException("No skill");
                String reason = unusableReason(skill);
                if (reason != null) throw new IllegalArgumentException(reason);
                requireTarget(skill.target, command.target());
            }
            case Command.ITEM -> {
                Item item = command.item();
                if (item == null || progress.count(item) <= 0) throw new IllegalArgumentException("No such item");
                requireTarget(item.target, command.target());
            }
            case Command.FLEE -> {
                if (!canFlee()) throw new IllegalArgumentException("Cannot flee from a boss");
            }
            case Command.SUPER -> {
                if (!canTransform()) throw new IllegalArgumentException("Cannot transform");
            }
            case Command.GUARD -> { }
            default -> throw new IllegalArgumentException("Unknown command");
        }
    }

    private void requireFoe(Combatant target) {
        if (target == null || target.isHero() || !target.alive()) throw new IllegalArgumentException("Pick a foe");
    }

    private void requireTarget(int kind, Combatant target) {
        switch (kind) {
            case Kinds.ENEMY -> requireFoe(target);
            case Kinds.ALLY -> {
                if (target == null || !target.isHero() || !target.alive()) throw new IllegalArgumentException("Pick an ally");
            }
            case Kinds.FALLEN_ALLY -> {
                if (target == null || !target.isHero() || target.alive()) throw new IllegalArgumentException("Pick a fallen ally");
            }
            default -> { }
        }
    }

    private List<Combatant> members(Skill skill) {
        List<Combatant> out = new ArrayList<>();
        for (HeroId id : HeroId.values()) {
            if (!skill.includes(id)) continue;
            Combatant member = null;
            for (Combatant c : party) if (c.hero.id == id) member = c;
            out.add(member);
        }
        return out;
    }

    private void useSkill(Combatant hero, Skill skill, Combatant target) {
        List<Combatant> members = members(skill);
        for (Combatant member : members) {
            member.ep -= skill.ep;
            if (member != hero) queue.remove(member);
        }
        String who = hero.name;
        if (skill.isTech()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) names.append(i == members.size() - 1 ? " and " : ", ");
                names.append(members.get(i).name);
            }
            who = names.toString();
        }
        Element element = heroElement(hero, skill.element());
        switch (skill.effect) {
            case Kinds.DAMAGE, Kinds.BREAK -> {
                int attack = 0;
                for (Combatant member : members) attack += member.atk();
                int power = skill.power;
                // A tech hits with the members' combined strength (three fifths of their sum).
                Combatant striker = hero;
                if (skill.isTech()) power = power * attack * 3 / 5 / Math.max(1, hero.atk());
                List<Combatant> targets = skill.target == Kinds.ALL_ENEMIES ? livingFoes() : List.of(target);
                events.add(new BattleEvent(BattleEvent.LUNGE, hero, skill.target == Kinds.ALL_ENEMIES ? null : target,
                        members.size(), who + (members.size() > 1 ? " use " : " uses ") + skill.label + "!",
                        element, false));
                for (Combatant foe : targets) {
                    strike(striker, foe, power, element, false);
                    if (skill.effect == Kinds.BREAK && foe.alive()) {
                        foe.defDown = 3;
                        events.add(new BattleEvent(BattleEvent.BUFF, hero, foe, 0, foe.name + "'s defence drops!",
                                Element.NONE, false));
                    }
                }
            }
            case Kinds.HEAL -> {
                events.add(new BattleEvent(BattleEvent.MESSAGE, hero, null, 0, who + " uses " + skill.label + "!",
                        Element.NONE, false));
                List<Combatant> targets = skill.target == Kinds.ALL_ALLIES ? livingParty() : List.of(target);
                for (Combatant ally : targets) heal(ally, skill.power + 4 * hero.hero.level());
            }
            case Kinds.REVIVE -> {
                events.add(new BattleEvent(BattleEvent.MESSAGE, hero, null, 0, who + " uses " + skill.label + "!",
                        Element.NONE, false));
                revive(target, skill.power);
            }
            case Kinds.HASTE -> {
                for (Combatant ally : livingParty()) ally.haste = Math.max(ally.haste, skill.power + (ally == hero ? 1 : 0));
                events.add(new BattleEvent(BattleEvent.BUFF, hero, null, 0,
                        who + " uses " + skill.label + "! The party speeds up!", Element.NONE, false));
            }
            case Kinds.SCAN -> {
                target.scanned = true;
                Element weak = target.kind.weakness();
                events.add(new BattleEvent(BattleEvent.BUFF, hero, target, 0, target.name + ": HP " + target.hp + "/"
                        + target.maxHp + ", weak to " + (weak == Element.NONE ? "nothing" : weak.label) + ".",
                        Element.NONE, false));
            }
            case Kinds.TAUNT -> {
                hero.taunt = skill.power + 1;
                hero.guarding = true;
                events.add(new BattleEvent(BattleEvent.BUFF, hero, hero, 0,
                        "Knuckles plants his feet. \"Come on, hit me!\"", Element.NONE, false));
            }
            default -> throw new IllegalStateException("Unhandled skill " + skill);
        }
    }

    private void useItem(Combatant user, Item item, Combatant target) {
        progress.takeItem(item);
        String line = user.name + " uses a " + item.label + "!";
        switch (item.effect) {
            case Kinds.HEAL -> {
                events.add(new BattleEvent(BattleEvent.MESSAGE, user, null, 0, line, Element.NONE, false));
                List<Combatant> targets = item.target == Kinds.ALL_ALLIES ? livingParty() : List.of(target);
                for (Combatant ally : targets) heal(ally, item.amount);
            }
            case Kinds.RESTORE_EP -> {
                int before = target.ep;
                target.ep = Math.min(target.maxEp, target.ep + item.amount);
                events.add(new BattleEvent(BattleEvent.EP, user, target, target.ep - before, line, Element.NONE, false));
            }
            case Kinds.HASTE -> {
                for (Combatant ally : livingParty()) ally.haste = Math.max(ally.haste, item.amount + (ally == user ? 1 : 0));
                events.add(new BattleEvent(BattleEvent.BUFF, user, null, 0, line + " The party speeds up!",
                        Element.NONE, false));
            }
            case Kinds.SHIELD -> {
                target.shield = Element.values()[item.amount];
                events.add(new BattleEvent(BattleEvent.BUFF, user, target, 0,
                        target.name + " is wrapped in a " + item.label + "!", target.shield, false));
            }
            case Kinds.INVINCIBLE -> {
                target.invincible = item.amount + (target == user ? 1 : 0);
                events.add(new BattleEvent(BattleEvent.BUFF, user, target, 0, target.name + " is invincible!",
                        Element.NONE, false));
            }
            case Kinds.REVIVE -> {
                events.add(new BattleEvent(BattleEvent.MESSAGE, user, null, 0, line, Element.NONE, false));
                revive(target, item.amount);
            }
            default -> throw new IllegalStateException("Unhandled item " + item);
        }
    }

    private void attemptFlee(Combatant hero) {
        events.add(new BattleEvent(BattleEvent.FLEE, hero, null, 0, "The party gets away!", Element.NONE, false));
        phase = Phase.FLED;
        writeBack();
    }

    private boolean drainSuper(Combatant sonic) {
        if (progress.rings() >= SUPER_DRAIN) {
            progress.spendRings(SUPER_DRAIN);
            return true;
        }
        sonic.superForm = false;
        events.add(new BattleEvent(BattleEvent.SUPER, sonic, sonic, 0, "Out of rings! Sonic returns to normal.",
                Element.NONE, false));
        return true;
    }

    private Element heroElement(Combatant hero, Element skillElement) {
        if (skillElement != Element.NONE) return skillElement;
        return hero.shield == null ? Element.NONE : hero.shield;
    }

    // ------------------------------------------------------------------ enemy turns

    private void enemyTurn(Combatant enemy) {
        if (livingParty().isEmpty()) return;
        EnemyKind kind = enemy.kind;
        Element element = kind.attackElement();
        if (enemy.charging) {
            enemy.charging = false;
            events.add(new BattleEvent(BattleEvent.LUNGE, enemy, null, 2, enemy.name + " unleashes its full power!",
                    element, false));
            for (Combatant hero : livingParty()) strike(enemy, hero, 20, element, false);
            return;
        }
        switch (kind.pattern) {
            case Kinds.SWEEP -> {
                if (rng.chance(35)) sweep(enemy, element, 7);
                else single(enemy, element, 10);
            }
            case Kinds.FLURRY -> {
                if (rng.chance(30)) {
                    events.add(new BattleEvent(BattleEvent.LUNGE, enemy, null, 1, enemy.name + " goes into a frenzy!",
                            element, false));
                    for (int i = 0; i < 2 && !livingParty().isEmpty(); i++) strike(enemy, pickTarget(), 6, element, false);
                } else {
                    single(enemy, element, 10);
                }
            }
            case Kinds.CHARGER -> {
                if (enemy.turnsTaken % 3 == 0) {
                    enemy.charging = true;
                    events.add(new BattleEvent(BattleEvent.CHARGE, enemy, null, 0,
                            enemy.name + " is charging up! Guard!", element, false));
                } else if (rng.chance(30)) {
                    sweep(enemy, element, 8);
                } else {
                    single(enemy, element, 12);
                }
            }
            case Kinds.RIVAL -> rivalTurn(enemy);
            default -> single(enemy, element, 10);
        }
    }

    /** Knuckles fights like a hero: he guards, he punches, and when hurt he drills. */
    private void rivalTurn(Combatant knuckles) {
        if (knuckles.hp * 2 < knuckles.maxHp && knuckles.turnsTaken % 2 == 0) {
            Combatant target = pickTarget();
            events.add(new BattleEvent(BattleEvent.LUNGE, knuckles, target, 0, "Knuckles dives with a Drill Claw!",
                    Element.NONE, false));
            strike(knuckles, target, 16, Element.NONE, false);
            if (target.alive()) {
                target.defDown = 2;
                events.add(new BattleEvent(BattleEvent.BUFF, knuckles, target, 0, target.name + "'s defence drops!",
                        Element.NONE, false));
            }
        } else if (knuckles.turnsTaken % 4 == 0) {
            knuckles.guarding = true;
            events.add(new BattleEvent(BattleEvent.BUFF, knuckles, knuckles, 0,
                    "Knuckles raises his fists: \"Is that all you've got?\"", Element.NONE, false));
        } else {
            single(knuckles, Element.NONE, 13);
        }
    }

    private void single(Combatant enemy, Element element, int power) {
        Combatant target = pickTarget();
        events.add(new BattleEvent(BattleEvent.LUNGE, enemy, target, 0, enemy.name + " " + enemy.kind.verb + " "
                + target.name + "!", element, false));
        strike(enemy, target, power, element, false);
    }

    private void sweep(Combatant enemy, Element element, int power) {
        events.add(new BattleEvent(BattleEvent.LUNGE, enemy, null, 1, enemy.name + " " + enemy.kind.verb
                + " the whole party!", element, false));
        for (Combatant hero : livingParty()) strike(enemy, hero, power, element, false);
    }

    private Combatant pickTarget() {
        List<Combatant> living = livingParty();
        for (Combatant c : living) if (c.taunt > 0) return c;
        return living.get(rng.nextInt(living.size()));
    }

    // ------------------------------------------------------------------ resolution

    /**
     * Deals {@code power} tenths of the attacker's attack to {@code target}. Shields block a whole
     * hit, guarding and Guardian Stance halve damage, weaknesses multiply by 1.5 and resistances
     * by 0.5. {@code bonusCrit} gives the slower heroes a slightly better critical chance.
     */
    int strike(Combatant attacker, Combatant target, int power, Element element, boolean bonusCrit) {
        if (!target.alive()) return 0;
        if (target.invincible > 0 || (target.superForm && !attacker.isHero())) {
            events.add(new BattleEvent(BattleEvent.BLOCK, attacker, target, 0, target.name + " is untouchable!",
                    element, false));
            return 0;
        }
        if (target.isHero() && target.shield != null && !attacker.isHero()) {
            Element shield = target.shield;
            target.shield = null;
            events.add(new BattleEvent(BattleEvent.BLOCK, attacker, target, 0, target.name + "'s " + shield.label
                    + " Shield absorbs the blow!", shield, false));
            return 0;
        }
        double base = attacker.atk() * power / 10.0;
        double damage = Math.max(base * 0.15, base - target.def() * 0.5);
        damage *= 0.9 + rng.nextDouble() * 0.2;
        boolean critical = attacker.isHero() && rng.chance(bonusCrit ? 10 : 7);
        if (critical) damage *= 1.5;
        String note = "";
        if (!target.isHero() && element != Element.NONE) {
            if (target.kind.weakness() == element) {
                damage *= 1.5;
                note = " It's super effective!";
            } else if (target.kind.resistance() == element) {
                damage *= 0.5;
                note = " It resists " + element.label + ".";
            }
        }
        if (target.guarding) damage *= 0.5;
        if (target.taunt > 0) damage *= 0.75;
        int amount = Math.max(1, (int) Math.round(damage));
        target.hp = Math.max(0, target.hp - amount);
        events.add(new BattleEvent(BattleEvent.HIT, attacker, target, amount,
                (critical ? "Critical! " : "") + target.name + " takes " + amount + " damage." + note, element, critical));
        if (!target.alive()) {
            target.charging = false;
            events.add(new BattleEvent(BattleEvent.KO, attacker, target, 0, target.isHero()
                    ? target.name + " is down!" : target.name + " is destroyed!", element, false));
        }
        return amount;
    }

    private void heal(Combatant ally, int amount) {
        if (!ally.alive()) return;
        int before = ally.hp;
        ally.hp = Math.min(ally.maxHp, ally.hp + amount);
        events.add(new BattleEvent(BattleEvent.HEAL, null, ally, ally.hp - before,
                ally.name + " recovers " + (ally.hp - before) + " HP.", Element.NONE, false));
    }

    private void revive(Combatant ally, int percent) {
        if (ally.alive()) return;
        ally.hp = Math.max(1, ally.maxHp * percent / 100);
        ally.superForm = false;
        events.add(new BattleEvent(BattleEvent.REVIVE, null, ally, ally.hp, ally.name + " is back on their feet!",
                Element.NONE, false));
    }

    /** Ends the battle when one side has fallen; returns true when it is over. */
    private boolean finished() {
        if (phase == Phase.VICTORY || phase == Phase.DEFEAT || phase == Phase.FLED) return true;
        if (livingFoes().isEmpty()) {
            win();
            return true;
        }
        if (livingParty().isEmpty()) {
            phase = Phase.DEFEAT;
            events.add(BattleEvent.message("The party has fallen..."));
            writeBack();
            return true;
        }
        return false;
    }

    private void win() {
        phase = Phase.VICTORY;
        for (Combatant foe : foes) {
            xpReward += foe.xp;
            ringReward += foe.rings;
            if (!foe.kind.boss && rng.chance(22)) {
                drops.add(rng.chance(70) ? Item.SUPER_RING : Item.BLUE_SPHERE);
            }
        }
        progress.addRings(ringReward);
        for (Item item : drops) progress.addItem(item, 1);
        writeBack();
        for (Combatant c : party) {
            int gained = c.hero.gainXp(xpReward);
            if (gained > 0) levelUps.add(c.name + " reached level " + c.hero.level() + "!" + newSkills(c.hero, gained));
        }
        if (bossBattle) progress.restAll();
        else progress.recoverAfterBattle();
        events.add(BattleEvent.message("Victory!"));
    }

    private static String newSkills(Hero hero, int gained) {
        List<String> learned = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (skill.isTech() || !skill.includes(hero.id)) continue;
            if (skill.learnLevel <= hero.level() && skill.learnLevel > hero.level() - gained) learned.add(skill.label);
        }
        return learned.isEmpty() ? "" : " Learned " + String.join(", ", learned) + "!";
    }

    private void writeBack() {
        for (Combatant c : party) c.writeBack();
    }

    /** Debug and test helper: knocks out every foe and resolves the victory. */
    public void forceVictory() {
        for (Combatant foe : foes) foe.hp = 0;
        events.clear();
        finished();
    }

    /** Remaining queue order including the current actor first. */
    public List<Combatant> turnStrip() {
        List<Combatant> out = new ArrayList<>();
        if (actor != null && actor.alive() && (phase == Phase.CHOOSE)) out.add(actor);
        out.addAll(upcoming());
        return Collections.unmodifiableList(out);
    }
}
