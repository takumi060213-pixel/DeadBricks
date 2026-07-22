package com.example.deadbricks2

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

data class DailyRecord(
    val date: String,
    val screenTimeMinutes: Long,
    val targetMinutes: Long,
    val materialCount: Long,
    val ticketCount: Long
)

data class RoomMember(
    val memberId: String,
    val memberName: String,
    val role: String,
    val screenTimeMinutes: Long,
    val targetMinutes: Long,
    val materialCount: Long,
    val lastUpdatedDate: String
)

class MainActivity : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences

    private var currentScreen by mutableStateOf("home")

    private var screenTimeMinutes by mutableLongStateOf(0L)
    private var materialCount by mutableLongStateOf(0L)
    private var message by mutableStateOf("前日の使用時間を自動確認します")

    private var targetMinutes by mutableLongStateOf(60L)
    private var targetInput by mutableStateOf("60")
    private var isParentMode by mutableStateOf(false)

    private var roomIdInput by mutableStateOf("room001")
    private var inviteCodeInput by mutableStateOf("")
    private var memberIdInput by mutableStateOf("member001")
    private var memberNameInput by mutableStateOf("たろう")
    private var memberRole by mutableStateOf("子供")
    private var isInFamilyRoom by mutableStateOf(false)

    private var taskInput by mutableStateOf("")

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

    private val historyRecords = mutableStateListOf<DailyRecord>()
    private val roomMembers = mutableStateListOf<RoomMember>()

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

        prefs = getSharedPreferences("deadbricks_settings", Context.MODE_PRIVATE)
        loadCommunitySettings()

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
                    "親画面"
                } else {
                    "子供画面"
                },
                color = textDark
            )

            if (isInFamilyRoom) {
                Text(
                    text = "参加中のルーム：${getRoomId()}",
                    color = textDark
                )
            }
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
                    prepareYesterdayRecordAutomatically()
                }

                if (screen == "family") {
                    loadRoomMembers()
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
                    message = "子供画面にしました"
                },
                colors = ButtonDefaults.buttonColors(containerColor = purple),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("子供画面")
            }

            Button(
                onClick = {
                    isParentMode = true
                    message = "親画面にしました"
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
            Text("前日データを再取得")
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
                    text = "端末から前日データを再取得して表示します",
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
                    onClick = { prepareYesterdayRecordAutomatically() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("端末から再取得してグラフ更新")
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
                        text = "家族ルームに参加すると履歴が保存されます",
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
            text = "家族コミュニティ",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    isParentMode = true
                    memberRole = "保護者"
                    message = "親画面にしました"
                },
                colors = ButtonDefaults.buttonColors(containerColor = pink),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("親画面")
            }

            Button(
                onClick = {
                    isParentMode = false
                    memberRole = "子供"
                    message = "子供画面にしました"
                },
                colors = ButtonDefaults.buttonColors(containerColor = purple),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("子供画面")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isParentMode) {
            ParentRoomCard()
        } else {
            ChildJoinRoomCard()
        }

        Spacer(modifier = Modifier.height(16.dp))

        CurrentRoomCard()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ルームメンバー",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (roomMembers.isEmpty()) {
            Text(
                text = "まだこのルームに家族がいません",
                color = textDark
            )
        } else {
            roomMembers.forEachIndexed { index, member ->
                val achievementText = if (member.screenTimeMinutes <= member.targetMinutes) {
                    "目標達成"
                } else {
                    "目標超過"
                }

                val achievementColor = if (member.screenTimeMinutes <= member.targetMinutes) {
                    green
                } else {
                    pink
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
                            text = if (member.role == "保護者") "👨‍👩‍👧" else if (index % 2 == 0) "👧" else "👦",
                            fontSize = 38.sp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = member.memberName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = "メンバーID：${member.memberId}",
                                color = textDark
                            )

                            Text(
                                text = "区分：${member.role}",
                                color = textDark
                            )

                            Text(
                                text = "前日の使用時間：${member.screenTimeMinutes}分",
                                color = textDark
                            )

                            Text(
                                text = "目標：${member.targetMinutes}分",
                                color = textDark
                            )

                            Text(
                                text = "素材：${member.materialCount}個",
                                color = textDark
                            )

                            Text(
                                text = "更新日：${member.lastUpdatedDate}",
                                color = textDark
                            )

                            Text(
                                text = achievementText,
                                fontWeight = FontWeight.Bold,
                                color = achievementColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun ParentRoomCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = lightPink)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "親：家族ルームを作成",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = pink
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "親がルームIDと招待コードを発行します。子供はこの2つを入力しないと参加できません。",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = { roomIdInput = it },
                    label = { Text("作成する家族ルームID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = { inviteCodeInput = it },
                    label = { Text("招待コード（空なら自動発行）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberIdInput,
                    onValueChange = { memberIdInput = it },
                    label = { Text("親のメンバーID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberNameInput,
                    onValueChange = { memberNameInput = it },
                    label = { Text("親の名前") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { createFamilyRoom() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = pink),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("家族ルームを作成する")
                }
            }
        }
    }

    @Composable
    private fun ChildJoinRoomCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "子供：家族ルームに参加",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "親から教えてもらったルームIDと招待コードを入力してください。",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = { roomIdInput = it },
                    label = { Text("家族ルームID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = { inviteCodeInput = it },
                    label = { Text("招待コード") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberIdInput,
                    onValueChange = { memberIdInput = it },
                    label = { Text("自分のメンバーID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberNameInput,
                    onValueChange = { memberNameInput = it },
                    label = { Text("自分の名前") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { joinFamilyRoom() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("家族ルームに参加する")
                }
            }
        }
    }

    @Composable
    private fun CurrentRoomCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = lightOrange)
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "現在のコミュニティ設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = orange
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isInFamilyRoom) {
                        "参加中"
                    } else {
                        "未参加"
                    },
                    color = textDark
                )

                Text(
                    text = "ルームID：${getRoomId()}",
                    color = textDark
                )

                Text(
                    text = "メンバーID：${getMemberId()}",
                    color = textDark
                )

                Text(
                    text = "名前：${getMemberName()}",
                    color = textDark
                )

                Text(
                    text = "区分：${getRoleName()}",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { loadRoomMembers() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("ルームメンバーを更新")
                }
            }
        }
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun createFamilyRoom() {
        val roomId = getRoomId()
        val memberId = getMemberId()
        val memberName = getMemberName()

        if (roomId.isBlank()) {
            message = "家族ルームIDを入力してください"
            return
        }

        if (memberId.isBlank()) {
            message = "親のメンバーIDを入力してください"
            return
        }

        var code = inviteCodeInput.trim()

        if (code.isBlank()) {
            code = generateInviteCode()
            inviteCodeInput = code
        }

        isParentMode = true
        memberRole = "保護者"
        isInFamilyRoom = true
        saveCommunitySettings()

        val roomData = hashMapOf(
            "roomId" to roomId,
            "inviteCode" to code,
            "createdByMemberId" to memberId,
            "createdByName" to memberName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("familyRooms")
            .document(roomId)
            .set(roomData)
            .addOnSuccessListener {
                if (hasUsageStatsPermission()) {
                    val minutes = getYesterdayScreenTimeMinutes()
                    screenTimeMinutes = minutes
                    materialCount = calculateMaterial(minutes)
                }

                saveCurrentStateToFirebase(
                    showMessage = true,
                    successMessage = "家族ルームを作成しました。招待コードは ${code} です"
                )
            }
            .addOnFailureListener {
                message = "家族ルームの作成に失敗しました"
            }
    }

    private fun joinFamilyRoom() {
        val roomId = getRoomId()
        val memberId = getMemberId()
        val code = inviteCodeInput.trim()

        if (roomId.isBlank()) {
            message = "家族ルームIDを入力してください"
            return
        }

        if (code.isBlank()) {
            message = "招待コードを入力してください"
            return
        }

        if (memberId.isBlank()) {
            message = "メンバーIDを入力してください"
            return
        }

        db.collection("familyRooms")
            .document(roomId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    message = "その家族ルームは存在しません"
                    return@addOnSuccessListener
                }

                val correctCode = document.getString("inviteCode") ?: ""

                if (correctCode != code) {
                    message = "招待コードが違うため参加できません"
                    return@addOnSuccessListener
                }

                isParentMode = false
                memberRole = "子供"
                isInFamilyRoom = true
                saveCommunitySettings()

                if (hasUsageStatsPermission()) {
                    val minutes = getYesterdayScreenTimeMinutes()
                    screenTimeMinutes = minutes
                    materialCount = calculateMaterial(minutes)
                }

                saveCurrentStateToFirebase(
                    showMessage = true,
                    successMessage = "${getMemberName()}が家族ルームに参加しました"
                )
            }
            .addOnFailureListener {
                message = "家族ルームの確認に失敗しました"
            }
    }

    private fun prepareYesterdayRecordAutomatically() {
        if (!hasUsageStatsPermission()) {
            message = "使用状況アクセスを許可してください"
            return
        }

        val minutes = getYesterdayScreenTimeMinutes()

        screenTimeMinutes = minutes
        materialCount = calculateMaterial(minutes)

        if (!isInFamilyRoom) {
            historyRecords.clear()
            message = "端末から前日の使用時間を再取得しました：${minutes}分"
            return
        }

        saveCurrentStateToFirebase(
            showMessage = true,
            successMessage = "端末から前日の使用時間を再取得して保存しました：${minutes}分"
        )
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

        if (isInFamilyRoom) {
            saveCurrentStateToFirebase(
                showMessage = true,
                successMessage = "目標時間を${targetMinutes}分に変更しました"
            )
        } else {
            message = "目標時間を${targetMinutes}分に変更しました"
        }
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
        tickets[index] = ticket.copy(status = "承認待ち")
        message = "${ticket.name}を親に承認申請しました"
        autoSaveCurrentStateSilently()
    }

    private fun approveTicket(index: Int) {
        val ticket = tickets[index]
        tickets[index] = ticket.copy(status = "承認済み")
        message = "${ticket.name}が承認されました"
        autoSaveCurrentStateSilently()
    }

    private fun useTicket(index: Int) {
        val ticket = tickets[index]

        if (ticket.status != "承認済み") {
            message = "承認済みのチケットだけ使用できます"
            return
        }

        tickets[index] = ticket.copy(status = "使用済み")
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
        tasks[index] = task.copy(completed = !task.completed)

        message = if (!task.completed) {
            "${task.title}を完了しました"
        } else {
            "${task.title}を未完了に戻しました"
        }

        autoSaveCurrentStateSilently()
    }

    private fun autoSaveCurrentStateSilently() {
        if (!isInFamilyRoom) {
            return
        }

        saveCurrentStateToFirebase(
            showMessage = false,
            successMessage = ""
        )
    }

    private fun saveCurrentStateToFirebase(
        showMessage: Boolean,
        successMessage: String
    ) {
        if (!isInFamilyRoom) {
            if (showMessage) {
                message = "家族ルームに参加していないため保存できません"
            }
            return
        }

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

        val memberData = hashMapOf(
            "roomId" to getRoomId(),
            "memberId" to getMemberId(),
            "memberName" to getMemberName(),
            "role" to getRoleName(),
            "screenTimeMinutes" to screenTimeMinutes,
            "targetMinutes" to targetMinutes,
            "materialCount" to materialCount,
            "lastUpdatedDate" to date,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val dailyData = hashMapOf(
            "date" to date,
            "roomId" to getRoomId(),
            "memberId" to getMemberId(),
            "memberName" to getMemberName(),
            "role" to getRoleName(),
            "screenTimeMinutes" to screenTimeMinutes,
            "targetMinutes" to targetMinutes,
            "materialCount" to materialCount,
            "tickets" to ticketList,
            "rewardTicketTypes" to rewardList,
            "tasks" to taskList,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val memberRef = db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(getMemberId())

        memberRef
            .set(memberData)
            .addOnSuccessListener {
                memberRef
                    .collection("dailyRecords")
                    .document(date)
                    .set(dailyData)
                    .addOnSuccessListener {
                        if (showMessage) {
                            message = successMessage
                        }

                        loadHistoryFromFirebase(false)
                        loadRoomMembers(false)
                    }
                    .addOnFailureListener {
                        if (showMessage) {
                            message = "前日データの保存に失敗しました"
                        }
                    }
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "ルームメンバー情報の保存に失敗しました"
                }
            }
    }

    private fun loadRecordIntoState(document: DocumentSnapshot) {
        screenTimeMinutes = document.getLong("screenTimeMinutes") ?: 0L
        targetMinutes = document.getLong("targetMinutes") ?: 60L
        materialCount = document.getLong("materialCount") ?: 0L
        targetInput = targetMinutes.toString()

        val savedMemberName = document.getString("memberName")
        if (!savedMemberName.isNullOrBlank()) {
            memberNameInput = savedMemberName
        }

        tickets.clear()
        rewardTicketTypes.clear()
        tasks.clear()

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
    }

    private fun loadHistoryFromFirebase(showMessage: Boolean = true) {
        historyRecords.clear()

        if (!isInFamilyRoom) {
            if (showMessage) {
                message = "家族ルームに参加するとグラフ履歴が表示されます"
            }
            return
        }

        if (showMessage) {
            message = "履歴を読み込み中です"
        }

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(getMemberId())
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

    private fun loadRoomMembers(showMessage: Boolean = true) {
        roomMembers.clear()

        if (!isInFamilyRoom) {
            if (showMessage) {
                message = "家族ルームに参加していません"
            }
            return
        }

        if (showMessage) {
            message = "ルームメンバーを読み込み中です"
        }

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    if (showMessage) {
                        message = "このルームにはまだメンバーがいません"
                    }
                    return@addOnSuccessListener
                }

                for (document in result.documents) {
                    val member = RoomMember(
                        memberId = document.getString("memberId") ?: document.id,
                        memberName = document.getString("memberName") ?: "名前なし",
                        role = document.getString("role") ?: "メンバー",
                        screenTimeMinutes = document.getLong("screenTimeMinutes") ?: 0L,
                        targetMinutes = document.getLong("targetMinutes") ?: 0L,
                        materialCount = document.getLong("materialCount") ?: 0L,
                        lastUpdatedDate = document.getString("lastUpdatedDate") ?: "-"
                    )

                    roomMembers.add(member)
                }

                if (showMessage) {
                    message = "ルームメンバーを読み込みました"
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "ルームメンバーの読み込みに失敗しました"
                }
            }
    }

    private fun saveCommunitySettings() {
        prefs.edit()
            .putString("roomId", roomIdInput)
            .putString("inviteCode", inviteCodeInput)
            .putString("memberId", memberIdInput)
            .putString("memberName", memberNameInput)
            .putString("memberRole", memberRole)
            .putBoolean("isInFamilyRoom", isInFamilyRoom)
            .apply()
    }

    private fun loadCommunitySettings() {
        roomIdInput = prefs.getString("roomId", "room001") ?: "room001"
        inviteCodeInput = prefs.getString("inviteCode", "") ?: ""
        memberIdInput = prefs.getString("memberId", "member001") ?: "member001"
        memberNameInput = prefs.getString("memberName", "たろう") ?: "たろう"
        memberRole = prefs.getString("memberRole", "子供") ?: "子供"
        isInFamilyRoom = prefs.getBoolean("isInFamilyRoom", false)
        isParentMode = memberRole == "保護者"
    }

    private fun generateInviteCode(): String {
        val number = (System.currentTimeMillis() % 9000L) + 1000L
        return number.toString()
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

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

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
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    currentPackageName = packageName
                    currentStartTime = event.timeStamp
                }

                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND,
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
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
        if (packageName == this.packageName) {
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

    private fun getRoomId(): String {
        return roomIdInput.trim()
    }

    private fun getMemberId(): String {
        return memberIdInput.trim()
    }

    private fun getMemberName(): String {
        return memberNameInput.ifBlank {
            "名前なし"
        }
    }

    private fun getRoleName(): String {
        return memberRole
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