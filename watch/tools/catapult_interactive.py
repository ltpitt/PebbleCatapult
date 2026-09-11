"""Deterministic Catapult interactive-protocol replay for ``pebble repl``.

This lets you send the exact ``SHOW_LIST`` payload that the Android app would
send, straight to the Catapult watchapp, bypassing Tasker AND the Android
companion. Use it to prove whether an on-device failure lives in the watch
protocol/rendering or in the Android transport.

Wire contract (mirrors
``mobile/.../InteractiveWatchMessage.kt`` and ``watch/src/connection/packets.c``):

    key 0  KEY_PACKET_ID   uint32  = 5 (SHOW_LIST)
    key 1  KEY_SESSION_ID  uint32  = session id
    key 2  KEY_TITLE       cstring = list title
    key 3  KEY_SEQUENCE    uint32  = chunk index (0-based)
    key 4  KEY_TOTAL       uint16  = total chunks (= item count, min 1)
    key 5  KEY_TERMINAL    uint8   = 1 on the last chunk, else 0
    key 6  KEY_ITEM_COUNT  uint8   = number of items
    key 7  KEY_ITEM_VALUE  cstring = item display value
    key 8  KEY_ITEM_ID     cstring = item id

One list item is sent per packet, matching ``ShowList.packets(...)``.

Usage inside the pebble REPL (physical watch reachable via the pebble tool):

    pebble repl
    >>> exec(open('watch/tools/catapult_interactive.py').read())
    >>> replay(pebble)                       # launches app + sends the demo list
    >>> # or customise:
    >>> replay(pebble, title="Choose a location",
    ...        items=[("home", "Home"), ("work", "Work"), ("other", "Other")])

``replay`` also prints any interactive response packet the watch sends back
(packet id 8 = list selection, 10 = cancel/error), so you can confirm the
full round-trip.
"""

from __future__ import absolute_import

import uuid as _uuid

CATAPULT_UUID = _uuid.UUID("54be2d78-a70c-4573-a73e-5b0f1323d4cd")

KEY_PACKET_ID = 0
KEY_SESSION_ID = 1
KEY_TITLE = 2
KEY_SEQUENCE = 3
KEY_TOTAL = 4
KEY_TERMINAL = 5
KEY_ITEM_COUNT = 6
KEY_ITEM_VALUE = 7
KEY_ITEM_ID = 8

PACKET_SHOW_LIST = 5


class Field(object):
    """A single typed AppMessage value: kind in {u8,u16,u32,str}."""

    __slots__ = ("kind", "value")

    def __init__(self, kind, value):
        self.kind = kind
        self.value = value

    def __eq__(self, other):
        return (
            isinstance(other, Field)
            and self.kind == other.kind
            and self.value == other.value
        )

    def __hash__(self):
        return hash((self.kind, self.value))

    def __repr__(self):
        return "Field(%r, %r)" % (self.kind, self.value)


def build_show_list_packets(session, title, items):
    """Return the ordered list of SHOW_LIST chunk dictionaries.

    :param session: interactive session id (uint32).
    :param title: list title string.
    :param items: iterable of ``(id, value)`` pairs.
    :returns: list of ``{key: Field}`` dicts, one per list item.
    """
    items = list(items)
    total = max(1, len(items))
    chunks = items if items else [None]
    packets = []
    for index, item in enumerate(chunks):
        packet = {
            KEY_PACKET_ID: Field("u32", PACKET_SHOW_LIST),
            KEY_SESSION_ID: Field("u32", session),
            KEY_TITLE: Field("str", title),
            KEY_SEQUENCE: Field("u32", index),
            KEY_TOTAL: Field("u16", total),
            KEY_TERMINAL: Field("u8", 1 if index == total - 1 else 0),
            KEY_ITEM_COUNT: Field("u8", len(items)),
        }
        if item is not None:
            item_id, item_value = item
            packet[KEY_ITEM_ID] = Field("str", item_id)
            packet[KEY_ITEM_VALUE] = Field("str", item_value)
        packets.append(packet)
    return packets


def _to_appmessage_dict(packet):
    """Convert a ``{key: Field}`` dict into libpebble2 AppMessage values."""
    from libpebble2.services.appmessage import Uint8, Uint16, Uint32, CString

    mapping = {"u8": Uint8, "u16": Uint16, "u32": Uint32, "str": CString}
    return {key: mapping[field.kind](field.value) for key, field in packet.items()}


