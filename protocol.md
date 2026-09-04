# AppMessage packets

Dictionary entry `0` will always contain the packet ID (uint8)

## Phone -> Watch

### Phone Welcome (packet 1)

Sent to the watch as the response to the packet 1.

* `1`. - phone protocol version (uint16)    
* `2` - Bucketsync sync data (byte array)
  * Sync status (uint8) - `2` - watch is up to date, `1` - this is the last sync packet, `0` - more packet 3 packets will follow this packet.
  * If latest bucketsync version does not match the watch's version:
    * Latest bucketsync version on the phone (uint16)
    * Number of currently active buckets (uint8)
    * For every active bucket:
      * Bucket id (uint8)
      * Bucket flags (uint8)
    * For every bucketsync updated bucket that can fit into this packet:
      * Bucket id (uint8)
      * Bucket size in bytes (uint8)
      * Bucket data (bytes)
* `3` - If this key exists, watch should auto-close after sync is completed (uint8)

### Interactive requests (packets 5-7)

Interactive packets use dictionary key `0` for the packet ID and key `1` for a
session ID (`uint32`). Packet IDs are deliberately outside the existing 0–4
range. Every packet also carries key `3` (zero-based chunk sequence, `uint32`),
key `4` (total chunk count, `uint16`) and key `5` (terminal marker, `uint8`;
`1` only on the final chunk). Chunks may arrive out of order, but a request is
displayed only after every sequence number from zero through total-1 has
arrived. A duplicate identical chunk is ignored; a duplicate with different
contents rejects the request.

* **5 SHOW_LIST (phone → watch):** key `2` title (UTF-8 text, at most 64
  bytes), key `3` item/chunk sequence (`uint32`), key `6` item count
  (`uint8`), key `7` item value (UTF-8 text, at most 64 bytes), and key `8`
  item ID (UTF-8 text, at most 32 bytes). A list contains at most 32 items;
  one item is carried per chunk and total chunks equals item count. Empty lists
  are represented by one terminal chunk with item count zero.
* **6 SHOW_CONFIRMATION (phone → watch):** key `2` title (UTF-8, at most 64
  bytes), key `7` message (UTF-8, at most 128 bytes).
* **7 CANCEL (phone → watch):** key `9` cancellation reason (UTF-8 text, at
  most 64 bytes), in addition to the common fields.

The phone must reject a required field that is absent or has the wrong type,
and must reject strings or item counts over these limits; it never truncates.
Each encoded packet must be strictly smaller than the incoming AppMessage
buffer size reported by Watch Welcome.

### Interactive responses (watch → phone)

* **8 LIST_SELECTION:** key `8` selected item ID (UTF-8 text, at most 32
  bytes), and key `7` selected item value (UTF-8 text, at most 64 bytes).
* **9 CONFIRMATION_RESULT:** key `8` accepted (`uint8`, `1` yes, `0` no).
* **10 CANCEL_OR_ERROR:** optional key `9` UTF-8 error text (at most 64 bytes).

Responses carry the same common session/chunk fields and are terminal. The
terminal marker is strictly `0` or `1`, and must be `1` exactly when sequence
equals total chunks minus one. LIST_SELECTION additionally requires exactly one
chunk and a terminal marker. The
phone ignores stale or unknown session IDs and never completes a session from
an incomplete response. A CANCEL response without an error is user
cancellation; an error is a display/protocol failure.

Protocol version 4 includes interactive packets 5–10. The watch displays list chunks in
the native menu (one item per chunk) and presents confirmation as Accept/Cancel; packet
7 immediately returns packet 10. Older watches negotiate only the version response and
continue to receive no bucket data until upgraded, avoiding unsafe mixed layouts.

### Show notification (packet 11)

Packet 11 is a phone-to-watch, one-way notification. It is not an interactive
session and therefore has no session ID, chunk fields, or response packet.

* `2` - title (UTF-8 text, at most 64 bytes)
* `7` - body (UTF-8 text, at most 128 bytes)
* `6` - vibration (`uint8`: `0` none, `1` short, `2` double)
* `8` - display duration in milliseconds (`uint32`, 0–300000)

The title and body must be valid, NUL-terminated UTF-8 strings with no embedded
NUL bytes and must fit their byte limits; they are never truncated. A duration of zero leaves the
notification open until Back or Select. A non-zero duration automatically
dismisses the notification and returns to the previous screen when the timer
expires. Showing a new notification dismisses the previous notification and
replaces its timer. The watch vibrates once using the requested pattern.

Packet 11 is available only when protocol negotiation reports version 4.
When the watch reports an older (or otherwise mismatched) version, the phone
responds with its version only and does not send bucket data or packet 11.
Notifications are fire-and-forget: the phone reports dispatch success or a
send/connection failure, while the watch sends no acknowledgement or result.

### Re-start bucketsync sync (packet 2)

Sent to the watch when the watchapp is open and buckets on the phone change

* `1` - Bucketsync sync data (byte array)
  * Sync complete flag (uint8) - `1` if this is the last sync packet, `0` if more packet 3 packets will follow.
  * Latest bucketsync version on the phone (uint16)
  * Number of currently active buckets (uint8)
  * For every active bucket:
    * Bucket id (uint8)
    * Bucket flags (uint8)
  * For every bucketsync updated bucket that can fit into this packet:
    * Bucket id (uint8)
    * Bucket size in bytes (uint8)
    * Bucket data (bytes)

### Follow up bucket data (packet 3)

Optionally sent to the watch after packets 1 or 2. Can be repeated until data for all changed buckets has been sent

* `1` - Bucketsync bucket data (byte array)
  * Sync complete flag (uint8) - `1` if this is the last sync packet, `0` if more packet 3 packets will follow.
  * For every bucketsync updated bucket that can fit into this packet:
    * Bucket id (uint8)
    * Bucket size in bytes (uint8)
    * Bucket data (bytes)


## Watch -> Phone

### Watch Welcome (packet 0)

Sent from the watch when the app is opened.

* `1` - watch protocol version (uint16)
* `2` - current bucketsync watch version (uint16)
* `3` - Appmessage incoming buffer size in bytes (uint16)
* `7` - List of bucket ids currently active on the watch (byte array)
  
### Trigger action (packet 4)

Sent from the watch when the app is opened.

* `1` - ID of the action to trigger (uint16)
* `2` - Text of the action to trigger (cstring)
* `3` - Argument to the action, usually user's voice (cstring) 

# Buckets

Watch can store up to 15 of them, up to 256 bytes each

Every bucket corresponds to one Tasker directory

Data in every bucket:
* Number of items (uint8)
* Items (Max 13 items - 19 bytes per item)
  * ID (uint16)
  * Target directory (uint8) - If item should open another directory, 0 otherwise
  * Flags (uint8) 
    * Respond with voice (0x01) - when 1, watch will show voice prompt before triggering action
  * Title (cstring) - up to 14 bytes + null terminator

Every bucket is stored in the `2001` - `2015` storage keys

# Non-bucket storage on the watch

160 bytes left over from buckets

`1000` - List of all buckets on the watch (up to 60 bytes)    
* array of tuples (up to 15 items)
* array size determined through `persist_get_size()`
* Tuple: (2 bytes each)
  * Bucket id (uint8)
  * Flags (uint8) - For future use

`1001` - Current version of the data on the watch (uint16)
`1002` - Protocol version of the last data writing on the watch (uint16)
  If this changes, the watch is wiped and re-synced to the phone