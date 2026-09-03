package com.matejdro.catapult.bluetooth

import com.matejdro.bucketsync.FakeBucketSyncRepository
import com.matejdro.bucketsync.api.Bucket
import com.matejdro.bucketsync.api.BucketUpdate
import com.matejdro.catapult.actionlist.api.CatapultAction
import com.matejdro.catapult.actionlist.api.CatapultDirectory
import com.matejdro.catapult.actionlist.api.MAX_ACTIONS_TO_SYNC
import com.matejdro.catapult.actionlist.test.FakeCatapultActionRepository
import com.matejdro.catapult.actionlist.test.FakeDirectoryListRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import si.inova.kotlinova.core.test.TestScopeWithDispatcherProvider
import kotlin.time.Duration.Companion.seconds

class WatchSyncerImplTest {
   val bucketSyncRepository = FakeBucketSyncRepository(BUCKET_DATA_VERSION.toInt())
   val actionRepository = FakeCatapultActionRepository()
   val directoryRepository = FakeDirectoryListRepository()

   private val scope = TestScopeWithDispatcherProvider()

   val watchSyncer = WatchSyncerImpl(
      bucketSyncRepository,
      lazy { actionRepository },
      lazy { directoryRepository }
   )

   @Test
   fun `Sync directory to the watch`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))

      actionRepository.insert(CatapultAction("Action A", directoryId = 1, id = 10))
      actionRepository.insert(CatapultAction("Action B", directoryId = 1, id = 11, targetDirectoryId = 2))
      actionRepository.insert(CatapultAction("Action C", directoryId = 1, id = 11, targetDirectoryId = 2, enabled = false))

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()) shouldBe BucketUpdate(
         1u,
         listOf(1u),
         listOf(
            Bucket(
               1u,
               byteArrayOf(
                  // Number of items
                  2,

                  // Action A
                  // ID
                  0, 10,
                  // Target directory (not applicable, so zero)
                  0,
                  // Flags
                  0,
                  // Title
                  0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x20, 0x41, 0,

                  // Action B
                  // ID
                  0, 11,
                  // Target directory
                  2,
                  // Flags
                  0,
                  // Title
                  0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x20, 0x42, 0
               )
            )
         )
      )
   }

   @Test
   fun `Sync top level directories as folders on the watch home`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Starting Directory"))
      directoryRepository.insert(CatapultDirectory(2, "car"))
      directoryRepository.insert(CatapultDirectory(3, "bike"))

      actionRepository.insert(CatapultAction("Action A", directoryId = 1, id = 10))

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()) shouldBe BucketUpdate(
         1u,
         listOf(1u),
         listOf(
            Bucket(
               1u,
               byteArrayOf(
                  // Number of items
                  3,

                  // Action A
                  0, 10,
                  0,
                  0,
                  0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x20, 0x41, 0,

                  // car folder
                  0, 2,
                  2,
                  0,
                  0x63, 0x61, 0x72, 0,

                  // bike folder
                  0, 3,
                  3,
                  0,
                  0x62, 0x69, 0x6b, 0x65, 0
               )
            )
         )
      )
   }

   @Test
   fun `Do not duplicate a folder when a manual directory link already exists`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Starting Directory"))
      directoryRepository.insert(CatapultDirectory(2, "car"))

      actionRepository.insert(CatapultAction("Manual link", directoryId = 1, id = 10, targetDirectoryId = 2))

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()).bucketsToUpdate.first().data.first() shouldBe 1
   }

   @Test
   fun `Do not add folders when syncing a non-starting directory`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Starting Directory"))
      directoryRepository.insert(CatapultDirectory(2, "car"))
      directoryRepository.insert(CatapultDirectory(3, "bike"))

      actionRepository.insert(CatapultAction("Action A", directoryId = 2, id = 10))

      watchSyncer.syncDirectory(2)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()).bucketsToUpdate.first().data.first() shouldBe 1
   }

   @Test
   fun `Keep all real actions when the starting directory is full`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Starting Directory"))
      directoryRepository.insert(CatapultDirectory(2, "car"))

      repeat(MAX_ACTIONS_TO_SYNC) { index ->
         actionRepository.insert(
            CatapultAction("Action $index", directoryId = 1, id = 100 + index)
         )
      }

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      val data = bucketSyncRepository.awaitNextUpdate(0u, emptyList()).bucketsToUpdate.first().data
      data.first() shouldBe MAX_ACTIONS_TO_SYNC.toByte()
      data[1] shouldBe 0
      data[2] shouldBe 100
   }

   @Test
   fun `Delete a directory from the watch`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))

      actionRepository.insert(CatapultAction("Action A", directoryId = 1, id = 10))
      actionRepository.insert(CatapultAction("Action B", directoryId = 1, id = 11, targetDirectoryId = 2))

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      watchSyncer.deleteDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()) shouldBe BucketUpdate(
         2u,
         emptyList(),
         emptyList(),
      )
   }

   @Test
   fun `Reload all data when protocol changes`() = scope.runTest {
      val bucketSyncRepositoryWithOldVersion = FakeBucketSyncRepository(0)

      val watchSyncer = WatchSyncerImpl(
         bucketSyncRepositoryWithOldVersion,
         lazy { actionRepository },
         lazy { directoryRepository }
      )

      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))
      directoryRepository.insert(CatapultDirectory(2, "Directory 2"))

      watchSyncer.init()

      val update = async { bucketSyncRepositoryWithOldVersion.awaitNextUpdate(0u, emptyList()) }
      delay(1.seconds)

      update.getCompleted().activeBuckets.shouldContainExactly(1u, 2u)
   }

   @Test
   fun `Do not reload all data when protocol changes`() = scope.runTest {
      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))
      directoryRepository.insert(CatapultDirectory(2, "Directory 2"))

      watchSyncer.init()

      val update = async { bucketSyncRepository.awaitNextUpdate(0u, emptyList()) }
      delay(1.seconds)
      update.isCompleted shouldBe false
      update.cancel()
   }

   @Test
   fun `Only sync first 13 actions to the watch`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))
      repeat(15) { index ->
         val id = index + 1
         actionRepository.insert(CatapultAction("Action $index", directoryId = 1, id = id))
      }

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList()).bucketsToUpdate.first().data.first() shouldBe 13
   }

   @Test
   fun `Send voice flag to the watch`() = scope.runTest {
      watchSyncer.init()

      directoryRepository.insert(CatapultDirectory(1, "Directory 1"))

      actionRepository.insert(CatapultAction("Action A", directoryId = 1, id = 10, voiceArgument = true))

      watchSyncer.syncDirectory(1)
      delay(1.seconds)

      bucketSyncRepository.awaitNextUpdate(0u, emptyList())
         .bucketsToUpdate
         .first()
         .data[4] shouldBe 1.toByte()
   }
}