def launch_app(pebble):
    """Ask the watch to start the Catapult app so it can receive AppMessages."""
    from libpebble2.protocol.apps import AppRunState, AppRunStateStart

    pebble.send_packet(AppRunState(data=AppRunStateStart(uuid=CATAPULT_UUID)))
    print("Requested launch of Catapult (%s)" % CATAPULT_UUID)


def _register_delivery_logging(service):
    """Print ACK/NACK for every chunk so dropped messages are visible.

    The Pebble firmware ACKs a push only when the target app is running and
    accepts the inbox; a NACK (or silence) means the app was not ready.
    """
    service.register_handler(
        "ack", lambda tid, uuid: print("   ACK  tid=%s (watch accepted the chunk)" % tid)
    )
    service.register_handler(
        "nack",
        lambda tid, uuid: print(
            "   NACK tid=%s (watch rejected it: app not running/inbox full)" % tid
        ),
    )


def send_show_list(pebble, session=1, title="Choose a location",
                   items=(("home", "Home"), ("work", "Work"), ("other", "Other")),
                   service=None):
    """Send the SHOW_LIST chunks to the watch via the AppMessage endpoint."""
    from libpebble2.services.appmessage import AppMessageService

    if service is None:
        service = AppMessageService(pebble)
    packets = build_show_list_packets(session, title, items)
    for packet in packets:
        tid = service.send_message(CATAPULT_UUID, _to_appmessage_dict(packet))
        seq = packet[KEY_SEQUENCE].value
        print("Sent SHOW_LIST chunk seq=%d tid=%s" % (seq, tid))
    return service


def _print_response(transaction_id, app_uuid, data):
    packet_id = data.get(KEY_PACKET_ID)
    label = {8: "LIST_SELECTION", 9: "CONFIRMATION_RESULT", 10: "CANCEL/ERROR"}.get(
        packet_id, "packet %s" % packet_id
    )
    print("<< watch response %s: %r" % (label, data))


def replay(pebble, session=1, title="Choose a location",
           items=(("home", "Home"), ("work", "Work"), ("other", "Other")),
           launch=True, launch_delay=2.0):
    """Full round-trip: (optionally) launch the app, send the list, print replies.

    The app must be running to receive messages, so after requesting the launch
    we wait ``launch_delay`` seconds before sending. ACK/NACK for each chunk is
    printed so you can tell whether the watch actually accepted the payload.

    After calling this, interact with the list on the watch; the selection or
    cancellation packet is printed here.
    """
    import time
    from libpebble2.services.appmessage import AppMessageService

    service = AppMessageService(pebble)
    _register_delivery_logging(service)
    service.register_handler("appmessage", _print_response)

    if launch:
        launch_app(pebble)
        print("Waiting %.1fs for Catapult to open on the watch..." % launch_delay)
        time.sleep(launch_delay)

    send_show_list(pebble, session=session, title=title, items=items, service=service)
    print("Watch for ACK/NACK above, then make a selection on the watch...")
    return service


# --- Emulator-only helpers (pebble repl --emulator) --------------------------
# These inject button presses through the websocket QEMU relay so the whole
# SHOW_LIST -> render -> selection round-trip can be exercised without hardware.

_QEMU_BUTTON_PROTOCOL = 8
BUTTON_BACK, BUTTON_UP, BUTTON_SELECT, BUTTON_DOWN = 1, 2, 4, 8


def press_button(pebble, mask, hold=0.15):
    """Press and release an emulator button (BUTTON_* mask). Emulator only."""
    import time
    from libpebble2.communication.transports.qemu.protocol import QemuButton
    from libpebble2.communication.transports.websocket.protocol import WebSocketRelayQemu
    from libpebble2.communication.transports.websocket import MessageTargetPhone

    def send(state):
        pkt = WebSocketRelayQemu(protocol=_QEMU_BUTTON_PROTOCOL, data=QemuButton(state=state).serialise())
        pebble.transport.send_packet(pkt, target=MessageTargetPhone())

    send(mask)
    time.sleep(hold)
    send(0)


def select_row(pebble, row=0):
    """Move the menu selection down ``row`` times then press SELECT. Emulator only."""
    import time
    for _ in range(row):
        press_button(pebble, BUTTON_DOWN)
        time.sleep(0.2)
    press_button(pebble, BUTTON_SELECT)


if __name__ == "__main__":
    # Without a connected `pebble` object this just prints the payload it would
    # send, which is handy for eyeballing the exact wire dictionaries.
    for packet in build_show_list_packets(
        session=1,
        title="Choose a location",
        items=[("home", "Home"), ("work", "Work"), ("other", "Other")],
    ):
        print(packet)
