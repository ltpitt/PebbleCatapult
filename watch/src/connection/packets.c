#include "packets.h"
#include "commons/connection/bluetooth.h"
#include "commons/connection/bucket_sync.h"
#include <pebble.h>

#include "../ui/window_status.h"

static void receive_phone_welcome(const DictionaryIterator* iterator);
static void receive_sync_restart(const DictionaryIterator* iterator);
static void receive_sync_next_packet(const DictionaryIterator* iterator);
static void receive_watch_packet(const DictionaryIterator* received);
static void show_interactive(const DictionaryIterator* received);
static void interactive_select(ClickRecognizerRef recognizer, void* context);

static uint8_t active_buckets_holder[MAX_BUCKETS];
static Window* interactive_window;
static MenuLayer* interactive_menu;
static uint32_t interactive_session;
static uint32_t interactive_packet;
static uint16_t interactive_total;
static uint8_t interactive_count;
static bool interactive_confirmation;
static char interactive_title[65];
static char interactive_message[129];
static char interactive_ids[32][33];
static char interactive_values[32][65];
static bool interactive_received[32];
static TextLayer* interactive_title_layer;
static TextLayer* interactive_message_layer;

static size_t interactive_bounded_strlen(const char* value, size_t limit)
{
    size_t length = 0;
    while (length < limit && value[length] != '\0') length++;
    return length;
}

static uint16_t interactive_rows(MenuLayer* menu, uint16_t section, void* context)
{
    return interactive_confirmation ? 2 : interactive_count;
}

static void interactive_draw(GContext* ctx, const Layer* cell, MenuIndex* index, void* context)
{
    menu_cell_basic_draw(ctx, cell,
        interactive_confirmation ? (index->row == 0 ? "Accept" : "Cancel") : interactive_values[index->row],
        interactive_confirmation ? NULL : interactive_ids[index->row], NULL);
}

static void interactive_click_config(void* context)
{
    menu_layer_set_click_config_onto_window(interactive_menu, interactive_window);
    window_single_click_subscribe(BUTTON_ID_SELECT, interactive_select);
    window_single_click_subscribe(BUTTON_ID_BACK, interactive_select);
}

static void interactive_unload(Window* window)
{
    menu_layer_destroy(interactive_menu);
    if (interactive_title_layer) text_layer_destroy(interactive_title_layer);
    if (interactive_message_layer) text_layer_destroy(interactive_message_layer);
    interactive_menu = NULL;
    interactive_title_layer = NULL;
    interactive_message_layer = NULL;
    interactive_window = NULL;
    interactive_count = 0;
    interactive_total = 0;
    interactive_confirmation = false;
    interactive_title[0] = '\0';
    interactive_message[0] = '\0';
    window_destroy(window);
}
static void interactive_send(uint32_t packet, const char* id, const char* value);
static void interactive_send_error(uint32_t session, const char* reason);

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
    const uint8_t packet_id = dict_find(received, 0)->value->uint8;

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
    default:
        break;
    }
}

static void interactive_send(uint32_t packet, const char* id, const char* value)
    {
        DictionaryIterator* iterator;
        app_message_outbox_begin(&iterator);
        dict_write_uint32(iterator, 0, packet);
        dict_write_uint32(iterator, 1, interactive_session);
        dict_write_uint32(iterator, 3, 0);
        dict_write_uint16(iterator, 4, 1);
        dict_write_uint8(iterator, 5, 1);
        if (id) dict_write_cstring(iterator, 8, id);
        if (value) dict_write_cstring(iterator, 7, value);
        if (packet == 9) dict_write_uint8(iterator, 8, value != NULL);
        bluetooth_app_message_outbox_send();
    }

static void interactive_send_error(uint32_t session, const char* reason)
{
    const uint32_t previous_session = interactive_session;
    interactive_session = session;
    interactive_send(10, NULL, reason);
    interactive_session = previous_session;
}

