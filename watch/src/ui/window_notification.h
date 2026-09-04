#pragma once

#include <pebble.h>

void window_notification_show(const char* title, const char* body,
                              uint8_t vibration, uint32_t duration_ms);
void window_notification_dismiss(void);
void window_notification_dismiss_all(void);
