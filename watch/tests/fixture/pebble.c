#include "pebble.h"

Tuple *dict_find(const DictionaryIterator *iterator, uint32_t key)
{
    if (!iterator) return NULL;
    for (size_t i = 0; i < iterator->count; i++) {
        if (iterator->tuples[i].key == key) return &iterator->tuples[i];
    }
    return NULL;
}
