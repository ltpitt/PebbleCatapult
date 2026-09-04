#include "packets.h"
#include "commons/connection/bluetooth.h"
#include "commons/connection/bucket_sync.h"
#include <pebble.h>

#include "../ui/window_status.h"
#include "../ui/window_interactive_list.h"
#include "../ui/window_interactive_confirm.h"
#include "../ui/window_notification.h"

static void receive_phone_welcome(const DictionaryIterator* iterator);
static void receive_sync_restart(const DictionaryIterator* iterator);
static void receive_sync_next_packet(const DictionaryIterator* iterator);
static void receive_watch_packet(const DictionaryIterator* received);
static void show_interactive(const DictionaryIterator* received);
static void show_notification(const DictionaryIterator* received);
static void interactive_send(uint32_t packet, uint32_t session, const char* id, const char* value);
static void interactive_send_error(uint32_t session, const char* reason);

static uint8_t active_buckets_holder[MAX_BUCKETS];
static uint32_t interactive_session;
static uint32_t interactive_packet;
static uint16_t interactive_total;
static uint8_t interactive_count;
static char interactive_title[65];
static char interactive_message[129];
static char interactive_ids[32][33];
static char interactive_values[32][65];
static bool interactive_received[32];
static bool interactive_completed;

static bool valid_utf8_cstring(const Tuple* tuple, size_t max_bytes)
{
    if (!tuple || tuple->type != TUPLE_CSTRING || tuple->length == 0 ||
        tuple->length > max_bytes + 1) return false;
    const unsigned char* text = (const unsigned char*)tuple->value->cstring;
    size_t length = tuple->length - 1;
    if (text[length] != '\0') return false;
    size_t i = 0;
    while (i < length) {
        uint32_t codepoint;
        uint8_t width;
        if (text[i] <= 0x7f) { codepoint = text[i++]; continue; }
        if (text[i] >= 0xc2 && text[i] <= 0xdf) { codepoint = text[i++] & 0x1f; width = 2; }
        else if (text[i] >= 0xe0 && text[i] <= 0xef) { codepoint = text[i++] & 0x0f; width = 3; }
        else if (text[i] >= 0xf0 && text[i] <= 0xf4) { codepoint = text[i++] & 0x07; width = 4; }
        else return false;
        if (i + width - 1 > length) return false;
        for (uint8_t j = 1; j < width; j++) {
            if ((text[i] & 0xc0) != 0x80) return false;
            codepoint = (codepoint << 6) | (text[i++] & 0x3f);
        }
        if ((width == 3 && codepoint < 0x800) ||
            (width == 4 && codepoint < 0x10000) ||
            codepoint > 0x10ffff || (codepoint >= 0xd800 && codepoint <= 0xdfff)) return false;
    }
    return true;
}

static size_t interactive_bounded_strlen(const char* value, size_t limit)
{
    size_t length = 0;
    while (length < limit && value[length] != '\0') length++;
    return length;
}

static bool interactive_uint_tuple_width(const Tuple* tuple, uint16_t width)
{
    return tuple && tuple->type == TUPLE_UINT && tuple->length == width;
}

static void interactive_dismiss_windows()
{
    window_interactive_list_dismiss();
    window_interactive_confirm_dismiss();
}

static void interactive_clear_window()
{
    interactive_dismiss_windows();
    interactive_session = 0;
    interactive_packet = 0;
    interactive_count = 0;
    interactive_total = 0;
    interactive_completed = false;
    for (uint8_t i = 0; i < 32; i++) interactive_received[i] = false;
}

void packets_init()
{
    bluetooth_register_reconnect_callback(send_watch_welcome);
    bluetooth_register_receive_watch_packet(receive_watch_packet);
}

void send_watch_welcome()
{
    const BucketList* active_buckets = bucket_sync_get_bucket_list();
    for (int i = 0; i < active_buckets->count; i++)
    {
        active_buckets_holder[i] = active_buckets->data[i].id;
    }

    DictionaryIterator* iterator;
    app_message_outbox_begin(&iterator);
    dict_write_uint8(iterator, 0, 0);
    dict_write_uint16(iterator, 1, PROTOCOL_VERSION);
    dict_write_uint16(iterator, 2, bucket_sync_current_version);
    dict_write_uint16(iterator, 3, appmessage_max_size);
    dict_write_data(iterator, 7, active_buckets_holder, active_buckets->count);
    bluetooth_app_message_outbox_send();
}

