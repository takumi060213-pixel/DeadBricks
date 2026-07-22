package com.example.deadbricks2

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DailyScreenTimeWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            if (!hasUsageStatsPermission(applicationContext)) {
                scheduleNextDailySave(applicationContext)
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(
                "deadbricks_settings",
                Context.MODE_PRIVATE
            )

            val roomId = prefs.getString("roomId", "")?.trim() ?: ""
            val memberId = prefs.getString("memberId", "")?.trim() ?: ""
            val memberName = prefs.getString("memberName", "名前なし") ?: "名前なし"
            val memberRole = prefs.getString("memberRole", "子供") ?: "子供"
            val isInFamilyRoom = prefs.getBoolean("isInFamilyRoom", false)
            val targetMinutes = prefs.getLong("targetMinutes", 60L)

            val yesterdayDate = getYesterdayDateString()
            val screenTimeMinutes = getYesterdayScreenTimeMinutes()
            val materialCount = calculateMaterial(
                targetMinutes = targetMinutes,
                screenTimeMinutes = screenTimeMinutes
            )

            prefs.edit()
                .putString("lastSavedDate", yesterdayDate)
                .putLong("lastSavedScreenTimeMinutes", screenTimeMinutes)
                .putLong("lastSavedMaterialCount", materialCount)
                .apply()

            if (!isInFamilyRoom || roomId.isBlank() || memberId.isBlank()) {
                scheduleNextDailySave(applicationContext)
                return Result.success()
            }

            val success = saveYesterdayDataToFirebase(
                roomId = roomId,
                memberId = memberId,
                memberName = memberName,
                memberRole = memberRole,
                date = yesterdayDate,
                screenTimeMinutes = screenTimeMinutes,
                targetMinutes = targetMinutes,
                materialCount = materialCount
            )

            scheduleNextDailySave(applicationContext)

            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            scheduleNextDailySave(applicationContext)
            Result.retry()
        }
    }

    private fun saveYesterdayDataToFirebase(
        roomId: String,
        memberId: String,
        memberName: String,
        memberRole: String,
        date: String,
        screenTimeMinutes: Long,
        targetMinutes: Long,
        materialCount: Long
    ): Boolean {
        val db = FirebaseFirestore.getInstance()
        val latch = CountDownLatch(1)
        var isSuccess = false

        val memberData = hashMapOf(
            "roomId" to roomId,
            "memberId" to memberId,
            "memberName" to memberName,
            "role" to memberRole,
            "screenTimeMinutes" to screenTimeMinutes,
            "targetMinutes" to targetMinutes,
            "materialCount" to materialCount,
            "lastUpdatedDate" to date,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val dailyData = hashMapOf(
            "date" to date,
            "roomId" to roomId,
            "memberId" to memberId,
            "memberName" to memberName,
            "role" to memberRole,
            "screenTimeMinutes" to screenTimeMinutes,
            "targetMinutes" to targetMinutes,
            "materialCount" to materialCount,
            "tickets" to emptyList<Map<String, Any>>(),
            "rewardTicketTypes" to emptyList<Map<String, Any>>(),
            "tasks" to emptyList<Map<String, Any>>(),
            "savedBy" to "DailyScreenTimeWorker",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val memberRef = db.collection("familyRooms")
            .document(roomId)
            .collection("members")
            .document(memberId)

        memberRef
            .set(memberData)
            .addOnSuccessListener {
                memberRef
                    .collection("dailyRecords")
                    .document(date)
                    .set(dailyData)
                    .addOnSuccessListener {
                        isSuccess = true
                        latch.countDown()
                    }
                    .addOnFailureListener {
                        isSuccess = false
                        latch.countDown()
                    }
            }
            .addOnFailureListener {
                isSuccess = false
                latch.countDown()
            }

        latch.await(30, TimeUnit.SECONDS)

        return isSuccess
    }

    private fun calculateMaterial(
        targetMinutes: Long,
        screenTimeMinutes: Long
    ): Long {
        val materialRate = 10L
        val savedMinutes = targetMinutes - screenTimeMinutes

        return if (savedMinutes > 0) {
            savedMinutes / materialRate
        } else {
            0L
        }
    }

    private fun getYesterdayScreenTimeMinutes(): Long {
        val usageStatsManager =
            applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val endTime = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -1)

        val startTime = calendar.timeInMillis

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var currentPackageName: String? = null
        var currentStartTime = 0L
        var totalTime = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            val packageName = event.packageName ?: continue

            if (shouldIgnorePackage(packageName)) {
                continue
            }

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    currentPackageName = packageName
                    currentStartTime = event.timeStamp
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (currentPackageName == packageName && currentStartTime > 0L) {
                        if (event.timeStamp > currentStartTime) {
                            totalTime += event.timeStamp - currentStartTime
                        }

                        currentPackageName = null
                        currentStartTime = 0L
                    }
                }
            }
        }

        if (currentPackageName != null && currentStartTime > 0L && endTime > currentStartTime) {
            totalTime += endTime - currentStartTime
        }

        return totalTime / 1000 / 60
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
        if (packageName == applicationContext.packageName) {
            return true
        }

        val ignorePackages = listOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.android.launcher",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.settings",
            "com.google.android.apps.wellbeing"
        )

        return ignorePackages.contains(packageName)
    }

    private fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)

        return SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            .format(calendar.time)
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )

        return mode == AppOpsManager.MODE_ALLOWED
    }

    companion object {
        private const val WORK_NAME = "daily_screen_time_save_work"

        fun scheduleNextDailySave(context: Context) {
            val delayMillis = getDelayUntilNextRun()

            val request = OneTimeWorkRequestBuilder<DailyScreenTimeWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        private fun getDelayUntilNextRun(): Long {
            val now = Calendar.getInstance()

            val nextRun = Calendar.getInstance()
            nextRun.set(Calendar.HOUR_OF_DAY, 0)
            nextRun.set(Calendar.MINUTE, 5)
            nextRun.set(Calendar.SECOND, 0)
            nextRun.set(Calendar.MILLISECOND, 0)

            if (nextRun.timeInMillis <= now.timeInMillis) {
                nextRun.add(Calendar.DAY_OF_YEAR, 1)
            }

            return nextRun.timeInMillis - now.timeInMillis
        }
    }
}