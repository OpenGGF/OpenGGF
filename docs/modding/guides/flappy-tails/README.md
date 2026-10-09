# Flappy Tails: a tutorial in nine parts

This tutorial makes **Flappy Tails**: Flappy Bird starring Sonic 3 & Knuckles' Tails, flying
on the ROM's own flight routine through five real zones. It starts from nothing, makes a
choice in part 1, and ends with the finished [`examples/flappy-tails`](../../../../examples/flappy-tails/README.md)
game and a way to film it.

You need Java 21, Maven and Python 3, an OpenGGF source checkout, and your own locked-on
Sonic 3 & Knuckles ROM set up for the engine. Code mods need the JVM build. Each part names
its checkpoint: a complete mod you can build and play at that point.

```bash
python3 examples/build_example.py flappy-tails/tutorial/part-4-flight --run   # any checkpoint
./launch-flappy-tails.sh                                                       # the finished game
```

## Part 1: native game or a scene of your own?

Before writing anything, decide what kind of mod Flappy Tails is. OpenGGF offers two very
different ways to make a game out of Sonic 3 & Knuckles, and the choice decides everything
after it.

**Change the native game.** Your mod runs *inside* a real S3K level. Tails is the engine's
own player object: the code that flies him through Angel Island, with his physics, animation,
sounds, collision, death, HUD and rewind. Your mod adds objects and policies around him: a
controller that holds him in place and moves the pipes, a filter that removes left and right
from the controller, a HUD layout, a new level to start in.

**Replace the whole screen with a scene.** Your mod draws every pixel itself, sixty times a
second, and no level runs. It decodes what it needs straight from the player's ROM: Tails'
frames and animation scripts, zone backgrounds with their parallax, title cards, badniks.
How Tails moves is your code; Flappy Tails ports the ROM's flight routine to keep his feel.

| | Native game | Scene |
| --- | --- | --- |
| Tails | the engine's real player: every ROM behaviour for free | drawn from ROM art; movement is yours (a port of the ROM's) |
| Look | a level: tiles and object sprites only | anything: backdrops, any sprite, text, effects |
| Zone art | a mod zone cannot borrow a stock zone's background or parallax, and S3K objects cannot take ROM art | every pictured act's backdrop, foreground, title card |
| Menus, results, medals | overlays drawn over a running level | ordinary screens |
| Death and retry | the stock death and level reload, unless you intercept them | yours: instant |
| Mod API it teaches | game-start zones, launch teams, input filters, HUD profiles, objects, rewind | scenes, ROM art, audio, storage, debug and capture |
| Best for | changing how the game itself plays | a different game made of the game's parts |

Neither is better. A native mod keeps everything that makes the game *the game* and changes
how it plays; a scene can look like anything and play like anything, but owns everything it
does.

- **Choose native** to see how far the real game bends:
  **[Path A: the native game](native.md)**. It tours the maintained native sample, which
  already flies native Tails through recycling pipes, and shows where that road ends.
- **Choose a scene** to build the finished game, polished: menus, five zones, medals, Super
  Tails. **[Path B starts at part 2](02-first-scene.md)**, and it is the rest of this tutorial.

You can read both. Path A is one page, and part 2 picks up the scene path whichever way you
came.

## Path B: the scene

| Part | You build | Checkpoint |
| --- | --- | --- |
| [2. A scene of your own](02-first-scene.md) | A startup scene: Angel Island's parallax and a ground strip cut from the level | `tutorial/part-2-first-scene` |
| [3. Tails from the ROM](03-tails.md) | Tails and his spinning tails, layered as the game does | `tutorial/part-3-tails` |
| [4. Flight](04-flight.md) | `Tails_Move_FlySwim` ported line for line; one button | `tutorial/part-4-flight` |
| [5. Pillars](05-pillars.md) | Pillars cut from the act's ground, a fair seeded course, a score and a best | `tutorial/part-5-pillars` |
| [6. The rules](06-rules.md) | A `Run` that decides and a scene that presents; Sonic's ring rules | the finished game |
| [7. The tour](07-the-tour.md) | Five zones, title cards on the ROM's timing, letters cut from the cards | the finished game |
| [8. Screens and polish](08-screens-and-polish.md) | Title, results, medals, records, juice, Super Tails | the finished game |
| [9. Tests, autopilot and the promo](09-tests-and-promo.md) | Proving it fair, flying it hands-free, filming it | the finished game |

Parts 2 to 5 each come with a complete checkpoint mod in
[`examples/flappy-tails/tutorial`](../../../../examples/flappy-tails/tutorial). Classes they
share with the finished game are the finished game's own (the engine tests check they never
drift). From part 6 on, the finished example is the checkpoint, and each part walks through its
source.

Read the [mod scene guide](../mod-scenes.md) alongside: it is the reference this tutorial
leans on.
