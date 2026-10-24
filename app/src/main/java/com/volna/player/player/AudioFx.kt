package com.volna.player.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 10-полосный эквалайзер на DynamicsProcessing (Android 9+).
 * На Pixel 7 Pro работает с нулевой задержкой и не грузит процессор.
 * Если устройство не поддерживает — откат на стандартный Equalizer.
 */
object AudioFx {
    val freqs = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val labels = listOf("32", "64", "125", "250", "500", "1к", "2к", "4к", "8к", "16к")
    const val MAX_DB = 12f
    const val CUSTOM = "Свой"

    val presets: List<Pair<String, List<Float>>> = listOf(
        "Обычный" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Бас" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Супербас" to listOf(9f, 8f, 6f, 3f, 0f, -1f, 0f, 1f, 2f, 2f),
        "Фонк" to listOf(8f, 7f, 4f, 0f, -2f, -1f, 1f, 3f, 4f, 3f),
        "Хип-хоп" to listOf(5f, 4f, 2f, 3f, -1f, -1f, 1f, 0f, 2f, 3f),
        "Электроника" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f),
        "Рок" to listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 4f),
        "Вокал" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 2f, 0f, -1f),
        "Акустика" to listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 2f, 2f, 1f),
        "Высокие" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f),
        "Ночь" to listOf(2f, 2f, 1f, 0f, 0f, -1f, -2f, -3f, -4f, -5f),
    )

    private lateinit var prefs: SharedPreferences

    private val _gains = MutableStateFlow(List(freqs.size) { 0f })
    val gains: StateFlow<List<Float>> = _gains.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _preset = MutableStateFlow("Обычный")
    val preset: StateFlow<String> = _preset.asStateFlow()

    private val _supported = MutableStateFlow(true)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    private var dp: DynamicsProcessing? = null
    private var legacy: Equalizer? = null

    fun attach(ctx: Context, sessionId: Int) {
        prefs = ctx.getSharedPreferences("eq", Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean("on", true)
        _preset.value = prefs.getString("preset", "Обычный") ?: "Обычный"
        prefs.getString("gains", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == freqs.size }?.let { _gains.value = it }

        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,          // стерео
                true, freqs.size, // pre-EQ: 10 полос
                false, 0,   // компрессор не нужен
                false, 0,   // post-EQ не нужен
                true,       // лимитер — защита от перегруза при подъёме басов
            ).build()
            dp = DynamicsProcessing(0, sessionId, cfg)
        } catch (e: Throwable) {
            dp = null
            try {
                legacy = Equalizer(0, sessionId)
            } catch (e2: Throwable) {
                _supported.value = false
            }
        }
        applyAll()
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean("on", on).apply()
        applyAll()
    }

    fun setBand(i: Int, db: Float) {
        val v = db.coerceIn(-MAX_DB, MAX_DB)
        _gains.value = _gains.value.toMutableList().also { it[i] = v }
        _preset.value = CUSTOM
        val d = dp
        if (d != null && _enabled.value) {
            try {
                d.setPreEqBandAllChannelsTo(i, band(i, v))
                d.setInputGainAllChannelsTo(headroom())
            } catch (e: Throwable) {
            }
        } else {
            applyLegacy()
        }
        save()
    }

    fun applyPreset(name: String) {
        val p = presets.firstOrNull { it.first == name } ?: return
        _gains.value = p.second
        _preset.value = name
        applyAll()
        save()
    }

    fun reset() = applyPreset("Обычный")

    private fun band(i: Int, gain: Float): DynamicsProcessing.EqBand {
        val cutoff = if (i == freqs.lastIndex) 20000f
        else sqrt(freqs[i].toFloat() * freqs[i + 1].toFloat())
        return DynamicsProcessing.EqBand(true, cutoff, gain)
    }

    /** При подъёме полос немного убавляем вход, чтобы не было хрипа. */
    private fun headroom(): Float {
        val maxBoost = max(0f, _gains.value.maxOrNull() ?: 0f)
        return -maxBoost * 0.5f
    }

    private fun applyAll() {
        val on = _enabled.value
        val g = _gains.value
        dp?.let { d ->
            try {
                val eq = DynamicsProcessing.Eq(true, true, freqs.size)
                for (i in freqs.indices) eq.setBand(i, band(i, g[i]))
                d.setPreEqAllChannelsTo(eq)
                d.setInputGainAllChannelsTo(if (on) headroom() else 0f)
                d.setEnabled(on)
            } catch (e: Throwable) {
            }
        }
        applyLegacy()
    }

    private fun applyLegacy() {
        val e = legacy ?: return
        try {
            val range = e.bandLevelRange
            val g = _gains.value
            for (b in 0 until e.numberOfBands) {
                val centerHz = e.getCenterFreq(b.toShort()) / 1000
                var nearest = 0
                for (i in freqs.indices) {
                    if (abs(freqs[i] - centerHz) < abs(freqs[nearest] - centerHz)) nearest = i
                }
                val level = (g[nearest] * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(b.toShort(), level.toShort())
            }
            e.setEnabled(_enabled.value)
        } catch (t: Throwable) {
        }
    }

    private fun save() {
        prefs.edit()
            .putString("gains", _gains.value.joinToString(","))
            .putString("preset", _preset.value)
            .apply()
    }
}
