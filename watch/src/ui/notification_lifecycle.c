#include "notification_lifecycle.h"

void notification_lifecycle_init(NotificationLifecycle* lifecycle)
{
    *lifecycle = (NotificationLifecycle){0};
}

void notification_lifecycle_show(NotificationLifecycle* lifecycle, uint32_t duration_ms)
{
    lifecycle->generation++;
    lifecycle->visible = true;
    lifecycle->timer_active = duration_ms > 0;
    lifecycle->timer_generation = lifecycle->generation;
}

bool notification_lifecycle_dismiss(NotificationLifecycle* lifecycle)
{
    if (!lifecycle->visible) return false;
    lifecycle->visible = false;
    lifecycle->timer_active = false;
    return true;
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

bool notification_lifecycle_timer_active(const NotificationLifecycle* lifecycle)
{
    return lifecycle->timer_active;
}

uint32_t notification_lifecycle_generation(const NotificationLifecycle* lifecycle)
{
    return lifecycle->generation;
}