void send_trigger_action(const uint16_t id, const char* name, const char* parameter)
{
    DictionaryIterator* iterator;
    app_message_outbox_begin(&iterator);
    dict_write_uint8(iterator, 0, 4);
    dict_write_uint16(iterator, 1, id);
    dict_write_cstring(iterator, 2, name);
    if (parameter != NULL)
    {
        dict_write_cstring(iterator, 3, parameter);
    }
    bluetooth_app_message_outbox_send();
}

static void receive_watch_packet(const DictionaryIterator* received)
{
    Tuple* packet_tuple = dict_find(received, 0);
    if (!packet_tuple || packet_tuple->type != TUPLE_UINT) return;
    const uint8_t packet_id = packet_tuple->value->uint8;

    switch (packet_id)
    {
    case 1:
        receive_phone_welcome(received);
        break;
    case 2:
        receive_sync_restart(received);
        break;
    case 3:
        receive_sync_next_packet(received);
        break;
    case 5:
    case 6:
    case 7:
        show_interactive(received);
        break;
    case 11:
        show_notification(received);
        break;
    default:
        break;
    }
}

static void show_notification(const DictionaryIterator* received)
{
    Tuple* packet = dict_find(received, 0);
    Tuple* title = dict_find(received, 2);
    Tuple* body = dict_find(received, 7);
    Tuple* vibration = dict_find(received, 6);
    Tuple* duration = dict_find(received, 8);
    if (!interactive_uint_tuple_width(packet, 4) || packet->value->uint32 != 11 ||
        !valid_utf8_cstring(title, 64) ||
        !valid_utf8_cstring(body, 128) ||
        !interactive_uint_tuple_width(vibration, 1) ||
        !interactive_uint_tuple_width(duration, 4) ||
        vibration->value->uint8 > 2 ||
        duration->value->uint32 > 300000) {
        APP_LOG(APP_LOG_LEVEL_ERROR, "Invalid notification packet");
        return;
    }
    interactive_clear_window();
    window_notification_dismiss_all();
    window_notification_show(title->value->cstring, body->value->cstring,
                             vibration->value->uint8, duration->value->uint32);
}

static void interactive_send(uint32_t packet, uint32_t session, const char* id, const char* value)
    {
        DictionaryIterator* iterator;
        app_message_outbox_begin(&iterator);
        dict_write_uint32(iterator, 0, packet);
        dict_write_uint32(iterator, 1, session);
        dict_write_uint32(iterator, 3, 0);
        dict_write_uint16(iterator, 4, 1);
        dict_write_uint8(iterator, 5, 1);
        if (id) dict_write_cstring(iterator, 8, id);
        if (value && packet != 10) dict_write_cstring(iterator, 7, value);
        if (value && packet == 10) dict_write_cstring(iterator, 9, value);
        if (packet == 9) dict_write_uint8(iterator, 8, value != NULL);
        bluetooth_app_message_outbox_send();
    }

static void interactive_send_error(uint32_t session, const char* reason)
{
    interactive_send(10, session, NULL, reason);
}

static void interactive_selection(uint32_t session, const char* id, const char* value, void* context) { interactive_send(8, session, id, value); }
static void interactive_confirmation_result(uint32_t session, bool accepted, void* context) { interactive_send(9, session, NULL, accepted ? "accepted" : NULL); }
static void interactive_cancel(uint32_t session, void* context) { interactive_send_error(session, NULL); }
static void interactive_display_error(uint32_t session, const char* reason, void* context) { interactive_send_error(session, reason); }

