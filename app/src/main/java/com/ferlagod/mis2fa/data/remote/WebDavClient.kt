// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis 2FA — https://github.com/ferlagod/mis_2fa

package com.ferlagod.mis2fa.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error HTTP devuelto por el servidor WebDAV. */
open class WebDavException(val code: Int, message: String) : IOException(message)

/** 401/403: usuario o contraseña de aplicación incorrectos. */
class WebDavAuthException(code: Int) : WebDavException(code, "Usuario o contraseña de aplicación incorrectos ($code)")

/** 409 al crear la carpeta: la ruta WebDAV no existe (usuario o URL mal escritos). */
class WebDavPathNotFoundException : WebDavException(409, "La ruta WebDAV no existe")

/** La dirección del servidor no es una URL válida. */
class WebDavInvalidUrlException(url: String) : WebDavException(0, "URL no válida: $url")

/** 412: el fichero remoto cambió desde la última lectura (control de concurrencia optimista). */
class WebDavPreconditionFailedException : WebDavException(412, "El fichero cambió en el servidor")

/** El fichero remoto supera [WebDavClient.MAX_DOWNLOAD_BYTES]: no es una bóveda de Mis 2FA. */
class WebDavTooLargeException : WebDavException(413, "El fichero del servidor es demasiado grande")

/** Fichero descargado junto con su ETag. */
class RemoteFile(val bytes: ByteArray, val etag: String?)

/**
 * Cliente WebDAV mínimo para Nextcloud / ownCloud (RFC 4918).
 *
 * Usa `If-Match` / `If-None-Match` en las subidas para no sobrescribir nunca un cambio
 * remoto que no hayamos visto.
 */
class WebDavClient(
    rootUrl: String,
    username: String,
    appPassword: String,
    private val http: OkHttpClient = sharedHttpClient
) {
    private val rootUrl: String = (if (rootUrl.endsWith("/")) rootUrl else "$rootUrl/").also {
        if (it.toHttpUrlOrNull() == null) throw WebDavInvalidUrlException(it)
    }
    private val authHeader: String = Credentials.basic(username, appPassword, Charsets.UTF_8)

    companion object {
        private const val USER_AGENT = "Mis2FA-Android"

        /**
         * Una bóveda con miles de cuentas ocupa unos cientos de KB. Más que esto es un error del
         * servidor (o un fichero ajeno) y cargarlo entero en memoria podría cerrar la app.
         */
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getetag/></d:prop></d:propfind>"""

        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Nunca seguir una redirección a otro sitio con las credenciales.
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        /**
         * ID interno del usuario en Nextcloud, que es el que va en la ruta WebDAV
         * (`/remote.php/dav/files/<id>/`). No siempre coincide con lo que se escribe al iniciar
         * sesión: quien entra con su email o con LDAP tiene otro ID.
         *
         * @return null si el servidor no responde a la API OCS (p. ej. ownCloud antiguo u otro
         *   servidor WebDAV): entonces se usa el nombre de usuario tal cual.
         * @throws WebDavAuthException si las credenciales no son válidas.
         */
        suspend fun fetchUserId(serverUrl: String, username: String, appPassword: String, http: OkHttpClient = sharedHttpClient): String? =
            withContext(Dispatchers.IO) {
                val url = serverUrl.trimEnd('/') + "/ocs/v2.php/cloud/user?format=json"
                if (url.toHttpUrlOrNull() == null) throw WebDavInvalidUrlException(url)
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(username, appPassword, Charsets.UTF_8))
                    .header("User-Agent", USER_AGENT)
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .build()
                try {
                    http.newCall(request).execute().use { response ->
                        if (response.code == 401) throw WebDavAuthException(response.code)
                        if (!response.isSuccessful) return@withContext null
                        val text = response.body?.string() ?: return@withContext null
                        JSONObject(text).optJSONObject("ocs")?.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                    }
                } catch (e: WebDavAuthException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (_: Exception) {
                    null // Respuesta que no es JSON de OCS.
                }
            }

        fun normalizeEtag(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.trim().removePrefix("W/").trim('"').takeIf { it.isNotBlank() }
        }
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", authHeader)
        .header("User-Agent", USER_AGENT)

    private fun Response.throwIfAuthError() {
        if (code == 401 || code == 403) throw WebDavAuthException(code)
    }

    private fun fileUrl(name: String) = rootUrl + name.removePrefix("/")

    /** Comprueba las credenciales y crea la carpeta de la app si no existe. */
    suspend fun ensureRootFolder(): Unit = withContext(Dispatchers.IO) {
        val check = request(rootUrl)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML))
            .header("Depth", "0")
            .build()
        http.newCall(check).execute().use { response ->
            response.throwIfAuthError()
            if (response.isSuccessful) return@withContext
            if (response.code != 404) throw WebDavException(response.code, "No se pudo acceder a la carpeta (${response.code})")
        }
        http.newCall(request(rootUrl).method("MKCOL", null).build()).execute().use { response ->
            response.throwIfAuthError()
            if (response.isSuccessful || response.code == 405) return@withContext // 405 = ya existe
            if (response.code == 409) throw WebDavPathNotFoundException()
            throw WebDavException(response.code, "No se pudo crear la carpeta (${response.code})")
        }
    }

    /** Descarga un fichero con su ETag; null si no existe. */
    suspend fun download(name: String): RemoteFile? = withContext(Dispatchers.IO) {
        http.newCall(request(fileUrl(name)).get().header("Cache-Control", "no-cache").build()).execute().use { response ->
            response.throwIfAuthError()
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw WebDavException(response.code, "Error al descargar $name (${response.code})")
            val body = response.body ?: return@withContext RemoteFile(ByteArray(0), null)
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) throw WebDavTooLargeException()
            // contentLength puede ser -1 (chunked): se lee como mucho un byte más del límite.
            val source = body.source()
            source.request(MAX_DOWNLOAD_BYTES + 1)
            if (source.buffer.size > MAX_DOWNLOAD_BYTES) throw WebDavTooLargeException()
            RemoteFile(source.buffer.readByteArray(), normalizeEtag(response.header("OC-ETag") ?: response.header("ETag")))
        }
    }

    /**
     * Sube un fichero.
     *
     * @param ifMatch solo sobrescribe si el ETag remoto coincide.
     * @param ifNoneMatch solo lo crea si NO existe.
     * @throws WebDavPreconditionFailedException si la condición no se cumple (412).
     */
    suspend fun upload(
        name: String,
        content: String,
        ifMatch: String? = null,
        ifNoneMatch: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        val builder = request(fileUrl(name)).put(content.toByteArray(Charsets.UTF_8).toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (ifMatch != null) builder.header("If-Match", "\"$ifMatch\"")
        else if (ifNoneMatch) builder.header("If-None-Match", "*")
        http.newCall(builder.build()).execute().use { response ->
            response.throwIfAuthError()
            if (response.code == 412) throw WebDavPreconditionFailedException()
            if (!response.isSuccessful) throw WebDavException(response.code, "Error al subir $name (${response.code})")
        }
    }
}
