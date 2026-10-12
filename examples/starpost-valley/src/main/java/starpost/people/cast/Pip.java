package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Pip, a Flicky: the valley's gossip and postie, nesting in your shipping signpost and never
 * landing for long. Speaks in pictures until the Chirp Translator.
 */
public final class Pip {
    private Pip() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("pip", "PIP")
                .about("FLICKY. POSTIE AND GOSSIP. NESTS IN YOUR SIGNPOST.")
                .body("animal:flicky").home("signpost").birthday(SPRING, 5).animal()
                .loves("sunflower", "sunflower_seeds", "bluesphere_berry")
                .likes("loop_berry", "ring_radish_seeds")
                .kind(Kind.FORAGE, Taste.LIKE).kind(Kind.SEED, Taste.LIKE)
                .dislikes("scrap", "marble_chip")
                .hates("fire_pepper");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().farm(600, "signpost", 0, 8).at(900, "path").at(1030, "seed_stall", -30).at(1200, "inn_porch", 20)
                .at(1400, "workshop_yard", -20).at(1530, "palms_east").at(1700, "gate", 20).farm(1830, "signpost", 0, 8);
        v.plan().rain().farm(600, "signpost", 0, 8).inside(1000, "inn").farm(1700, "signpost", 0, 8);
    }

    private static void lines(VillagerDef v) {
        v.line("HELLO HELLO HELLO! I'M PIP! I LIVE IN YOUR SIGNPOST NOW! I HOPE THAT'S OKAY! IT'S OKAY!")
                .first().pic("flicky", "house", "farmer", "heart", "!");
        v.line("I DELIVER THE POST! LETTERS, PARCELS, NEWS. MOSTLY NEWS. I MAKE SOME OF THE NEWS.")
                .pic("flicky", "flicky", "house", "!");
        v.line("DANDEL SOLD THREE PACKETS OF SEEDS TODAY! THAT'S THREE MORE THAN TUESDAY!")
                .pic("who:dandel", "item:ring_radish_seeds", "!");
        v.line("ROBOTNIK SENDS A LOT OF LETTERS. I READ THEM. FOR SAFETY. THEY'RE MOSTLY ABOUT HIM.")
                .pic("robotnik", "flicky", "?", "anger");
        v.line("I CAN'T SIT STILL. FLICKIES DON'T SIT STILL. WE HOVER. HOVERING IS SITTING FOR BIRDS.")
                .pic("flicky", "no", "zzz");
        v.line("CLEMENTINE FED ME A CRUMB AND I'VE BEEN FULL FOR THREE HOURS. I'M VERY SMALL.")
                .pic("who:clementine", "food", "heart");
        v.line("BARNABY TOLD ME HIS HAT STORY AGAIN. THE HAT GETS BIGGER EVERY TIME.")
                .pic("who:barnaby", "fish", "...");
        v.line("BLOSSOMS! THE WHOLE VALLEY SMELLS LIKE A BIRTHDAY! IS IT SOMEONE'S BIRTHDAY? IT'S SPRING'S!")
                .spring().pic("sun", "gift", "heart");
        v.line("THE SUNFLOWERS ARE BACK. I SIT ON THEM. THEY DON'T MIND. I'M VERY SMALL.")
                .spring().pic("item:sunflower", "flicky", "heart");
        v.line("SUMMER! LONG DAYS MEAN LONG ROUTES. MY WINGS ARE TIRED AND HAPPY.").summer()
                .pic("sun", "flicky", "zzz", "heart");
        v.line("AT THE END OF SUMMER ALL THE FLICKIES FLY OVER THE LAKE. IT'S CALLED THE NIGHT OF THE FLICKIES!")
                .summer().pic("flicky", "flicky", "flicky", "moon", "!");
        v.line("THE LEAVES ARE FALLING AND I KEEP DELIVERING THEM BY MISTAKE.").fall().pic("flicky", "sad", "?");
        v.line("IT'S COLD. FLICKIES FLY SOUTH IN WINTER. I FLY TO YOUR SIGNPOST. IT'S SOUTH OF THE INN.")
                .winter().pic("snow", "flicky", "house");
        v.line("I PUT A LITTLE SCARF ON. NOBODY NOTICED. YOU NOTICED! YOU DIDN'T? WELL NOW YOU HAVE.")
                .winter().pic("snow", "heart", "?");
        v.line("RAIN! WET FEATHERS! I'M HIDING UNDER YOUR SIGNPOST. IT'S A VERY GOOD SIGNPOST.")
                .rain().pic("rain", "flicky", "house");
        v.line("THE MAIL IS LATE BECAUSE OF THE RAIN. THE MAIL IS ALWAYS LATE. I JUST BLAME THE RAIN.")
                .rain().pic("rain", "flicky", "clock");
        v.line("KNUCKLES GLARED AT ME TODAY. I GLARED BACK. HE WON. HE ALWAYS WINS.")
                .pic("who:knuckles", "anger", "flicky", "sad").flag("knuckles_watching");
        v.line("I TOLD EVERYONE YOU'RE NICE. NOW YOU HAVE TO BE NICE. THOSE ARE THE RULES.").hearts(2)
                .pic("farmer", "heart", "flicky", "!");
        v.line("YOU'RE MY FAVOURITE PERSON TO DELIVER TO. DON'T TELL TAILS. HE GETS PARCELS. YOU GET LETTERS.")
                .hearts(4).pic("farmer", "heart", "flicky");
        v.line("I WAS IN A MOTOBUG ONCE. IT WAS DARK AND LOUD. NOW I LIVE IN A SIGNPOST. IT'S BRIGHT AND QUIET.")
                .hearts(6).pic("badnik", "moon", "sad", "house", "sun");
        v.line("I CAN TALK NOW! I MEAN I ALWAYS COULD. NOW YOU CAN LISTEN! I HAVE SO MUCH TO TELL YOU!")
                .flag(People.TRANSLATOR).after("tails_2", 5);
        v.line("TOMORROW'S THE NIGHT OF THE FLICKIES! I'M LEADING THE FRONT. OR THE MIDDLE. SOMEWHERE GOOD.")
                .on(SUMMER, 27).pic("flicky", "flicky", "moon", "!");
        v.line("IT'S MY BIRTHDAY! I'M ONE! OR TWO. FLICKIES DON'T COUNT VERY HIGH.").birthday()
                .pic("gift", "flicky", "heart", "!");
        v.line("I KEEP READING MY MUM'S LETTER. THANK YOU FOR FINDING IT. I'M NOT CRYING. IT'S RAIN.")
                .after("pip_4", 6).pic("flicky", "flicky", "heart", "sad");

        v.again("STILL HERE! FOR A SECOND! NOW I'M GONE! NO, STILL HERE!");
        v.again("I HAVE MORE NEWS BUT I FORGOT IT. I'LL REMEMBER BY TOMORROW.");

        v.gift(Taste.LOVE, "SEEDS! SUNFLOWER SEEDS! YOU'RE THE BEST NEIGHBOUR IN THE WHOLE VALLEY!")
                .pic("item:sunflower", "heart", "heart", "!");
        v.gift(Taste.LIKE, "OOH! THANK YOU! I'LL PUT IT IN THE NEST!").pic("house", "heart");
        v.gift(Taste.NEUTRAL, "FOR ME? IT'S BIGGER THAN ME. I'LL TELL EVERYONE YOU GAVE IT TO ME.").pic("flicky", "?", "heart");
        v.gift(Taste.DISLIKE, "IT'S VERY HEAVY AND VERY POINTY. I CAN'T LIFT IT. I'LL VISIT IT.").pic("sweat", "no");
        v.gift(Taste.HATE, "HOT! HOT HOT HOT! WHY WOULD A BIRD WANT FIRE?").pic("anger", "sun", "no", "!");
        v.birthdayGift("A BIRTHDAY PRESENT! I'M TELLING EVERYONE! EVERYONE! EVERYONE!").pic("gift", "heart", "!", "!");
        v.thanks("THANK YOU THANK YOU THANK YOU! (THIS LETTER DELIVERED BY ME.) - PIP")
                .pic("flicky", "heart", "gift", "!");
    }

    private static void events(Cast cast) {
        cast.event("pip_2", "pip", 2).onFarm().between(630, 1100).dry()
                .summary("Pip moves into the signpost officially and makes you her post's first stop.")
                .enter("pip", 1, 200, 36)
                .say("pip", "GOOD MORNING! BIG NEWS! I'VE DECIDED YOUR SIGNPOST IS MY OFFICIAL HOME.",
                        "flicky", "house", "!")
                .say("pip", "THAT MAKES YOUR FARM THE FIRST STOP ON MY ROUTE. EVERY MORNING. FIRST. YOU.",
                        "farmer", "flicky", "clock", "!")
                .hop("pip")
                .narrate("PIP DRAWS A MAP OF HER ROUTE IN THE DIRT WITH ONE FOOT. IT IS VERY SMALL.")
                .say("pip", "IF YOU EVER NEED TO SEND SOMETHING, JUST SHOUT. I'LL HEAR. I HEAR EVERYTHING.",
                        "flicky", "heart")
                .flag("pip_nest")
                .leave("pip", -240);

        cast.event("pip_4", "pip", 4).near("waterfall", 220).between(900, 1800).dry().requires("pip_2")
                .summary("The lost letter: a letter from Pip's mother, from before the badniks.")
                .enter("pip", 1, 160, 40)
                .say("pip", "OH NO OH NO OH NO. I DROPPED A LETTER IN THE WATERFALL. IT'S NOT JUST ANY LETTER.",
                        "flicky", "flicky", "rain", "sad", "!")
                .say("pip", "IT'S MINE. IT'S FROM MY MUM. FROM BEFORE THE BADNIKS. I'VE NEVER OPENED IT.",
                        "flicky", "flicky", "heart", "badnik", "sad")
                .emote("farmer", "!")
                .narrate("YOU WADE INTO THE POOL AND FISH A SOGGY ENVELOPE OUT OF THE REEDS.")
                .sfx(Sfx.SPLASH).pause(20)
                .emote("pip", "heart")
                .say("pip", "YOU FOUND IT! IT'S WET BUT YOU CAN STILL READ IT. CAN YOU READ IT TO ME? I CAN'T. I'M A BIRD.",
                        "farmer", "flicky", "heart", "?")
                .narrate("'DEAR PIP. WHEREVER YOU FLY, COME HOME TO THE VALLEY. WE'LL BE THE GREEN BIT. LOVE, MUM.'")
                .emote("pip", "sad")
                .say("pip", "...SHE WAS RIGHT. IT IS THE GREEN BIT.", "house", "heart", "sad")
                .flag("pip_letter_found")
                .leave("pip", -240);

        cast.event("pip_6", "pip", 6).near("lake", 220).between(1800, 2300).dry().requires("pip_4")
                .summary("Pip organises the Night of the Flickies and asks you to light the way.")
                .music(Tunes.S1, Tunes.SLZ)
                .enter("pip", -1, 200, 40)
                .say("pip", "I'M ORGANISING THE NIGHT OF THE FLICKIES THIS YEAR. ME! THE SMALLEST ONE!",
                        "flicky", "flicky", "moon", "!")
                .say("pip", "EVERY FLICKY IN THE VALLEY FLIES ONCE ROUND THE LAKE, FOR EVERYONE WHO NEVER GOT OUT OF A CAPSULE.",
                        "flicky", "flicky", "flicky", "sad", "heart")
                .say("pip", "WE NEED SOMEONE TO STAND ON THE JETTY AND WAVE. SO WE KNOW WHERE HOME IS. WILL YOU?",
                        "farmer", "house", "?")
                .emote("farmer", "heart")
                .hop("pip")
                .say("pip", "YES! I KNEW IT! I'M TELLING EVERYONE! NOT THE SECRET PART. THERE ISN'T ONE. YES THERE IS.",
                        "heart", "!", "!")
                .flag("flicky_night_wave")
                .leave("pip", 260);

        cast.event("pip_8", "pip", 8).onFarm().between(630, 1200).seasons(FALL, WINTER).dry().requires("pip_6")
                .summary("Pip will lead the winter migration, and promises to come back.")
                .enter("pip", 1, 200, 36)
                .say("pip", "I HAVE TO TELL YOU SOMETHING AND I'VE PRACTISED IT AND NOW I'VE FORGOTTEN IT.",
                        "flicky", "sweat", "?")
                .say("pip", "THE OTHER FLICKIES ASKED ME TO LEAD THE MIGRATION. FAR AWAY. ALL WINTER.",
                        "flicky", "flicky", "snow", "arrow")
                .emote("farmer", "sad")
                .say("pip", "BUT FLICKIES ALWAYS COME BACK. THAT'S THE WHOLE POINT OF A FLICKY.",
                        "flicky", "arrow", "house", "heart")
                .say("pip", "SO KEEP MY SIGNPOST WARM. AND IF ANY LETTERS COME, DON'T READ THEM. ...READ THEM.",
                        "house", "flicky", "heart")
                .flag("pip_migration")
                .leave("pip", -260);
    }
}
