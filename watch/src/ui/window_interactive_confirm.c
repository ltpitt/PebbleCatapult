#include "window_interactive_confirm.h"

#include <string.h>

typedef struct {
    uint32_t session;
    char title[65], message[129];
    WindowInteractiveConfirmCallback confirm;
    WindowInteractiveConfirmCancelCallback cancel;
    WindowInteractiveConfirmErrorCallback error;
    void* context;
    Window* window;
    MenuLayer* menu;
    TextLayer* title_layer;
    TextLayer* message_layer;
} InteractiveConfirm;

static InteractiveConfirm* active;

void window_interactive_confirm_dismiss(void)
{
    if (active) window_stack_pop(false);
}

static uint16_t rows(MenuLayer* menu, uint16_t section, void* context) { return 2; }
static void draw(GContext* ctx, const Layer* cell, MenuIndex* index, void* context)
{
    menu_cell_basic_draw(ctx, cell, index->row == 0 ? "Accept" : "Cancel", NULL, NULL);
}
static void click(ClickRecognizerRef recognizer, void* context)
{
    InteractiveConfirm* confirm = context;
    if (click_recognizer_get_button_id(recognizer) == BUTTON_ID_BACK) {
        if (confirm->cancel) confirm->cancel(confirm->session, confirm->context);
    } else {
        MenuIndex index = menu_layer_get_selected_index(confirm->menu);
        if (confirm->confirm) confirm->confirm(confirm->session, index.row == 0, confirm->context);
    }
    window_stack_pop(true);
}
static void click_config(void* context)
{
    InteractiveConfirm* confirm = context;
    menu_layer_set_click_config_onto_window(confirm->menu, confirm->window);
    window_single_click_subscribe(BUTTON_ID_SELECT, click);
    window_single_click_subscribe(BUTTON_ID_BACK, click);
}
static void unload(Window* window)
{
    InteractiveConfirm* confirm = window_get_user_data(window);
    menu_layer_destroy(confirm->menu); text_layer_destroy(confirm->title_layer);
    text_layer_destroy(confirm->message_layer);
    if (active == confirm) active = NULL;
    free(confirm); window_destroy(window);
}
bool window_interactive_confirm_show(
    uint32_t session, const char* title, const char* message,
    WindowInteractiveConfirmCallback confirm_callback,
    WindowInteractiveConfirmCancelCallback cancel,
    WindowInteractiveConfirmErrorCallback error, void* context)
{
    if (!title || !message || !confirm_callback) {
        if (error) error(session, "Unable to display interactive confirmation", context);
        return false;
    }
    if (active) window_stack_pop(false);
    InteractiveConfirm* confirm = calloc(1, sizeof(*confirm));
    if (!confirm) { if (error) error(session, "Unable to display interactive confirmation", context); return false; }
    confirm->session = session; strncpy(confirm->title, title, 64); strncpy(confirm->message, message, 128);
    confirm->confirm = confirm_callback; confirm->cancel = cancel; confirm->error = error; confirm->context = context;
    confirm->window = window_create();
    if (!confirm->window) { free(confirm); if (error) error(session, "Unable to display interactive confirmation", context); return false; }
    Layer* root = window_get_root_layer(confirm->window); GRect bounds = layer_get_bounds(root);
    confirm->menu = menu_layer_create(bounds);
    confirm->title_layer = text_layer_create(GRect(4, 2, bounds.size.w - 8, 24));
    confirm->message_layer = text_layer_create(GRect(4, 27, bounds.size.w - 8, 36));
    if (!confirm->menu || !confirm->title_layer || !confirm->message_layer) {
        if (confirm->menu) menu_layer_destroy(confirm->menu);
        if (confirm->title_layer) text_layer_destroy(confirm->title_layer);
        if (confirm->message_layer) text_layer_destroy(confirm->message_layer);
        window_destroy(confirm->window); free(confirm);
        if (error) error(session, "Unable to display interactive confirmation", context);
        return false;
    }
    text_layer_set_text(confirm->title_layer, confirm->title); text_layer_set_font(confirm->title_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
    text_layer_set_text(confirm->message_layer, confirm->message); text_layer_set_font(confirm->message_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14));
    bounds.origin.y += 64; bounds.size.h -= 64; layer_set_frame(menu_layer_get_layer(confirm->menu), bounds);
    menu_layer_set_callbacks(confirm->menu, confirm, (MenuLayerCallbacks){ .get_num_rows = rows, .draw_row = draw });
    layer_add_child(root, text_layer_get_layer(confirm->title_layer)); layer_add_child(root, text_layer_get_layer(confirm->message_layer)); layer_add_child(root, menu_layer_get_layer(confirm->menu));
    window_set_click_config_provider_with_context(confirm->window, click_config, confirm);
    window_set_window_handlers(confirm->window, (WindowHandlers){ .unload = unload }); window_set_user_data(confirm->window, confirm);
    active = confirm; window_stack_push(confirm->window, true); return true;
}
