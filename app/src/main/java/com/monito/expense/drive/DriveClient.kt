package com.monito.expense.drive

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Google Drive v3 REST client (folders, list, upload, download, delete) over HttpURLConnection. */
class DriveClient(private val accessToken: String) {

    data class DriveFile(val id: String, val name: String, val size: Long, val modifiedTime: String)

    class DriveException(val code: Int, message: String) : Exception("HTTP $code: $message")

    private val base = "https://www.googleapis.com/drive/v3"
    private val uploadBase = "https://www.googleapis.com/upload/drive/v3"

    /** Finds or creates `Monito/backups` (or any path) and returns the id of the last folder. */
    fun ensureFolderPath(path: List<String>): String {
        var parent = "root"
        for (name in path) {
            parent = findFolder(name, parent) ?: createFolder(name, parent)
        }
        return parent
    }

    private fun findFolder(name: String, parent: String): String? {
        val q = "name = '${name.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.folder' and '$parent' in parents and trashed = false"
        val json = get("$base/files?q=${enc(q)}&fields=files(id,name)&spaces=drive")
        val files = json.getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createFolder(name: String, parent: String): String {
        val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder").put("parents", listOf(parent))
        return send("POST", "$base/files?fields=id", "application/json; charset=UTF-8", body.toString().toByteArray()).getString("id")
    }

    fun list(folderId: String): List<DriveFile> {
        val q = "'$folderId' in parents and trashed = false"
        val json = get("$base/files?q=${enc(q)}&fields=files(id,name,size,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=100&spaces=drive")
        val arr = json.getJSONArray("files")
        return (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            DriveFile(f.getString("id"), f.getString("name"), f.optString("size", "0").toLongOrNull() ?: 0L, f.optString("modifiedTime"))
        }
    }

    fun upload(folderId: String, name: String, bytes: ByteArray, mime: String = "application/octet-stream"): DriveFile {
        val boundary = "monito-" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name).put("parents", listOf(folderId)).toString()
        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
        out.write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
        out.write(bytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        val json = send(
            "POST",
            "$uploadBase/files?uploadType=multipart&fields=id,name,size,modifiedTime",
            "multipart/related; boundary=$boundary",
            out.toByteArray(),
        )
        return DriveFile(json.getString("id"), json.getString("name"), json.optString("size", "0").toLongOrNull() ?: bytes.size.toLong(), json.optString("modifiedTime"))
    }

    fun download(fileId: String): ByteArray {
        val conn = open("GET", "$base/files/$fileId?alt=media")
        try {
            check(conn)
            return conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
    }

    fun delete(fileId: String) {
        val conn = open("DELETE", "$base/files/$fileId")
        try {
            check(conn)
        } finally {
            conn.disconnect()
        }
    }

    // ---- helpers

    private fun get(url: String): JSONObject {
        val conn = open("GET", url)
        try {
            check(conn)
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun send(method: String, url: String, contentType: String, body: ByteArray): JSONObject {
        val conn = open(method, url)
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            check(conn)
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 30_000
            readTimeout = 120_000
        }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) {
            val msg = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                ?.let { runCatching { JSONObject(it).getJSONObject("error").getString("message") }.getOrDefault(it) }
                ?: conn.responseMessage.orEmpty()
            throw DriveException(code, msg.take(300))
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
