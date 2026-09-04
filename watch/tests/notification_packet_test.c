#include "connection/notification_packet.h"

#include <assert.h>
#include <string.h>

static TupleValue values[5];
static char title[200];
static char body[200];
static Tuple tuples[5];

static void valid_packet(DictionaryIterator *iterator)
{
    values[0].uint32 = 11;
    tuples[0] = (Tuple){0, TUPLE_UINT, 4, &values[0]};
    values[1].uint32 = 1;
    tuples[1] = (Tuple){6, TUPLE_UINT, 1, &values[1]};
    values[2].uint32 = 300000;
    tuples[2] = (Tuple){8, TUPLE_UINT, 4, &values[2]};
    strcpy(title, "Door");
    values[3].cstring = title;
    tuples[3] = (Tuple){2, TUPLE_CSTRING, 5, &values[3]};
    strcpy(body, "Front door opened");
    values[4].cstring = body;
    tuples[4] = (Tuple){7, TUPLE_CSTRING, 18, &values[4]};
    *iterator = (DictionaryIterator){tuples, 5};
}

static void assert_rejected(DictionaryIterator *iterator)
{
    NotificationPacket packet;
    assert(!decode_notification_packet(iterator, &packet));
}

static void test_missing_fields(void)
{
    DictionaryIterator iterator;
    valid_packet(&iterator);
    for (size_t missing = 0; missing < iterator.count; missing++) {
        Tuple saved = iterator.tuples[missing];
        iterator.tuples[missing] = iterator.tuples[iterator.count - 1];
        iterator.count--;
        assert_rejected(&iterator);
        iterator.count++;
        iterator.tuples[iterator.count - 1] = saved;
    }
}

static void test_wrong_types(void)
{
    DictionaryIterator iterator;
    valid_packet(&iterator);
    for (size_t i = 0; i < iterator.count; i++) {
        TupleType saved = iterator.tuples[i].type;
        iterator.tuples[i].type = saved == TUPLE_UINT ? TUPLE_CSTRING : TUPLE_UINT;
        assert_rejected(&iterator);
        iterator.tuples[i].type = saved;
    }
}

static void test_oversized_text(void)
{
    DictionaryIterator iterator;
    valid_packet(&iterator);
    memset(title, 'a', 65);
    title[65] = '\0';
    tuples[3].length = 66;
    assert_rejected(&iterator);

    valid_packet(&iterator);
    memset(body, 'b', 129);
    body[129] = '\0';
    tuples[4].length = 130;
    assert_rejected(&iterator);
}

static void test_embedded_nul(void)
{
    DictionaryIterator iterator;
    valid_packet(&iterator);
    memcpy(title, "Door\0hidden", 12);
    tuples[3].length = 12;
    assert_rejected(&iterator);
}

static void test_invalid_vibration_and_duration(void)
{
    DictionaryIterator iterator;
    valid_packet(&iterator);
    values[1].uint32 = 3;
    tuples[1].length = 1;
    assert_rejected(&iterator);

    valid_packet(&iterator);
    values[2].uint32 = 300001;
    tuples[2].length = 4;
    assert_rejected(&iterator);

    valid_packet(&iterator);
    tuples[2].length = 2;
    assert_rejected(&iterator);
}

static void test_valid_packet(void)
{
    DictionaryIterator iterator;
    NotificationPacket packet;
    valid_packet(&iterator);
    assert(decode_notification_packet(&iterator, &packet));
    assert(strcmp(packet.title, "Door") == 0);
    assert(strcmp(packet.body, "Front door opened") == 0);
    assert(packet.vibration == 1);
    assert(packet.duration_ms == 300000);
}

int main(void)
{
    test_missing_fields();
    test_wrong_types();
    test_oversized_text();
    test_embedded_nul();
    test_invalid_vibration_and_duration();
    test_valid_packet();
    return 0;
}
