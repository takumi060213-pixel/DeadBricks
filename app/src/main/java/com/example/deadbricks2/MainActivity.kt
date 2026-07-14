package com.example.deadbricks2

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Ticket(
    val name: String,
    val status: String
)

data class RewardTicketType(
    val name: String,
    val cost: Long
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

data class DailyRecord(
    val date: String,
    val screenTimeMinutes: Long,
    val targetMinutes: Long,
    val materialCount: Long,
    val ticketCount: Long
)

class MainActivity : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()

    private var currentScreen by mutableStateOf("home")

    private var screenTimeMinutes by mutableLongStateOf(0L)
    private var materialCount by mutableLongStateOf(0L)
    private var message by mutableStateOf("前日の使用時間を自動確認します")

    private var targetMinutes by mutableLongStateOf(60L)
    private var targetInput by mutableStateOf("60")
    private var isParentMode by mutableStateOf(false)

    private var taskInput by mutableStateOf("")

    private var familyNameInput by mutableStateOf("")
    private var familyScreenTimeInput by mutableStateOf("")
    private var familyTargetTimeInput by mutableStateOf("")
    private var familyMaterialInput by mutableStateOf("")

    private var rewardNameInput by mutableStateOf("")
    private var rewardCostInput by mutableStateOf("")

    private val tickets = mutableStateListOf<Ticket>()

    private val rewardTicketTypes = mutableStateListOf(
        RewardTicketType("おやつ豪華になる券", 5),
        RewardTicketType("お小遣い10円券", 10),
        RewardTicketType("背景変更券", 8)
    )

    private val tasks = mutableStateListOf(
        DailyTask("宿題をやる", false),
        DailyTask("漢字ドリル（10分）", false),
        DailyTask("計算ドリル（15分）", false),
        DailyTask("読書（15分）", false)
    )

    private val familyMembers = mutableStateListOf<FamilyMember>()
    private val historyRecords = mutableStateListOf<DailyRecord>()

    private val materialRate = 10L

    private val backgroundColor = Color(0xFFF7F4FF)
    private val purple = Color(0xFF8B6BE8)
    private val lightPurple = Color(0xFFECE5FF)
    private val green = Color(0xFF6DC56D)
    private val lightGreen = Color(0xFFE7F7E7)
    private val orange = Color(0xFFFFB74D)
    private val lightOrange = Color(0xFFFFF3DD)
    private val pink = Color(0xFFFF7AA2)
    private val lightPink = Color(0xFFFFE6EF)
    private val cardWhite = Color(0xFFFFFFFF)
    private val textDark = Color(0xFF333333)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Header()

                        Spacer(modifier = Modifier.height(12.dp))

                        when (currentScreen) {
                            "home" -> HomeScreen()
                            "craft" -> CraftScreen()
                            "task" -> TaskScreen()
                            "graph" -> GraphScreen()
                            "family" -> FamilyScreen()
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    BottomMenu()
                }
            }
        }

        prepareYesterdayRecordAutomatically()
    }

    override fun onResume() {
        super.onResume()
        prepareYesterdayRecordAutomatically()
    }

    @Composable
    private fun Header() {
        Column {
            Text(
                text = "デットブリックス",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = purple
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isParentMode) {
                    "保護者モード"
                } else {
                    "子供モード"
                },
                color = textDark
            )
        }
    }

    @Composable
    private fun BottomMenu() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuButton("👨‍👩‍👧", "コミュニティ", "family")
                MenuButton("🏠", "ホーム", "home")
                MenuButton("📊", "グラフ", "graph")
            }
        }
    }

    @Composable
    private fun MenuButton(icon: String, label: String, screen: String) {
        val selected = currentScreen == screen

        Button(
            onClick = {
                currentScreen = screen

                if (screen == "graph") {
                    loadHistoryFromFirebase()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) purple else lightPurple,
                contentColor = if (selected) Color.White else purple
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = "$icon $label")
        }
    }

    @Composable
    private fun HomeScreen() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F4FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getAnimalFace(),
                    fontSize = 80.sp
                )

                Text(
                    text = "おはよう！",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Text(
                    text = getAnimalMessage(),
                    color = textDark
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "前日の使用時間",
                    fontSize = 16.sp,
                    color = textDark
                )

                Text(
                    text = "${screenTimeMinutes}分",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Text(
                    text = "目標 ${targetMinutes}分",
                    color = textDark
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = lightOrange)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧱",
                    fontSize = 34.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "前日の結果でもらえる素材",
                        color = textDark
                    )

                    Text(
                        text = "${materialCount}個",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = orange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "今日やること",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = lightGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "✅", fontSize = 42.sp)

                    Text(
                        text = "タスク",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = green
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { currentScreen = "task" },
                        colors = ButtonDefaults.buttonColors(containerColor = green),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("開く")
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = lightPurple),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎫", fontSize = 42.sp)

                    Text(
                        text = "クラフト",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = purple
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { currentScreen = "craft" },
                        colors = ButtonDefaults.buttonColors(containerColor = purple),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("開く")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    isParentMode = false
                    message = "子供モードにしました"
                },
                colors = ButtonDefaults.buttonColors(containerColor = purple),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("子供画面")
            }

            Button(
                onClick = {
                    isParentMode = true
                    message = "保護者モードにしました"
                },
                colors = ButtonDefaults.buttonColors(containerColor = pink),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("親画面")
            }
        }

        if (isParentMode) {
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = lightPink)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "目標設定",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = pink
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = pink),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("目標を変更")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { openUsageAccessSettings() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = purple),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("使用状況アクセスを許可する")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { prepareYesterdayRecordAutomatically() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = green),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("前日データを自動確認")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun CraftScreen() {
        Text(
            text = "クラフト",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = lightOrange)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🧱", fontSize = 32.sp)

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "もっている素材：${materialCount}個",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "報酬チケット追加",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardNameInput,
                    onValueChange = { rewardNameInput = it },
                    label = { Text("チケット名") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardCostInput,
                    onValueChange = { rewardCostInput = it },
                    label = { Text("必要素材数") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addRewardTicketType() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("報酬チケットを追加")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "作れるチケット",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        rewardTicketTypes.forEachIndexed { index, reward ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = cardWhite)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎫", fontSize = 34.sp)

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = reward.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = "必要素材：${reward.cost}個",
                                color = textDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { craftTicket(reward.name, reward.cost) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("クラフトする")
                    }

                    if (isParentMode) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { removeRewardTicketType(index) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = pink),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("この報酬を削除")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "所持チケット",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (tickets.isEmpty()) {
            Text("まだチケットはありません", color = textDark)
        } else {
            tickets.forEachIndexed { index, ticket ->
                TicketCard(index, ticket)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun TicketCard(index: Int, ticket: Ticket) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = lightPurple)
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "🎟 ${ticket.name}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Text(
                    text = "状態：${ticket.status}",
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { requestApproval(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("親に承認申請する")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { approveTicket(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("親が承認する")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { useTicket(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("チケットを使う")
                }
            }
        }
    }

    @Composable
    private fun TaskScreen() {
        Text(
            text = "やること",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    label = { Text("追加するタスク") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addTask() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("＋ やることを追加")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        tasks.forEachIndexed { index, task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (task.completed) lightGreen else cardWhite
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = {
                            toggleTask(index)
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = task.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )

                        Text(
                            text = if (task.completed) "完了" else "未完了",
                            color = if (task.completed) green else textDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun GraphScreen() {
        Text(
            text = "ぼくのグラフ",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "自動保存された前日データを表示します",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "前日の使用時間：${screenTimeMinutes}分",
                    color = textDark
                )

                Text(
                    text = "素材：${materialCount}個",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { loadHistoryFromFirebase() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("グラフを更新")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "保存済みデータの平均",
                    color = textDark
                )

                val average = if (historyRecords.isEmpty()) {
                    0L
                } else {
                    historyRecords.map { it.screenTimeMinutes }.average().toLong()
                }

                Text(
                    text = if (historyRecords.isEmpty()) {
                        "--分"
                    } else {
                        "${average}分"
                    },
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (historyRecords.isEmpty()) {
                    Text(
                        text = "まだ自動保存された履歴がありません",
                        color = textDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "アプリを起動すると、前日分が未保存の場合は自動で保存されます",
                        color = textDark
                    )
                } else {
                    val records = historyRecords.reversed()
                    val maxMinutes = records
                        .maxOfOrNull { it.screenTimeMinutes }
                        ?.coerceAtLeast(1L) ?: 1L

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        records.forEach { record ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                val barHeight = ((record.screenTimeMinutes.toDouble() / maxMinutes.toDouble()) * 150.0)
                                    .toInt()
                                    .coerceAtLeast(8)

                                Text(
                                    text = "${record.screenTimeMinutes}分",
                                    fontSize = 11.sp,
                                    color = textDark
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(barHeight.dp)
                                        .background(
                                            purple,
                                            RoundedCornerShape(10.dp)
                                        )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = record.date.takeLast(5),
                                    fontSize = 11.sp,
                                    color = textDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (historyRecords.isNotEmpty()) {
            historyRecords.forEach { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = lightPurple)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("日付：${record.date}", color = textDark)
                        Text("使用時間：${record.screenTimeMinutes}分", color = textDark)
                        Text("目標時間：${record.targetMinutes}分", color = textDark)
                        Text("素材：${record.materialCount}個", color = textDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun FamilyScreen() {
        Text(
            text = "コミュニティ",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
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
                    label = { Text("前日の使用時間（分）") },
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("家族を追加")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addMyDataToFamily() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("自分の前日データを追加")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (familyMembers.isEmpty()) {
            Text("まだ家族は登録されていません", color = textDark)
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
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = lightPurple)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (index % 2 == 0) "👧" else "👦",
                            fontSize = 36.sp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = member.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = "前日の使用時間　${member.screenTime}分",
                                color = textDark
                            )

                            Text(
                                text = "達成率　${achievementRate}%",
                                color = purple
                            )
                        }

                        Button(
                            onClick = { removeFamilyMember(index) },
                            colors = ButtonDefaults.buttonColors(containerColor = pink),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("削除")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun prepareYesterdayRecordAutomatically() {
        if (!hasUsageStatsPermission()) {
            message = "使用状況アクセスを許可してください"
            return
        }

        val date = getYesterdayDateString()

        db.collection("families")
            .document("demoFamily")
            .collection("children")
            .document("testChild")
            .collection("dailyRecords")
            .document(date)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    loadRecordIntoState(document)
                    message = "前日分は自動保存済みです"
                    loadHistoryFromFirebase(false)
                } else {
                    val minutes = getYesterdayScreenTimeMinutes()
                    val material = calculateMaterial(minutes)

                    screenTimeMinutes = minutes
                    materialCount = material

                    saveCurrentStateToFirebase(
                        showMessage = true,
                        successMessage = "日付が変わったため、前日分を自動保存しました"
                    )
                }
            }
            .addOnFailureListener {
                message = "前日分の自動確認に失敗しました"
            }
    }

    private fun updateTargetTime() {
        val newTarget = targetInput.toLongOrNull()

        if (newTarget == null || newTarget <= 0) {
            message = "正しい目標時間を入力してください"
            return
        }

        targetMinutes = newTarget

        if (hasUsageStatsPermission()) {
            val minutes = getYesterdayScreenTimeMinutes()
            screenTimeMinutes = minutes
            materialCount = calculateMaterial(minutes)
        }

        saveCurrentStateToFirebase(
            showMessage = true,
            successMessage = "目標時間を${targetMinutes}分に変更しました"
        )
    }

    private fun calculateMaterial(minutes: Long): Long {
        val savedMinutes = targetMinutes - minutes

        return if (savedMinutes > 0) {
            savedMinutes / materialRate
        } else {
            0
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
            materialCount >= 10 -> "前日はたくさんスマホを我慢できたね！"
            materialCount >= 5 -> "いい感じにがんばれています"
            materialCount >= 1 -> "少しだけ素材をゲットできました"
            else -> "今日は素材を集められるようにがんばろう"
        }
    }

    private fun addRewardTicketType() {
        val name = rewardNameInput
        val cost = rewardCostInput.toLongOrNull()

        if (name.isBlank()) {
            message = "チケット名を入力してください"
            return
        }

        if (cost == null || cost <= 0) {
            message = "必要素材数を正しく入力してください"
            return
        }

        rewardTicketTypes.add(
            RewardTicketType(
                name = name,
                cost = cost
            )
        )

        rewardNameInput = ""
        rewardCostInput = ""

        message = "${name}を報酬チケットに追加しました"
        autoSaveCurrentStateSilently()
    }

    private fun removeRewardTicketType(index: Int) {
        val name = rewardTicketTypes[index].name
        rewardTicketTypes.removeAt(index)
        message = "${name}を削除しました"
        autoSaveCurrentStateSilently()
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
            autoSaveCurrentStateSilently()
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
        autoSaveCurrentStateSilently()
    }

    private fun approveTicket(index: Int) {
        val ticket = tickets[index]

        tickets[index] = ticket.copy(
            status = "承認済み"
        )

        message = "${ticket.name}が承認されました"
        autoSaveCurrentStateSilently()
    }

    private fun useTicket(index: Int) {
        val ticket = tickets[index]

        if (ticket.status != "承認済み") {
            message = "承認済みのチケットだけ使用できます"
            return
        }

        tickets[index] = ticket.copy(
            status = "使用済み"
        )

        message = "${ticket.name}を使用済みにしました"
        autoSaveCurrentStateSilently()
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
            autoSaveCurrentStateSilently()
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

        autoSaveCurrentStateSilently()
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
        autoSaveCurrentStateSilently()
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

        message = "自分の前日データを家族に追加しました"
        autoSaveCurrentStateSilently()
    }

    private fun removeFamilyMember(index: Int) {
        val name = familyMembers[index].name
        familyMembers.removeAt(index)
        message = "${name}を削除しました"
        autoSaveCurrentStateSilently()
    }

    private fun autoSaveCurrentStateSilently() {
        saveCurrentStateToFirebase(
            showMessage = false,
            successMessage = ""
        )
    }

    private fun saveCurrentStateToFirebase(
        showMessage: Boolean,
        successMessage: String
    ) {
        val date = getYesterdayDateString()

        val ticketList = tickets.map {
            mapOf(
                "name" to it.name,
                "status" to it.status
            )
        }

        val rewardList = rewardTicketTypes.map {
            mapOf(
                "name" to it.name,
                "cost" to it.cost
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
            "rewardTicketTypes" to rewardList,
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
                if (showMessage) {
                    message = successMessage
                }

                loadHistoryFromFirebase(false)
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "自動保存に失敗しました"
                }
            }
    }

    private fun loadRecordIntoState(document: DocumentSnapshot) {
        screenTimeMinutes = document.getLong("screenTimeMinutes") ?: 0L
        targetMinutes = document.getLong("targetMinutes") ?: 60L
        materialCount = document.getLong("materialCount") ?: 0L
        targetInput = targetMinutes.toString()

        tickets.clear()
        rewardTicketTypes.clear()
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

        val rewardList = document.get("rewardTicketTypes") as? List<*>
        rewardList?.forEach { item ->
            val map = item as? Map<*, *>
            val name = map?.get("name") as? String ?: return@forEach
            val cost = toLongValue(map["cost"])

            rewardTicketTypes.add(
                RewardTicketType(
                    name = name,
                    cost = cost
                )
            )
        }

        if (rewardTicketTypes.isEmpty()) {
            rewardTicketTypes.add(RewardTicketType("おやつ豪華になる券", 5))
            rewardTicketTypes.add(RewardTicketType("お小遣い10円券", 10))
            rewardTicketTypes.add(RewardTicketType("背景変更券", 8))
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

        if (tasks.isEmpty()) {
            tasks.add(DailyTask("宿題をやる", false))
            tasks.add(DailyTask("漢字ドリル（10分）", false))
            tasks.add(DailyTask("計算ドリル（15分）", false))
            tasks.add(DailyTask("読書（15分）", false))
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
    }

    private fun loadHistoryFromFirebase(showMessage: Boolean = true) {
        historyRecords.clear()

        if (showMessage) {
            message = "履歴を読み込み中です"
        }

        db.collection("families")
            .document("demoFamily")
            .collection("children")
            .document("testChild")
            .collection("dailyRecords")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(7)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    if (showMessage) {
                        message = "保存された履歴がありません"
                    }
                    return@addOnSuccessListener
                }

                for (document in result.documents) {
                    val date = document.getString("date") ?: document.id
                    val screenTime = document.getLong("screenTimeMinutes") ?: 0L
                    val targetTime = document.getLong("targetMinutes") ?: 0L
                    val material = document.getLong("materialCount") ?: 0L

                    val ticketList = document.get("tickets") as? List<*>
                    val ticketCount = ticketList?.size?.toLong() ?: 0L

                    historyRecords.add(
                        DailyRecord(
                            date = date,
                            screenTimeMinutes = screenTime,
                            targetMinutes = targetTime,
                            materialCount = material,
                            ticketCount = ticketCount
                        )
                    )
                }

                if (showMessage) {
                    message = "履歴を読み込みました"
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "履歴読み込みに失敗しました"
                }
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

    private fun getYesterdayScreenTimeMinutes(): Long {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val endTime = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, -1)

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

    private fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)

        return SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            .format(calendar.time)
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