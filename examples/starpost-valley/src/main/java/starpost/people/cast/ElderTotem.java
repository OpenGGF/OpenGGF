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
 * The Elder Totem: Green Hill's own totem pole on the meadow, the valley's memory. Speaks in
 * riddles (in words: it is not an animal) and accepts offerings. A grandparent without inventing
 * a human.
 */
public final class ElderTotem {
    private ElderTotem() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("elder_totem", "ELDER TOTEM")
                .about("THE VALLEY'S MEMORY. STANDS ON THE MEADOW.")
                .body("totem").home("meadow").birthday(WINTER, 21)
                .loves("totem_choke", "totem_leek", "hill_daffodil")
                .likes("spring_tulip", "sunflower", "starpost_corn")
                .kind(Kind.FORAGE, Taste.LIKE).kind(Kind.CROP, Taste.LIKE).kind(Kind.MATERIAL, Taste.DISLIKE)
                .hates("egg_plant", "eggman_pumpkin");
        v.plan().at(600, "meadow", 40);
        lines(v);
        events(cast);
    }

    private static void lines(VillagerDef v) {
        v.line("...A NEW FOOTSTEP. QUICK. LIGHT. IT HAS RUN A LONG WAY TO STAND STILL HERE.").first();
        v.line("I HAVE FOUR FACES. ONE FOR EACH SEASON. THIS ONE IS FOR LISTENING.");
        v.line("WHAT GROWS SLOWER THAN A TREE, AND FASTER THAN A HEDGEHOG CAN NOTICE? A HOME.");
        v.line("THE HILL REMEMBERS EVERY FOOTSTEP. YOURS ARE FAST. PLANT SLOWLY.");
        v.line("BEFORE THE BADNIKS, THE VALLEY SANG. AFTER, IT WAS QUIET. NOW IT HUMS. HUMMING IS A START.");
        v.line("I WAS CARVED BY HANDS I NEVER SAW. YOU ARE CARVING THIS VALLEY. DO YOU SEE YOUR HANDS?");
        v.line("A RING IS A CIRCLE. A CIRCLE IS A ROAD THAT COMES HOME. YOU ARE ON ONE.");
        v.line("WHAT CAN YOU HOLD THAT CANNOT BE CARRIED? THINK ABOUT IT ON YOUR WALK.").hearts(1);
        v.line("SPRING. THE GROUND WAKES BEFORE THE FLOWERS DO. THE GROUND IS ALWAYS FIRST.").spring();
        v.line("THE SUNFLOWERS FACE THE SUN. I FACE THE VALLEY. WE ARE BOTH WAITING FOR SOMETHING TO RISE.").spring();
        v.line("SUMMER. THE LONGEST DAYS. EVEN SO, THEY END. THAT IS WHY THEY ARE BEAUTIFUL.").summer();
        v.line("THE FLICKIES FLY OVER THE LAKE AT SUMMER'S END. THEY DO NOT KNOW WHY. I DO. I WILL NOT SAY.").summer();
        v.line("FALL. THE VALLEY LETS GO OF ITS LEAVES. LETTING GO IS ALSO A HARVEST.").fall();
        v.line("THE NIGHTS GROW LONG. GOOD. THE STARS HAVE THINGS TO TELL ME.").fall();
        v.line("WINTER. NOTHING GROWS. EVERYTHING REMEMBERS. THIS IS THE SEASON OF ROOTS.").winter();
        v.line("SNOW ON MY HEAD. I HAVE WORN THIS HAT FOR A THOUSAND WINTERS. IT STILL FITS.").winter();
        v.line("RAIN. THE SKY WATERS WHAT YOU FORGOT. THE SKY IS A KIND FARMER.").rain();
        v.line("YOU COME BACK. THE ONES WHO COME BACK ARE THE ONES WHO STAY.").hearts(2);
        v.line("I HAVE SEEN MANY HEROES PASS THIS HILL. YOU ARE THE FIRST TO STOP AND PLANT SOMETHING.")
                .farmer("sonic").hearts(3);
        v.line("THE FOX WITH TWO TAILS BUILDS SKY-BOATS. NOW HE BUILDS ROWS OF RADISH. BOTH FLY, IN THEIR WAY.")
                .farmer("tails").hearts(3);
        v.line("THE GUARDIAN GUARDS A STONE. NOW HE GUARDS A FIELD. THE FIELD IS GRATEFUL. THE STONE IS NOT.")
                .farmer("knuckles").hearts(3);
        v.line("THE SMALL ONES SPEAK NOW. THEY ALWAYS SPOKE. YOU HAVE ONLY JUST STARTED LISTENING.")
                .flag(People.TRANSLATOR);
        v.line("WHEN THE YEAR TURNS, I WILL ASK THE VALLEY HOW IT IS. IT WILL TELL ME ABOUT YOU.").hearts(4);
        v.line("A BIRTHDAY, FOR A POST OF WOOD. THE MOSS ON MY NORTH SIDE SAYS TODAY. THE MOSS IS NEVER WRONG.")
                .birthday();

        v.again("...THE TOTEM IS SILENT. IT IS LISTENING.");
        v.again("...A FACE FLICKERS. THE TOTEM IS THINKING.");

        v.gift(Taste.LOVE, "AN OFFERING FROM THE VALLEY'S OWN SOIL. THE OLD FACES REMEMBER THIS TASTE. THANK YOU.");
        v.gift(Taste.LIKE, "THE VALLEY THANKS YOU THROUGH ME. I AM ITS MOUTH. IT IS MY STOMACH.");
        v.gift(Taste.NEUTRAL, "...THE TOTEM ACCEPTS IT. THE TOTEM ACCEPTS MOST THINGS.");
        v.gift(Taste.DISLIKE, "WOOD AND STONE. I AM WOOD. I KNOW STONE. I DO NOT NEED MORE OF EITHER.");
        v.gift(Taste.HATE, "HIS FACE. CARVED IN A VEGETABLE. THE MOUNTAIN IS MOCKED. TAKE IT DOWN THE HILL.");
        v.birthdayGift("AN OFFERING ON MY DAY. NOBODY HAS DONE THAT IN A THOUSAND YEARS. THE MOSS IS PLEASED.");
        v.thanks("THE HILL REMEMBERS YOUR OFFERING. SO DO I. - THE ELDER TOTEM (WRITTEN BY PIP, WHO WAS PASSING)");
    }

    private static void events(Cast cast) {
        cast.event("elder_totem_2", "elder_totem", 2).near("meadow", 200).between(1800, 2300).dry()
                .summary("The totem remembers the valley before the badniks, and its faces light up.")
                .music(Tunes.S1, Tunes.SLZ)
                .pause(30)
                .say("elder_totem", "...STAY. LISTEN. TONIGHT THE HILL WILL SHOW YOU SOMETHING IT HAS NOT SHOWN ANYONE.")
                .sfx(Sfx.SIGNPOST).emote("elder_totem", "sparkle")
                .say("elder_totem", "BEFORE THE BADNIKS, THE MEADOW WAS FULL OF NESTS. EVERY FLOWER HAD A BIRD ON IT.")
                .say("elder_totem", "THE NIGHT THE MACHINES CAME, THE BIRDS WENT QUIET. I HAVE BEEN QUIET WITH THEM.")
                .emote("farmer", "sad")
                .say("elder_totem", "NOW THE BIRDS ARE BACK, AND SO, IT SEEMS, AM I. THANK YOU, FAST ONE.")
                .flag("totem_awake");

        cast.event("elder_totem_4", "elder_totem", 4).near("meadow", 200).between(600, 1200).dry()
                .requires("elder_totem_2")
                .summary("The totem asks the farmer what they want the valley to be, and promises to remember.")
                .say("elder_totem", "A QUESTION, FAST ONE. WHEN THE YEAR TURNS, THE VALLEY WILL BE ASKED HOW IT IS.")
                .say("elder_totem", "WHAT DO YOU WANT IT TO SAY?")
                .emote("farmer", "?")
                .pause(40)
                .emote("farmer", "heart")
                .say("elder_totem", "...YES. THAT IS WHAT I HOPED. I WILL REMEMBER IT FOR YOU, IN CASE YOU FORGET.")
                .say("elder_totem", "HERE. A LEEK FROM MY ROOTS. THEY GROW ONLY WHERE SOMEONE IS LISTENING.")
                .give("totem_leek", 3)
                .flag("totem_promise");
    }
}
