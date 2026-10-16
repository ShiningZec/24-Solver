# 24 Solver — Quick Tutorial

24 Solver finds **every** way to reach a target number (24 by default) from the numbers you type.
Each number is used exactly once, with `+ - × ÷` and any bracketing, and every solution is listed.
Since 1.1 it also groups the answers, so you can see how many *genuinely different* solutions there
are.

## The screen, top to bottom

| line | what it shows |
| --- | --- |
| `TARGET:` | the number you are solving for (24 to start with) |
| `NUMBERS:` | the numbers you have entered so far |
| `INPUT:` | the number you are typing right now |

Below that is the keypad, and a wide **SOLVE** button at the bottom.

```
[ 1 ] [ 2 ] [ 3 ]
[ 4 ] [ 5 ] [ 6 ]
[ 7 ] [ 8 ] [ 9 ]
[+/-] [ 0 ] [NEXT]
[CLEAR][DEL][TARGET]
[       SOLVE       ]
```

## Entering numbers

Tap digits to build a number, `+/-` to flip its sign, `DEL` to remove the last character.
Press `NEXT` to keep the number: `INPUT` clears and the number appears in `NUMBERS`.
You can enter up to **five** numbers, each with **|value| < 256**; anything else is refused with a
short message and left in the input line so you can correct it.

Whatever you have typed but not confirmed is **not lost** when the screen rotates — the numbers, the
target, the line you were typing and the TARGET key all come back exactly as they were.

## Solving for a different target

Tap `TARGET`. The key stays lit and the keypad now types the target instead of numbers — the
`TARGET INPUT` line starts empty, so type the value you want.

- Tap `TARGET` again to leave: whatever you typed is written back to `TARGET:`.
- Or press `NEXT` to keep it and stay in target mode.
- Tap `TARGET` twice without typing anything and the target simply stays as it was.
- Values beyond ±16384 are refused, and the key stays lit so you can fix the input.

## Starting over

- `CLEAR` **tap** — clears only what the current mode owns, and says nothing: with `TARGET` off it
  empties `NUMBERS`, with `TARGET` lit it puts the target back to 24.
- `CLEAR` **long press (about half a second)** — the "start over" gesture: numbers and target both
  return to the defaults, with a light tick and a short *All Numbers Set to Default* message.
- `DEL` **long press (about half a second)** — drops the last number in `NUMBERS` (handy when only
  the fifth number was mistyped). You feel a tick when a number was actually removed.

## Solving

Press `SOLVE`. The app checks the input, then works on a background thread: `TARGET:` shows
*SOLVING…* and the button is disabled meanwhile. `BACK` returns to the keypad, ready for the next
puzzle. If nothing reaches the target you get
*No expression built from these numbers reaches the target.*

For 1, 2, 3, 4 and a target of 24 the result screen opens with **242** answers, listed simplest
first — brackets are only printed where they change the meaning, so you read `(1 + 2 + 3) * 4`
rather than `(((1 + 2) + 3) * 4)`.

## All answers, or the genuinely different ones

Most of those 242 answers are the same idea written differently: `12 + 13` and `13 + 12`, or
`(1 + 2) + 3` and `1 + (2 + 3)`. The switch above the list lets you choose:

| button | what you get |
| --- | --- |
| `All (242)` | every distinct expression the solver found |
| `Essential (28)` | one answer per group of expressions that differ only by swapping or regrouping `+` and `*` |

The counts on the buttons tell you what each choice contains, and your choice survives a rotation
and a trip back to the keypad. When there is only one answer — or none — the switch hides itself,
because there would be nothing to switch between. Note what "essential" does *not* do: it groups by
the commutativity and associativity of `+` and `*` only, so `1 - (2 - 3)` and `1 - 2 + 3` are still
listed separately even though both are the same value.

## Good to know

- The ticks are the system's own touch feedback (`HapticFeedbackConstants.KEYBOARD_TAP`). Turning
  off touch feedback / haptics in Android Settings silences them; the app never vibrates on its own
  and asks for no vibration permission.
- The answer list is plain selectable text, one expression per line, so you can copy an expression
  out of it.
- Every answer is exact — the solver computes with fractions, so results like `1 + 1/9` are real
  answers rather than rounding artefacts. `8 / (3 - 8 / 3)` (from 3, 3, 8, 8) is a solution that
  floating-point arithmetic cannot even recognise.
- The interface is available in Chinese; the app follows your device language and falls back to
  English.
- Five numbers is the practical ceiling for listing *every* expression: beyond that the number of
  expressions explodes (six numbers already have more than thirty million of them). The app caps the
  input at five, and the solver refuses anything larger with a clear message rather than appearing to
  hang.
