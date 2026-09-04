#include "notification_packet.h"

static bool valid_utf8_cstring(const Tuple* tuple, size_t max_bytes)
{
    if (!tuple || tuple->type != TUPLE_CSTRING || tuple->length == 0 ||
        tuple->length > max_bytes + 1) return false;

    const unsigned char* text = (const unsigned char*)tuple->value->cstring;
    const size_t length = tuple->length - 1;
    if (text[length] != '\0') return false;

    size_t i = 0;
    while (i < length) {
        uint32_t codepoint;
        uint8_t width;
        if (text[i] <= 0x7f) {
            if (text[i++] == '\0') return false;
            continue;
        }
        if (text[i] >= 0xc2 && text[i] <= 0xdf) { codepoint = text[i++] & 0x1f; width = 2; }
        else if (text[i] >= 0xe0 && text[i] <= 0xef) { codepoint = text[i++] & 0x0f; width = 3; }
        else if (text[i] >= 0xf0 && text[i] <= 0xf4) { codepoint = text[i++] & 0x07; width = 4; }
        else return false;
        if (i + width - 1 > length) return false;
        for (uint8_t j = 1; j < width; j++) {
            if ((text[i] & 0xc0) != 0x80) return false;
            codepoint = (codepoint << 6) | (text[i++] & 0x3f);
        }
        if ((width == 3 && codepoint < 0x800) ||
            (width == 4 && codepoint < 0x10000) ||
            codepoint > 0x10ffff || (codepoint >= 0xd800 && codepoint <= 0xdfff)) return false;
    }
    return true;
}

static bool uint_tuple_width(const Tuple* tuple, uint16_t width)
{
    return tuple && tuple->type == TUPLE_UINT && tuple->length == width;
}

bool decode_notification_packet(const DictionaryIterator* received, NotificationPacket* packet)
{
    if (!received || !packet) return false;

    Tuple* id = dict_find(received, 0);
    Tuple* title = dict_find(received, 2);
    Tuple* body = dict_find(received, 7);
    Tuple* vibration = dict_find(received, 6);
    Tuple* duration = dict_find(received, 8);
    if (!uint_tuple_width(id, 4) || id->value->uint32 != 11 ||
        !valid_utf8_cstring(title, 64) || !valid_utf8_cstring(body, 128) ||
        !uint_tuple_width(vibration, 1) || !uint_tuple_width(duration, 4) ||
        vibration->value->uint8 > 2 || duration->value->uint32 > 300000) {
        return false;
    }

    packet->title = title->value->cstring;
    packet->body = body->value->cstring;
    packet->vibration = vibration->value->uint8;
    packet->duration_ms = duration->value->uint32;
    return true;
}
