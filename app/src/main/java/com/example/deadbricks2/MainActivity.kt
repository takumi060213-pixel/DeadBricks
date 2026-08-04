package com.example.deadbricks2

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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

data class ChildStatus(
    val memberId: String,
    val memberName: String,
    val screenTimeMinutes: Long,
    val targetMinutes: Long,
    val materialCount: Long,
    val lastUpdatedDate: String,
    val recordDate: String,
    val tickets: List<Ticket>,
    val tasks: List<DailyTask>
)

class MainActivity : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var prefs: SharedPreferences

    private var hasSelectedRole by mutableStateOf(false)
    private var currentScreen by mutableStateOf("home")

    private var screenTimeMinutes by mutableLongStateOf(0L)
    private var materialCount by mutableLongStateOf(0L)
    private var message by mutableStateOf("がめんをえらんでください")

    private var lastSavedDate by mutableStateOf("")
    private var lastSavedScreenTimeMinutes by mutableLongStateOf(0L)
    private var lastSavedMaterialCount by mutableLongStateOf(0L)
    private var lastMaterialRewardDate by mutableStateOf("")

    private var targetMinutes by mutableLongStateOf(60L)
    private var targetInput by mutableStateOf("60")
    private var isParentMode by mutableStateOf(false)

    private var roomIdInput by mutableStateOf("")
    private var inviteCodeInput by mutableStateOf("")
    private var memberIdInput by mutableStateOf("")
    private var memberNameInput by mutableStateOf("たろう")
    private var memberRole by mutableStateOf("子供")
    private var isInFamilyRoom by mutableStateOf(false)

    private var taskInput by mutableStateOf("")
    private var rewardNameInput by mutableStateOf("")
    private var rewardCostInput by mutableStateOf("")

    private val tickets = mutableStateListOf<Ticket>()

    private val rewardTicketTypes = mutableStateListOf(
        RewardTicketType("おやつがふえるチケット", 5),
        RewardTicketType("おこづかい10えんチケット", 10),
        RewardTicketType("きせかえチケット", 8)
    )

    private val tasks = mutableStateListOf(
        DailyTask("しゅくだいをする", false),
        DailyTask("かんじドリルをする", false),
        DailyTask("けいさんドリルをする", false),
        DailyTask("ほんをよむ", false)
    )

    private val historyRecords = mutableStateListOf<DailyRecord>()
    private val roomMembers = mutableStateListOf<RoomMember>()
    private val childStatusList = mutableStateListOf<ChildStatus>()

    private val materialRate = 1L

    private val backgroundColor = Color(0xFFF2F8E8)
    private val purple = Color(0xFF6D4FD8)
    private val lightPurple = Color(0xFFECE5FF)
    private val green = Color(0xFF75B843)
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
        loadLastAutoSavedRecordFromLocal()

        DailyScreenTimeWorker.scheduleNextDailySave(this)

        setContent {
            MaterialTheme {
                if (!hasSelectedRole) {
                    RoleSelectScreen()
                } else {
                    MainAppScreen()
                }
            }
        }

        if (hasSelectedRole) {
            if (isInFamilyRoom) {
                loadRoomSettingsFromFirebase(showMessage = false)
            }

            if (isParentMode) {
                loadParentChildStatuses()
            } else {
                prepareYesterdayRecordAutomatically()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasSelectedRole) {
            loadLastAutoSavedRecordFromLocal()
            DailyScreenTimeWorker.scheduleNextDailySave(this)

            if (isInFamilyRoom) {
                loadRoomSettingsFromFirebase(showMessage = false)
            }

            if (isParentMode) {
                loadParentChildStatuses()
            } else {
                prepareYesterdayRecordAutomatically()
            }
        }
    }

    @Composable
    private fun RoleSelectScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "デットブリックス",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = purple
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "つかうがめんをえらんでください",
                fontSize = 19.sp,
                color = textDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { openUsageAccessSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = green),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "スマホのじかんをみられるようにする",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = lightPink),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "👨‍👩‍👧", fontSize = 56.sp)

                    Text(
                        text = "おやのがめん",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = pink
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "へやをつくったり、こどものじょうきょうをみます",
                        color = textDark,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { selectParentMode() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = pink),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("おやではじめる")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = lightPurple),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🐼", fontSize = 56.sp)

                    Text(
                        text = "こどものがめん",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = purple
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "そざいをあつめて、チケットをつくります",
                        color = textDark,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { selectChildMode() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = purple),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("こどもではじめる")
                    }
                }
            }
        }
    }

    private fun selectParentMode() {
        hasSelectedRole = true
        isParentMode = true
        memberRole = "保護者"
        message = "親画面にしました"
        saveCommunitySettings()

        if (isInFamilyRoom) {
            loadRoomSettingsFromFirebase(showMessage = false)
        }

        loadParentChildStatuses()
    }

    private fun selectChildMode() {
        hasSelectedRole = true
        isParentMode = false
        memberRole = "子供"
        message = "こどものがめんにしたよ"
        saveCommunitySettings()

        if (isInFamilyRoom) {
            loadRoomSettingsFromFirebase(showMessage = false)
        }

        if (hasUsageStatsPermission()) {
            prepareYesterdayRecordAutomatically()
        }
    }

    private fun backToRoleSelectScreen() {
        hasSelectedRole = false
        currentScreen = "home"
        message = "がめんをえらんでください"

        prefs.edit()
            .putBoolean("hasSelectedRole", false)
            .apply()
    }

    @Composable
    private fun MainAppScreen() {
        if (currentScreen == "home") {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                HomeScreen()

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    BottomMenu()
                }
            }
        } else {
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
                text = uiText("親画面", "こどものがめん"),
                color = textDark
            )

            if (isInFamilyRoom) {
                Text(
                    text = uiText(
                        "参加中のルーム：${getRoomId()}",
                        "はいっているへや：${getRoomId()}"
                    ),
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
                MenuButton("👨‍👩‍👧", uiText("コミュニティ", "かぞく"), "family")
                MenuButton("🏠", uiText("ホーム", "ホーム"), "home")
                MenuButton("📊", uiText("グラフ", "ぐらふ"), "graph")
            }
        }
    }

    @Composable
    private fun MenuButton(icon: String, label: String, screen: String) {
        val selected = currentScreen == screen

        Button(
            onClick = {
                currentScreen = screen

                if (isInFamilyRoom) {
                    loadRoomSettingsFromFirebase(showMessage = false)
                }

                if (screen == "home" && isParentMode) {
                    loadParentChildStatuses()
                }

                if (screen == "graph") {
                    loadLastAutoSavedRecordFromLocal()

                    if (!isParentMode) {
                        refreshLast7DaysUsageToFirebase()
                    } else {
                        loadHistoryFromFirebase()
                    }
                }

                if (screen == "family") {
                    loadRoomMembers()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) purple else Color.White,
                contentColor = if (selected) Color.White else textDark
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(text = "$icon $label")
        }
    }

    @Composable
    private fun HomeScreen() {
        if (isParentMode) {
            ParentHomeScreen()
        } else {
            ChildHomeScreen()
        }
    }

    @Composable
    private fun ParentHomeScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 102.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { backToRoleSelectScreen() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = purple
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "＜",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "親ホーム",
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.width(58.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = lightPink),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "目標時間の設定",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = pink
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it },
                            label = { Text("目標時間（分）") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { updateTargetTime() },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = pink),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("変更")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { loadParentChildStatuses() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = purple),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("子供の状況を更新")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                ParentRewardSettingCard()

                Spacer(modifier = Modifier.height(10.dp))

                ParentTaskSettingCard()

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "子供の状況",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (childStatusList.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cardWhite)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "まだ子供のデータがありません",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "家族ルームに子供が参加して、前日データが保存されると表示されます。",
                                color = textDark
                            )
                        }
                    }
                } else {
                    childStatusList.forEachIndexed { childIndex, child ->
                        ParentChildStatusCard(
                            childIndex = childIndex,
                            child = child
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = textDark
                )
            }
        }
    }

    @Composable
    private fun ParentRewardSettingCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = lightOrange),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "チケット設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = orange
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardNameInput,
                    onValueChange = { rewardNameInput = it },
                    label = { Text("チケット名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardCostInput,
                    onValueChange = { rewardCostInput = it },
                    label = { Text("必要素材数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addRewardTicketType() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("チケットを追加")
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (rewardTicketTypes.isEmpty()) {
                    Text(
                        text = "まだチケット設定はありません",
                        color = textDark
                    )
                } else {
                    rewardTicketTypes.forEachIndexed { index, reward ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardWhite)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🎫 ${reward.name}",
                                    fontWeight = FontWeight.Bold,
                                    color = textDark
                                )

                                Text(
                                    text = "必要素材：${reward.cost}個",
                                    color = textDark
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = { removeRewardTicketType(index) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = pink),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("削除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ParentTaskSettingCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = lightGreen),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "タスク設定",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = green
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    label = { Text("タスク名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addTask() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("タスクを追加")
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (tasks.isEmpty()) {
                    Text(
                        text = "まだタスクはありません",
                        color = textDark
                    )
                } else {
                    tasks.forEachIndexed { index, task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardWhite)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "✅ ${task.title}",
                                    fontWeight = FontWeight.Bold,
                                    color = textDark
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = { removeTask(index) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = pink),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("削除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ParentChildStatusCard(
        childIndex: Int,
        child: ChildStatus
    ) {
        val isAchieved = child.screenTimeMinutes <= child.targetMinutes
        val waitingTickets = child.tickets
            .mapIndexed { index, ticket -> index to ticket }
            .filter { it.second.status == "承認待ち" }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "👧 ${child.memberName}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(text = "前日の使用時間：${child.screenTimeMinutes}分", color = textDark)
                Text(text = "目標時間：${child.targetMinutes}分", color = textDark)
                Text(text = "節約時間：${calculateSavedTimeForDisplay(child.screenTimeMinutes)}分", color = textDark)
                Text(text = "持っている素材：${child.materialCount}個", color = textDark)
                Text(text = "更新日：${child.lastUpdatedDate}", color = textDark)

                Text(
                    text = if (isAchieved) "状況：目標達成" else "状況：目標超過",
                    fontWeight = FontWeight.Bold,
                    color = if (isAchieved) green else pink
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "承認待ちチケット",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (waitingTickets.isEmpty()) {
                    Text(text = "承認待ちのチケットはありません", color = textDark)
                } else {
                    waitingTickets.forEach { pair ->
                        val ticketIndex = pair.first
                        val ticket = pair.second

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = lightPurple)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🎟 ${ticket.name}",
                                    fontWeight = FontWeight.Bold,
                                    color = textDark
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        approveChildTicket(
                                            childIndex = childIndex,
                                            ticketIndex = ticketIndex
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = green),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("承認する")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "タスク確認",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (child.tasks.isEmpty()) {
                    Text(text = "タスクはありません", color = textDark)
                } else {
                    child.tasks.forEach { task ->
                        Text(
                            text = if (task.completed) "✅ ${task.title}" else "⬜ ${task.title}",
                            color = textDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { deleteChildMember(childIndex) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = pink),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("この子供を削除")
                }
            }
        }
    }

    @Composable
    private fun ChildHomeScreen() {
        val savedMinutes = calculateSavedTimeForDisplay(screenTimeMinutes)
        val pandaMessage = getPandaHealthMessage()

        val pandaSize = when {
            screenTimeMinutes <= targetMinutes -> 185.dp
            screenTimeMinutes <= targetMinutes + 30L -> 170.dp
            else -> 155.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.sougen),
                contentDescription = "home background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 102.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { backToRoleSelectScreen() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = purple
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "＜",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "ぱんだ",
                        modifier = Modifier.weight(1f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textDark
                    )

                    Spacer(modifier = Modifier.width(58.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = getPandaImageResource()),
                        contentDescription = "ぱんだ",
                        modifier = Modifier
                            .size(pandaSize)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )

                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 42.dp, end = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = pandaMessage,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "きのうせつやくできたじかん",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${savedMinutes}ふん",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = "もくひょう ${targetMinutes}ふん",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "きのうつかったじかん：${screenTimeMinutes}ふん",
                            fontSize = 13.sp,
                            color = textDark
                        )

                        Text(
                            text = "ぱんだ：${pandaMessage}",
                            fontSize = 13.sp,
                            color = textDark
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "もっているそざい",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )

                        Spacer(modifier = Modifier.height(5.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "🌿", fontSize = 30.sp)

                            Text(
                                text = "${materialCount}こ",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { currentScreen = "craft" },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 7.dp)
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = orange),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "🎫 つくる",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { currentScreen = "task" },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 7.dp)
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = green),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "✅ やること",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = textDark
                )
            }
        }
    }

    @Composable
    private fun CraftScreen() {
        Text(
            text = uiText("チケット設定", "チケットをつくる"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isParentMode) {
            ParentRewardSettingCard()

            Spacer(modifier = Modifier.height(14.dp))
        } else {
            if (isInFamilyRoom) {
                Button(
                    onClick = {
                        loadRoomSettingsFromFirebase(showMessage = false)
                        loadMyLatestRecordFromFirebase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("チケット情報を更新")
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = lightOrange)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🌿", fontSize = 32.sp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "もっているそざい：${materialCount}こ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "つくれるチケット",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (rewardTicketTypes.isEmpty()) {
                Text(
                    text = "まだチケットはないよ",
                    color = textDark
                )
            } else {
                rewardTicketTypes.forEach { reward ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = cardWhite)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        text = "いるそざい：${reward.cost}こ",
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
                                Text("つくる")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "もっているチケット",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (tickets.isEmpty()) {
                Text("まだチケットはないよ", color = textDark)
            } else {
                tickets.forEachIndexed { index, ticket ->
                    TicketCard(index, ticket)
                }
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
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎟 ${ticket.name}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Text(
                    text = uiText(
                        "状態：${ticket.status}",
                        "いま：${displayTicketStatus(ticket.status)}"
                    ),
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (ticket.status == "未申請") {
                    Button(
                        onClick = { requestApproval(index) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = purple),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(uiText("親に承認申請する", "おうちのひとにおねがいする"))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (ticket.status == "承認待ち") {
                    Text(
                        text = uiText(
                            "親の承認待ちです",
                            "おうちのひとのOKまち"
                        ),
                        color = textDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }

                Button(
                    onClick = { useTicket(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = orange),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("チケットを使う", "チケットをつかう"))
                }
            }
        }
    }

    @Composable
    private fun TaskScreen() {
        Text(
            text = uiText("タスク設定", "やること"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isParentMode) {
            ParentTaskSettingCard()

            Spacer(modifier = Modifier.height(14.dp))
        } else {
            if (isInFamilyRoom) {
                Button(
                    onClick = { loadRoomSettingsFromFirebase(showMessage = true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("やることを更新")
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = lightGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "おうちのひとがきめたやること",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = green
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "できたらチェックしてね",
                        color = textDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (tasks.isEmpty()) {
                Text(
                    text = "まだやることはないよ",
                    color = textDark
                )
            } else {
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
                                    text = if (task.completed) "できた" else "まだ",
                                    color = if (task.completed) green else textDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = message, color = textDark)
    }

    @Composable
    private fun GraphScreen() {
        val graphRecords = historyRecords
            .distinctBy { it.date }
            .sortedBy { it.date }

        Text(
            text = "しようじかん",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (!isParentMode) {
                    refreshLast7DaysUsageToFirebase()
                } else {
                    loadHistoryFromFirebase()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = purple),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(uiText("グラフを更新", "ぐらふをこうしん"))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = cardWhite)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (graphRecords.isEmpty()) {
                    Text(
                        text = uiText(
                            "まだ保存された履歴がありません",
                            "まだデータはないよ"
                        ),
                        color = textDark
                    )
                } else {
                    val maxMinutes = graphRecords
                        .maxOfOrNull { it.screenTimeMinutes }
                        ?.coerceAtLeast(1L) ?: 1L

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        graphRecords.forEach { record ->
                            Column(
                                modifier = Modifier.width(62.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                val barHeight =
                                    ((record.screenTimeMinutes.toDouble() / maxMinutes.toDouble()) * 170.0)
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
                                        .width(28.dp)
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
    }

    @Composable
    private fun FamilyScreen() {
        Text(
            text = uiText("家族コミュニティ", "かぞくのへや"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = purple
        )

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
            text = uiText("ルームメンバー", "へやのひと"),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (roomMembers.isEmpty()) {
            Text(
                text = uiText("まだこのルームに家族がいません", "まだだれもいないよ"),
                color = textDark
            )
        } else {
            roomMembers.forEachIndexed { index, member ->
                val achievementText = if (member.screenTimeMinutes <= member.targetMinutes) {
                    uiText("目標達成", "できた")
                } else {
                    uiText("目標超過", "こえた")
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.memberName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "区分：${member.role}",
                                    "だれ：${displayRole(member.role)}"
                                ),
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "前日の使用時間：${member.screenTimeMinutes}分",
                                    "きのうのじかん：${member.screenTimeMinutes}ふん"
                                ),
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "節約時間：${calculateSavedTimeForDisplay(member.screenTimeMinutes)}分",
                                    "せつやく：${calculateSavedTimeForDisplay(member.screenTimeMinutes)}ふん"
                                ),
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "目標：${member.targetMinutes}分",
                                    "もくひょう：${member.targetMinutes}ふん"
                                ),
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "素材：${member.materialCount}個",
                                    "そざい：${member.materialCount}こ"
                                ),
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "更新日：${member.lastUpdatedDate}",
                                    "ひづけ：${member.lastUpdatedDate}"
                                ),
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "親：家族ルームを作成",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = pink
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ボタンを押すとルームIDが自動で作られます。子供はこのIDを入力して参加します。",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = memberNameInput,
                    onValueChange = { memberNameInput = it },
                    label = { Text("親の名前") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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

                Spacer(modifier = Modifier.height(12.dp))

                if (isInFamilyRoom) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardWhite)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "作成されたルームID",
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = getRoomId(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = purple
                            )

                            Text(
                                text = "このIDを子供画面で入力してください。",
                                color = textDark
                            )
                        }
                    }
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "かぞくのへやにはいる",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "おうちのひとからきいたルームIDをいれてね",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = { roomIdInput = it },
                    label = { Text("ルームID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberNameInput,
                    onValueChange = { memberNameInput = it },
                    label = { Text("じぶんのなまえ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { joinFamilyRoom() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("へやにはいる")
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
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = uiText("現在のコミュニティ設定", "いまのへや"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = orange
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isInFamilyRoom) {
                        uiText("参加中", "はいっている")
                    } else {
                        uiText("未参加", "まだはいっていない")
                    },
                    color = textDark
                )

                Text(
                    text = uiText("ルームID：${getRoomId()}", "へやのID：${getRoomId()}"),
                    color = textDark
                )

                Text(
                    text = uiText("名前：${getMemberName()}", "なまえ：${getMemberName()}"),
                    color = textDark
                )

                Text(
                    text = uiText("区分：${getRoleName()}", "だれ：${displayRole(getRoleName())}"),
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { loadRoomMembers() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("ルームメンバーを更新", "へやのひとをみる"))
                }
            }
        }
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun createFamilyRoom() {
        val memberName = getMemberName()

        val newRoomId = generateRoomId()
        val newMemberId = generateMemberId()

        roomIdInput = newRoomId
        memberIdInput = newMemberId
        inviteCodeInput = ""

        hasSelectedRole = true
        isParentMode = true
        memberRole = "保護者"
        isInFamilyRoom = true
        saveCommunitySettings()

        val roomData = hashMapOf(
            "roomId" to newRoomId,
            "createdByMemberId" to newMemberId,
            "createdByName" to memberName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val parentData = hashMapOf(
            "roomId" to newRoomId,
            "memberId" to newMemberId,
            "memberName" to memberName,
            "role" to "保護者",
            "screenTimeMinutes" to 0L,
            "targetMinutes" to targetMinutes,
            "materialCount" to 0L,
            "lastUpdatedDate" to "-",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("familyRooms")
            .document(newRoomId)
            .set(roomData, SetOptions.merge())
            .addOnSuccessListener {
                db.collection("familyRooms")
                    .document(newRoomId)
                    .collection("members")
                    .document(newMemberId)
                    .set(parentData, SetOptions.merge())
                    .addOnSuccessListener {
                        saveRoomSettingsToFirebase(showMessage = false)
                        message = "家族ルームを作成しました。ルームIDは ${newRoomId} です"
                        loadParentChildStatuses()
                    }
                    .addOnFailureListener {
                        message = "親メンバーの保存に失敗しました"
                    }
            }
            .addOnFailureListener {
                message = "家族ルームの作成に失敗しました"
            }
    }

    private fun joinFamilyRoom() {
        val roomId = getRoomId()

        if (roomId.isBlank()) {
            setMessage("ルームIDを入力してください", "ルームIDをいれてね")
            return
        }

        val childMemberId = generateMemberId()
        memberIdInput = childMemberId

        db.collection("familyRooms")
            .document(roomId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    setMessage("その家族ルームは存在しません", "そのへやはないよ")
                    return@addOnSuccessListener
                }

                hasSelectedRole = true
                isParentMode = false
                memberRole = "子供"
                isInFamilyRoom = true
                saveCommunitySettings()

                val childData = hashMapOf(
                    "roomId" to roomId,
                    "memberId" to childMemberId,
                    "memberName" to getMemberName(),
                    "role" to "子供",
                    "screenTimeMinutes" to screenTimeMinutes,
                    "targetMinutes" to targetMinutes,
                    "materialCount" to materialCount,
                    "lastUpdatedDate" to lastSavedDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("familyRooms")
                    .document(roomId)
                    .collection("members")
                    .document(childMemberId)
                    .set(childData, SetOptions.merge())
                    .addOnSuccessListener {
                        loadRoomSettingsFromFirebase(showMessage = false)
                        prepareYesterdayRecordAutomatically()
                        setMessage(
                            "${getMemberName()}が家族ルームに参加しました",
                            "${getMemberName()}がへやにはいったよ"
                        )
                    }
                    .addOnFailureListener {
                        setMessage("メンバー登録に失敗しました", "へやにはいれなかったよ")
                    }
            }
            .addOnFailureListener {
                setMessage("家族ルームの確認に失敗しました", "へやをみつけられなかったよ")
            }
    }

    private fun prepareYesterdayRecordAutomatically() {
        if (!hasUsageStatsPermission()) {
            setMessage("使用状況アクセスを許可してください", "スマホのじかんをみられるようにしてね")
            return
        }

        val date = getYesterdayDateString()
        val minutes = getYesterdayScreenTimeMinutes()
        val earnedMaterial = calculateMaterial(minutes)

        screenTimeMinutes = minutes

        if (lastMaterialRewardDate != date) {
            materialCount += earnedMaterial
            lastMaterialRewardDate = date

            prefs.edit()
                .putString("lastMaterialRewardDate", lastMaterialRewardDate)
                .apply()

            setMessage(
                "前日データを保存しました：${minutes}分、素材を${earnedMaterial}個獲得しました",
                "きのうのデータをとったよ：${minutes}ふん、そざいを${earnedMaterial}こもらったよ"
            )
        } else {
            setMessage(
                "前日データは保存済みです：${minutes}分",
                "きのうのデータはもうとったよ：${minutes}ふん"
            )
        }

        saveLastRecordToLocal()

        if (isInFamilyRoom) {
            saveScreenTimeOnlyToFirebase()
        }
    }

    private fun saveLastRecordToLocal() {
        val date = getYesterdayDateString()

        lastSavedDate = date
        lastSavedScreenTimeMinutes = screenTimeMinutes
        lastSavedMaterialCount = materialCount

        prefs.edit()
            .putString("lastSavedDate", date)
            .putLong("lastSavedScreenTimeMinutes", screenTimeMinutes)
            .putLong("lastSavedMaterialCount", materialCount)
            .putString("lastMaterialRewardDate", lastMaterialRewardDate)
            .putLong("targetMinutes", targetMinutes)
            .apply()
    }

    private fun loadLastAutoSavedRecordFromLocal() {
        lastSavedDate = prefs.getString("lastSavedDate", "") ?: ""
        lastSavedScreenTimeMinutes = prefs.getLong("lastSavedScreenTimeMinutes", 0L)
        lastSavedMaterialCount = prefs.getLong("lastSavedMaterialCount", 0L)
        lastMaterialRewardDate = prefs.getString("lastMaterialRewardDate", "") ?: ""

        if (lastSavedDate.isNotBlank()) {
            screenTimeMinutes = lastSavedScreenTimeMinutes
            materialCount = lastSavedMaterialCount
        }
    }

    private fun updateTargetTime() {
        val newTarget = targetInput.toLongOrNull()

        if (newTarget == null || newTarget <= 0) {
            message = "正しい目標時間を入力してください"
            return
        }

        targetMinutes = newTarget

        prefs.edit()
            .putLong("targetMinutes", targetMinutes)
            .apply()

        if (hasUsageStatsPermission() && !isParentMode) {
            val minutes = getYesterdayScreenTimeMinutes()
            screenTimeMinutes = minutes
            saveLastRecordToLocal()
        }

        if (isInFamilyRoom) {
            saveScreenTimeOnlyToFirebase()
        }

        message = "目標時間を${targetMinutes}分に変更しました"
    }

    private fun calculateMaterial(minutes: Long): Long {
        val savedByGoal = (targetMinutes - minutes).coerceAtLeast(0L)
        return savedByGoal / materialRate
    }

    private fun calculateSavedTimeForDisplay(minutes: Long): Long {
        val oneDayMinutes = 24L * 60L
        return (oneDayMinutes - minutes).coerceAtLeast(0L)
    }

    private fun addRewardTicketType() {
        if (!isParentMode) {
            setMessage(
                "チケットは親画面だけで追加できます",
                "チケットはおうちのひとがきめるよ"
            )
            return
        }

        val name = rewardNameInput.trim()
        val cost = rewardCostInput.toLongOrNull()

        if (name.isBlank()) {
            setMessage("チケット名を入力してください", "チケットのなまえをいれてね")
            return
        }

        if (cost == null || cost <= 0) {
            setMessage("必要素材数を正しく入力してください", "いるそざいのかずをいれてね")
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

        saveRoomSettingsToFirebase(showMessage = false)

        setMessage(
            "${name}をチケットに追加しました",
            "${name}をふやしたよ"
        )
    }

    private fun removeRewardTicketType(index: Int) {
        if (!isParentMode) {
            return
        }

        if (index < 0 || index >= rewardTicketTypes.size) {
            return
        }

        val name = rewardTicketTypes[index].name
        rewardTicketTypes.removeAt(index)

        saveRoomSettingsToFirebase(showMessage = false)

        message = "${name}を削除しました"
    }

    private fun craftTicket(ticketName: String, cost: Long) {
        if (materialCount >= cost) {
            materialCount -= cost
            tickets.add(Ticket(name = ticketName, status = "未申請"))
            setMessage("${ticketName}をクラフトしました", "${ticketName}をつくったよ")
            autoSaveCurrentStateSilently()
        } else {
            setMessage("素材が足りません", "そざいがたりないよ")
        }
    }

    private fun requestApproval(index: Int) {
        val ticket = tickets[index]

        if (ticket.status != "未申請") {
            setMessage("このチケットはすでに申請されています", "もうおねがいしているよ")
            return
        }

        tickets[index] = ticket.copy(status = "承認待ち")
        setMessage("${ticket.name}を親に承認申請しました", "${ticket.name}をおねがいしたよ")
        autoSaveCurrentStateSilently()
    }

    private fun approveChildTicket(childIndex: Int, ticketIndex: Int) {
        if (childIndex < 0 || childIndex >= childStatusList.size) return

        val child = childStatusList[childIndex]

        if (ticketIndex < 0 || ticketIndex >= child.tickets.size) return

        if (child.recordDate.isBlank()) {
            message = "保存データがないため承認できません"
            return
        }

        val updatedTickets = child.tickets.toMutableList()
        val ticket = updatedTickets[ticketIndex]

        if (ticket.status != "承認待ち") {
            message = "承認待ちのチケットではありません"
            return
        }

        updatedTickets[ticketIndex] = ticket.copy(status = "承認済み")

        val ticketMapList = updatedTickets.map {
            mapOf("name" to it.name, "status" to it.status)
        }

        val updateData = hashMapOf(
            "tickets" to ticketMapList,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(child.memberId)
            .collection("dailyRecords")
            .document(child.recordDate)
            .set(updateData, SetOptions.merge())
            .addOnSuccessListener {
                childStatusList[childIndex] = child.copy(tickets = updatedTickets)
                message = "${child.memberName}の${ticket.name}を承認しました"
            }
            .addOnFailureListener {
                message = "チケットの承認に失敗しました"
            }
    }

    private fun useTicket(index: Int) {
        val ticket = tickets[index]

        if (ticket.status != "承認済み") {
            setMessage("承認済みのチケットだけ使用できます", "OKされたチケットだけつかえるよ")
            return
        }

        tickets[index] = ticket.copy(status = "使用済み")
        setMessage("${ticket.name}を使用済みにしました", "${ticket.name}をつかったよ")
        autoSaveCurrentStateSilently()
    }

    private fun addTask() {
        if (!isParentMode) {
            setMessage(
                "タスクは親画面だけで追加できます",
                "やることはおうちのひとがきめるよ"
            )
            return
        }

        val title = taskInput.trim()

        if (title.isBlank()) {
            setMessage("タスク名を入力してください", "やることをいれてね")
            return
        }

        tasks.add(
            DailyTask(
                title = title,
                completed = false
            )
        )

        taskInput = ""

        saveRoomSettingsToFirebase(showMessage = false)

        setMessage(
            "タスクを追加しました",
            "やることをふやしたよ"
        )
    }

    private fun removeTask(index: Int) {
        if (!isParentMode) {
            return
        }

        if (index < 0 || index >= tasks.size) {
            return
        }

        val title = tasks[index].title
        tasks.removeAt(index)

        saveRoomSettingsToFirebase(showMessage = false)

        message = "${title}を削除しました"
    }

    private fun toggleTask(index: Int) {
        if (isParentMode) {
            return
        }

        if (index < 0 || index >= tasks.size) {
            return
        }

        val task = tasks[index]
        tasks[index] = task.copy(completed = !task.completed)

        if (!task.completed) {
            setMessage("${task.title}を完了しました", "${task.title}ができたよ")
        } else {
            setMessage("${task.title}を未完了に戻しました", "${task.title}をまだにしたよ")
        }

        autoSaveCurrentStateSilently()
    }

    private fun autoSaveCurrentStateSilently() {
        if (!isInFamilyRoom) return

        saveCurrentStateToFirebase(
            showMessage = false,
            successMessage = ""
        )
    }

    private fun saveRoomSettingsToFirebase(showMessage: Boolean = true) {
        if (!isInFamilyRoom) {
            if (showMessage) {
                message = "家族ルームを作成すると設定を保存できます"
            }
            return
        }

        val rewardList = rewardTicketTypes.map {
            mapOf(
                "name" to it.name,
                "cost" to it.cost
            )
        }

        val taskTemplateList = tasks.map {
            mapOf(
                "title" to it.title
            )
        }

        val roomSettings = hashMapOf(
            "rewardTicketTypes" to rewardList,
            "taskTemplates" to taskTemplateList,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("familyRooms")
            .document(getRoomId())
            .set(roomSettings, SetOptions.merge())
            .addOnSuccessListener {
                if (showMessage) {
                    message = "親の設定を保存しました"
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "親の設定保存に失敗しました"
                }
            }
    }

    private fun loadRoomSettingsFromFirebase(showMessage: Boolean = false) {
        if (!isInFamilyRoom) {
            return
        }

        db.collection("familyRooms")
            .document(getRoomId())
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    return@addOnSuccessListener
                }

                val rewardList = document.get("rewardTicketTypes") as? List<*>

                if (rewardList != null) {
                    rewardTicketTypes.clear()

                    rewardList.forEach { item ->
                        val map = item as? Map<*, *> ?: return@forEach
                        val name = map["name"] as? String ?: return@forEach
                        val cost = toLongValue(map["cost"])

                        if (name.isNotBlank() && cost > 0) {
                            rewardTicketTypes.add(
                                RewardTicketType(
                                    name = name,
                                    cost = cost
                                )
                            )
                        }
                    }
                }

                val completedMap = tasks.associate {
                    it.title to it.completed
                }

                val taskTemplateList = document.get("taskTemplates") as? List<*>

                if (taskTemplateList != null) {
                    tasks.clear()

                    taskTemplateList.forEach { item ->
                        val map = item as? Map<*, *> ?: return@forEach
                        val title = map["title"] as? String ?: return@forEach

                        if (title.isNotBlank()) {
                            tasks.add(
                                DailyTask(
                                    title = title,
                                    completed = completedMap[title] ?: false
                                )
                            )
                        }
                    }
                }

                if (showMessage) {
                    message = "親の設定を読み込みました"
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    message = "親の設定を読み込めませんでした"
                }
            }
    }

    private fun saveScreenTimeOnlyToFirebase() {
        if (!isInFamilyRoom) return

        val date = getYesterdayDateString()

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
            "savedBy" to "screenTimeOnly",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val memberRef = db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(getMemberId())

        memberRef
            .set(memberData, SetOptions.merge())
            .addOnSuccessListener {
                memberRef
                    .collection("dailyRecords")
                    .document(date)
                    .set(dailyData, SetOptions.merge())
            }
    }

    private fun saveCurrentStateToFirebase(showMessage: Boolean, successMessage: String) {
        if (!isInFamilyRoom) {
            if (showMessage) {
                setMessage("家族ルームに参加していないためFirebaseには保存できません", "へやにはいっていないから、まだのこせないよ")
            }
            return
        }

        val date = getYesterdayDateString()

        val ticketList = tickets.map {
            mapOf("name" to it.name, "status" to it.status)
        }

        val rewardList = rewardTicketTypes.map {
            mapOf("name" to it.name, "cost" to it.cost)
        }

        val taskList = tasks.map {
            mapOf("title" to it.title, "completed" to it.completed)
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
            "savedBy" to "MainActivity",
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val memberRef = db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(getMemberId())

        memberRef
            .set(memberData, SetOptions.merge())
            .addOnSuccessListener {
                memberRef
                    .collection("dailyRecords")
                    .document(date)
                    .set(dailyData, SetOptions.merge())
                    .addOnSuccessListener {
                        if (showMessage) {
                            message = successMessage
                        }

                        loadHistoryFromFirebase(false)
                        loadRoomMembers(false)
                    }
            }
    }

    private fun loadRecordIntoState(document: DocumentSnapshot) {
        screenTimeMinutes = document.getLong("screenTimeMinutes") ?: screenTimeMinutes
        targetMinutes = document.getLong("targetMinutes") ?: targetMinutes
        materialCount = document.getLong("materialCount") ?: materialCount
        targetInput = targetMinutes.toString()

        val savedMemberName = document.getString("memberName")
        if (!savedMemberName.isNullOrBlank()) {
            memberNameInput = savedMemberName
        }

        tickets.clear()

        val ticketList = document.get("tickets") as? List<*>
        ticketList?.forEach { item ->
            val map = item as? Map<*, *>
            val name = map?.get("name") as? String ?: return@forEach
            val status = map["status"] as? String ?: "未申請"

            tickets.add(Ticket(name = name, status = status))
        }
    }

    private fun loadMyLatestRecordFromFirebase(showMessage: Boolean = true) {
        if (!isInFamilyRoom) {
            if (showMessage) {
                setMessage("家族ルームに参加していません", "へやにはいっていないよ")
            }
            return
        }

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(getMemberId())
            .collection("dailyRecords")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    if (showMessage) {
                        setMessage("保存データがありません", "まだデータがないよ")
                    }
                    return@addOnSuccessListener
                }

                loadRecordIntoState(result.documents.first())

                if (showMessage) {
                    setMessage("チケット情報を更新しました", "チケットをこうしんしたよ")
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    setMessage("チケット情報の更新に失敗しました", "チケットをこうしんできなかったよ")
                }
            }
    }

    private fun loadParentChildStatuses() {
        childStatusList.clear()

        if (!isInFamilyRoom) {
            message = "家族ルームに参加していません"
            return
        }

        message = "子供の状況を読み込み中です"

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .get()
            .addOnSuccessListener { memberResult ->
                if (memberResult.isEmpty) {
                    message = "このルームにはまだメンバーがいません"
                    return@addOnSuccessListener
                }

                val childMembers = memberResult.documents.filter { document ->
                    val role = document.getString("role") ?: ""
                    role != "保護者"
                }

                if (childMembers.isEmpty()) {
                    message = "子供メンバーがまだいません"
                    return@addOnSuccessListener
                }

                childMembers.forEach { memberDocument ->
                    val childMemberId = memberDocument.getString("memberId") ?: memberDocument.id
                    val childMemberName = memberDocument.getString("memberName") ?: "名前なし"

                    db.collection("familyRooms")
                        .document(getRoomId())
                        .collection("members")
                        .document(childMemberId)
                        .collection("dailyRecords")
                        .orderBy("date", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { recordResult ->
                            if (recordResult.isEmpty) {
                                childStatusList.add(
                                    ChildStatus(
                                        memberId = childMemberId,
                                        memberName = childMemberName,
                                        screenTimeMinutes = memberDocument.getLong("screenTimeMinutes") ?: 0L,
                                        targetMinutes = memberDocument.getLong("targetMinutes") ?: targetMinutes,
                                        materialCount = memberDocument.getLong("materialCount") ?: 0L,
                                        lastUpdatedDate = memberDocument.getString("lastUpdatedDate") ?: "-",
                                        recordDate = "",
                                        tickets = emptyList(),
                                        tasks = emptyList()
                                    )
                                )

                                message = "子供の状況を読み込みました"
                                return@addOnSuccessListener
                            }

                            val record = recordResult.documents.first()

                            val ticketsFromRecord = mutableListOf<Ticket>()
                            val ticketList = record.get("tickets") as? List<*>

                            ticketList?.forEach { item ->
                                val map = item as? Map<*, *>
                                val name = map?.get("name") as? String ?: return@forEach
                                val status = map["status"] as? String ?: "未申請"

                                ticketsFromRecord.add(Ticket(name = name, status = status))
                            }

                            val tasksFromRecord = mutableListOf<DailyTask>()
                            val taskList = record.get("tasks") as? List<*>

                            taskList?.forEach { item ->
                                val map = item as? Map<*, *>
                                val title = map?.get("title") as? String ?: return@forEach
                                val completed = map["completed"] as? Boolean ?: false

                                tasksFromRecord.add(DailyTask(title = title, completed = completed))
                            }

                            childStatusList.add(
                                ChildStatus(
                                    memberId = childMemberId,
                                    memberName = record.getString("memberName") ?: childMemberName,
                                    screenTimeMinutes = record.getLong("screenTimeMinutes") ?: 0L,
                                    targetMinutes = record.getLong("targetMinutes") ?: targetMinutes,
                                    materialCount = record.getLong("materialCount") ?: 0L,
                                    lastUpdatedDate = record.getString("date") ?: "-",
                                    recordDate = record.getString("date") ?: record.id,
                                    tickets = ticketsFromRecord,
                                    tasks = tasksFromRecord
                                )
                            )

                            message = "子供の状況を読み込みました"
                        }
                        .addOnFailureListener {
                            message = "子供の記録を読み込めませんでした"
                        }
                }
            }
            .addOnFailureListener {
                message = "子供の状況を読み込めませんでした"
            }
    }

    private fun deleteChildMember(childIndex: Int) {
        if (!isParentMode) {
            message = "子供の削除は親画面だけでできます"
            return
        }

        if (!isInFamilyRoom) {
            message = "家族ルームに参加していません"
            return
        }

        if (childIndex < 0 || childIndex >= childStatusList.size) {
            message = "削除する子供が見つかりません"
            return
        }

        val child = childStatusList[childIndex]

        message = "${child.memberName}を削除しています"

        val memberRef = db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .document(child.memberId)

        memberRef
            .collection("dailyRecords")
            .get()
            .addOnSuccessListener { records ->
                val batch = db.batch()

                records.documents.forEach { document ->
                    batch.delete(document.reference)
                }

                batch.delete(memberRef)

                batch.commit()
                    .addOnSuccessListener {
                        childStatusList.removeAll { it.memberId == child.memberId }
                        roomMembers.removeAll { it.memberId == child.memberId }

                        message = "${child.memberName}を削除しました"

                        loadRoomMembers(false)
                    }
                    .addOnFailureListener {
                        message = "子供の削除に失敗しました"
                    }
            }
            .addOnFailureListener {
                message = "子供の記録を確認できませんでした"
            }
    }

    private fun refreshLast7DaysUsageToFirebase() {
        if (!hasUsageStatsPermission()) {
            setMessage("使用状況アクセスを許可してください", "スマホのじかんをみられるようにしてね")
            return
        }

        message = "スマホから過去7日分を取り直しています"

        val newRecords = mutableListOf<DailyRecord>()

        for (daysAgo in 7 downTo 1) {
            val date = getDateStringDaysAgo(daysAgo)
            val minutes = getScreenTimeMinutesForDay(daysAgo)

            if (daysAgo == 1) {
                screenTimeMinutes = minutes
                lastSavedDate = date
                lastSavedScreenTimeMinutes = minutes

                prefs.edit()
                    .putString("lastSavedDate", date)
                    .putLong("lastSavedScreenTimeMinutes", minutes)
                    .putLong("lastSavedMaterialCount", materialCount)
                    .apply()
            }

            newRecords.add(
                DailyRecord(
                    date = date,
                    screenTimeMinutes = minutes,
                    targetMinutes = targetMinutes,
                    materialCount = materialCount,
                    ticketCount = tickets.size.toLong()
                )
            )

            if (isInFamilyRoom) {
                val memberRef = db.collection("familyRooms")
                    .document(getRoomId())
                    .collection("members")
                    .document(getMemberId())

                val dailyData = hashMapOf(
                    "date" to date,
                    "roomId" to getRoomId(),
                    "memberId" to getMemberId(),
                    "memberName" to getMemberName(),
                    "role" to getRoleName(),
                    "screenTimeMinutes" to minutes,
                    "targetMinutes" to targetMinutes,
                    "materialCount" to materialCount,
                    "savedBy" to "refreshLast7Days",
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                memberRef
                    .collection("dailyRecords")
                    .document(date)
                    .set(dailyData, SetOptions.merge())
            }
        }

        historyRecords.clear()
        historyRecords.addAll(newRecords.sortedBy { it.date })

        if (isInFamilyRoom) {
            val memberRef = db.collection("familyRooms")
                .document(getRoomId())
                .collection("members")
                .document(getMemberId())

            val memberData = hashMapOf(
                "roomId" to getRoomId(),
                "memberId" to getMemberId(),
                "memberName" to getMemberName(),
                "role" to getRoleName(),
                "screenTimeMinutes" to screenTimeMinutes,
                "targetMinutes" to targetMinutes,
                "materialCount" to materialCount,
                "lastUpdatedDate" to getYesterdayDateString(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            memberRef.set(memberData, SetOptions.merge())
        }

        setMessage(
            "スマホから取ったデータでグラフを更新しました",
            "スマホのデータでぐらふをこうしんしたよ"
        )
    }

    private fun loadHistoryFromFirebase(showMessage: Boolean = true) {
        historyRecords.clear()

        if (!isInFamilyRoom) {
            if (showMessage) {
                setMessage("家族ルームに参加するとグラフ履歴が表示されます", "へやにはいると、ぐらふがみられるよ")
            }
            return
        }

        if (showMessage) {
            setMessage("履歴を読み込み中です", "データをよんでいるよ")
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
                        setMessage("保存された履歴がありません", "まだデータはないよ")
                    }
                    return@addOnSuccessListener
                }

                val loadedRecords = mutableListOf<DailyRecord>()

                for (document in result.documents) {
                    val date = document.getString("date") ?: document.id
                    val screenTime = document.getLong("screenTimeMinutes") ?: 0L
                    val targetTime = document.getLong("targetMinutes") ?: 0L
                    val material = document.getLong("materialCount") ?: 0L

                    val ticketList = document.get("tickets") as? List<*>
                    val ticketCount = ticketList?.size?.toLong() ?: 0L

                    loadedRecords.add(
                        DailyRecord(
                            date = date,
                            screenTimeMinutes = screenTime,
                            targetMinutes = targetTime,
                            materialCount = material,
                            ticketCount = ticketCount
                        )
                    )
                }

                historyRecords.clear()
                historyRecords.addAll(
                    loadedRecords
                        .distinctBy { it.date }
                        .sortedBy { it.date }
                )

                if (showMessage) {
                    setMessage("履歴を読み込みました", "データをよんだよ")
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    setMessage("履歴読み込みに失敗しました", "データをよめなかったよ")
                }
            }
    }

    private fun loadRoomMembers(showMessage: Boolean = true) {
        roomMembers.clear()

        if (!isInFamilyRoom) {
            if (showMessage) {
                setMessage("家族ルームに参加していません", "まだへやにはいっていないよ")
            }
            return
        }

        if (showMessage) {
            setMessage("ルームメンバーを読み込み中です", "へやのひとをよんでいるよ")
        }

        db.collection("familyRooms")
            .document(getRoomId())
            .collection("members")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    if (showMessage) {
                        setMessage("このルームにはまだメンバーがいません", "まだだれもいないよ")
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
                    setMessage("ルームメンバーを読み込みました", "へやのひとをよんだよ")
                }
            }
            .addOnFailureListener {
                if (showMessage) {
                    setMessage("ルームメンバーの読み込みに失敗しました", "へやのひとをよめなかったよ")
                }
            }
    }

    private fun saveCommunitySettings() {
        prefs.edit()
            .putBoolean("hasSelectedRole", hasSelectedRole)
            .putString("roomId", roomIdInput)
            .putString("inviteCode", inviteCodeInput)
            .putString("memberId", memberIdInput)
            .putString("memberName", memberNameInput)
            .putString("memberRole", memberRole)
            .putBoolean("isInFamilyRoom", isInFamilyRoom)
            .putLong("targetMinutes", targetMinutes)
            .putString("lastMaterialRewardDate", lastMaterialRewardDate)
            .apply()
    }

    private fun loadCommunitySettings() {
        hasSelectedRole = prefs.getBoolean("hasSelectedRole", false)

        roomIdInput = prefs.getString("roomId", "") ?: ""
        inviteCodeInput = prefs.getString("inviteCode", "") ?: ""
        memberIdInput = prefs.getString("memberId", "") ?: ""
        memberNameInput = prefs.getString("memberName", "たろう") ?: "たろう"
        memberRole = prefs.getString("memberRole", "子供") ?: "子供"
        isInFamilyRoom = prefs.getBoolean("isInFamilyRoom", false)

        targetMinutes = prefs.getLong("targetMinutes", 60L)
        targetInput = targetMinutes.toString()
        lastMaterialRewardDate = prefs.getString("lastMaterialRewardDate", "") ?: ""

        isParentMode = memberRole == "保護者"

        if (!hasSelectedRole) {
            message = "がめんをえらんでください"
        }
    }

    private fun generateRoomId(): String {
        val number = (System.currentTimeMillis() % 900000L) + 100000L
        return "room$number"
    }

    private fun generateMemberId(): String {
        val number = (System.currentTimeMillis() % 900000L) + 100000L
        return "member$number"
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
        return getScreenTimeMinutesForDay(1)
    }

    private fun getScreenTimeMinutesForDay(daysAgo: Int): Long {
        val range = getDayRangeMillis(daysAgo)
        val startTime = range.first
        val endTime = range.second

        return getScreenTimeFromUsageEvents(startTime, endTime)
    }

    private fun getScreenTimeFromUsageEvents(
        startTime: Long,
        endTime: Long
    ): Long {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

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

            val eventTime = event.timeStamp.coerceIn(startTime, endTime)

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (currentPackageName != null && currentStartTime > 0L) {
                        val usedTime = eventTime - currentStartTime

                        if (usedTime > 0L) {
                            totalTime += usedTime
                        }
                    }

                    currentPackageName = packageName
                    currentStartTime = eventTime
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPackageName == packageName && currentStartTime > 0L) {
                        val usedTime = eventTime - currentStartTime

                        if (usedTime > 0L) {
                            totalTime += usedTime
                        }

                        currentPackageName = null
                        currentStartTime = 0L
                    }
                }
            }
        }

        if (currentPackageName != null && currentStartTime > 0L) {
            val usedTime = endTime - currentStartTime

            if (usedTime > 0L) {
                totalTime += usedTime
            }
        }

        return totalTime / 1000 / 60
    }

    private fun getDayRangeMillis(daysAgo: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startTime = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)

        val endTime = calendar.timeInMillis

        return Pair(startTime, endTime)
    }

    private fun getDateStringDaysAgo(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)

        return SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN)
            .format(calendar.time)
    }

    private fun shouldIgnorePackage(packageName: String): Boolean {
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
            "com.google.android.apps.wellbeing"
        )

        return ignorePackages.contains(packageName)
    }

    private fun getYesterdayDateString(): String {
        return getDateStringDaysAgo(1)
    }

    private fun getRoomId(): String {
        return roomIdInput.trim()
    }

    private fun getMemberId(): String {
        if (memberIdInput.isBlank()) {
            memberIdInput = generateMemberId()
            saveCommunitySettings()
        }

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

    private fun uiText(parentText: String, childText: String): String {
        return if (isParentMode) {
            parentText
        } else {
            childText
        }
    }

    private fun setMessage(parentText: String, childText: String) {
        message = uiText(parentText, childText)
    }

    private fun displayRole(role: String): String {
        return when (role) {
            "保護者" -> uiText("保護者", "おうちのひと")
            "子供" -> uiText("子供", "こども")
            else -> uiText(role, "メンバー")
        }
    }

    private fun displayTicketStatus(status: String): String {
        return when (status) {
            "未申請" -> "まだ"
            "承認待ち" -> "まってる"
            "承認済み" -> "つかえる"
            "使用済み" -> "つかった"
            else -> status
        }
    }

    private fun getPandaHealthMessage(): String {
        return when {
            screenTimeMinutes <= targetMinutes -> "げんき！"
            screenTimeMinutes <= targetMinutes + 30L -> "ちょっとつかれた"
            else -> "ぐったり…"
        }
    }

    private fun getPandaImageResource(): Int {
        return when {
            screenTimeMinutes <= targetMinutes -> R.drawable.panda_good
            screenTimeMinutes <= targetMinutes + 30L -> R.drawable.panda_tired
            else -> R.drawable.panda_bad
        }
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