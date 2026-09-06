#include <pebble.h>
#include "commons/connection/bluetooth.h"
#include "commons/connection/bucket_sync.h"
#include "ui/window_action_list.h"
#include "connection/packets.h"
#include "ui/window_status.h"


const uint16_t PROTOCOL_VERSION = 4;

static void app_focus_changed(bool in_focus)
{
    APP_LOG(APP_LOG_LEVEL_INFO, "App focus changed: in_focus=%d", in_focus);
}

int main(void)
{
    APP_LOG(APP_LOG_LEVEL_INFO, "Catapult watchapp started");
    app_focus_service_subscribe(app_focus_changed);
    packets_init();
    bluetooth_init();
    bucket_sync_init();

    send_watch_welcome();

    uint8_t tmp[PERSIST_DATA_MAX_LENGTH];
    const bool loaded = bucket_sync_load_bucket(1, tmp);

    if (!loaded || tmp[0] == 0)
    {
        window_status_show_empty();
    }
    else
    {
        window_action_list_show(1);
    }

    app_event_loop();
}