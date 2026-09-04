#include "ui/notification_lifecycle.h"

#include <assert.h>

typedef struct {
    unsigned int cancellations;
} TimerRecorder;

static void record_cancel(void* context)
{
    TimerRecorder* recorder = context;
    recorder->cancellations++;
}

static void init_lifecycle(NotificationLifecycle* lifecycle, TimerRecorder* recorder)
{
    notification_lifecycle_init_with_cancel_timer(lifecycle, record_cancel, recorder);
}

static void test_back_dismissal(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    assert(notification_lifecycle_back(&lifecycle));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(recorder.cancellations == 1);
    assert(!notification_lifecycle_dismiss(&lifecycle));
}

static void test_select_dismissal(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    assert(notification_lifecycle_select(&lifecycle));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(recorder.cancellations == 1);
}

static void test_automatic_dismissal(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t generation = notification_lifecycle_generation(&lifecycle);
    assert(notification_lifecycle_timer_fired(&lifecycle, generation));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
}

static void test_zero_duration_persists(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 0);
    assert(lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(!notification_lifecycle_timer_fired(&lifecycle,
                                               notification_lifecycle_generation(&lifecycle)));
    assert(lifecycle.visible);
}

static void test_timer_registration_failure_persists_for_manual_dismissal(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    notification_lifecycle_timer_registration_failed(&lifecycle);
    assert(lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(!notification_lifecycle_timer_fired(
        &lifecycle, notification_lifecycle_generation(&lifecycle)));
    assert(lifecycle.visible);
}

static void test_replacement_ignores_old_timer(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t old_generation = notification_lifecycle_generation(&lifecycle);
    notification_lifecycle_show(&lifecycle, 5000);
    assert(recorder.cancellations == 1);
    uint32_t new_generation = notification_lifecycle_generation(&lifecycle);
    assert(old_generation != new_generation);
    assert(!notification_lifecycle_timer_fired(&lifecycle, old_generation));
    assert(lifecycle.visible);
    assert(notification_lifecycle_timer_fired(&lifecycle, new_generation));
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(recorder.cancellations == 1);
}

static void test_unload_cancels_timer(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    notification_lifecycle_unload(&lifecycle);
    assert(!lifecycle.visible);
    assert(!notification_lifecycle_timer_active(&lifecycle));
    assert(recorder.cancellations == 1);
}

static void test_timer_firing_after_interactive_replacement_is_ignored(void)
{
    NotificationLifecycle lifecycle;
    TimerRecorder recorder = {0};
    init_lifecycle(&lifecycle, &recorder);
    notification_lifecycle_show(&lifecycle, 5000);
    uint32_t generation = notification_lifecycle_generation(&lifecycle);
    notification_lifecycle_dismiss(&lifecycle);
    assert(!notification_lifecycle_timer_fired(&lifecycle, generation));
    assert(!lifecycle.visible);
}

int main(void)
{
    test_back_dismissal();
    test_select_dismissal();
    test_automatic_dismissal();
    test_zero_duration_persists();
    test_timer_registration_failure_persists_for_manual_dismissal();
    test_replacement_ignores_old_timer();
    test_unload_cancels_timer();
    test_timer_firing_after_interactive_replacement_is_ignored();
    return 0;
}
