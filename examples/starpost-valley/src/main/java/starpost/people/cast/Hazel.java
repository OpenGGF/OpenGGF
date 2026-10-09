package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.SUN;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Hazel, a Ricky squirrel: a kid in a treehouse over Tails's workshop museum who wants to be fast.
 * Speaks in pictures until the Chirp Translator.
 */
public final class Hazel {
    private Hazel() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("hazel", "HAZEL")
                .about("RICKY. TREEHOUSE OVER THE MUSEUM. WANTS TO BE FAST.")
                .body("animal:ricky").home("workshop").birthday(FALL, 12).animal()
                .loves("palm_coconut", "acorn", "chili_dog")
                .likes("loop_berry", "bluesphere_berry", "marble_chip")
                .kind(Kind.RELIC, Taste.LOVE).kind(Kind.FORAGE, Taste.LIKE)
                .dislikes("totem_choke", "palm_bean")
                .hates("egg_plant");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "workshop").at(800, "workshop_yard", 40).at(1000, "palms_east").at(1200, "plaza", 60)
                .at(1400, "spring", 30).at(1700, "workshop_yard", 40).inside(1900, "workshop");
        v.plan().weekdays(SUN).inside(600, "workshop").farm(1000, "field", 120, 44).at(1400, "spring", 30)
                .at(1700, "workshop_yard", 40).inside(1900, "workshop");
        v.plan().rain().inside(600, "workshop");
    }

    private static void lines(VillagerDef v) {
        v.line("WHOA! YOU'RE {FARMER}! I'M HAZEL! I'M GOING TO BE THE FASTEST SQUIRREL EVER! WATCH! ...DID YOU SEE?")
                .first().pic("who:hazel", "farmer", "arrow", "!", "?");
        v.line("I RAN TO THE LOOP AND BACK! IT TOOK ME ALL MORNING! THAT'S A RECORD! FOR ME!")
                .pic("arrow", "clock", "!");
        v.line("I HELP TAILS AT THE MUSEUM. I DUST THE RELICS. I DUST THEM FAST.").pic("who:tails", "house", "sparkle");
        v.line("WHEN I GROW UP I'M GOING TO RUN THE LOOP. THE WHOLE LOOP. UPSIDE DOWN AND EVERYTHING.")
                .pic("arrow", "!", "heart");
        v.line("MY TAIL IS VERY FLUFFY. IT SLOWS ME DOWN. I'M TRYING TO RUN WITHOUT IT. IT FOLLOWS ME.")
                .pic("sad", "arrow", "?");
        v.line("I BOUNCED ON THE SPRING BY THE TOTEMS FORTY TIMES TODAY. FORTY-ONE. I'M DIZZY.")
                .pic("sparkle", "!", "zzz");
        v.line("SPRING! EVERYTHING'S NEW! I'M NEW TOO. I'M SEVEN.").spring().pic("sun", "heart", "!");
        v.line("I'M PRACTISING FOR THE RING HUNT. I'VE FOUND ELEVEN RINGS. TEN WERE BOTTLE CAPS.").spring()
                .pic("ring", "ring", "?", "!");
        v.line("SUMMER! THE DAYS ARE LONG SO I CAN RUN LONGER! I'M STILL NOT FAST. BUT LONGER.").summer()
                .pic("sun", "arrow", "clock");
        v.line("I'M ENTERING THE GREAT VALLEY RACE! I'M GOING TO COME LAST! BUT I'LL BE IN IT!").summer()
                .pic("arrow", "!", "heart");
        v.line("FALL! I'M BURYING COCONUTS FOR WINTER. I FORGET WHERE. THAT'S WHY THERE'S SO MANY PALM TREES.")
                .fall().pic("item:palm_coconut", "house", "?");
        v.line("WINTER! I'M TOO COLD TO RUN. I'M RUNNING ANYWAY. TO KEEP WARM.").winter()
                .pic("snow", "arrow", "!");
        v.line("RAIN! TAILS SAYS NO RUNNING INDOORS. I'M RUNNING VERY SLOWLY INDOORS.").rain()
                .pic("rain", "house", "arrow", "sweat");
        v.line("YOU'RE SO FAST. HOW DO YOU DO THE SPIN THING? CAN YOU TEACH ME? NOT YET? LATER? LATER!")
                .farmer("sonic").hearts(2).pic("farmer", "arrow", "?", "!");
        v.line("TAILS, CAN YOU FLY ME TO THE TOP OF THE TOTEM LEDGE? JUST ONCE? TWICE? OKAY ONCE.")
                .farmer("tails").hearts(2).pic("farmer", "arrow", "?");
        v.line("KNUCKLES! CAN YOU CLIMB WITH ME ON YOUR BACK? I'M VERY LIGHT. MOSTLY TAIL.")
                .farmer("knuckles").hearts(2).pic("farmer", "arrow", "?");
        v.line("YOU'RE MY HERO. DON'T TELL TAILS. HE THINKS HE'S MY HERO. HE CAN BE SECOND.").hearts(4)
                .pic("farmer", "heart", "who:tails");
        v.line("A RELIC WENT MISSING FROM THE MUSEUM. A STAR POST CAP. I DIDN'T TAKE IT. I DUST IT. I'M WORRIED.")
                .after("hazel_4", 7).pic("sparkle", "no", "sad", "?");
        v.line("I CAN TALK! I CAN TALK REALLY FAST! LISTEN! WAIT. THE TRANSLATOR CAN'T KEEP UP.")
                .flag(People.TRANSLATOR).after("tails_2", 6);
        v.line("TOMORROW'S THE RING HUNT! I'M GOING TO FIND A RING! A REAL ONE!").on(SPRING, 12)
                .pic("ring", "!", "heart");
        v.line("IT'S MY BIRTHDAY! I'M EIGHT! I'M FASTER THAN SEVEN!").birthday().pic("gift", "arrow", "heart", "!");

        v.line("TAILS SAYS DON'T RUN WITH SCISSORS. I DON'T HAVE SCISSORS. CAN I HAVE SCISSORS?")
                .pic("who:tails", "arrow", "?");
        v.line("I CLIMBED THE TOTEM LEDGE! KNUCKLES CARRIED ME DOWN. HE DIDN'T SAY ANYTHING. HE NEVER DOES.")
                .flag("knuckles_watching").pic("who:knuckles", "house", "heart");
        v.line("I'M NOT THE FASTEST SQUIRREL. BUT I'M THE FASTEST ME. TAILS SAYS THAT'S WHAT COUNTS.").hearts(6)
                .pic("arrow", "heart", "who:tails");
        v.line("THE MUSEUM HAS A RING MOULD. YOU POUR GOLD IN AND A RING COMES OUT. I POURED WATER. A PUDDLE CAME OUT.")
                .pic("ring", "rain", "?");

        v.again("WATCH! WATCH THIS! ...DID YOU WATCH?").pic("arrow", "?");
        v.again("RACE YOU! ...I WIN. YOU DIDN'T RUN. I STILL WIN.").pic("arrow", "heart", "!");

        v.gift(Taste.LOVE, "FOR ME?! THIS IS THE BEST THING ANYONE HAS EVER GIVEN ANYONE EVER!")
                .pic("heart", "heart", "!", "!");
        v.gift(Taste.LIKE, "COOL! THANKS! I'M GOING TO BURY IT. NO I'M NOT. YES I AM.").pic("house", "heart");
        v.gift(Taste.NEUTRAL, "OH! THANKS! WHAT IS IT? NEVER MIND. THANKS!").pic("?", "heart");
        v.gift(Taste.DISLIKE, "IT'S A VEGETABLE. IT'S A SLOW VEGETABLE. I CAN TELL.").pic("no", "...");
        v.gift(Taste.HATE, "THAT VEGETABLE HAS A MOUSTACHE AND IT'S LOOKING AT ME.").pic("robotnik", "sweat", "no");
        v.birthdayGift("A BIRTHDAY PRESENT! I'M GOING TO OPEN IT SO FAST! ...IT'S OPEN! I LOVE IT!")
                .pic("gift", "arrow", "heart", "!");
        v.thanks("DEAR {FARMER}! THANK YOU FOR THE PRESENT! I RAN ALL THE WAY TO THE INN TO SHOW EVERYONE! - HAZEL")
                .pic("gift", "arrow", "house", "heart");
    }

    private static void events(Cast cast) {
        cast.event("hazel_2", "hazel", 2).near("plaza", 200).between(900, 1700).dry()
                .summary("Hazel races you across the plaza (and loses, happily).")
                .enter("hazel", -1, 200, 36)
                .say("hazel", "{FARMER}! RACE ME! FROM HERE TO THE INN! READY? GO!",
                        "farmer", "arrow", "house", "!")
                .walk("hazel", 120, 18)
                .narrate("YOU WALK. HAZEL RUNS. YOU STILL GET THERE FIRST.")
                .walk("hazel", 60, 12)
                .emote("hazel", "sweat")
                .say("hazel", "YOU WON! BY A LOT! BUT I WAS FASTER THAN YESTERDAY. YESTERDAY I FELL OVER.",
                        "farmer", "heart", "arrow", "!")
                .hop("hazel")
                .say("hazel", "AGAIN TOMORROW! AND EVERY DAY UNTIL I WIN! THAT'S A PROMISE!", "clock", "arrow", "!")
                .flag("hazel_race")
                .leave("hazel", 260);

        cast.event("hazel_4", "hazel", 4).near("workshop", 160).between(900, 1700).dry().requires("hazel_2")
                .summary("Hazel shows you the museum shelf she dusts, and a gap where a relic was.")
                .enter("hazel", 1, 200, 36)
                .say("hazel", "COME SEE! COME SEE! I DUST THE MUSEUM SHELF EVERY MORNING. FAST. LOOK HOW SHINY.",
                        "house", "sparkle", "!")
                .sfx(Sfx.RING)
                .say("hazel", "THAT'S A RING MOULD. THAT'S A TOTEM CHIP. AND THAT'S... THAT'S WHERE THE STAR POST CAP WAS.",
                        "ring", "sparkle", "no", "?")
                .emote("hazel", "!")
                .say("hazel", "IT'S GONE! SOMEONE TOOK IT! I DIDN'T TAKE IT! I'D NEVER! I DUST IT!",
                        "sparkle", "no", "sad", "!")
                .emote("farmer", "?")
                .say("hazel", "WILL YOU HELP ME FIND IT? WE'LL BE DETECTIVES. FAST DETECTIVES.", "farmer", "heart", "?")
                .flag("museum_relic_missing")
                .leave("hazel", 260);
    }
}
