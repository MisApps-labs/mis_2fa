// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/MisApps-labs/mis_2fa

package com.ferlagod.mis2fa.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap
import com.ferlagod.mis2fa.util.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Descarga el icono de un servicio desde **su propia web** (sin pasar por servicios de terceros
 * que recopilan favicons, que verían la lista entera de cuentas).
 *
 * Solo se usa cuando el usuario lo pide. La web del servicio ve la IP del dispositivo y que se
 * ha pedido su icono, igual que si se abriera en el navegador; no se envía nada de la cuenta.
 *
 * El icono se guarda en la bóveda como PNG de [ICON_SIZE] px en Base64.
 */
object ServiceIcons {

    private const val TAG = "ServiceIcons"

    /** Lado del icono guardado. Basta para el avatar y mantiene la bóveda pequeña. */
    const val ICON_SIZE = 96

    /** Un icono guardado no puede pasar de esto (en Base64): protege el tamaño de la bóveda. */
    const val MAX_STORED_CHARS = 48 * 1024 // por debajo de Token.MAX_ICON_CHARS

    private const val MAX_HTML_BYTES = 512L * 1024
    private const val MAX_IMAGE_BYTES = 1024L * 1024
    private const val MAX_IMAGE_SIDE = 4096
    private const val MIN_IMAGE_SIDE = 16

    /** Sin cookies ni caché; con redirecciones (muchas webs mandan a `www.`). Solo HTTPS por la configuración de red. */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Agente genérico: algunas webs rechazan clientes desconocidos. No identifica al usuario. */
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"

    /** Emisores cuyo dominio no es "nombre.com". */
    private val KNOWN_DOMAINS = mapOf(
        "aws" to "aws.amazon.com",
        "amazonwebservices" to "aws.amazon.com",
        "twitter" to "x.com",
        "x" to "x.com",
        "proton" to "proton.me",
        "protonmail" to "proton.me",
        "protonmailch" to "proton.me",
        "tutanota" to "tuta.com",
        "tuta" to "tuta.com",
        "microsoft" to "microsoft.com",
        "microsoftaccount" to "microsoft.com",
        "googleaccount" to "google.com",
        "gmail" to "google.com",
        "ubisoft" to "ubisoft.com",
        "epicgames" to "epicgames.com",
        "steam" to "steampowered.com",
        "battlenet" to "battle.net",
        "blizzard" to "battle.net",
        "binance" to "binance.com",
        "coinbase" to "coinbase.com",
        "bitwarden" to "bitwarden.com",
        "1password" to "1password.com",
        "nintendo" to "nintendo.com",
        "nintendoaccount" to "nintendo.com",
        "playstation" to "playstation.com",
        "sony" to "playstation.com",
        "psn" to "playstation.com",
        "ea" to "ea.com",
        "electronicarts" to "ea.com",
        "gitlab" to "gitlab.com",
        "npm" to "npmjs.com",
        "npmjs" to "npmjs.com",
        "pypi" to "pypi.org",
        "wikipedia" to "wikipedia.org",
        "mozilla" to "mozilla.org",
        "firefox" to "mozilla.org",
        "hetzner" to "hetzner.com",
        "ovh" to "ovhcloud.com",
        "ovhcloud" to "ovhcloud.com",
        "digitalocean" to "digitalocean.com",
        "cloudflare" to "cloudflare.com",
        "namecheap" to "namecheap.com",
        "kraken" to "kraken.com",
        "mega" to "mega.io",
        "linkedin" to "linkedin.com",
        "reddit" to "reddit.com",
        "discord" to "discord.com",
        "mastodon" to "joinmastodon.org",
        "mastodonsocial" to "mastodon.social",
        "codeberg" to "codeberg.org",
        "codebergorg" to "codeberg.org",
        "nextcloud" to "nextcloud.com",
        "adobe" to "adobe.com",
        "adobeid" to "adobe.com",
        "apple" to "apple.com",
        "appleid" to "apple.com",
        "paypal" to "paypal.com",
        "dropbox" to "dropbox.com",
        "facebook" to "facebook.com",
        "meta" to "facebook.com",
        "instagram" to "instagram.com",
        "tiktok" to "tiktok.com",
        "twitch" to "twitch.tv",
        "slack" to "slack.com",
        "zoom" to "zoom.us",
        "atlassian" to "atlassian.com",
        "jira" to "atlassian.com",
        "bitbucket" to "bitbucket.org",
        "docker" to "docker.com",
        "dockerhub" to "hub.docker.com",
        "heroku" to "heroku.com",
        "vercel" to "vercel.com",
        "netlify" to "netlify.com",
        "openai" to "openai.com",
        "chatgpt" to "openai.com",
        "anthropic" to "anthropic.com",
        "claude" to "anthropic.com",
        "ionos" to "ionos.com",
        "godaddy" to "godaddy.com",
        "revolut" to "revolut.com",
        "wise" to "wise.com",
        "n26" to "n26.com",
    )

