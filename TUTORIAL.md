# 24 Solver — Quick Tutorial

24 Solver finds **every** way to reach a target number (24 by default) from the numbers you type.
Each number is used exactly once, with `+ - × ÷` and any bracketing, and every solution is listed.

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
*SOLVING…* and the button is disabled meanwhile. The result screen lists every expression found —
for 1, 2, 3, 4 there are 242 of them — and `BACK` returns to the keypad, ready for the next puzzle.
If nothing reaches the target you get *No expression built from these numbers reaches the target.*

## Good to know

- The ticks are the system's own touch feedback (`HapticFeedbackConstants.KEYBOARD_TAP`). Turning
  off touch feedback / haptics in Android Settings silences them; the app never vibrates on its own
  and asks for no vibration permission.
- The answer list is plain selectable text, one expression per line, so you can copy an expression
  out of it.
- Every answer is exact — the solver computes with fractions, so results like `1 + 1/9` are real
  answers rather than rounding artefacts.
