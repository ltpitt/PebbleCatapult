#include "window_notification.h"
#include "notification_lifecycle.h"

#include <string.h>

typedef struct {
    Window* window;
    TextLayer* title_layer;
    TextLayer* body_layer;
    ScrollLayer* scroll_layer;
    AppTimer* timer;
    NotificationLifecycle lifecycle;
    char title[65];
    char body[129];
} Notification;

static Notification* active;

static void cancel_timer(void* context)
{
    Notification* notification = context;
    if (notification->timer) {
        app_timer_cancel(notification->timer);
        notification->timer = NULL;
    }
}

static void back_click(ClickRecognizerRef recognizer, void* context)
{
    Notification* notification = context;
    notification_lifecycle_back(&notification->lifecycle);
    window_notification_dismiss();
}

static void select_click(ClickRecognizerRef recognizer, void* context)
{
    Notification* notification = context;
    notification_lifecycle_select(&notification->lifecycle);
    window_notification_dismiss();
}

static void click_config(void* context)
{
    Notification* notification = context;
    scroll_layer_set_click_config_onto_window(notification->scroll_layer, notification->window);
    window_single_click_subscribe(BUTTON_ID_BACK, back_click);
    window_single_click_subscribe(BUTTON_ID_SELECT, select_click);
}

static void dismiss_timer(void* context)
{
    Notification* notification = context;
    if (active == notification &&
        notification_lifecycle_timer_fired(&notification->lifecycle,
                                           notification_lifecycle_generation(&notification->lifecycle))) {
        notification->timer = NULL;
        window_notification_dismiss();
    }
}

static void unload(Window* window)
{
    Notification* notification = window_get_user_data(window);
    notification_lifecycle_unload(&notification->lifecycle);
    cancel_timer(notification);
    text_layer_destroy(notification->title_layer);
    text_layer_destroy(notification->body_layer);
    scroll_layer_destroy(notification->scroll_layer);
    if (active == notification) active = NULL;
    free(notification);
    window_destroy(window);
}

void window_notification_dismiss(void)
{
    if (active) {
        notification_lifecycle_dismiss(&active->lifecycle);
        window_stack_pop(false);
    }
}

void window_notification_dismiss_all(void)
{
    window_notification_dismiss();
}

void window_notification_show(const char* title, const char* body,
                              uint8_t vibration, uint32_t duration_ms)
{
    if (!title || !body) return;
    window_notification_dismiss();

    Notification* notification = calloc(1, sizeof(*notification));
    if (!notification) return;
    notification_lifecycle_init_with_cancel_timer(
        &notification->lifecycle, cancel_timer, notification);
    strncpy(notification->title, title, sizeof(notification->title) - 1);
    strncpy(notification->body, body, sizeof(notification->body) - 1);

    notification->window = window_create();
    if (!notification->window) {
        free(notification);
        return;
    }

    Layer* root = window_get_root_layer(notification->window);
    GRect bounds = layer_get_bounds(root);
    notification->title_layer = text_layer_create(GRect(4, 2, bounds.size.w - 8, 24));
    notification->scroll_layer = scroll_layer_create(
        GRect(0, 28, bounds.size.w, bounds.size.h - 28));
    notification->body_layer = text_layer_create(GRect(4, 4, bounds.size.w - 8, 20));
    if (!notification->title_layer || !notification->scroll_layer || !notification->body_layer) {
        if (notification->title_layer) text_layer_destroy(notification->title_layer);
        if (notification->body_layer) text_layer_destroy(notification->body_layer);
        if (notification->scroll_layer) scroll_layer_destroy(notification->scroll_layer);
        window_destroy(notification->window);
        free(notification);
        return;
    }

    text_layer_set_font(notification->title_layer,
                        fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD));
    text_layer_set_text(notification->title_layer, notification->title);
    text_layer_set_font(notification->body_layer,
                        fonts_get_system_font(FONT_KEY_GOTHIC_14));
    text_layer_set_text(notification->body_layer, notification->body);
    text_layer_set_overflow_mode(notification->body_layer, GTextOverflowModeWordWrap);

    GSize content_size = text_layer_get_content_size(notification->body_layer);
    content_size.w = bounds.size.w;
    content_size.h += 8;
    text_layer_set_size(notification->body_layer,
                        GRect(4, 4, bounds.size.w - 8, content_size.h - 8).size);
    scroll_layer_set_content_size(notification->scroll_layer, content_size);
    scroll_layer_add_child(notification->scroll_layer, text_layer_get_layer(notification->body_layer));
    layer_add_child(root, text_layer_get_layer(notification->title_layer));
    layer_add_child(root, scroll_layer_get_layer(notification->scroll_layer));

    window_set_user_data(notification->window, notification);
    window_set_click_config_provider_with_context(notification->window, click_config, notification);
    window_set_window_handlers(notification->window, (WindowHandlers){ .unload = unload });
    active = notification;
    notification_lifecycle_show(&notification->lifecycle, duration_ms);
    window_stack_push(notification->window, true);

    if (vibration == 1) {
        static const uint32_t short_pattern[] = { 200 };
        vibes_enqueue_custom_pattern((VibePattern){ .durations = short_pattern, .num_segments = 1 });
    } else if (vibration == 2) {
        static const uint32_t double_pattern[] = { 200, 100, 200 };
        vibes_enqueue_custom_pattern((VibePattern){ .durations = double_pattern, .num_segments = 3 });
    }
    if (notification_lifecycle_timer_active(&notification->lifecycle)) {
        notification->timer = app_timer_register(duration_ms, dismiss_timer, notification);
    }
}