    private val DOMAIN_REGEX = Regex("""^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$""")

    /**
     * Propone un dominio a partir del emisor: "GitHub" → github.com, "Proton" → proton.me,
     * "login.example.org" → login.example.org. Null si no hay emisor (la cuenta suele ser un email
     * de otro servicio, así que no sirve para adivinar).
     */
    fun guessDomain(issuer: String): String? {
        val raw = issuer.trim()
        if (raw.isEmpty()) return null
        normalizeDomain(raw)?.let { if ('.' in it) return it }
        // "Google (Trabajo)", "GitHub: empresa" o "Slack - equipo": lo que va detrás sobra.
        val name = raw.split('(', '[', ':', '|', '/').first().substringBefore(" - ").trim().ifEmpty { raw }
        // Quita acentos y todo lo que no sea letra o número: "Epic Games" → "epicgames".
        val key = TextSearch.fold(name).filter { it.isLetterOrDigit() && it.code < 128 }
        if (key.isEmpty()) return null
        KNOWN_DOMAINS[key]?.let { return it }
        // "Microsoft Outlook": si la primera palabra es un servicio conocido, se usa esa.
        val firstWord = TextSearch.fold(name).split(Regex("[^a-z0-9]+")).firstOrNull { it.isNotEmpty() }
        if (firstWord != null && firstWord != key) KNOWN_DOMAINS[firstWord]?.let { return it }
        return "$key.com"
    }

    /** Deja solo el nombre de máquina de lo que escriba el usuario ("https://www.x.com/login" → www.x.com). */
    fun normalizeDomain(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
        s = s.trimEnd('.')
        val host = "https://$s/".toHttpUrlOrNull()?.host ?: return null
        return host.takeIf { DOMAIN_REGEX.matches(it) }
    }

    private data class Candidate(val url: HttpUrl, val score: Int)

