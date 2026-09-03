package com.matejdro.catapult.bluetooth

import com.matejdro.bucketsync.BucketSyncRepository
import com.matejdro.catapult.actionlist.api.CatapultAction
import com.matejdro.catapult.actionlist.api.CatapultActionRepository
import com.matejdro.catapult.actionlist.api.DirectoryListRepository
import com.matejdro.catapult.actionlist.api.MAX_ACTIONS_TO_SYNC
import com.matejdro.catapult.common.flow.firstData
import com.matejdro.pebble.bluetooth.common.util.writeUByte
import com.matejdro.pebble.bluetooth.common.util.writeUShort
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dispatch.core.withDefault
import logcat.LogPriority
import logcat.logcat
import okio.Buffer

@Inject
@ContributesBinding(AppScope::class)
class WatchSyncerImpl(
   private val bucketSyncRepository: BucketSyncRepository,
   private val actionRepository: Lazy<CatapultActionRepository>,
   private val directoryRepository: Lazy<DirectoryListRepository>,
) : WatchSyncer {
   override suspend fun init() {
      val reloadAllData = !bucketSyncRepository.init(BUCKET_DATA_VERSION.toInt())
      if (reloadAllData) {
         logcat { "Got different protocol version, resetting all data" }
         val allDirectories = directoryRepository.value.getAll().firstData()
         for (directory in allDirectories) {
            syncDirectory(directory.id)
         }
      }
   }

   @Suppress("MissingUseCall") // writeUtf8 returns `this` — buffer is already in a use block
   override suspend fun syncDirectory(id: Int) = withDefault {
      val items = actionRepository.value.getAll(id, limit = MAX_ACTIONS_TO_SYNC, onlyEnabled = true)
         .firstData()
         .toMutableList()

      if (id == STARTING_DIRECTORY_ID) {
         appendDirectoriesAsFolders(items)
      }

      logcat { "Syncing directory $id, ${items.size} items" }

      val data = Buffer().use { buffer ->
         buffer.writeUByte(items.size.toUByte())
         for (item in items) {
            buffer.writeUShort(item.id.toUShort())
            buffer.writeUByte(item.targetDirectoryId?.toUByte() ?: 0u)
            buffer.writeUByte(if (item.voiceArgument) 1u else 0u)
            buffer.writeUtf8(item.title)
            buffer.writeUByte(0u) // Null terminator
         }
         buffer.readByteArray()
      }
      logcat(LogPriority.DEBUG, null) { "Size: ${data.size} bytes" }

      bucketSyncRepository.updateBucket(id.toUByte(), data)
   }

   private suspend fun appendDirectoriesAsFolders(items: MutableList<CatapultAction>) {
      val remainingSlots = MAX_ACTIONS_TO_SYNC - items.size
      if (remainingSlots <= 0) return

      val alreadyLinked = items.mapNotNull { it.targetDirectoryId }.toSet()

      val folders = directoryRepository.value.getAll().firstData()
         .asSequence()
         .sortedBy { it.id }
         .filter { it.id != STARTING_DIRECTORY_ID && it.id !in alreadyLinked }
         .take(remainingSlots)
         .map { directory ->
            CatapultAction(
               title = directory.title,
               directoryId = STARTING_DIRECTORY_ID,
               id = directory.id,
               targetDirectoryId = directory.id,
            )
         }

      items += folders
   }

   override suspend fun deleteDirectory(id: Int) {
      logcat { "Deleting directory $id" }
      bucketSyncRepository.deleteBucket(id.toUByte())
   }
}
