#pragma once

#include <pebble.h>

typedef struct {
    const char* title;
    const char* body;
    uint8_t vibration;
    uint32_t duration_ms;
} NotificationPacket;

bool decode_notification_packet(const DictionaryIterator* received, NotificationPacket* packet);
