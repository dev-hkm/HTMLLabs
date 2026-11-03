package com.hkm.htmllabs.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

object CloudBackupManager {
    private const val ENDPOINT = "https://html-labs-backup.hoangkhanhminh2005-vl.workers.dev"
    private const val TOKEN_PREFIX = "hkm-"
    private const val TOKEN_BODY_LENGTH = 36
    private val tokenRegex = Regex("^hkm-[A-Za-z0-9]{36}$")
    private val random = SecureRandom()
    private const val TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    data class CloudBackupStatus(
        val success: Boolean,
        val message: String,
        val updatedAt: String? = null,
        val itemCount: Int? = null
    )

    fun generateToken(): String {
        val suffix = buildString {
            repeat(TOKEN_BODY_LENGTH) {
                append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)])
            }
        }
        return TOKEN_PREFIX + suffix
    }

    fun isValidToken(token: String): Boolean {
        return tokenRegex.matches(token.trim())
    }

    fun maskToken(token: String): String {
        if (!isValidToken(token)) return "Invalid token"
        return token.take(8) + "..." + token.takeLast(6)
    }

    suspend fun uploadBackup(cards: List<HtmlCard>, token: String): CloudBackupStatus {
        if (!isValidToken(token)) {
            return CloudBackupStatus(false, "Invalid backup token")
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val backupJson = BackupManager.createBackupJson(cards)
                val response = request(
                    path = "/backup",
                    method = "POST",
                    body = JSONObject()
                        .put("token", token.trim())
                        .put("backupJson", backupJson)
                        .toString()
                )

                if (!response.isSuccessful) {
                    return@runCatching CloudBackupStatus(
                        false,
                        parseErrorMessage(response.body, "Cloud backup failed")
                    )
                }

                val payload = JSONObject(response.body)
                CloudBackupStatus(
                    success = true,
                    message = "Cloud backup uploaded",
                    updatedAt = payload.optString("updatedAt", ""),
                    itemCount = cards.size
                )
            }.getOrElse {
                CloudBackupStatus(false, it.message ?: "Cloud backup failed")
            }
        }
    }

    suspend fun restoreBackup(token: String): Pair<CloudBackupStatus, BackupManager.BackupData?> {
        if (!isValidToken(token)) {
            return CloudBackupStatus(false, "Invalid backup token") to null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = request(
                    path = "/backup/${token.trim()}",
                    method = "GET"
                )

                if (!response.isSuccessful) {
                    return@runCatching CloudBackupStatus(
                        false,
                        parseErrorMessage(response.body, "Cloud restore failed")
                    ) to null
                }

                val payload = JSONObject(response.body)
                val backupJson = payload.optString("backupJson", "")
                val parsedBackup = BackupManager.parseBackupJson(backupJson)
                if (parsedBackup == null) {
                    CloudBackupStatus(false, "Cloud backup data is invalid") to null
                } else {
                    CloudBackupStatus(
                        success = true,
                        message = "Cloud backup restored",
                        updatedAt = payload.optString("updatedAt", ""),
                        itemCount = parsedBackup.items.size
                    ) to parsedBackup
                }
            }.getOrElse {
                CloudBackupStatus(false, it.message ?: "Cloud restore failed") to null
            }
        }
    }

    suspend fun deleteBackup(token: String): CloudBackupStatus {
        if (!isValidToken(token)) {
            return CloudBackupStatus(false, "Invalid backup token")
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = request(
                    path = "/backup/${token.trim()}",
                    method = "DELETE"
                )

                if (!response.isSuccessful) {
                    return@runCatching CloudBackupStatus(
                        false,
                        parseErrorMessage(response.body, "Delete cloud backup failed")
                    )
                }

                CloudBackupStatus(true, "Cloud backup deleted")
            }.getOrElse {
                CloudBackupStatus(false, it.message ?: "Delete cloud backup failed")
            }
        }
    }

    private data class HttpResult(
        val statusCode: Int,
        val body: String
    ) {
        val isSuccessful: Boolean = statusCode in 200..299
    }

    private fun request(path: String, method: String, body: String? = null): HttpResult {
        val connection = (URL(ENDPOINT + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            doInput = true
            useCaches = false
        }

        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

        val responseBody = stream.use { input ->
            BufferedReader(input.reader(Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }

        connection.disconnect()
        return HttpResult(status, responseBody)
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return runCatching {
            when (JSONObject(body).optString("error")) {
                "invalid_token" -> "Invalid backup token"
                "not_found" -> "No cloud backup found for this token"
                "missing_backup_json" -> "Backup payload is empty"
                else -> fallback
            }
        }.getOrDefault(fallback)
    }
}
