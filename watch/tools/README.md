# Catapult watch tools

Diagnostic helpers that talk to the Catapult watchapp directly, bypassing
Tasker and the Android companion. Use them to isolate whether an interactive
failure lives in the **watch protocol/rendering** or in the **Android
transport**.

## `catapult_interactive.py` — SHOW_LIST replay

Sends the exact `SHOW_LIST` payload the Android app would send (`InteractiveWatchMessage.ShowList`),
straight to the watchapp over the AppMessage endpoint, and prints the watch's
response packet (selection / cancel / error).

### Run

The watch must be reachable by the `pebble` tool (same transport you used
before, e.g. `--phone <ip>` or the emulator).

```sh
pebble repl            # add --phone <phone-ip> if needed
```

Then inside the REPL:

```python
exec(open('watch/tools/catapult_interactive.py').read())

# Launch the app, send the demo list, and print responses:
replay(pebble)

# Or customise:
replay(pebble,
       title="Choose a location",
       items=[("home", "Home"), ("work", "Work"), ("other", "Other")])
```

Make a selection on the watch; the returned `LIST_SELECTION` (packet id 8) or
`CANCEL/ERROR` (packet id 10) is printed in the REPL.

### Automated round-trip on the emulator

When connected to an emulator you can inject the button press too, so the whole
`SHOW_LIST -> render -> selection` path runs unattended:

```python
exec(open('watch/tools/catapult_interactive.py').read())
replay(pebble)
select_row(pebble, row=0)   # highlights + selects the first item ("Home")
```

Expected response in the REPL:

```
<< watch response LIST_SELECTION: {0: 8, 1: 1, 8: 'home', 7: 'Home'}
```

### Interpreting the result

- **List renders and selection comes back** → the watch protocol + UI are
  correct; the on-device bug is in the Android transport/session path.
- **No list, or an error packet** → the failure is in the packet encoding or
  the watch receiver; the printed error string comes straight from
  `watch/src/connection/packets.c`.

### Inspect the wire payload without a watch

```sh
python3 watch/tools/catapult_interactive.py
```

Prints the exact per-chunk dictionaries that would be sent.

## `test_catapult_interactive.py`

Structural tests asserting the builder mirrors the Android serializer and the
Pebble receiver (keys `0..8`, types, sequencing, terminal flag).

```sh
python3 watch/tools/test_catapult_interactive.py
```
