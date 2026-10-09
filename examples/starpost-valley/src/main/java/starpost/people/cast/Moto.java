package starpost.people.cast;

import static starpost.core.Calendar.SPRING;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.Taste;
import starpost.people.VillagerDef;

/**
 * Moto, the farm's pet Motobug (Sonic 1's): pottering about the field, beeping. Speaks in
 * pictures until Tails reprograms him in the 2-heart event, after which his beeps come with a
 * translation.
 */
public final class Moto {
    private Moto() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("moto", "MOTO")
                .about("YOUR PET MOTOBUG. BEEPS. LOVES BEING PATTED.")
                .body("motobug").home("field").birthday(SPRING, 1).animal().pet()
                .loves("scrap", "oil_can")
                .kind(Kind.MATERIAL, Taste.LIKE).kind(Kind.MINERAL, Taste.LIKE).kind(Kind.CROP, Taste.NEUTRAL)
                .dislikes("fibre")
                .hates("frost_ring");
        v.plan().farm(600, "field", -40, 30);
        v.plan().rain().farm(600, "door", 20, 6);
        lines(v);
    }

    private static void lines(VillagerDef v) {
        v.line("BEEP? ...BEEP! (MOTO HAS DECIDED YOU ARE HIS.)").first().pic("badnik", "farmer", "heart", "!");
        v.line("BEEP BEEP. (GOOD MORNING, BOSS.)").between(600, 1100).pic("badnik", "sun", "heart");
        v.line("BWEEP. (MOTO HAS FOUND A ROCK. MOTO IS PROUD OF THE ROCK.)").pic("badnik", "sparkle", "!");
        v.line("BEEP. BEEP. BEEP. (MOTO IS COUNTING THE PLOTS. HE GETS TO THREE.)").pic("badnik", "clock", "?");
        v.line("BRRRRRRT. (MOTO WOULD LIKE TO BE PATTED ON THE SHELL. AGAIN.)").pic("badnik", "heart", "!");
        v.line("BEEP BOOP. (MOTO WAS GUARDING THE FIELD. FROM A BUTTERFLY.)").pic("badnik", "anger", "?");
        v.line("BEEEEP. (MOTO IS A GOOD BADNIK NOW. HE WOULD LIKE YOU TO KNOW.)").hearts(2)
                .pic("badnik", "heart", "yes");
        v.line("BWEEP BWEEP. (MOTO FOLLOWED A FLICKY ALL MORNING. IT DID NOT NOTICE.)").pic("badnik", "flicky", "...");
        v.line("BEEP. (THE SUN IS WARM ON MOTO'S SHELL. MOTO APPROVES.)").dry().pic("badnik", "sun", "heart");
        v.line("BEEP... (MOTO DOES NOT LIKE RAIN. RAIN MEANS RUST.)").rain().pic("badnik", "rain", "sad");
        v.line("BRT. (MOTO SAW SOMETHING IN THE DARK. IT WAS A CABBAGE.)").between(1800, 2600)
                .pic("badnik", "moon", "sweat");
        v.line("BEEP BEEP BEEP! (MOTO'S WHEEL SQUEAKS LESS SINCE TAILS FIXED HIM. HE IS VERY PLEASED.)")
                .flag("moto_reprogrammed").pic("badnik", "who:tails", "heart");
        v.line("BWEEEEP. (MOTO LOVES YOU. THAT IS ALL. THAT IS THE WHOLE BEEP.)").hearts(5).pic("badnik", "heart", "heart");
        v.line("BEEP BEEP BEEP BEEP. (MOTO HAS SEEN A BUZZ BOMBER. MOTO IS NOT IMPRESSED.)").pic("badnik", "...");
        v.line("BEEP. (A BUTTERFLY LANDED ON MOTO. MOTO IS NOT MOVING. EVER AGAIN.)").spring()
                .pic("badnik", "heart", "zzz");
        v.line("BRRRT. (MOTO IS COLD. MOTO WOULD LIKE TO SIT NEAR THE HOUSE.)").winter().pic("badnik", "snow", "house");
        v.line("BWEEP. (MOTO PUSHED A ROCK OFF THE FIELD. IT TOOK ALL MORNING. HE IS TIRED AND PROUD.)").hearts(3)
                .pic("badnik", "sparkle", "zzz", "!");
        v.line("BEEP! (MOTO WOULD LIKE TO RACE. MOTO'S TOP SPEED IS WALKING.)").pic("badnik", "arrow", "?");

        v.again("BEEP. (STILL HERE.)").pic("badnik", "heart");
        v.again("BRRT? (AGAIN? OKAY.)").pic("badnik", "?");

        v.gift(Taste.LOVE, "BWEEEEEP! (MOTO HAS NEVER BEEN SO HAPPY. HIS WHEEL IS SPINNING ON ITS OWN.)")
                .pic("badnik", "heart", "heart", "!");
        v.gift(Taste.LIKE, "BEEP BEEP! (MOTO WILL KEEP THIS UNDER HIS SHELL.)").pic("badnik", "heart");
        v.gift(Taste.NEUTRAL, "BEEP? (MOTO SNIFFS IT. MOTO DOES NOT HAVE A NOSE.)").pic("badnik", "?");
        v.gift(Taste.DISLIKE, "BRT. (MOTO DOES NOT WANT WEEDS. MOTO HAS ENOUGH WEEDS.)").pic("badnik", "no");
        v.gift(Taste.HATE, "BWAAP! (ICE! RUST! MOTO ROLLS AWAY VERY FAST.)").pic("badnik", "snow", "anger", "!");
        v.birthdayGift("BEEP BEEP BEEP! (IT IS THE DAY MOTO MET YOU. MOTO REMEMBERS.)").pic("badnik", "gift", "heart");
    }
}
