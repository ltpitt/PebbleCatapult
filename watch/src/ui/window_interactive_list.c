#include "window_interactive_list.h"

#include <string.h>

typedef struct {
    uint32_t session;
    uint8_t count;
    char title[65];
    const char (*ids)[33];
    const char (*values)[65];
    WindowInteractiveListSelectionCallback selection;
    WindowInteractiveListCancelCallback cancel;
    WindowInteractiveListErrorCallback error;
    void* context;
    Window* window;
    MenuLayer* menu;
    TextLayer* title_layer;
    bool resolved;
} InteractiveList;

static InteractiveList* active;

void window_interactive_list_dismiss(void)
{
    if (active) {
        active->resolved = true;
        window_stack_pop(false);
    }
}

static uint16_t rows(MenuLayer* menu, uint16_t section, void* context)
{
    return ((InteractiveList*)context)->count;
}

static int16_t cell_height(MenuLayer* menu, MenuIndex* index, void* context)
{
    return 40;
}

static void draw(GContext* ctx, const Layer* cell, MenuIndex* index, void* context)
{
    InteractiveList* list = context;
    menu_cell_basic_draw(ctx, cell, list->values[index->row], list->ids[index->row], NULL);
}

static void select_callback(MenuLayer* menu, MenuIndex* index, void* context)
{
    InteractiveList* list = context;
    list->resolved = true;
    if (index->row < list->count && list->selection)
        list->selection(list->session, list->ids[index->row], list->values[index->row], list->context);
    window_stack_pop(true);
}

static void unload(Window* window)
{
    InteractiveList* list = window_get_user_data(window);
    if (!list->resolved && list->cancel) list->cancel(list->session, list->context);
    menu_layer_destroy(list->menu);
    text_layer_destroy(list->title_layer);
    if (active == list) active = NULL;
    free(list);
    window_destroy(window);
}

bool window_interactive_list_show(
    uint32_t session, const char* title, const char ids[][33], const char values[][65],
    uint8_t count, WindowInteractiveListSelectionCallback selection,
    WindowInteractiveListCancelCallback cancel, WindowInteractiveListErrorCallback error, void* context)
{
    if (!title || !ids || !values || count > 32 || !selection) {
        if (error) error(session, "Unable to display interactive list", context);
        return false;
    }
    if (active) window_stack_pop(false);
    InteractiveList* list = calloc(1, sizeof(*list));
    if (!list) {
        if (error) error(session, "Unable to display interactive list", context);
        return false;
    }
    list->session = session;
    list->count = count;
    strncpy(list->title, title, sizeof(list->title) - 1);
    list->ids = ids;
    list->values = values;
    list->selection = selection; list->cancel = cancel; list->error = error; list->context = context;
    list->window = window_create();
    if (!list->window) { free(list); if (error) error(session, "Unable to display interactive list", context); return false; }
    list->menu = menu_layer_create(layer_get_bounds(window_get_root_layer(list->window)));
    list->title_layer = text_layer_create(GRect(4, 2, 136, 24));
    if (!list->menu || !list->title_layer) {
        if (list->menu) menu_layer_destroy(list->menu);
        if (list->title_layer) text_layer_destroy(list->title_layer);
        window_destroy(list->window); free(list);
        if (error) error(session, "Unable to display interactive list", context);
        return false;
    }
    text_layer_set_text(list->title_layer, list->title);
    text_layer_set_font(list->title_layer, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
    Layer* root = window_get_root_layer(list->window);
    GRect bounds = layer_get_bounds(root); bounds.origin.y += 26; bounds.size.h -= 26;
    layer_set_frame(menu_layer_get_layer(list->menu), bounds);
    menu_layer_set_callbacks(list->menu, list,
        (MenuLayerCallbacks){ .get_num_rows = rows, .get_cell_height = cell_height, .draw_row = draw,
            .select_click = select_callback });
    layer_add_child(root, text_layer_get_layer(list->title_layer));
    layer_add_child(root, menu_layer_get_layer(list->menu));
    menu_layer_set_click_config_onto_window(list->menu, list->window);
    window_set_window_handlers(list->window, (WindowHandlers){ .unload = unload });
    window_set_user_data(list->window, list);
    active = list;
    window_stack_push(list->window, true);
    return true;
}
