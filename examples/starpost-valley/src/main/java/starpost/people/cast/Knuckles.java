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
 * Knuckles: glides into the valley on Summer 1 of the first year, after an Emerald Shard, and
 * keeps watch from the totem ledge. Blunt, proud, allergic to lies; suspects Sonic, trusts Tails
 * (mostly).
 */
public final class Knuckles {
    /** Summer 1, year 1, as a day number. */
    static final int ARRIVAL = 28;

    private Knuckles() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("knuckles", "KNUCKLES")
                .about("GUARDIAN. WATCHES FROM THE TOTEM LEDGE.")
                .body("hero:knuckles").home("ledge").birthday(WINTER, 2).hero().arrives(ARRIVAL)
                .loves("marble_grape", "emerald_shard", "ruby")
                .likes("palm_coconut", "loop_berry", "ruby_berry")
                .kind(Kind.MINERAL, Taste.LIKE)
                .dislikes("chili_dog", "spring_tulip")
                .hates("egg_plant", "eggman_pumpkin");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().at(600, "ledge").at(1000, "ruins", -30).at(1300, "ledge").at(1700, "palms_far").at(2000, "ledge");
        v.plan().rain().at(600, "ledge").inside(900, "ruins").at(1800, "ledge");
    }

    private static void lines(VillagerDef v) {
        v.line("SO YOU'RE THE ONE EVERYONE KEEPS TALKING ABOUT. I'M KNUCKLES. I'M NOT HERE FOR YOU. I'M HERE FOR A SHARD.")
                .first();
        v.line("SONIC. OF COURSE IT'S YOU. IF YOU'RE AFTER THE EMERALD SHARD, YOU'LL HAVE TO GET PAST ME.")
                .first().farmer("sonic");
        v.line("TAILS. YOU, I TRUST. MOSTLY. DON'T TOUCH ANYTHING THAT GLOWS.").first().farmer("tails");
        v.line("A SHARD OF THE MASTER EMERALD FELL NEAR HERE. I CAN FEEL IT. IT'S ANNOYING.");
        v.line("THE RUINS HUM AT NIGHT. NOT THE EMERALD. SOMETHING ELSE. SOMETHING MECHANICAL.");
        v.line("I DON'T TRUST ANYONE WHO SMILES THAT MUCH. I'M LOOKING AT ROBOTNIK. AND AT PIP, A LITTLE.");
        v.line("I STAND HERE BECAUSE THE LEDGE IS HIGH. YOU SEE EVERYTHING FROM UP HERE. MOSTLY RABBITS.");
        v.line("I CAN CLIMB ANY WALL IN THIS VALLEY. I HAVE. TWICE. IT'S A SMALL VALLEY.");
        v.line("HOT. THE ISLAND'S HOT TOO, BUT IT'S A GOOD HOT. THIS IS A FARM HOT.").summer();
        v.line("SUMMER STORMS ARE GOOD FOR ONE THING. THEY SHOW YOU WHO'S AFRAID OF THUNDER. NOT ME.").summer();
        v.line("THE GRAPES ON THE TRELLISES LOOK LIKE THE ONES ON THE ISLAND. ALMOST.").fall();
        v.line("THE LEAVES ARE THE SAME COLOUR AS ME. GOOD CAMOUFLAGE. NOT THAT I NEED IT.").fall();
        v.line("SNOW ON THE LEDGE. I'M NOT COLD. ECHIDNAS DON'T GET COLD. WE GET... FOCUSED.").winter();
        v.line("THE LAKE FROZE AT THE EDGES. I PUNCHED IT. NOW IT'S NOT FROZEN AT ONE EDGE.").winter();
        v.line("RAIN DOESN'T BOTHER ME. IT BOTHERS THE RUINS. THEY DRIP. I'M GOING IN.").rain();
        v.line("SONIC. IF YOU EVEN LOOK AT THAT SHARD, I'LL KNOW.").farmer("sonic").below(4);
        v.line("YOU ACTUALLY WORK HARD. I DIDN'T THINK YOU KNEW HOW TO STAND IN ONE PLACE. HMPH.")
                .farmer("sonic").hearts(3);
        v.line("I'M NOT SAYING YOU'RE A GOOD FARMER, SONIC. I'M SAYING YOUR RADISHES ARE ACCEPTABLE.")
                .farmer("sonic").hearts(5);
        v.line("TAILS. YOU SHOULD SLEEP MORE. I CAN SEE YOUR LAMP FROM THE LEDGE AT TWO IN THE MORNING.")
                .farmer("tails");
        v.line("IF ANYONE BOTHERS YOUR FARM, TAILS, THEY'LL HAVE TO GET PAST ME. THAT'S NOT A FAVOUR. IT'S A FACT.")
                .farmer("tails").hearts(4);
        v.line("YOU'RE NOT AS BAD AS I THOUGHT. THAT'S A COMPLIMENT. DON'T GET USED TO IT.").hearts(2);
        v.line("I TOLD PUD THE DARK IS JUST THE LIGHT TAKING A BREAK. HE DIDN'T BELIEVE ME. NEITHER DO I.")
                .hearts(4);
        v.line("ON THE ISLAND, NOBODY TALKS TO ME. HERE, EVERYBODY DOES. IT'S... LOUD. I DON'T HATE IT.")
                .hearts(6);
        v.line("THE ANIMALS THINK I'M SCARY. GOOD. NOW THEY CAN TELL ME THAT TO MY FACE.").flag(People.TRANSLATOR);
        v.line("TOMORROW THE FLICKIES FLY. I'LL WATCH FROM THE LEDGE. BEST SEAT IN THE VALLEY.").on(SUMMER, 27);
        v.line("YOU KNOW IT'S MY BIRTHDAY? WHO TOLD YOU? ...PIP. IT WAS PIP. THANKS.").birthday();
        v.line("DON'T TELL ANYONE ABOUT LAST NIGHT. I MEAN IT. ...THANKS FOR LISTENING.").after("knuckles_8", 5);

        v.again("STILL HERE. STILL WATCHING.");
        v.again("WHAT. I'M GUARDING. GO FARM SOMETHING.");

        v.gift(Taste.LOVE, "...THIS IS GOOD. REALLY GOOD. WHERE DID YOU... NEVER MIND. THANK YOU.");
        v.gift(Taste.LIKE, "HMPH. NOT BAD. I'LL KEEP IT.");
        v.gift(Taste.NEUTRAL, "WHAT AM I SUPPOSED TO DO WITH THIS? ...FINE. I'LL GUARD IT.");
        v.gift(Taste.DISLIKE, "YOU THINK I'D EAT THAT? I HAVE STANDARDS.");
        v.gift(Taste.HATE, "THAT HAS ROBOTNIK'S FACE ON IT. WHY WOULD YOU HAND ME ROBOTNIK'S FACE?");
        v.birthdayGift("FOR ME? ON MY BIRTHDAY? ...I DON'T KNOW WHAT YOU SAY. ON THE ISLAND THERE'S NOBODY TO SAY IT TO.");
        v.thanks("{FARMER}. THE GIFT WAS GOOD. THAT'S ALL. - K");
    }

    private static void events(Cast cast) {
        cast.event("knuckles_2", "knuckles", 2).near("ledge", 200).between(800, 1800).dry()
                .summary("Knuckles suspects the farmer is after the Emerald Shard.")
                .place("knuckles", 60).face("knuckles", 0)
                .say("knuckles", "STOP RIGHT THERE.")
                .emote("farmer", "!")
                .say("knuckles", "YOU'VE BEEN WALKING PAST MY LEDGE EVERY DAY. LOOKING. ARE YOU AFTER THE SHARD?")
                .sayIf("sonic", "knuckles", "BECAUSE LAST TIME YOU 'JUST HAPPENED TO BE PASSING', AN ISLAND FELL OUT OF THE SKY.")
                .sayIf("tails", "knuckles", "I KNOW YOU, TAILS. BUT SONIC SENT YOU, DIDN'T HE? HE'S ALWAYS SENDING YOU.")
                .emote("farmer", "sweat")
                .say("knuckles", "...FINE. YOU'RE JUST FARMING. BUT I'M WATCHING YOU, {FARMER}.")
                .say("knuckles", "AND IF YOU FIND A GREEN SHARD IN YOUR TURNIPS, YOU BRING IT TO ME. GOT IT?")
                .flag("knuckles_watching")
                .leave("knuckles", 260);

        cast.event("knuckles_4", "knuckles", 4).near("meadow", 200).between(900, 1700).dry().requires("knuckles_2")
                .summary("A test: Knuckles makes the farmer find what's buried in the meadow.")
                .enter("knuckles", -1, 220, 44)
                .say("knuckles", "YOU WANT ME TO TRUST YOU? THEN PROVE YOU CAN FIND THINGS. SOMETHING'S BURIED HERE.")
                .say("knuckles", "I KNOW WHAT. YOU DON'T. DIG.")
                .emote("farmer", "?")
                .sfx(Sfx.GROUND_SLIDE).pause(20).sfx(Sfx.GROUND_SLIDE).pause(20).sfx(Sfx.BREAK)
                .give("marble_chip", 5)
                .say("knuckles", "MARBLE. FROM THE RUINS. YOU FOUND IT IN TWELVE SECONDS. IT TOOK ME TEN.")
                .emote("knuckles", "anger")
                .say("knuckles", "...THAT'S NOT A COMPLIMENT. THAT'S A MEASUREMENT. YOU PASS.")
                .flag("knuckles_test_passed")
                .leave("knuckles", 260);

        cast.event("knuckles_6", "knuckles", 6).onFarm().between(900, 1500).dry().requires("knuckles_4")
                .summary("Knuckles teaches the farmer to dig properly: buried finds in the field.")
                .enter("knuckles", 1, 220, 34)
                .say("knuckles", "YOUR FIELD. IT'S FULL OF ROCKS. YOU KEEP GOING ROUND THEM. WHY?")
                .say("knuckles", "WATCH. YOU DON'T DIG DOWN. YOU DIG THROUGH. LIKE THE GROUND OWES YOU MONEY.")
                .sfx(Sfx.BREAK).pause(16).sfx(Sfx.BREAK)
                .say("knuckles", "THERE'S ALWAYS SOMETHING UNDER A FARM. OLD RINGS. MARBLE. SEEDS NOBODY PLANTED.")
                .give("marble_grape_seeds", 3)
                .say("knuckles", "GRAPE SEEDS. FROM THE ISLAND. THEY GROW ON TRELLISES. DON'T MAKE IT WEIRD.")
                .flag("knuckles_dig_lesson")
                .leave("knuckles", 260);

        cast.event("knuckles_8", "knuckles", 8).near("ledge", 220).between(2000, 2500).dry().requires("knuckles_6")
                .summary("On the ledge at night: Knuckles admits guarding an island alone is lonely.")
                .music(Tunes.S1, Tunes.SLZ)
                .place("knuckles", 50).face("knuckles", 1)
                .pause(40)
                .say("knuckles", "...YOU'RE UP LATE. SIT. OR DON'T. IT'S A LEDGE. IT'S NOT MINE.")
                .say("knuckles", "ON THE ISLAND, AT NIGHT, IT'S JUST ME AND THE EMERALD. IT DOESN'T TALK.")
                .say("knuckles", "I TELL MYSELF THAT'S FINE. GUARDIANS DON'T NEED COMPANY. IT'S IN THE NAME.")
                .face("knuckles", 0)
                .say("knuckles", "THEN I CAME HERE, AND EVERY MORNING SOMEONE WAVES AT ME. I DIDN'T KNOW I WANTED THAT.")
                .emote("farmer", "heart")
                .say("knuckles", "IF YOU TELL SONIC I SAID ANY OF THIS, I'LL BURY YOU NEXT TO THE MARBLE.")
                .sayIf("sonic", "knuckles", "...YOU ARE SONIC. I KNOW. THAT MAKES IT WORSE.")
                .leave("knuckles", 200);
    }
}
