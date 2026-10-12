package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.SAT;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Pud, a Picky pig: lives in a shack at the mouth of the Marble Ruins, brave about gems and
 * frightened of the dark since his capture. Speaks in pictures until the Chirp Translator.
 */
public final class Pud {
    private Pud() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("pud", "PUD")
                .about("PICKY. LIVES BY THE RUINS. LOVES GEMS. FEARS THE DARK.")
                .body("animal:picky").home("shack").birthday(SUMMER, 17).animal()
                .loves("ruby", "ruby_berry", "emerald_shard", "frost_ring")
                .likes("marble_chip", "totem_choke", "spin_spud")
                .kind(Kind.MINERAL, Taste.LOVE).kind(Kind.RELIC, Taste.LIKE)
                .dislikes("fibre")
                .hates("scrap_amaranth", "scrap");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "shack").at(800, "shack", 18).at(1100, "meadow", -30).at(1400, "ruins", -36)
                .at(1600, "shack", 18).inside(1930, "shack");
        v.plan().weekdays(SAT).inside(600, "shack").at(1000, "plaza", 50).at(1500, "inn_porch", -20)
                .at(1700, "shack", 18).inside(1930, "shack");
        v.plan().rain().inside(600, "shack");
    }

    private static void lines(VillagerDef v) {
        v.line("HI. I'M PUD. THAT'S THE RUINS. I DON'T GO IN THE RUINS. I GO NEAR THE RUINS. THAT'S DIFFERENT.")
                .first().pic("who:pud", "house", "moon", "no", "sweat");
        v.line("I FOUND A SHINY STONE TODAY. IT WAS A SNAIL. STILL COUNTS.").pic("sparkle", "?", "heart");
        v.line("THERE ARE RUBIES IN THE RUINS. I CAN SMELL THEM. RUBIES SMELL RED.").pic("moon", "sparkle", "!");
        v.line("THE DARK IS JUST A ROOM WITH THE LIGHTS OFF. I KNOW THAT. MY LEGS DON'T.").pic("moon", "sweat", "sad");
        v.line("WHEN THEY CAPTURED ME THEY PUT ME IN THE DARK FOR A LONG TIME. I DON'T LIKE TALKING ABOUT IT.")
                .hearts(3).pic("badnik", "moon", "sad", "...");
        v.line("I SORT MY GEMS BY COLOUR. THEN BY SIZE. THEN BY HOW MUCH I LIKE THEM. RED WINS.")
                .pic("sparkle", "heart", "!");
        v.line("KNUCKLES GOES IN THE RUINS EVERY DAY. HE SAYS HE ISN'T SCARED. HE'S LYING. NOBODY'S THAT BRAVE.")
                .flag("knuckles_watching").pic("who:knuckles", "moon", "sweat", "?");
        v.line("SPRING RAIN WASHES NEW STONES OUT OF THE CLIFF. BEST TIME FOR STONE HUNTING.").spring()
                .pic("rain", "sparkle", "heart");
        v.line("THE DAYS ARE LONG IN SUMMER. LONG DAYS ARE MY FAVOURITE. LESS NIGHT.").summer()
                .pic("sun", "heart", "moon", "no");
        v.line("IN FALL THE NIGHTS GET LONGER. I'VE GOT MORE LAMPS. EIGHT. I'M GETTING A NINTH.").fall()
                .pic("moon", "sweat", "sparkle");
        v.line("SNOW IS SHINY. SNOW IS THE SHINIEST THING THERE IS. IT DOESN'T KEEP. I TRIED.").winter()
                .pic("snow", "sparkle", "sad");
        v.line("WINTER NIGHTS ARE THE LONGEST. I SLEEP WITH ALL MY LAMPS ON. ALL NINE.").winter()
                .pic("moon", "zzz", "sparkle");
        v.line("THE RUINS DRIP WHEN IT RAINS. DRIP DRIP DRIP. IT SOUNDS LIKE FOOTSTEPS. IT ISN'T. I CHECKED.")
                .rain().pic("rain", "house", "sweat");
        v.line("YOU'RE BRAVE. YOU RUN INTO THINGS. I WANT TO BE BRAVE LIKE THAT. MAYBE SLOWER.").hearts(2)
                .pic("farmer", "heart", "!");
        v.line("I WENT ONE STEP INTO THE RUINS TODAY. ONE. THEN I CAME OUT. ONE IS MORE THAN NONE.")
                .after("pud_4", 6).pic("house", "moon", "!", "heart");
        v.line("I CAN TALK NOW! WELL, YOU CAN LISTEN. LISTEN TO THIS: RUBIES. THAT'S ALL. RUBIES.")
                .flag(People.TRANSLATOR).after("tails_2", 6);
        v.line("A BIRTHDAY! MY BIRTHDAY! I'M GOING TO SPEND IT IN THE SUNSHINE. ALL OF IT.").birthday()
                .pic("gift", "sun", "heart");

        v.line("THE RUINS USED TO BE A PALACE. MARBLE EVERYWHERE. NOW IT'S MARBLE EVERYWHERE AND BADNIKS.")
                .pic("house", "sparkle", "badnik", "sad");
        v.line("I'M SAVING FOR A TENTH LAMP. RUSTY SAYS NINE IS ENOUGH. RUSTY CAN SEE IN THE DARK. IT'S NOT FAIR.")
                .pic("sparkle", "who:rusty", "moon", "anger");
        v.line("IF YOU FIND A RUBY, I'LL TRADE YOU FOR IT. FOR ANOTHER RUBY. I JUST WANT TO HOLD ONE.")
                .pic("sparkle", "farmer", "heart");
        v.line("I SPENT ALL DAY IN THE SUN. I'M PINK. I WAS ALREADY PINK. NOW I'M PINKER.").summer()
                .pic("sun", "heart", "!");
        v.line("HAZEL BURIES THINGS IN THE MEADOW. I DIG THEM UP. WE DON'T TALK ABOUT IT.").fall()
                .pic("who:hazel", "house", "sweat");
        v.line("KNUCKLES! ARE EMERALDS SHINIER THAN RUBIES? DON'T ANSWER. I WANT BOTH.").farmer("knuckles")
                .pic("farmer", "sparkle", "sparkle", "?");
        v.line("YOU OPENED MY CAPSULE, I THINK. IT WAS BLUE AND FAST. I SAID THANK YOU BUT YOU WERE GONE.")
                .farmer("sonic").hearts(2).pic("farmer", "badnik", "heart");
        v.line("TAILS, CAN YOU BUILD A LAMP THAT NEVER GOES OUT? I'LL PAY. IN RUBIES. THAT I DON'T HAVE.")
                .farmer("tails").pic("farmer", "sparkle", "moon", "?");
        v.line("WHEN YOU GO IN THE RUINS I SIT AT THE DOOR AND COUNT TO A THOUSAND. YOU ALWAYS COME OUT FIRST.")
                .hearts(4).pic("farmer", "house", "clock", "heart");
        v.line("TOMORROW NIGHT THE SKY IS FULL OF FLICKIES. IT'S THE ONLY NIGHT I'M NOT SCARED OF THE DARK.")
                .on(SUMMER, 27).pic("flicky", "moon", "heart");

        v.again("STILL HERE. NOT IN THERE. HERE.").pic("house", "no", "moon");
        v.again("DID YOU HEAR THAT? NO? GOOD. NEITHER DID I.").pic("sweat", "?");

        v.gift(Taste.LOVE, "IT'S SHINY! IT'S SO SHINY! I'M GOING TO SLEEP WITH IT UNDER MY PILLOW!")
                .pic("sparkle", "heart", "heart", "!");
        v.gift(Taste.LIKE, "OOH. NICE. I'LL PUT IT IN THE COLLECTION. SECOND ROW.").pic("sparkle", "heart");
        v.gift(Taste.NEUTRAL, "THANKS! IT'S NOT SHINY. BUT THANKS.").pic("heart", "...");
        v.gift(Taste.DISLIKE, "WEEDS? I LIVE NEXT TO THE RUINS. I HAVE ENOUGH WEEDS.").pic("no", "...");
        v.gift(Taste.HATE, "THAT'S FROM SCRAP BRAIN. THAT'S WHERE THEY TOOK US. PLEASE. TAKE IT AWAY.")
                .pic("badnik", "moon", "sad", "no");
        v.birthdayGift("A PRESENT! ON MY BIRTHDAY! IS IT SHINY? IT DOESN'T MATTER. IT'S FROM YOU.")
                .pic("gift", "sparkle", "heart");
        v.thanks("THANK YOU FOR THE PRESENT. I SLEPT WITH IT UNDER MY PILLOW. ONLY ONE LAMP ON. - PUD")
                .pic("gift", "zzz", "heart");
    }

    private static void events(Cast cast) {
        cast.event("pud_2", "pud", 2).near("ruins", 180).between(1600, 2100).dry()
                .summary("Pud won't go in the Ruins, but sells you a lamp for when you do.")
                .place("pud", -40).face("pud", 0)
                .say("pud", "YOU'RE GOING IN THERE? AT NIGHT? IT'S DARK IN THERE. VERY DARK. DARKER THAN OUT HERE.",
                        "farmer", "house", "moon", "sweat", "?")
                .emote("pud", "sweat")
                .say("pud", "I HAVE LAMPS. NINE LAMPS. HERE. TAKE ONE. NO, TAKE TWO. NO, ONE. I NEED EIGHT.",
                        "sparkle", "farmer", "heart")
                .flag("pud_lamp").sfx(Sfx.GRAB)
                .narrate("YOU GOT PUD'S SPARE LAMP. THE RUINS WILL BE A LITTLE LESS DARK.")
                .say("pud", "IF YOU SEE ANYTHING SHINY IN THERE, IT'S PROBABLY A RUBY. IF IT BLINKS, RUN.",
                        "sparkle", "heart", "badnik", "!");

        cast.event("pud_4", "pud", 4).near("ruins", 180).between(1000, 1700).dry().requires("pud_2")
                .summary("Pud walks into the Ruins' mouth with you, holding the lamp.")
                .place("pud", -40).face("pud", 0)
                .say("pud", "I'VE DECIDED SOMETHING. I'M COMING WITH YOU. TO THE DOOR. JUST THE DOOR.",
                        "who:pud", "farmer", "house", "!")
                .walk("pud", 30, 5)
                .emote("pud", "sweat")
                .pause(30)
                .say("pud", "...IT'S NOT SO BAD. IT'S JUST A ROOM. A ROOM WITH THE LIGHTS OFF.", "moon", "house", "sweat")
                .say("pud", "I'LL HOLD THE LAMP. YOU DO THE BRAVE PART. THAT'S A TEAM.", "sparkle", "farmer", "heart")
                .emote("farmer", "heart")
                .flag("pud_brave")
                .walk("pud", -40, 12);
    }
}
