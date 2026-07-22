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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size


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

    private var hasSelectedRole by mutableStateOf(false)
    private var currentScreen by mutableStateOf("home")

    private var screenTimeMinutes by mutableLongStateOf(0L)
    private var materialCount by mutableLongStateOf(0L)
    private var message by mutableStateOf("がめんをえらんでください")

    private var lastSavedDate by mutableStateOf("")
    private var lastSavedScreenTimeMinutes by mutableLongStateOf(0L)
    private var lastSavedMaterialCount by mutableLongStateOf(0L)

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

    private val materialRate = 10L

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
            prepareYesterdayRecordAutomatically()
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasSelectedRole) {
            loadLastAutoSavedRecordFromLocal()
            DailyScreenTimeWorker.scheduleNextDailySave(this)
            prepareYesterdayRecordAutomatically()
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
                    Text(
                        text = "👨‍👩‍👧",
                        fontSize = 56.sp
                    )

                    Text(
                        text = "おやのがめん",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = pink
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "へやをつくったり、こどものじかんをみます",
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
                    Text(
                        text = "🐼",
                        fontSize = 56.sp
                    )

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

        if (hasUsageStatsPermission()) {
            prepareYesterdayRecordAutomatically()
        }
    }

    private fun selectChildMode() {
        hasSelectedRole = true
        isParentMode = false
        memberRole = "子供"
        message = "こどものがめんにしたよ"
        saveCommunitySettings()

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

                if (screen == "graph") {
                    loadLastAutoSavedRecordFromLocal()
                    loadHistoryFromFirebase()
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
        val savedMinutes = (targetMinutes - screenTimeMinutes).coerceAtLeast(0L)

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                        text = uiText("ホーム", "ぱんだ"),
                        modifier = Modifier.weight(1f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textDark
                    )

                    Spacer(modifier = Modifier.width(58.dp))
                }

                Spacer(modifier = Modifier.height(36.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.panda),
                        contentDescription = "ぱんだ",
                        modifier = Modifier
                            .size(170.dp)
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
                            text = "おはよう！",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF7FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = uiText("前日の節約時間", "きのうせつやくできたじかん"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${savedMinutes}${uiText("分", "ふん")}",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )

                            Text(
                                text = uiText(
                                    "目標 ${targetMinutes}分",
                                    "もくひょう ${targetMinutes}ふん"
                                ),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = uiText("獲得できる素材", "もらえるそざい"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🌿",
                                fontSize = 32.sp
                            )

                            Text(
                                text = "${materialCount}${uiText("個", "こ")}",
                                fontSize = 32.sp,
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
                            text = uiText("🎫 クラフト", "🎫 つくる"),
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
                            text = uiText("✅ やること", "✅ やること"),
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
            text = uiText("クラフト", "チケットをつくる"),
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
                Text(text = "🌿", fontSize = 32.sp)

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = uiText(
                        "持っている素材：${materialCount}個",
                        "もっているそざい：${materialCount}こ"
                    ),
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
                    text = uiText("報酬チケット追加", "チケットをふやす"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardNameInput,
                    onValueChange = { rewardNameInput = it },
                    label = { Text(uiText("チケット名", "チケットのなまえ")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rewardCostInput,
                    onValueChange = { rewardCostInput = it },
                    label = { Text(uiText("必要素材数", "いるそざいのかず")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addRewardTicketType() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("報酬チケットを追加", "チケットをふやす"))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiText("作れるチケット", "つくれるチケット"),
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
                                text = uiText(
                                    "必要素材：${reward.cost}個",
                                    "いるそざい：${reward.cost}こ"
                                ),
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
                        Text(uiText("クラフトする", "つくる"))
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
            text = uiText("所持チケット", "もっているチケット"),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (tickets.isEmpty()) {
            Text(uiText("まだチケットはありません", "まだチケットはないよ"), color = textDark)
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
                    text = uiText(
                        "状態：${ticket.status}",
                        "いま：${displayTicketStatus(ticket.status)}"
                    ),
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { requestApproval(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("親に承認申請する", "おうちのひとにおねがいする"))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { approveTicket(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("親が承認する", "OKする"))
                }

                Spacer(modifier = Modifier.height(6.dp))

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
            text = uiText("やること", "やること"),
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
                    label = { Text(uiText("追加するタスク", "ふやすやること")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { addTask() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("＋ やることを追加", "＋ やることをふやす"))
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
                            text = if (task.completed) {
                                uiText("完了", "できた")
                            } else {
                                uiText("未完了", "まだ")
                            },
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
            text = uiText("ぼくのグラフ", "ぼくのぐらふ"),
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
                    text = uiText(
                        "自動保存された前日データを表示します",
                        "きのうのデータをみます"
                    ),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = uiText(
                        "前日の使用時間：${screenTimeMinutes}分",
                        "きのうのじかん：${screenTimeMinutes}ふん"
                    ),
                    color = textDark
                )

                Text(
                    text = uiText("素材：${materialCount}個", "そざい：${materialCount}こ"),
                    color = textDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (lastSavedDate.isBlank()) {
                        uiText("最終自動保存日：未保存", "まだデータはないよ")
                    } else {
                        uiText("最終自動保存日：${lastSavedDate}", "さいごにとったひ：${lastSavedDate}")
                    },
                    color = textDark
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        loadLastAutoSavedRecordFromLocal()
                        loadHistoryFromFirebase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("グラフを更新", "ぐらふをこうしん"))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { prepareYesterdayRecordAutomatically() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(uiText("今すぐ前日データを保存", "いまデータをとる"))
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
                    text = uiText("保存済みデータの平均", "へいきん"),
                    color = textDark
                )

                val average = if (historyRecords.isEmpty()) {
                    0L
                } else {
                    historyRecords.map { it.screenTimeMinutes }.average().toLong()
                }

                Text(
                    text = if (historyRecords.isEmpty()) {
                        "--${uiText("分", "ふん")}"
                    } else {
                        "${average}${uiText("分", "ふん")}"
                    },
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (historyRecords.isEmpty()) {
                    Text(
                        text = uiText("まだ保存された履歴がありません", "まだデータはないよ"),
                        color = textDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uiText(
                            "家族ルームに参加すると、日付変更後に前日データが自動保存されます",
                            "かぞくのへやにはいると、データがのこるよ"
                        ),
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
                                    text = "${record.screenTimeMinutes}${uiText("分", "ふん")}",
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
                        Text(
                            uiText("日付：${record.date}", "ひづけ：${record.date}"),
                            color = textDark
                        )

                        Text(
                            uiText(
                                "使用時間：${record.screenTimeMinutes}分",
                                "つかったじかん：${record.screenTimeMinutes}ふん"
                            ),
                            color = textDark
                        )

                        Text(
                            uiText(
                                "目標時間：${record.targetMinutes}分",
                                "もくひょう：${record.targetMinutes}ふん"
                            ),
                            color = textDark
                        )

                        Text(
                            uiText("素材：${record.materialCount}個", "そざい：${record.materialCount}こ"),
                            color = textDark
                        )
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
                                text = uiText(
                                    "メンバーID：${member.memberId}",
                                    "ばんごう：${member.memberId}"
                                ),
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
                    text = "かぞくのへやにはいる",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = purple
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "おうちのひとからきいたIDとコードをいれてね",
                    color = textDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomIdInput,
                    onValueChange = { roomIdInput = it },
                    label = { Text("へやのID") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inviteCodeInput,
                    onValueChange = { inviteCodeInput = it },
                    label = { Text("あいことば") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberIdInput,
                    onValueChange = { memberIdInput = it },
                    label = { Text("じぶんのばんごう") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memberNameInput,
                    onValueChange = { memberNameInput = it },
                    label = { Text("じぶんのなまえ") },
                    modifier = Modifier.fillMaxWidth()
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
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
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
                    text = uiText("メンバーID：${getMemberId()}", "ばんごう：${getMemberId()}"),
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
        val roomId = getRoomId()
        val memberId = getMemberId()
        val memberName = getMemberName()

        if (roomId.isBlank()) {
            setMessage("家族ルームIDを入力してください", "へやのIDをいれてね")
            return
        }

        if (memberId.isBlank()) {
            setMessage("親のメンバーIDを入力してください", "ばんごうをいれてね")
            return
        }

        var code = inviteCodeInput.trim()

        if (code.isBlank()) {
            code = generateInviteCode()
            inviteCodeInput = code
        }

        hasSelectedRole = true
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
                prepareYesterdayRecordAutomatically()
                message = "家族ルームを作成しました。招待コードは ${code} です"
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
            setMessage("家族ルームIDを入力してください", "へやのIDをいれてね")
            return
        }

        if (code.isBlank()) {
            setMessage("招待コードを入力してください", "あいことばをいれてね")
            return
        }

        if (memberId.isBlank()) {
            setMessage("メンバーIDを入力してください", "ばんごうをいれてね")
            return
        }

        db.collection("familyRooms")
            .document(roomId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    setMessage("その家族ルームは存在しません", "そのへやはないよ")
                    return@addOnSuccessListener
                }

                val correctCode = document.getString("inviteCode") ?: ""

                if (correctCode != code) {
                    setMessage("招待コードが違うため参加できません", "あいことばがちがうよ")
                    return@addOnSuccessListener
                }

                hasSelectedRole = true
                isParentMode = false
                memberRole = "子供"
                isInFamilyRoom = true
                saveCommunitySettings()

                prepareYesterdayRecordAutomatically()
                setMessage("${getMemberName()}が家族ルームに参加しました", "${getMemberName()}がへやにはいったよ")
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

        val minutes = getYesterdayScreenTimeMinutes()

        screenTimeMinutes = minutes
        materialCount = calculateMaterial(minutes)

        saveLastRecordToLocal()

        if (!isInFamilyRoom) {
            historyRecords.clear()
            setMessage("前日データを端末に保存しました：${minutes}分", "きのうのデータをとったよ：${minutes}ふん")
            return
        }

        saveCurrentStateToFirebase(
            showMessage = true,
            successMessage = uiText("前日データを保存しました：${minutes}分", "きのうのデータをとったよ：${minutes}ふん")
        )
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
            .putLong("targetMinutes", targetMinutes)
            .apply()
    }

    private fun loadLastAutoSavedRecordFromLocal() {
        lastSavedDate = prefs.getString("lastSavedDate", "") ?: ""
        lastSavedScreenTimeMinutes = prefs.getLong("lastSavedScreenTimeMinutes", 0L)
        lastSavedMaterialCount = prefs.getLong("lastSavedMaterialCount", 0L)

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

        if (hasUsageStatsPermission()) {
            val minutes = getYesterdayScreenTimeMinutes()
            screenTimeMinutes = minutes
            materialCount = calculateMaterial(minutes)
            saveLastRecordToLocal()
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
            0L
        }
    }

    private fun addRewardTicketType() {
        val name = rewardNameInput
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

        setMessage("${name}を報酬チケットに追加しました", "${name}をふやしたよ")
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
            setMessage("${ticketName}をクラフトしました", "${ticketName}をつくったよ")
            autoSaveCurrentStateSilently()
        } else {
            setMessage("素材が足りません", "そざいがたりないよ")
        }
    }

    private fun requestApproval(index: Int) {
        val ticket = tickets[index]
        tickets[index] = ticket.copy(status = "承認待ち")
        setMessage("${ticket.name}を親に承認申請しました", "${ticket.name}をおねがいしたよ")
        autoSaveCurrentStateSilently()
    }

    private fun approveTicket(index: Int) {
        val ticket = tickets[index]
        tickets[index] = ticket.copy(status = "承認済み")
        setMessage("${ticket.name}が承認されました", "${ticket.name}がOKになったよ")
        autoSaveCurrentStateSilently()
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
        if (taskInput.isNotBlank()) {
            tasks.add(
                DailyTask(
                    title = taskInput,
                    completed = false
                )
            )
            setMessage("タスクを追加しました", "やることをふやしたよ")
            taskInput = ""
            autoSaveCurrentStateSilently()
        } else {
            setMessage("タスク名を入力してください", "やることをいれてね")
        }
    }

    private fun toggleTask(index: Int) {
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
                setMessage("家族ルームに参加していないためFirebaseには保存できません", "へやにはいっていないから、まだのこせないよ")
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
            "savedBy" to "MainActivity",
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
                            setMessage("前日データのFirebase保存に失敗しました", "データをのこせなかったよ")
                        }
                    }
            }
            .addOnFailureListener {
                if (showMessage) {
                    setMessage("ルームメンバー情報のFirebase保存に失敗しました", "へやのデータをのこせなかったよ")
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
            rewardTicketTypes.add(RewardTicketType("おやつがふえるチケット", 5))
            rewardTicketTypes.add(RewardTicketType("おこづかい10えんチケット", 10))
            rewardTicketTypes.add(RewardTicketType("きせかえチケット", 8))
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
            tasks.add(DailyTask("しゅくだいをする", false))
            tasks.add(DailyTask("かんじドリルをする", false))
            tasks.add(DailyTask("けいさんドリルをする", false))
            tasks.add(DailyTask("ほんをよむ", false))
        }
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
            .apply()
    }

    private fun loadCommunitySettings() {
        hasSelectedRole = prefs.getBoolean("hasSelectedRole", false)

        roomIdInput = prefs.getString("roomId", "room001") ?: "room001"
        inviteCodeInput = prefs.getString("inviteCode", "") ?: ""
        memberIdInput = prefs.getString("memberId", "member001") ?: "member001"
        memberNameInput = prefs.getString("memberName", "たろう") ?: "たろう"
        memberRole = prefs.getString("memberRole", "子供") ?: "子供"
        isInFamilyRoom = prefs.getBoolean("isInFamilyRoom", false)

        targetMinutes = prefs.getLong("targetMinutes", 60L)
        targetInput = targetMinutes.toString()

        isParentMode = memberRole == "保護者"

        if (!hasSelectedRole) {
            message = "がめんをえらんでください"
        }
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