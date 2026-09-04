#include "notification_lifecycle.h"

void notification_lifecycle_init(NotificationLifecycle* lifecycle)
{
    *lifecycle = (NotificationLifecycle){0};
}

void notification_lifecycle_init_with_cancel_timer(
    NotificationLifecycle* lifecycle,
    NotificationLifecycleCancelTimer cancel_timer,
    void* cancel_timer_context)
{
    notification_lifecycle_init(lifecycle);
    lifecycle->cancel_timer = cancel_timer;
    lifecycle->cancel_timer_context = cancel_timer_context;
}

static void cancel_timer(NotificationLifecycle* lifecycle)
{
    if (lifecycle->timer_active && lifecycle->cancel_timer) {
        lifecycle->cancel_timer(lifecycle->cancel_timer_context);
    }
    lifecycle->timer_active = false;
}

void notification_lifecycle_show(NotificationLifecycle* lifecycle, uint32_t duration_ms)
{
    cancel_timer(lifecycle);
    lifecycle->generation++;
    lifecycle->visible = true;
    lifecycle->timer_active = duration_ms > 0;
    lifecycle->timer_generation = lifecycle->generation;
}

bool notification_lifecycle_dismiss(NotificationLifecycle* lifecycle)
{
    if (!lifecycle->visible) return false;
    lifecycle->visible = false;
    cancel_timer(lifecycle);
    return true;
}

bool notification_lifecycle_back(NotificationLifecycle* lifecycle)
{
    if (!lifecycle->visible) return false;
    lifecycle->visible = false;
    cancel_timer(lifecycle);
    return true;
}

bool notification_lifecycle_select(NotificationLifecycle* lifecycle)
{
    if (!lifecycle->visible) return false;
    lifecycle->visible = false;
    cancel_timer(lifecycle);
    return true;
}

void notification_lifecycle_unload(NotificationLifecycle* lifecycle)
{
    lifecycle->visible = false;
    cancel_timer(lifecycle);
}

bool notification_lifecycle_timer_fired(NotificationLifecycle* lifecycle, uint32_t generation)
{
    if (!lifecycle->visible || !lifecycle->timer_active ||
        lifecycle->timer_generation != generation) {
        return false;
    }
    lifecycle->timer_active = false;
    lifecycle->visible = false;
    return true;
}

void notification_lifecycle_timer_registration_failed(NotificationLifecycle* lifecycle)
{
    lifecycle->timer_active = false;
}

bool notification_lifecycle_timer_active(const NotificationLifecycle* lifecycle)
{
    return lifecycle->timer_active;
}

uint32_t notification_lifecycle_generation(const NotificationLifecycle* lifecycle)
{
    return lifecycle->generation;
}