static void show_interactive(const DictionaryIterator* received)
    {
        Tuple* packet = dict_find(received, 0);
        Tuple* session = dict_find(received, 1);
        Tuple* title = dict_find(received, 2);
        const uint32_t error_session =
            interactive_uint_tuple_width(session, 4) ? session->value->uint32 : interactive_session;
        if (!interactive_uint_tuple_width(packet, 4) || !interactive_uint_tuple_width(session, 4)) {
            interactive_send_error(error_session, "Missing interactive metadata");
            return;
        }
        Tuple* sequence = dict_find(received, 3);
        Tuple* total = dict_find(received, 4);
        Tuple* terminal = dict_find(received, 5);
        if (!interactive_uint_tuple_width(sequence, 4) ||
            !interactive_uint_tuple_width(total, 2) ||
            !interactive_uint_tuple_width(terminal, 1)) {
            interactive_send_error(error_session, "Invalid interactive metadata");
            return;
        }
        const uint32_t incoming_session = session->value->uint32;
        if (incoming_session != interactive_session) {
            for (uint8_t i = 0; i < 32; i++) interactive_received[i] = false;
            interactive_count = 0;
            interactive_total = 0;
            interactive_completed = false;
        }
        const bool duplicate_completed = interactive_completed && incoming_session == interactive_session;
        if (duplicate_completed && packet->value->uint32 != interactive_packet) {
            interactive_send_error(incoming_session, "Conflicting interactive request");
            return;
        }
        interactive_session = incoming_session;
        interactive_packet = packet->value->uint32;
        if (interactive_packet == 5) {
            Tuple* sequence = dict_find(received, 3);
            bool any_received = false;
            for (uint8_t i = 0; i < 32; i++) any_received |= interactive_received[i];
            if (sequence && sequence->value->uint32 == 0 && !any_received) {
                for (uint8_t i = 0; i < 32; i++) interactive_received[i] = false;
            }
        }
        if (interactive_packet == 7) {
            Tuple* reason = dict_find(received, 9);
            if (sequence->value->uint32 != 0 || total->value->uint16 != 1 ||
                terminal->value->uint8 != 1 ||
                !reason || reason->type != TUPLE_CSTRING ||
                interactive_bounded_strlen(reason->value->cstring, 65) >= 65) {
                interactive_send_error(incoming_session, "Invalid cancellation reason");
                return;
            }
            interactive_clear_window();
            interactive_send_error(incoming_session, NULL);
            return;
        }
        if (interactive_packet != 5 && interactive_packet != 6) {
            interactive_send_error(incoming_session, "Invalid interactive request");
            return;
        }
        if (!title || title->type != TUPLE_CSTRING ||
            interactive_bounded_strlen(title->value->cstring, sizeof(interactive_title)) >= sizeof(interactive_title)) {
            interactive_send_error(incoming_session, "Invalid interactive title");
            return;
        }
        if (duplicate_completed && strcmp(interactive_title, title->value->cstring) != 0) {
            interactive_send_error(incoming_session, "Conflicting interactive request");
            return;
        }
        strncpy(interactive_title, title->value->cstring, sizeof(interactive_title) - 1);
        interactive_title[sizeof(interactive_title) - 1] = '\0';
        if (interactive_packet == 5) {
            Tuple* count = dict_find(received, 6);
            Tuple* id = dict_find(received, 8);
            Tuple* value = dict_find(received, 7);
            Tuple* sequence_tuple = sequence;
            Tuple* total_tuple = total;
            Tuple* terminal_tuple = terminal;
            if (!count || count->type != TUPLE_UINT || count->value->uint8 > 32) {
                interactive_send_error(incoming_session, "Invalid list metadata");
                return;
            }
            if (interactive_total != 0 &&
                (count->value->uint8 != interactive_count ||
                 total_tuple->value->uint16 != interactive_total)) {
                interactive_send_error(incoming_session, "Inconsistent list chunks");
                return;
            }
            interactive_count = count->value->uint8;
            interactive_total = total_tuple->value->uint16;
            uint32_t sequence = sequence_tuple->value->uint32;
            if ((interactive_count == 0 && (sequence != 0 || interactive_total != 1)) ||
                (interactive_count > 0 && (interactive_total != interactive_count || sequence >= interactive_total)) ||
                (terminal_tuple->value->uint8 != (sequence + 1 == interactive_total))) {
                interactive_send_error(incoming_session, "Inconsistent list chunks");
                return;
            }
            if (interactive_count > 0 &&
                (!id || id->type != TUPLE_CSTRING || !value || value->type != TUPLE_CSTRING ||
                 id->value->cstring[0] == '\0' ||
                 interactive_bounded_strlen(id->value->cstring, sizeof(interactive_ids[0])) >= sizeof(interactive_ids[0]) ||
                 interactive_bounded_strlen(value->value->cstring, sizeof(interactive_values[0])) >= sizeof(interactive_values[0]))) {
                interactive_send_error(incoming_session, "Invalid list item");
                return;
            }
            if (interactive_count > 0) {
                if (interactive_received[sequence] &&
                    (strcmp(interactive_ids[sequence], id->value->cstring) != 0 ||
                     strcmp(interactive_values[sequence], value->value->cstring) != 0)) {
                    interactive_send_error(incoming_session, "Conflicting list chunk");
                    return;
                }
                strncpy(interactive_ids[sequence], id->value->cstring, sizeof(interactive_ids[sequence]) - 1);
                strncpy(interactive_values[sequence], value->value->cstring, sizeof(interactive_values[sequence]) - 1);
                interactive_ids[sequence][sizeof(interactive_ids[sequence]) - 1] = '\0';
                interactive_values[sequence][sizeof(interactive_values[sequence]) - 1] = '\0';
                interactive_received[sequence] = true;
            }
            if (interactive_count == 0) {
                /* The single empty-list chunk is complete without an item. */
                interactive_total = 1;
            }
            for (uint8_t i = 0; i < interactive_count; i++)
                if (!interactive_received[i]) {
                    return;
                }
        } else {
            Tuple* message = dict_find(received, 7);
            if (!message || message->type != TUPLE_CSTRING ||
                interactive_bounded_strlen(message->value->cstring, sizeof(interactive_message)) >= sizeof(interactive_message)) {
                interactive_send_error(incoming_session, "Invalid confirmation message");
                return;
            }
            if (duplicate_completed && strcmp(interactive_message, message->value->cstring) != 0) {
                interactive_send_error(incoming_session, "Conflicting interactive request");
                return;
            }
            strncpy(interactive_message, message->value->cstring, sizeof(interactive_message) - 1);
            interactive_message[sizeof(interactive_message) - 1] = '\0';
            Tuple* sequence = dict_find(received, 3);
            Tuple* total = dict_find(received, 4);
            Tuple* terminal = dict_find(received, 5);
            if (!sequence || sequence->type != TUPLE_UINT || sequence->value->uint32 != 0 ||
                !total || total->type != TUPLE_UINT || total->value->uint16 != 1 ||
                !terminal || terminal->type != TUPLE_UINT || terminal->value->uint8 != 1) {
                interactive_send_error(incoming_session, "Invalid confirmation metadata");
                return;
            }
            interactive_count = 0;
        }
        if (duplicate_completed) return;
        interactive_completed = true;
        interactive_dismiss_windows();
        if (interactive_packet == 5) {
            window_interactive_list_show(incoming_session, interactive_title,
                (const char (*)[33])interactive_ids, (const char (*)[65])interactive_values,
                interactive_count, interactive_selection, interactive_cancel, interactive_display_error, NULL);
        } else {
            window_interactive_confirm_show(incoming_session, interactive_title, interactive_message,
                interactive_confirmation_result, interactive_cancel, interactive_display_error, NULL);
        }
    }

