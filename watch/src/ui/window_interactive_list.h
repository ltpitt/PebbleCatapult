#pragma once

#include <pebble.h>
#include <stdint.h>

typedef void (*WindowInteractiveListSelectionCallback)(
    uint32_t session, const char* id, const char* value, void* context);
typedef void (*WindowInteractiveListCancelCallback)(uint32_t session, void* context);
typedef void (*WindowInteractiveListErrorCallback)(
    uint32_t session, const char* reason, void* context);

bool window_interactive_list_show(
    uint32_t session,
    const char* title,
    const char ids[][33],
    const char values[][65],
    uint8_t count,
    WindowInteractiveListSelectionCallback selection,
    WindowInteractiveListCancelCallback cancel,
    WindowInteractiveListErrorCallback error,
    void* context);

void window_interactive_list_dismiss(void);
