#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    bool visible;
    bool timer_active;
    uint32_t generation;
    uint32_t timer_generation;
} NotificationLifecycle;

void notification_lifecycle_init(NotificationLifecycle* lifecycle);
void notification_lifecycle_show(NotificationLifecycle* lifecycle, uint32_t duration_ms);
bool notification_lifecycle_dismiss(NotificationLifecycle* lifecycle);
bool notification_lifecycle_timer_fired(NotificationLifecycle* lifecycle, uint32_t generation);
bool notification_lifecycle_timer_active(const NotificationLifecycle* lifecycle);
uint32_t notification_lifecycle_generation(const NotificationLifecycle* lifecycle);