void receive_phone_welcome(const DictionaryIterator* iterator)
{
    if (launch_reason() == APP_LAUNCH_PHONE && dict_find(iterator, 3) != NULL)
    {
        bucket_sync_set_auto_close_after_sync();
    }

    const uint16_t phone_protocol_version = dict_find(iterator, 1)->value->uint16;
    if (phone_protocol_version != PROTOCOL_VERSION)
    {
        if (phone_protocol_version > PROTOCOL_VERSION)
        {
            window_status_show_error("Version mismatch\n\nPlease update watch app");
        }
        else
        {
            window_status_show_error("Version mismatch\n\nPlease update phone app");
        }
        return;
    }

    // ReSharper disable once CppLocalVariableMayBeConst
    Tuple* dict_entry = dict_find(iterator, 2);

    bucket_sync_on_start_received(dict_entry->value->data, dict_entry->length);
}

void receive_sync_restart(const DictionaryIterator* iterator)
{
    // ReSharper disable once CppLocalVariableMayBeConst
    Tuple* dict_entry = dict_find(iterator, 1);

    bucket_sync_on_start_received(dict_entry->value->data, dict_entry->length);
}

void receive_sync_next_packet(const DictionaryIterator* iterator)
{
    // ReSharper disable once CppLocalVariableMayBeConst
    Tuple* dict_entry = dict_find(iterator, 1);

    bucket_sync_on_next_packet_received(dict_entry->value->data, dict_entry->length);
}