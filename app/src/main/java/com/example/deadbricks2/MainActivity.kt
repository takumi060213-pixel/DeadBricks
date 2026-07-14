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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Ticket(
    val name: String,
    val status: String
)

data class DailyTask(
    val title: String,
    val completed: Boolean
)

data class FamilyMember(
    val name: String,
    val screenTime: Long,
    val targetTime: Long,
    val material: Long
)

class MainActivity : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()

    private var screenTimeMinutes by mutableLongStateOf(0L)
    private var materialCount by mutableLongStateOf(0L)
    private var message by mutableStateOf("使用時間を取得してください")

    private var taskInput by mutableStateOf("")

    private val tickets = mutableStateListOf<Ticket>()

    private val tasks = mutableStateListOf(
        DailyTask("宿題をする", false),
        DailyTask("明日の準備をする", false),
        DailyTask("歯みがきをする", false)
    )

    private var targetMinutes by mutableLongStateOf(120L)
    private var targetInput by mutableStateOf("120")
    private var isParentMode by mutableStateOf(false)

    private var familyNameInput by mutableStateOf("")
    private var familyScreenTimeInput by mutableStateOf("")
    private var familyTargetTimeInput by mutableStateOf("")
    private var familyMaterialInput by mutableStateOf("")

    private val familyMembers = mutableStateListOf<FamilyMember>()

    private val materialRate = 10L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "デットブリックス",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { isParentMode = false }
                        ) {
                            Text("子供画面")
                        }

                        Button(
                            onClick = { isParentMode = true }
                        ) {
                            Text("親画面")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isParentMode) {
                            "現在：親画面"
                        } else {
                            "現在：子供画面"
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = getAnimalFace(),
                                fontSize = 64.sp
                            )

                            Text(
                                text = getAnimalState(),
                                style = MaterialTheme.typography.titleLarge
                            )

                            Text(text = getAnimalMessage())
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "今日の使用時間：${screenTimeMinutes}分")
                    Text(text = "目標時間：${targetMinutes}分")
                    Text(text = "素材：${materialCount}個")
                    Text(text = message)

                    if (isParentMode) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "親用設定",
                                    style = MaterialTheme.typography.titleLarge
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = targetInput,
                                    onValueChange = { targetInput = it },
                                    label = { Text("1日の目標時間（分）") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { updateTargetTime() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("目標時間を設定する")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { openUsageAccessSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用状況アクセスを許可する")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { updateScreenTime() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("使用時間を更新する")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { saveToFirebase() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Firebaseに保存する")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { loadFromFirebase() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Firebaseから読み込む")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "クラフト",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { craftTicket("おやつ豪華になる券", 5) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("素材5個 → おやつ豪華になる券")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { craftTicket("お小遣い10円券", 10) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("素材10個 → お小遣い10円券")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { craftTicket("背景変更券", 8) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("素材8個 → 背景変更券")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "所持チケット",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (tickets.isEmpty()) {
                        Text("まだチケットはありません")
                    } else {
                        tickets.forEachIndexed { index, ticket ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(text = ticket.name)
                                    Text(text = "状態：${ticket.status}")

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { requestApproval(index) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("親に承認申請する")
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = { approveTicket(index) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("親が承認する")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "日々のタスクボード",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        label = { Text("追加するタスク") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { addTask() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("タスクを追加")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    tasks.forEachIndexed { index, task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.completed,
                                    onCheckedChange = {
                                        toggleTask(index)
                                    }
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = task.title)
                                    Text(
                                        text = if (task.completed) {
                                            "完了"
                                        } else {
                                            "未完了"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "家族コミュニティ",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = familyNameInput,
                        onValueChange = { familyNameInput = it },
                        label = { Text("家族の名前") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = familyScreenTimeInput,
                        onValueChange = { familyScreenTimeInput = it },
                        label = { Text("使用時間（分）") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = familyTargetTimeInput,
                        onValueChange = { familyTargetTimeInput = it },
                        label = { Text("目標時間（分）") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = familyMaterialInput,
                        onValueChange = { familyMaterialInput = it },
                        label = { Text("素材数") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { addFamilyMember() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("家族を追加")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { addMyDataToFamily() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("自分のデータを家族に追加")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (familyMembers.isEmpty()) {
                        Text("まだ家族は登録されていません")
                    } else {
                        familyMembers.forEachIndexed { index, member ->
                            val achievementRate = if (member.targetTime > 0) {
                                ((member.targetTime - member.screenTime) * 100 / member.targetTime)
                            } else {
                                0
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(text = member.name)
                                    Text(text = "使用時間：${member.screenTime}分")
                                    Text(text = "目標時間：${member.targetTime}分")
                                    Text(text = "節約達成率：${achievementRate}%")
                                    Text(text = "素材：${member.material}個")

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { removeFamilyMember(index) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("削除")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
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

    private fun updateTargetTime() {
        val newTarget = targetInput.toLongOrNull()

        if (newTarget == null || newTarget <= 0) {
            message = "正しい目標時間を入力してください"
            return
        }

        targetMinutes = newTarget
        message = "目標時間を${targetMinutes}分に設定しました"

        if (hasUsageStatsPermission()) {
            updateScreenTime()
        }
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

    private fun getAnimalFace(): String {
        return when {
            materialCount >= 10 -> "🐼✨"
            materialCount >= 5 -> "🐼"
            materialCount >= 1 -> "🐼💧"
            else -> "🐼💤"
        }
    }

    private fun getAnimalState(): String {
        return when {
            materialCount >= 10 -> "元気いっぱい"
            materialCount >= 5 -> "元気"
            materialCount >= 1 -> "少し疲れている"
            else -> "しょんぼり"
        }
    }

    private fun getAnimalMessage(): String {
        return when {
            materialCount >= 10 -> "今日はたくさんスマホを我慢できたね！"
            materialCount >= 5 -> "いい感じに頑張れています"
            materialCount >= 1 -> "少しだけ素材をゲットできました"
            else -> "明日は素材を集めよう"
        }
    }

    private fun craftTicket(ticketName: String, cost: Long) {
        if (materialCount >= cost) {
            materialCount -= cost
            tickets.add(
                Ticket(
                    name = ticketName,
                    status = "未申請"
                )
            )
            message = "${ticketName}をクラフトしました"
        } else {
            message = "素材が足りません"
        }
    }

    private fun requestApproval(index: Int) {
        val ticket = tickets[index]

        tickets[index] = ticket.copy(
            status = "承認待ち"
        )

        message = "${ticket.name}を親に承認申請しました"
    }

    private fun approveTicket(index: Int) {
        val ticket = tickets[index]

        tickets[index] = ticket.copy(
            status = "承認済み"
        )

        message = "${ticket.name}が承認されました"
    }

    private fun addTask() {
        if (taskInput.isNotBlank()) {
            tasks.add(
                DailyTask(
                    title = taskInput,
                    completed = false
                )
            )
            message = "タスクを追加しました"
            taskInput = ""
        } else {
            message = "タスク名を入力してください"
        }
    }

    private fun toggleTask(index: Int) {
        val task = tasks[index]

        tasks[index] = task.copy(
            completed = !task.completed
        )

        message = if (!task.completed) {
            "${task.title}を完了しました"
        } else {
            "${task.title}を未完了に戻しました"
        }
    }

    private fun addFamilyMember() {
        val name = familyNameInput
        val screenTime = familyScreenTimeInput.toLongOrNull()
        val targetTime = familyTargetTimeInput.toLongOrNull()
        val material = familyMaterialInput.toLongOrNull()

        if (name.isBlank()) {
            message = "家族の名前を入力してください"
            return
        }

        if (screenTime == null || screenTime < 0) {
            message = "使用時間を正しく入力してください"
            return
        }

        if (targetTime == null || targetTime <= 0) {
            message = "目標時間を正しく入力してください"
            return
        }

        if (material == null || material < 0) {
            message = "素材数を正しく入力してください"
            return
        }

        familyMembers.add(
            FamilyMember(
                name = name,
                screenTime = screenTime,
                targetTime = targetTime,
                material = material
            )
        )

        familyNameInput = ""
        familyScreenTimeInput = ""
        familyTargetTimeInput = ""
        familyMaterialInput = ""

        message = "${name}を家族に追加しました"
    }

    private fun addMyDataToFamily() {
        familyMembers.add(
            FamilyMember(
                name = "自分",
                screenTime = screenTimeMinutes,
                targetTime = targetMinutes,
                material = materialCount
            )
        )

        message = "自分のデータを家族に追加しました"
    }

    private fun removeFamilyMember(index: Int) {
        val name = familyMembers[index].name
        familyMembers.removeAt(index)
        message = "${name}を削除しました"
    }

    private fun saveToFirebase() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            .format(System.currentTimeMillis())

        val ticketList = tickets.map {
            mapOf(
                "name" to it.name,
                "status" to it.status
            )
        }

        val taskList = tasks.map {
            mapOf(
                "title" to it.title,
                "completed" to it.completed
            )
        }

        val familyList = familyMembers.map {
            mapOf(
                "name" to it.name,
                "screenTime" to it.screenTime,
                "targetTime" to it.targetTime,
                "material" to it.material
            )
        }

        val data = hashMapOf(
            "date" to date,
            "screenTimeMinutes" to screenTimeMinutes,
            "targetMinutes" to targetMinutes,
            "materialCount" to materialCount,
            "tickets" to ticketList,
            "tasks" to taskList,
            "familyMembers" to familyList,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("families")
            .document("demoFamily")
            .collection("children")
            .document("testChild")
            .collection("dailyRecords")
            .document(date)
            .set(data)
            .addOnSuccessListener {
                message = "Firebaseに保存しました"
            }
            .addOnFailureListener {
                message = "Firebase保存に失敗しました"
            }
    }

    private fun loadFromFirebase() {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            .format(System.currentTimeMillis())

        db.collection("families")
            .document("demoFamily")
            .collection("children")
            .document("testChild")
            .collection("dailyRecords")
            .document(date)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    message = "今日のデータはまだ保存されていません"
                    return@addOnSuccessListener
                }

                screenTimeMinutes = document.getLong("screenTimeMinutes") ?: 0L
                targetMinutes = document.getLong("targetMinutes") ?: 120L
                materialCount = document.getLong("materialCount") ?: 0L
                targetInput = targetMinutes.toString()

                tickets.clear()
                tasks.clear()
                familyMembers.clear()

                val ticketList = document.get("tickets") as? List<*>
                ticketList?.forEach { item ->
                    val map = item as? Map<*, *>
                    val name = map?.get("name") as? String ?: return@forEach
                    val status = map["status"] as? String ?: "未申請"

                    tickets.add(
                        Ticket(
                            name = name,
                            status = status
                        )
                    )
                }

                val taskList = document.get("tasks") as? List<*>
                taskList?.forEach { item ->
                    val map = item as? Map<*, *>
                    val title = map?.get("title") as? String ?: return@forEach
                    val completed = map["completed"] as? Boolean ?: false

                    tasks.add(
                        DailyTask(
                            title = title,
                            completed = completed
                        )
                    )
                }

                val familyList = document.get("familyMembers") as? List<*>
                familyList?.forEach { item ->
                    val map = item as? Map<*, *>
                    val name = map?.get("name") as? String ?: return@forEach
                    val screenTime = toLongValue(map["screenTime"])
                    val targetTime = toLongValue(map["targetTime"])
                    val material = toLongValue(map["material"])

                    familyMembers.add(
                        FamilyMember(
                            name = name,
                            screenTime = screenTime,
                            targetTime = targetTime,
                            material = material
                        )
                    )
                }

                message = "Firebaseから読み込みました"
            }
            .addOnFailureListener {
                message = "Firebase読み込みに失敗しました"
            }
    }

    private fun toLongValue(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
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