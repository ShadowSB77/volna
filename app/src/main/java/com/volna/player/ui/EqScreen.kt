package com.volna.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volna.player.player.AudioFx
import com.volna.player.ui.theme.VColors
import kotlin.math.roundToInt

private val SliderInset = 18.dp

@Composable
fun EqScreen() {
    val gains by AudioFx.gains.collectAsStateWithLifecycle()
    val enabled by AudioFx.enabled.collectAsStateWithLifecycle()
    val preset by AudioFx.preset.collectAsStateWithLifecycle()
    val supported by AudioFx.supported.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Эквалайзер", Modifier.weight(1f).padding(start = 4.dp))
            Switch(checked = enabled, onCheckedChange = { AudioFx.setEnabled(it) })
        }
        Text(
            if (supported) "10 полос, работает для всего, что играет в приложении"
            else "Это устройство не поддерживает аудиоэффекты",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 14.dp),
        )

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 14.dp, horizontal = 6.dp)
                .alpha(if (enabled) 1f else 0.4f),
        ) {
            // значения полос
            Row(Modifier.fillMaxWidth()) {
                gains.forEach { g ->
                    Text(
                        (if (g > 0) "+" else "") + (if (g % 1f == 0f) g.toInt().toString() else "%.1f".format(g)),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            EqPanel(gains, enabled) { i, v -> AudioFx.setBand(i, v) }
            Row(Modifier.fillMaxWidth()) {
                AudioFx.labels.forEach { l ->
                    Text(
                        l, fontSize = 11.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                "Гц",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Пресеты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { AudioFx.reset() }) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.padding(3.dp))
                Text("Сбросить")
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            val names = AudioFx.presets.map { it.first } + AudioFx.CUSTOM
            items(names) { name ->
                FilterChip(
                    selected = preset == name,
                    onClick = { if (name != AudioFx.CUSTOM) AudioFx.applyPreset(name) },
                    label = { Text(name) },
                    enabled = enabled,
                )
            }
        }
        Text(
            "Совет: тяни ползунки вверх-вниз. Лимитер защищает от хрипа, даже если сильно поднять басы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp),
        )
    }
}

@Composable
private fun EqPanel(gains: List<Float>, enabled: Boolean, onChange: (Int, Float) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        // плавная кривая АЧХ под ползунками
        Canvas(Modifier.fillMaxSize().padding(vertical = SliderInset)) {
            val n = gains.size
            val w = size.width / n
            val h = size.height
            for (db in listOf(-6f, 0f, 6f)) {
                val y = h * (0.5f - db / (2 * AudioFx.MAX_DB))
                drawLine(
                    Color.White.copy(alpha = if (db == 0f) 0.14f else 0.06f),
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                )
            }
            val pts = gains.mapIndexed { i, g -> Offset(w * (i + 0.5f), h * (0.5f - g / (2 * AudioFx.MAX_DB))) }
            val curve = Path().apply {
                moveTo(0f, pts[0].y)
                lineTo(pts[0].x, pts[0].y)
                for (i in 1 until n) {
                    val p0 = pts[i - 1]
                    val p1 = pts[i]
                    val mx = (p0.x + p1.x) / 2f
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                }
                lineTo(size.width, pts.last().y)
            }
            val fill = Path().apply {
                addPath(curve)
                lineTo(size.width, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(primary.copy(alpha = 0.32f), Color.Transparent)))
            drawPath(
                curve,
                Brush.horizontalGradient(VColors.brandColors),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Row(Modifier.fillMaxSize()) {
            gains.forEachIndexed { i, g ->
                VerticalBandSlider(
                    value = g,
                    enabled = enabled,
                    onChange = { onChange(i, it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun VerticalBandSlider(value: Float, enabled: Boolean, onChange: (Float) -> Unit, modifier: Modifier) {
    val cb by rememberUpdatedState(onChange)
    val on by rememberUpdatedState(enabled)
    val primary = MaterialTheme.colorScheme.primary

    fun toDb(y: Float, height: Float): Float {
        val f = (y / height).coerceIn(0f, 1f)
        val db = (0.5f - f) * 2f * AudioFx.MAX_DB
        return (db * 2f).roundToInt() / 2f   // шаг 0.5 дБ
    }

    Box(
        modifier
            .padding(vertical = SliderInset)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> if (on) cb(toDb(o.y, size.height.toFloat())) },
                    onVerticalDrag = { change, _ ->
                        if (on) {
                            change.consume()
                            cb(toDb(change.position.y, size.height.toFloat()))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (on) cb(0f) },
                    onTap = { o -> if (on) cb(toDb(o.y, size.height.toFloat())) },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val h = size.height
            val y = h * (0.5f - value / (2 * AudioFx.MAX_DB))
            drawLine(
                Color.White.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, h),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
            drawLine(
                primary, Offset(x, h / 2f), Offset(x, y),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
            drawCircle(primary, radius = 10.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }
}
