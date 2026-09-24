package com.reedkit.delta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reedkit.delta.data.CalibrationStore
import com.reedkit.delta.data.Keys
import com.reedkit.delta.score.ScoreParser
import com.reedkit.delta.service.HarmonicaAccessibilityService
import com.reedkit.delta.service.OverlayController
import com.reedkit.delta.service.OverlayService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReedkitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun ReedkitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Material You 莫奈动态取色（minSdk 31+ 原生支持）
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(HarmonicaAccessibilityService.isEnabled()) }
    var overlayOn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val store = remember { CalibrationStore(context) }
    var calibrated by remember { mutableStateOf(store.allCalibrated()) }

    var scoreText by remember { mutableStateOf(ScoreParser.SAMPLE) }
    var bpmText by remember { mutableStateOf("90") }
    val isPlaying by OverlayController.isPlaying.collectAsState()

    // 回到前台时刷新权限状态
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityOn = HarmonicaAccessibilityService.isEnabled()
            overlayOn = Settings.canDrawOverlays(context)
            calibrated = store.allCalibrated()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Reedkit",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "三角洲行动 · 口琴自动演奏",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // —— 第一步：无障碍 ——
        GuideCard(
            step = "第 1 步",
            title = "开启无障碍服务",
            done = accessibilityOn,
            description = "用于模拟手势点击游戏按键（canPerformGestures）。点击按钮跳转系统设置，找到「Reedkit」并开启。"
        ) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("去开启") }
        }

        // —— 第二步：悬浮窗 ——
        GuideCard(
            step = "第 2 步",
            title = "允许悬浮窗",
            done = overlayOn,
            description = "悬浮窗用于在游戏内控制开始 / 停止，并执行按键校准。"
        ) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                )
            }) { Text("去授权") }
        }

        // —— 第三步：校准 ——
        GuideCard(
            step = "第 3 步",
            title = "按键校准",
            done = calibrated,
            description = "打开游戏口琴界面后启动校准，按提示依次点击 8 个音符键（1–i）与 4 个调性键（半音/升调/自然音/降调），系统会自动记录坐标。"
        ) {
            Button(
                enabled = overlayOn,
                onClick = {
                    OverlayService.start(context)
                    val i = Intent(context, OverlayService::class.java).setAction("calibrate")
                    context.startForegroundService(i)
                }
            ) { Text("开始校准") }
        }

        // —— 乐谱与演奏 ——
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("乐谱演奏", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "简谱格式：1 2 3 … i；# 表示半音，^ 升调，, 降调；- 延长一拍，0 休止；" +
                        "支持「自然音:…; 升调:…」状态段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { scoreText = it; OverlayController.updateScore(it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("乐谱") }
                )
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = {
                        bpmText = it.filter(Char::isDigit)
                        bpmText.toIntOrNull()?.let(OverlayController::updateBpm)
                    },
                    label = { Text("BPM（30–300）") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = accessibilityOn && calibrated && scoreText.isNotBlank(),
                        onClick = {
                            OverlayController.updateScore(scoreText)
                            OverlayController.toggle(context)
                        }
                    ) {
                        Text(if (isPlaying) "停止" else "开始演奏")
                    }
                    OutlinedButton(
                        enabled = overlayOn,
                        onClick = { OverlayService.start(context) }
                    ) { Text("显示悬浮窗") }
                }
            }
        }

        // —— 说明 ——
        ElevatedCard {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "使用流程：开启无障碍 → 授权悬浮窗 → 在游戏中打开口琴 → 回到本应用启动校准 " +
                        "→ 粘贴乐谱并设置 BPM → 点击开始，或切回游戏用悬浮窗控制。\n\n" +
                        "演奏时先切换音区（互斥），再切换半音（独立开关），已在目标状态时自动跳过，最后点击音符键。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun GuideCard(
    step: String,
    title: String,
    done: Boolean,
    description: String,
    action: @Composable () -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("$step · $title", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!done) action()
        }
    }
}