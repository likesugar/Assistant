package com.ziyuan.Assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziyuan.Assistant.HomeActivity
import com.ziyuan.Assistant.R

class ComposeHome : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HomeScreen() }
    }
}

private data class Func(val id: Int, val icon: String, val title: String, val sub: String, val startColor: Color, val endColor: Color)

private val FUNCS = listOf(
    Func(R.id.cardPlayer, "🪩", "资源嗅探", "网页媒体嗅探", Color(0xFF5C6BC0), Color(0xFF3949AB)),
    Func(R.id.cardDownload, "➼", "资源下载", "下载管理器", Color(0xFF42A5F5), Color(0xFF1E88E5)),
    Func(R.id.cardVideoDl, "🎬", "视频下载", "在线视频解析", Color(0xFFEC407A), Color(0xFFD81B60)),
    Func(R.id.cardSpider, "🕷", "嗅探引擎", "Spider 引擎", Color(0xFF26A69A), Color(0xFF00897B)),
    Func(R.id.cardXhs, "📕", "小红书", "图文视频提取", Color(0xFFEF5350), Color(0xFFE53935)),
    Func(R.id.cardLive, "📺", "直播", "直播间录制", Color(0xFFFFA726), Color(0xFFF57C00)),
    Func(R.id.cardChat, "💬", "聊天室", "WebChat", Color(0xFFAB47BC), Color(0xFF8E24AA)),
    Func(R.id.calendarCard, "📅", "日历", "签到与日程", Color(0xFF66BB6A), Color(0xFF43A047)),
    Func(R.id.cardMed, "💊", "用药提醒", "按时吃药", Color(0xFFEF5350), Color(0xFFE53935)),
    Func(R.id.cardTapper, "👆", "点击器", "自动连点", Color(0xFF42A5F5), Color(0xFF1E88E5)),
    Func(R.id.cardMetronome, "🥁", "节拍器", "节拍+天气", Color(0xFF26A69A), Color(0xFF00897B)),
    Func(R.id.cardWeb2Apk, "🌐", "网页转应用", "H5 打包应用", Color(0xFF66BB6A), Color(0xFF43A047)),
    Func(R.id.cardGkd, "🛡", "GKD", "跳过广告", Color(0xFFAB47BC), Color(0xFF8E24AA)),
    Func(R.id.cardH5Ed, "⟩", "HTML5", "WebCat 编辑器", Color(0xFFFFA726), Color(0xFFF57C00)),
    Func(R.id.cardEnt, "🎡", "娱乐", "小游戏", Color(0xFFEC407A), Color(0xFFD81B60)),
    Func(R.id.cardShortcut, "⚡", "快捷方式", "创建快捷键", Color(0xFF5C6BC0), Color(0xFF3949AB)),
)

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color(0xFFF3F5F9))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 渐变 Header
            Box(
                Modifier.fillMaxWidth().padding(16.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFFAB47BC))), RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                Column {
                    Text(greeting(), color = Color(0xDDFFFFFF), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("助手工具箱", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            // 卡片网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(FUNCS) { f ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.clickable {
                            // 跳转原功能页（复用原 Activity 路由）
                            try {
                                val intent = android.content.Intent(ctx, Class.forName("com.ziyuan.Assistant.HomeActivity"))
                                intent.putExtra("open_card", ctx.resources.getResourceEntryName(f.id))
                                ctx.startActivity(intent)
                            } catch (_: Throwable) { }
                        }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp)
                                    .background(Brush.linearGradient(listOf(f.startColor, f.endColor)), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(f.icon, fontSize = 20.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(f.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2329))
                                Spacer(Modifier.height(2.dp))
                                Text(f.sub, fontSize = 11.sp, color = Color(0xFF8A94A6))
                            }
                        }
                    }
                }
            }
        }
        // 胶囊底栏
        CapsuleBar(selectedTab) { selectedTab = it }
    }
}

@Composable
private fun CapsuleBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("首页" to "🏠", "工具" to "🧰", "设置" to "⚙️")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF3A3F52),
            shadowElevation = 12.dp,
            modifier = Modifier.padding(bottom = 28.dp)
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, (label, icon) ->
                    val isSel = selected == i
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSel) Color(0xFF5C6BC0) else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 14.sp)
                            if (isSel) { Spacer(Modifier.width(6.dp)); Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "夜深了"
        h < 12 -> "早上好"
        h < 18 -> "下午好"
        else -> "晚上好"
    }
}
