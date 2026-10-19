package com.example.lyrics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R

private val DEFAULT_SAMPLE_LRC = """
[00:00.00] •••
[00:03.00] 腕间的微光 随节拍亮起 / Wristlight awakens with the beat
[00:07.50] 穿越深邃夜空 化作流星 / Crossing the deep night sky like a meteor
[00:12.20] 每一句歌词 都在掌心呼吸 / Every lyric line breathes in your palm
[00:17.00] 顺滑滚动着 温柔的记忆 / Smoothly scrolling through gentle memories
[00:22.00] •••
[00:26.00] 即便在最小巧的屏幕里 / Even inside the most compact screen
[00:31.00] 也能拥抱整片璀璨银河 / You can embrace the entire glittering galaxy
""".trimIndent()

@Composable
fun CustomLrcImportDialog(
    accentColor: Color,
    onDismiss: () -> Unit,
    onApplyLrc: (title: String, artist: String, lrcContent: String) -> Unit
) {
    var title by remember { mutableStateOf("腕间星河 (Custom Live)") }
    var artist by remember { mutableStateOf("Wear Studio") }
    var lrcText by remember { mutableStateOf(DEFAULT_SAMPLE_LRC) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141722),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_lrc_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_lrc_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = "支持标准 [mm:ss.xx] 时间轴与「原文 / 译文」双语格式，自动生成逐字高亮时间轴：",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("歌曲名") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lrc_title_input")
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("歌手") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lrc_artist_input")
                    )
                }

                OutlinedTextField(
                    value = lrcText,
                    onValueChange = { lrcText = it },
                    label = { Text("LRC 歌词内容") },
                    minLines = 6,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lrc_content_input")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lrc_cancel_button")
                    ) {
                        Text(stringResource(R.string.custom_lrc_cancel), color = Color.White)
                    }
                    Button(
                        onClick = {
                            onApplyLrc(title, artist, lrcText)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lrc_apply_button")
                    ) {
                        Text(
                            text = stringResource(R.string.custom_lrc_apply),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
