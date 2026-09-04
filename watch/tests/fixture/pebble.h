#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef enum {
    TUPLE_UINT = 0,
    TUPLE_CSTRING = 1,
} TupleType;

typedef union {
    uint8_t uint8;
    uint16_t uint16;
    uint32_t uint32;
    char *cstring;
} TupleValue;

typedef struct {
    uint32_t key;
    TupleType type;
    uint16_t length;
    TupleValue *value;
} Tuple;

typedef struct {
    Tuple *tuples;
    size_t count;
} DictionaryIterator;

Tuple *dict_find(const DictionaryIterator *iterator, uint32_t key);
