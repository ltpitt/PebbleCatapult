#include "ui/notification_lifecycle.h"

#include <assert.h>

static void test_back_dismissal(void)
{
    NotificationLifecycle lifecycle;
    notification_lifecycle_init(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    assert(notification_lifecycle_dismiss(&lifecycle));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(!notification_lifecycle_dismiss(&lifecycle));
}

static void test_select_dismissal(void)
{
    NotificationLifecycle lifecycle;
    notification_lifecycle_init(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    assert(notification_lifecycle_dismiss(&lifecycle));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
}

static void test_automatic_dismissal(void)
{
    NotificationLifecycle lifecycle;
    notification_lifecycle_init(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t generation = notification_lifecycle_generation(&lifecycle);
    assert(notification_lifecycle_timer_fired(&lifecycle, generation));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
}

static void test_zero_duration_persists(void)
{
    NotificationLifecycle lifecycle;
    notification_lifecycle_init(&lifecycle);
    notification_lifecycle_show(&lifecycle, 0);
    assert(lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(!notification_lifecycle_timer_fired(&lifecycle,
                                               notification_lifecycle_generation(&lifecycle)));
    assert(lifecycle.visible);
}

static void test_replacement_ignores_old_timer(void)
{
    NotificationLifecycle lifecycle;
    notification_lifecycle_init(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t old_generation = notification_lifecycle_generation(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t new_generation = notification_lifecycle_generation(&lifecycle);
    assert(old_generation != new_generation);
    assert(!notification_lifecycle_timer_fired(&lifecycle, old_generation));
    assert(lifecycle.visible);
    assert(notification_lifecycle_timer_fired(&lifecycle, new_generation));
    assert(!lifecycle.visible);
}

int main(void)
{
    test_back_dismissal();
    test_select_dismissal();
    test_automatic_dismissal();
    test_zero_duration_persists();
    test_replacement_ignores_old_timer();
    return 0;
}
