package com.reedkit.delta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.reedkit.delta.data.ScoreStore
import com.reedkit.delta.score.ScoreParser
import com.reedkit.delta.service.HarmonicaAccessibilityService
import com.reedkit.delta.service.OverlayController
import com.reedkit.delta.service.OverlayService
import com.reedkit.delta.service.PlayState

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

    val scoreStore = remember { ScoreStore(context) }
    var savedScores by remember { mutableStateOf(scoreStore.list()) }

    var scoreText by remember { mutableStateOf(ScoreParser.SAMPLE) }
    var bpmText by remember { mutableStateOf("90") }
    val playState by OverlayController.playState.collectAsState()
    val playing = playState != PlayState.IDLE

    // txt 导入（SAF）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        }.onSuccess { text ->
            if (!text.isNullOrBlank()) {
                scoreText = text.trim()
                OverlayController.updateScore(scoreText)
                Toast.makeText(context, "乐谱已导入", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 回到前台时刷新权限状态
    LaunchedEffect(Unit) {
        OverlayController.updateScore(scoreText)
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
            description = "悬浮窗用于在游戏内控制开始 / 暂停 / 停止，并执行按键校准。"
        ) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                )
            }) { Text("去授权") }
        }

        // —— 第三步：校准（引导式，不再点击即开始）——
        GuideCard(
            step = "第 3 步",
            title = "按键校准",
            done = calibrated,
            description = "先启动悬浮窗，然后进入游戏打开口琴界面，在悬浮窗上点「校准」按钮，按提示依次点击 8 个音符键（1–i）与 4 个调性键。可随时重新校准。"
        ) {
            Button(
                enabled = overlayOn,
                onClick = {
                    OverlayService.start(context)
                    Toast.makeText(
                        context,
                        "悬浮窗已启动，请进入游戏后点悬浮窗上的「校准」",
                        Toast.LENGTH_LONG
                    ).show()
                }
            ) { Text(if (calibrated) "重新校准" else "启动校准引导") }
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

                // txt 导入 + 保存
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("text/plain"))
                    }) { Text("导入 txt") }
                    OutlinedButton(
                        enabled = scoreText.isNotBlank(),
                        onClick = {
                            val name = "乐谱 ${savedScores.size + 1}"
                            scoreStore.save(name, scoreText, bpmText.toIntOrNull() ?: 90)
                            savedScores = scoreStore.list()
                            Toast.makeText(context, "已保存为「$name」", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("保存乐谱") }
                }

                // 已保存乐谱列表
                if (savedScores.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) {
                            Text("我的乐谱（${savedScores.size}）")
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            savedScores.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.name} · ${s.bpm} BPM") },
                                    onClick = {
                                        scoreText = s.text
                                        bpmText = s.bpm.toString()
                                        OverlayController.updateScore(s.text)
                                        OverlayController.updateBpm(s.bpm)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 演奏控制
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = accessibilityOn && calibrated && scoreText.isNotBlank() && !playing,
                        onClick = {
                            OverlayController.updateScore(scoreText)
                            OverlayController.start(context)
                        }
                    ) { Text("开始") }
                    OutlinedButton(
                        enabled = playing,
                        onClick = {
                            if (playState == PlayState.PLAYING) OverlayController.pause()
                            else OverlayController.resume()
                        }
                    ) { Text(if (playState == PlayState.PAUSED) "继续" else "暂停") }
                    OutlinedButton(
                        enabled = playing,
                        onClick = { OverlayController.stop() }
                    ) { Text("停止") }
                }

                OutlinedButton(
                    enabled = overlayOn,
                    onClick = { OverlayService.start(context) }
                ) { Text("显示悬浮窗") }
            }
        }

        // —— 说明 ——
        ElevatedCard {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "使用流程：开启无障碍 → 授权悬浮窗 → 启动悬浮窗后进入游戏打开口琴 → " +
                        "在悬浮窗上完成校准 → 切回游戏，用悬浮窗的开始 / 暂停 / 停止控制演奏。\n\n" +
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
