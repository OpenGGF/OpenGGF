package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.FRI;
import static starpost.people.cast.Tunes.SUN;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Clementine, a Cucky hen: cook at the Lamppost Inn, warm and bossy, feeds everyone whether they
 * are hungry or not. Speaks in pictures until the Chirp Translator. Partner at 10 hearts.
 */
public final class Clementine {
    private Clementine() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("clementine", "CLEMENTINE")
                .about("CUCKY. COOK AT THE LAMPPOST INN. FEEDS EVERYONE.")
                .body("animal:cucky").home("inn").birthday(SUMMER, 6).animal()
                .partner("LEAVES A CHILI DOG IN YOUR MONITOR EVERY MORNING")
                .loves("starpost_corn", "honey", "spin_spud", "motobug_tomato")
                .likes("palm_bean", "totem_leek", "chili_dog", "sunflower")
                .kind(Kind.CROP, Taste.LIKE).kind(Kind.FOOD, Taste.LIKE).kind(Kind.ANIMAL_GOOD, Taste.LIKE)
                .dislikes("fibre", "scrap")
                .hates("egg");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "inn").at(700, "inn_porch", -20).inside(830, "inn").at(1400, "seed_stall", -40)
                .inside(1530, "inn");
        v.plan().weekdays(SUN).inside(600, "inn").farm(1000, "door", 30, 12).at(1300, "seed_stall", -40)
                .inside(1500, "inn");
        v.plan().rain().inside(600, "inn");
    }

    private static void lines(VillagerDef v) {
        v.line("YOU'RE THE NEW ONE. YOU'RE TOO THIN. SIT. EAT. THEN WE'LL TALK.").first()
                .pic("farmer", "food", "food", "!");
        v.line("HAVE YOU EATEN? YOU HAVEN'T. I CAN TELL. I CAN ALWAYS TELL.").pic("farmer", "food", "?");
        v.line("A KITCHEN IS JUST A FARM THAT'S BEEN CHOPPED UP SMALL. SO WE'RE IN THE SAME BUSINESS.")
                .pic("food", "house", "heart");
        v.line("RUSTY SWEEPS THE SAME SPOT FORTY TIMES. I'VE STOPPED TELLING HIM. IT'S THE CLEANEST SPOT IN THE VALLEY.")
                .pic("who:rusty", "house", "...");
        v.line("SONIC EATS STANDING UP. ON THE MOVE. I'VE SEEN HIM EAT SOUP RUNNING. IT SHOULDN'T BE POSSIBLE.")
                .pic("who:sonic", "food", "!");
        v.line("I DON'T KNOW HOW TO COOK FOR ONE. I ONLY KNOW HOW TO COOK FOR EVERYONE.").pic("food", "heart", "heart");
        v.line("THE SECRET TO GOOD SOUP IS: MORE. THAT'S THE WHOLE SECRET.").pic("food", "food", "food", "!");
        v.line("SPRING! RADISHES! I'M PUTTING RADISHES IN EVERYTHING. EVERYTHING.").spring()
                .pic("item:ring_radish", "food", "!");
        v.line("THE FIRST SPUDS OF SPRING ARE MY FAVOURITE THING IN THE WORLD. THE SECOND FAVOURITE IS THE SECOND SPUDS.")
                .spring().pic("item:spin_spud", "heart", "heart");
        v.line("TOO HOT TO COOK INDOORS. I'M COOKING ON THE PORCH. RUSTY IS FANNING ME WITH A TRAY.").summer()
                .pic("sun", "food", "who:rusty");
        v.line("CORN! FINALLY! I'VE BEEN WAITING ALL YEAR FOR CORN.").summer().pic("item:starpost_corn", "heart", "!");
        v.line("FALL IS SOUP SEASON. ALL SEASONS ARE SOUP SEASON. FALL IS EXTRA SOUP SEASON.").fall()
                .pic("food", "food", "heart");
        v.line("I'M CATERING THE VALLEY FAIR. ROBOTNIK IS JUDGING. I'M PUTTING EXTRA PEPPER IN HIS.").fall()
                .pic("robotnik", "item:fire_pepper", "!");
        v.line("WINTER. NOTHING GROWS. EVERYBODY'S HUNGRY. MY BUSY SEASON.").winter().pic("snow", "food", "!");
        v.line("WRAP UP WARM. AND EAT SOMETHING. BOTH. AT THE SAME TIME IF YOU CAN.").winter()
                .pic("snow", "farmer", "food");
        v.line("RAIN MEANS EVERYONE COMES TO THE INN. I'VE MADE SIXTY BOWLS. I'LL NEED SEVENTY.").rain()
                .pic("rain", "house", "food", "food");
        v.line("JUKEBOX NIGHT! I DON'T DANCE. I SHIMMY. WITH A LADLE.").weekdays(FRI).pic("note", "food", "heart");
        v.line("YOU'RE LOOKING HEALTHIER. THAT'S MY DOING. YOU'RE WELCOME.").hearts(2).pic("farmer", "heart", "food");
        v.line("I WAS IN A CAPSULE A LONG TIME. ALL I THOUGHT ABOUT WAS FOOD. NOW I MAKE IT. FOR EVERYONE.")
                .hearts(4).pic("sad", "food", "heart");
        v.line("YOU'RE FAMILY NOW. FAMILY GETS SECONDS. AND THIRDS. AND A BAG TO TAKE HOME.").hearts(6)
                .pic("farmer", "heart", "food", "food");
        v.line("AT LAST! NOW YOU CAN UNDERSTAND ME WHEN I TELL YOU TO EAT YOUR GREENS.").flag(People.TRANSLATOR)
                .after("tails_2", 6);
        v.line("MORNING, PARTNER. THERE'S A CHILI DOG IN YOUR MONITOR. DON'T ARGUE.").flag("partner_clementine");
        v.line("TOMORROW'S THE FAIR. I'VE BEEN BAKING SINCE TUESDAY. I CAN'T FEEL MY WINGS.").on(FALL, 15)
                .pic("food", "food", "clock", "!");
        v.line("A BIRTHDAY? MINE? WELL. I SUPPOSE SOMEONE ELSE CAN COOK. NO. I'LL COOK.").birthday()
                .pic("gift", "food", "heart");
        v.line("HAVE YOU TRIED THE CHILI DOG RECIPE YET? DON'T TELL SONIC IT'S HALF PEPPER.")
                .after("clementine_6", 7).pic("item:chili_dog", "item:fire_pepper", "!");

        v.again("BACK FOR SECONDS? I KNEW IT.").pic("food", "food", "!");
        v.again("NO MORE TALKING. EATING. GO.").pic("food", "!");

        v.gift(Taste.LOVE, "OH, NOW THIS IS SOMETHING. I KNOW EXACTLY WHAT THIS IS FOR. DINNER. YOURS.")
                .pic("heart", "heart", "food", "!");
        v.gift(Taste.LIKE, "LOVELY. THIS'LL GO IN TOMORROW'S POT.").pic("food", "heart");
        v.gift(Taste.NEUTRAL, "THANK YOU, DEAR. I'LL FIND A USE FOR IT. I ALWAYS DO.").pic("heart", "...");
        v.gift(Taste.DISLIKE, "WE DON'T COOK WITH THAT. NOT IN MY KITCHEN.").pic("no", "food");
        v.gift(Taste.HATE, "IS THIS... AN EGG? DO YOU KNOW WHO I AM? THAT COULD BE MY COUSIN.")
                .pic("item:egg", "sad", "anger", "!");
        v.birthdayGift("FOR MY BIRTHDAY? YOU SWEET THING. SIT DOWN, I'M MAKING YOU A CAKE TO SAY THANK YOU.")
                .pic("gift", "heart", "food");
        v.thanks("THANK YOU FOR THE GIFT, DEAR. THERE'S A PLATE WAITING AT THE INN WITH YOUR NAME ON IT. - CLEMENTINE")
                .pic("gift", "heart", "house", "food");
    }

    private static void events(Cast cast) {
        cast.event("clementine_2", "clementine", 2).near("inn", 160).between(700, 1200).dry()
                .summary("Second breakfast: Clementine feeds you and hands over her radish stew recipe.")
                .enter("clementine", 1, 200, 34)
                .say("clementine", "THERE YOU ARE. YOU WALKED PAST THE INN THREE TIMES WITHOUT EATING. I COUNTED.",
                        "farmer", "house", "no", "food", "anger")
                .narrate("CLEMENTINE PUSHES A BOWL OF RADISH STEW INTO YOUR HANDS. IT IS VERY HOT AND VERY GOOD.")
                .emote("farmer", "heart")
                .say("clementine", "GOOD. NOW YOU KNOW WHAT A RADISH IS FOR. HERE. TAKE THE RECIPE. IT'S IN PICTURES.",
                        "item:ring_radish", "food", "heart")
                .flag("recipe_radish_stew").sfx(Sfx.PERFECT)
                .narrate("YOU GOT CLEMENTINE'S RADISH STEW RECIPE.")
                .leave("clementine", 240);

        cast.event("clementine_4", "clementine", 4).near("inn", 160).between(1500, 2000).dry()
                .requires("clementine_2")
                .summary("The cookbook: Clementine's recipes are all in her head, so she dictates one.")
                .enter("clementine", 1, 200, 34)
                .say("clementine", "I'VE BEEN THINKING. ALL MY RECIPES ARE IN HERE.", "food", "food", "?")
                .narrate("SHE TAPS HER HEAD WITH A WING.")
                .say("clementine", "AND IF ANYTHING HAPPENED TO ME, NOBODY WOULD KNOW HOW MUCH PEPPER GOES IN THE STEW.",
                        "sad", "item:fire_pepper", "?")
                .say("clementine", "SO YOU'RE WRITING THEM DOWN. ALL OF THEM. STARTING NOW. HERE'S A BOOK.",
                        "farmer", "food", "!")
                .flag("lamppost_cookbook").sfx(Sfx.GRAB)
                .narrate("YOU GOT THE LAMPPOST COOKBOOK. IT IS EMPTY, BUT NOT FOR LONG.")
                .say("clementine", "PAGE ONE: STEW. INGREDIENTS: MORE. METHOD: MORE. THERE. YOU'RE A COOK.",
                        "food", "food", "heart")
                .leave("clementine", 240);

        cast.event("clementine_6", "clementine", 6).near("inn", 160).between(1800, 2300).dry()
                .requires("clementine_4")
                .summary("The Chili Dog recipe, the valley's best Momentum food.")
                .music(Tunes.S1, Tunes.SYZ)
                .enter("clementine", 1, 200, 34)
                .say("clementine", "SHH. COME CLOSER. I'M GOING TO TELL YOU SOMETHING NOBODY KNOWS.",
                        "sweat", "farmer", "?")
                .say("clementine", "THE CHILI DOG. SONIC'S CHILI DOG. THE ONE HE RUNS ACROSS THE VALLEY FOR.",
                        "who:sonic", "item:chili_dog", "!")
                .say("clementine", "IT'S HALF FIRE SHIELD PEPPER. THAT'S WHY IT MAKES YOU RUN. NOW YOU KNOW. DON'T TELL HIM.",
                        "item:fire_pepper", "item:chili_dog", "no")
                .flag("recipe_chili_dog")
                .give("chili_dog", 3)
                .narrate("YOU LEARNED THE CHILI DOG RECIPE.")
                .emote("clementine", "heart")
                .leave("clementine", 240);

        cast.event("clementine_8", "clementine", 8).near("capsule", 220).between(1700, 2200).dry()
                .requires("clementine_6")
                .summary("At the Great Capsule, Clementine remembers the night it opened and decides to stay.")
                .music(Tunes.S1, Tunes.SLZ)
                .enter("clementine", -1, 200, 36).face("clementine", 1)
                .pause(40)
                .say("clementine", "I COME UP HERE SOMETIMES. TO LOOK AT IT. THE CAPSULE.", "house", "...")
                .say("clementine", "IT WAS DARK INSIDE. THEN THE LID BLEW OFF AND THERE WAS SKY. SO MUCH SKY.",
                        "moon", "badnik", "sun", "!")
                .sayIf("sonic", "clementine", "YOU DID THAT. I KNOW YOU DON'T REMEMBER. YOU OPENED A HUNDRED OF THEM.",
                        "farmer", "heart")
                .face("clementine", 0)
                .say("clementine", "I ALWAYS MEANT TO LEAVE. SEE THE WORLD. BUT THE WORLD COMES TO THE INN. HUNGRY.",
                        "house", "food", "heart")
                .say("clementine", "I'M STAYING. THIS IS WHERE I'M NEEDED. AND, IT TURNS OUT, WHERE I WANT TO BE.",
                        "house", "heart", "heart")
                .emote("farmer", "heart")
                .leave("clementine", -240);

        cast.event("clementine_10", "clementine", 10).onFarm().between(630, 1100).dry().requires("clementine_8")
                .partner()
                .summary("A basket at dawn: Clementine asks to be Partners and keeps a kitchen garden on the farm.")
                .music(Tunes.S1, Tunes.ENDING)
                .enter("clementine", 1, 220, 34)
                .say("clementine", "MORNING. I BROUGHT BREAKFAST. AND A QUESTION. EAT FIRST. QUESTION AFTER.",
                        "sun", "food", "?")
                .give("chili_dog", 1)
                .pause(30)
                .say("clementine", "EVERY DAY YOU GROW THINGS AND I COOK THINGS. WHY DON'T WE DO IT TOGETHER?",
                        "farmer", "item:ring_radish", "food", "heart", "?")
                .say("clementine", "A KITCHEN GARDEN. HERE. AND EVERY MORNING THERE'S SOMETHING HOT IN YOUR MONITOR.",
                        "house", "food", "clock")
                .emote("farmer", "heart")
                .say("clementine", "PARTNERS, THEN. GOOD. NOW EAT YOUR GREENS.", "heart", "food", "!")
                .flag("partner_clementine").flag("inn_kitchen_garden")
                .narrate("CLEMENTINE IS YOUR PARTNER. EVERY MORNING SHE LEAVES A CHILI DOG IN YOUR MONITOR.")
                .leave("clementine", 260);
    }
}
