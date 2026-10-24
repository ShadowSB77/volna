package com.volna.player.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object Http {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    class HttpException(val code: Int) : IOException("HTTP $code")

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36"

    fun open(url: String, desktop: Boolean = false): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", if (desktop) DESKTOP_UA else UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
        return c
    }

    fun getText(url: String, desktop: Boolean = false): String {
        val c = open(url, desktop)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpException(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Раскрывает короткие ссылки вида on.soundcloud.com/xxxx. */
    fun finalUrl(url: String): String {
        val c = open(url)
        try {
            c.responseCode
            return c.url.toString()
        } finally {
            c.disconnect()
        }
    }

    fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException -> "Нет интернета"
        is SocketTimeoutException -> "Сервер долго не отвечает, попробуй ещё раз"
        is HttpException -> when (e.code) {
            404 -> "Не найдено: ссылка неверная или плейлист приватный"
            401, 403 -> "Доступ закрыт"
            429 -> "Слишком много запросов, подожди минуту"
            else -> "Ошибка сервера (${e.code})"
        }
        else -> e.message ?: "Что-то пошло не так"
    }
}
