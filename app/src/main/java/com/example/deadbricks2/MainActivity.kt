package com.example.deadbricks2

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private var screenTimeMinutes by mutableStateOf(0L)
    private var materialCount by mutableStateOf(0L)
    private var message by mutableStateOf("使用時間を取得してください")

    private val targetMinutes = 120L
    private val materialRate = 10L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "デットブリックス",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "今日の使用時間：${screenTimeMinutes}分")
                    Text(text = "目標時間：${targetMinutes}分")
                    Text(text = "獲得素材：${materialCount}個")

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = message)

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            openUsageAccessSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用状況アクセスを許可する")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            updateScreenTime()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用時間を更新する")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasUsageStatsPermission()) {
            updateScreenTime()
        }
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun updateScreenTime() {
        if (!hasUsageStatsPermission()) {
            message = "使用状況アクセスを許可してください"
            return
        }

        val minutes = getTodayScreenTimeMinutes()

        val savedMinutes = targetMinutes - minutes
        val material = if (savedMinutes > 0) {
            savedMinutes / materialRate
        } else {
            0
        }

        screenTimeMinutes = minutes
        materialCount = material

        message = if (savedMinutes > 0) {
            "${savedMinutes}分節約できました"
        } else {
            "今日は目標時間を超えています"
        }
    }

    private fun getTodayScreenTimeMinutes(): Long {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()

        val endTime = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        var totalTime = 0L

        for (usageStats in usageStatsList) {
            totalTime += usageStats.totalTimeInForeground
        }

        return totalTime / 1000 / 60
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )

        return mode == AppOpsManager.MODE_ALLOWED
    }
}