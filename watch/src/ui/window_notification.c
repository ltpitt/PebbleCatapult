#include "window_notification.h"
#include "notification_lifecycle.h"

#include <stdio.h>
#include <string.h>

#define DIALOG_MESSAGE_WINDOW_MARGIN 10

typedef struct {
    Window* window;
    Layer* background_layer;
    BitmapLayer* icon_layer;
    GBitmap* icon;
    TextLayer* message_layer;
    Animation* appear_animation;
    AppTimer* timer;
    NotificationLifecycle lifecycle;
    char message[195];
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

static void background_update(Layer* layer, GContext* context)
{
    graphics_context_set_fill_color(
        context, PBL_IF_COLOR_ELSE(GColorYellow, GColorWhite));
    graphics_fill_rect(context, layer_get_bounds(layer), 0, GCornerNone);
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
    window_single_click_subscribe(BUTTON_ID_BACK, back_click);
    window_single_click_subscribe(BUTTON_ID_SELECT, select_click);
}

static void animation_stopped(Animation* animation, bool finished, void* context)
{
    Notification* notification = context;
    notification->appear_animation = NULL;
}

static void appear(Window* window)
{
    Notification* notification = window_get_user_data(window);
    GRect bounds = layer_get_bounds(window_get_root_layer(window));
    GRect icon_bounds = gbitmap_get_bounds(notification->icon);
    GRect icon_final = GRect(
        DIALOG_MESSAGE_WINDOW_MARGIN,
        DIALOG_MESSAGE_WINDOW_MARGIN,
        icon_bounds.size.w, icon_bounds.size.h);
    GRect label_final = GRect(
        DIALOG_MESSAGE_WINDOW_MARGIN,
        icon_final.origin.y + icon_final.size.h + 5,
        bounds.size.w - (2 * DIALOG_MESSAGE_WINDOW_MARGIN),
        bounds.size.h);

    Animation* background = (Animation*)property_animation_create_layer_frame(
        notification->background_layer,
        &(GRect){GPoint(0, bounds.size.h), GSize(bounds.size.w, 0)},
        &bounds);
    Animation* icon = (Animation*)property_animation_create_layer_frame(
        bitmap_layer_get_layer(notification->icon_layer),
        &(GRect){GPoint(icon_final.origin.x, bounds.size.h), icon_bounds.size},
        &icon_final);
    Animation* label = (Animation*)property_animation_create_layer_frame(
        text_layer_get_layer(notification->message_layer),
        &(GRect){GPoint(label_final.origin.x, bounds.size.h), label_final.size},
        &label_final);

    notification->appear_animation = animation_spawn_create(background, icon, label, NULL);
    animation_set_handlers(notification->appear_animation,
                           (AnimationHandlers){.stopped = animation_stopped},
                           notification);
    animation_set_delay(notification->appear_animation, 700);
    animation_schedule(notification->appear_animation);
}

static void dismiss_timer(void* context)
{
    Notification* notification = context;
    if (active == notification &&
        notification_lifecycle_timer_fired(
            &notification->lifecycle,
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
    if (notification->appear_animation) {
        animation_unschedule(notification->appear_animation);
    }
    layer_destroy(notification->background_layer);
    bitmap_layer_destroy(notification->icon_layer);
    text_layer_destroy(notification->message_layer);
    gbitmap_destroy(notification->icon);
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
    snprintf(notification->message, sizeof(notification->message), "%s\n%s", title, body);

    notification->window = window_create();
    notification->icon = gbitmap_create_with_resource(RESOURCE_ID_WARNING);
    GRect bounds = GRect(0, 0, 144, 168);
    notification->background_layer = layer_create(
        GRect(0, bounds.size.h, bounds.size.w, bounds.size.h));
    GRect icon_bounds = gbitmap_get_bounds(notification->icon);
    notification->icon_layer = bitmap_layer_create(
        GRect(DIALOG_MESSAGE_WINDOW_MARGIN, bounds.size.h + DIALOG_MESSAGE_WINDOW_MARGIN,
              icon_bounds.size.w, icon_bounds.size.h));
    notification->message_layer = text_layer_create(GRect(8, 168, 128, 100));
    if (!notification->window || !notification->icon ||
        !notification->background_layer || !notification->icon_layer ||
        !notification->message_layer) {
        if (notification->window) window_destroy(notification->window);
        if (notification->icon) gbitmap_destroy(notification->icon);
        if (notification->background_layer) layer_destroy(notification->background_layer);
        if (notification->icon_layer) bitmap_layer_destroy(notification->icon_layer);
        if (notification->message_layer) text_layer_destroy(notification->message_layer);
        free(notification);
        return;
    }

    window_set_background_color(
        notification->window, PBL_IF_COLOR_ELSE(GColorBlack, GColorWhite));
    layer_set_update_proc(notification->background_layer, background_update);
    bitmap_layer_set_bitmap(notification->icon_layer, notification->icon);
    bitmap_layer_set_compositing_mode(notification->icon_layer, GCompOpSet);
    text_layer_set_background_color(notification->message_layer, GColorClear);
    text_layer_set_text_color(notification->message_layer, GColorBlack);
    text_layer_set_font(notification->message_layer,
                        fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
    text_layer_set_text_alignment(notification->message_layer, GTextAlignmentCenter);
    text_layer_set_overflow_mode(notification->message_layer, GTextOverflowModeWordWrap);
    text_layer_set_text(notification->message_layer, notification->message);

    Layer* root = window_get_root_layer(notification->window);
    layer_add_child(root, notification->background_layer);
    layer_add_child(root, bitmap_layer_get_layer(notification->icon_layer));
    layer_add_child(root, text_layer_get_layer(notification->message_layer));

    window_set_user_data(notification->window, notification);
    window_set_click_config_provider_with_context(notification->window, click_config, notification);
    window_set_window_handlers(notification->window,
                               (WindowHandlers){.appear = appear, .unload = unload});
    active = notification;
    notification_lifecycle_show(&notification->lifecycle, duration_ms);
    window_stack_push(notification->window, true);

    if (vibration == 1) {
        static const uint32_t short_pattern[] = {200};
        vibes_enqueue_custom_pattern((VibePattern){.durations = short_pattern, .num_segments = 1});
    } else if (vibration == 2) {
        static const uint32_t double_pattern[] = {200, 100, 200};
        vibes_enqueue_custom_pattern((VibePattern){.durations = double_pattern, .num_segments = 3});
    }
    if (notification_lifecycle_timer_active(&notification->lifecycle)) {
        notification->timer = app_timer_register(duration_ms, dismiss_timer, notification);
        if (!notification->timer) {
            notification_lifecycle_timer_registration_failed(&notification->lifecycle);
        }
    }
}