    /**
     * Enlaces a iconos que declara una página (`<link rel="icon">`, `apple-touch-icon`…), del
     * más adecuado al menos. Se descartan los SVG, que Android no sabe decodificar.
     */
    fun parseIconLinks(html: String, base: HttpUrl): List<HttpUrl> {
        val candidates = mutableListOf<Candidate>()
        for (tag in LINK_TAG.findAll(html)) {
            val attrs = attributes(tag.value)
            val rel = attrs["rel"]?.lowercase()?.split(Regex("\\s+")) ?: continue
            if ("mask-icon" in rel) continue
            val isTouch = rel.any { it.startsWith("apple-touch-icon") }
            if (!isTouch && "icon" !in rel) continue
            val href = attrs["href"]?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("data:") } ?: continue
            val type = attrs["type"]?.lowercase().orEmpty()
            if ("svg" in type || href.substringBefore('?').lowercase().endsWith(".svg")) continue
            val url = base.resolve(href)?.takeIf { it.isHttps } ?: continue
            val size = attrs["sizes"]?.lowercase()?.split(Regex("\\s+"))
                ?.mapNotNull { it.substringBefore('x').toIntOrNull() }?.maxOrNull()
            val effective = size ?: if (isTouch) 180 else 32
            // Lo ideal es un icono algo mayor que el guardado; uno enorme solo cuesta más descarga.
            val score = if (effective >= ICON_SIZE) 10_000 - effective else effective
            candidates += Candidate(url, score)
        }
        return candidates.sortedByDescending { it.score }.map { it.url }.distinct()
    }

    private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

    private fun attributes(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[2] ?: m.groups[3] ?: m.groups[4])!!.value
        }

    private fun decodeEntities(s: String) = s.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"").replace("&#47;", "/")

    /**
     * Busca y descarga el icono de [domain].
     *
     * @return el icono listo para guardar (PNG en Base64), o null si no se encuentra ninguno.
     */
    suspend fun fetch(domain: String): String? = withContext(Dispatchers.IO) {
        val host = normalizeDomain(domain) ?: return@withContext null
        val home = "https://$host/".toHttpUrlOrNull() ?: return@withContext null
        val candidates = mutableListOf<HttpUrl>()
        var origin = home
        try {
            get(home, MAX_HTML_BYTES)?.let { (finalUrl, bytes) ->
                origin = finalUrl
                candidates += parseIconLinks(String(bytes, Charsets.UTF_8), finalUrl)
            }
        } catch (e: Exception) {
            Log.i(TAG, "No se puede abrir la web de $host: ${e.javaClass.simpleName}")
        }
        for (path in listOf("/apple-touch-icon.png", "/favicon.ico")) {
            origin.resolve(path)?.let { candidates += it }
            if (origin.host != home.host) home.resolve(path)?.let { candidates += it }
        }
        for (url in candidates.distinct().take(8)) {
            val bytes = try {
                get(url, MAX_IMAGE_BYTES)?.second
            } catch (e: Exception) {
                null
            } ?: continue
            encode(bytes)?.let { return@withContext it }
        }
        null
    }

    /** GET con límite de tamaño. Devuelve la URL final (tras redirecciones) y el contenido. */
    private fun get(url: HttpUrl, maxBytes: Long): Pair<HttpUrl, ByteArray>? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val finalUrl = response.request.url
            if (!finalUrl.isHttps) return null
            val body = response.body ?: return null
            if (body.contentLength() > maxBytes) return null
            val source = body.source()
            if (source.request(maxBytes + 1)) return null
            return finalUrl to source.buffer.readByteArray()
        }
    }

    /**
     * Decodifica una imagen descargada y la convierte en un PNG cuadrado de [ICON_SIZE] px.
     * Rechaza lo que no es imagen y las dimensiones absurdas (para no reservar memoria de más).
     */
    private fun encode(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w < MIN_IMAGE_SIDE || h < MIN_IMAGE_SIDE || w > MAX_IMAGE_SIDE || h > MAX_IMAGE_SIDE) return null
        var sample = 1
        while (minOf(w, h) / (sample * 2) >= ICON_SIZE) sample *= 2
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = createBitmap(ICON_SIZE, ICON_SIZE)
        // Se centra conservando la proporción.
        val scale = ICON_SIZE.toFloat() / maxOf(source.width, source.height)
        val dw = (source.width * scale).toInt().coerceAtLeast(1)
        val dh = (source.height * scale).toInt().coerceAtLeast(1)
        val left = (ICON_SIZE - dw) / 2
        val top = (ICON_SIZE - dh) / 2
        Canvas(out).drawBitmap(source, null, Rect(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        source.recycle()
        val png = ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        out.recycle()
        return Base64.getEncoder().encodeToString(png).takeIf { it.length <= MAX_STORED_CHARS }
    }

    /** Decodifica un icono guardado; null si está dañado. */
    fun decode(icon: String): Bitmap? = try {
        val bytes = Base64.getDecoder().decode(icon)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth in 1..512 && bounds.outHeight in 1..512) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
    } catch (e: IllegalArgumentException) {
        null
    }
}