static void interactive_select(ClickRecognizerRef recognizer, void* context)
    {
        MenuIndex index = menu_layer_get_selected_index(interactive_menu);
        if (recognizer && click_recognizer_get_button_id(recognizer) == BUTTON_ID_BACK)
            interactive_send(10, NULL, NULL);
        else if (interactive_confirmation)
            interactive_send(9, NULL, index.row == 0 ? "accepted" : NULL);
        else
            interactive_send(8, interactive_ids[index.row], interactive_values[index.row]);
        window_stack_pop(true);
    }

static void show_interactive(const DictionaryIterator* received)
    {
        Tuple* packet = dict_find(received, 0);
        Tuple* session = dict_find(received, 1);
        Tuple* title = dict_find(received, 2);
        const uint32_t error_session =
            session && session->type == TUPLE_UINT ? session->value->uint32 : interactive_session;
        if (!packet || packet->type != TUPLE_UINT || !session || session->type != TUPLE_UINT) {
            interactive_send_error(error_session, "Missing interactive metadata");
            return;
        }
        const uint32_t incoming_session = session->value->uint32;
        if (incoming_session != interactive_session) {
            for (uint8_t i = 0; i < 32; i++) interactive_received[i] = false;
            interactive_count = 0;
            interactive_total = 0;
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
            interactive_send(10, NULL, NULL);
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
        strncpy(interactive_title, title->value->cstring, sizeof(interactive_title) - 1);
        interactive_title[sizeof(interactive_title) - 1] = '\0';
        interactive_confirmation = interactive_packet == 6;
        if (interactive_packet == 5) {
            Tuple* count = dict_find(received, 6);
            Tuple* id = dict_find(received, 8);
            Tuple* value = dict_find(received, 7);
            Tuple* sequence_tuple = dict_find(received, 3);
            Tuple* total_tuple = dict_find(received, 4);
            Tuple* terminal_tuple = dict_find(received, 5);
            if (!count || count->type != TUPLE_UINT || count->value->uint8 > 32 ||
                !sequence_tuple || sequence_tuple->type != TUPLE_UINT ||
                !total_tuple || total_tuple->type != TUPLE_UINT ||
                !terminal_tuple || terminal_tuple->type != TUPLE_UINT ||
                terminal_tuple->value->uint8 > 1) {
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
        if (interactive_window) window_stack_pop(false);
        interactive_window = window_create();
        Layer* root = window_get_root_layer(interactive_window);
        GRect bounds = layer_get_bounds(root);
        interactive_menu = menu_layer_create(bounds);
        interactive_title_layer = text_layer_create(GRect(4, 2, bounds.size.w - 8, 24));
        text_layer_set_text(interactive_title_layer, interactive_title);
        text_layer_set_font(interactive_title_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
        layer_add_child(root, text_layer_get_layer(interactive_title_layer));
        if (interactive_confirmation) {
            interactive_message_layer = text_layer_create(GRect(4, 27, bounds.size.w - 8, 36));
            text_layer_set_text(interactive_message_layer, interactive_message);
            text_layer_set_font(interactive_message_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
            layer_add_child(root, text_layer_get_layer(interactive_message_layer));
        }
        bounds.origin.y += interactive_confirmation ? 64 : 26;
        bounds.size.h -= interactive_confirmation ? 64 : 26;
        layer_set_frame(menu_layer_get_layer(interactive_menu), bounds);
        menu_layer_set_callbacks(interactive_menu, NULL, (MenuLayerCallbacks){
            .get_num_rows = interactive_rows, .draw_row = interactive_draw
        });
        layer_add_child(root, menu_layer_get_layer(interactive_menu));
        window_set_click_config_provider(interactive_window, interactive_click_config);
        window_set_window_handlers(interactive_window, (WindowHandlers){ .unload = interactive_unload });
        window_stack_push(interactive_window, true);
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