"""Structural tests for the Catapult interactive protocol replay builder.

These assert that ``build_show_list_packets`` mirrors the Android wire
serializer (``InteractiveWatchMessage.ShowList.packets``) and the Pebble
receiver (``watch/src/connection/packets.c``) exactly, so a ``pebble repl``
replay reproduces the real ``SHOW_LIST`` payload byte-for-byte.

Run with the pebble-tool interpreter:
    python3 watch/tools/test_catapult_interactive.py
"""

from catapult_interactive import Field, build_show_list_packets


def test_three_item_list_produces_one_packet_per_item():
    packets = build_show_list_packets(
        session=1,
        title="Choose a location",
        items=[("home", "Home"), ("work", "Work"), ("other", "Other")],
    )

    assert len(packets) == 3, packets

    for index, packet in enumerate(packets):
        assert packet[0] == Field("u32", 5), packet          # KEY_PACKET_ID
        assert packet[1] == Field("u32", 1), packet          # KEY_SESSION_ID
        assert packet[2] == Field("str", "Choose a location")  # KEY_TITLE
        assert packet[3] == Field("u32", index), packet      # KEY_SEQUENCE
        assert packet[4] == Field("u16", 3), packet          # KEY_TOTAL
        assert packet[6] == Field("u8", 3), packet           # KEY_ITEM_COUNT

    assert packets[0][5] == Field("u8", 0)  # KEY_TERMINAL, not last
    assert packets[1][5] == Field("u8", 0)
    assert packets[2][5] == Field("u8", 1)  # last chunk is terminal

    assert packets[0][8] == Field("str", "home")   # KEY_ITEM_ID
    assert packets[0][7] == Field("str", "Home")   # KEY_ITEM_VALUE
    assert packets[2][8] == Field("str", "other")
    assert packets[2][7] == Field("str", "Other")


def test_single_item_list_is_terminal_immediately():
    packets = build_show_list_packets(session=9, title="Pick", items=[("a", "A")])

    assert len(packets) == 1
    packet = packets[0]
    assert packet[4] == Field("u16", 1)  # total
    assert packet[5] == Field("u8", 1)   # terminal
    assert packet[6] == Field("u8", 1)   # item count


def test_keys_match_the_wire_contract():
    packet = build_show_list_packets(session=1, title="T", items=[("i", "V")])[0]
    assert set(packet.keys()) == {0, 1, 2, 3, 4, 5, 6, 7, 8}


if __name__ == "__main__":
    import sys

    failures = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"PASS {name}")
            except AssertionError as error:
                failures += 1
                print(f"FAIL {name}: {error}")
    sys.exit(1 if failures else 0)
