#pragma once

#include <pebble.h>
#include <stdint.h>

typedef void (*WindowInteractiveConfirmCallback)(uint32_t session, bool accepted, void* context);
typedef void (*WindowInteractiveConfirmCancelCallback)(uint32_t session, void* context);
typedef void (*WindowInteractiveConfirmErrorCallback)(
    uint32_t session, const char* reason, void* context);

bool window_interactive_confirm_show(
    uint32_t session,
    const char* title,
    const char* message,
    WindowInteractiveConfirmCallback confirm,
    WindowInteractiveConfirmCancelCallback cancel,
    WindowInteractiveConfirmErrorCallback error,
    void* context);

void window_interactive_confirm_dismiss(void);
