package com.example.personalcloud

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class HttpServer(
    private val context: Context,
    private val port: Int,
    private val username: String,
    private val password: String
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val storageDir = File(context.getExternalFilesDir(null), "CloudStorage")

    companion object {
        const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB limit per chunk
        const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks for upload
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun start() {
        if (isRunning) return

        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let { handleClient(it) }
                    } catch (e: Exception) {
                        if (isRunning) e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()

                val requestLine = input.readLine() ?: return@Thread
                val headers = mutableMapOf<String, String>()

                var line: String?
                while (input.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val parts = line!!.split(": ", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0].lowercase()] = parts[1]
                    }
                }

                val parts = requestLine.split(" ")
                if (parts.size < 3) {
                    sendResponse(output, 400, "Bad Request", "text/plain")
                    socket.close()
                    return@Thread
                }

                val method = parts[0]
                val path = URLDecoder.decode(parts[1], "UTF-8")

                // Check authentication
                if (!isAuthenticated(headers)) {
                    sendAuthRequired(output)
                    socket.close()
                    return@Thread
                }

                when (method) {
                    "GET" -> handleGet(path, output)
                    "POST" -> handlePost(path, input, headers, output)
                    "DELETE" -> handleDelete(path, output)
                    else -> sendResponse(output, 405, "Method Not Allowed", "text/plain")
                }

                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    socket.close()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }.start()
    }

    private fun isAuthenticated(headers: Map<String, String>): Boolean {
        val authHeader = headers["authorization"] ?: return false
        if (!authHeader.startsWith("Basic ")) return false

        val credentials = String(Base64.decode(authHeader.substring(6), Base64.DEFAULT))
        val expectedCredentials = "$username:$password"

        return credentials == expectedCredentials
    }

    private fun sendAuthRequired(output: OutputStream) {
        val response = "HTTP/1.1 401 Unauthorized\r\n" +
                "WWW-Authenticate: Basic realm=\"Personal Cloud\"\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
        output.write(response.toByteArray())
        output.flush()
    }

    private fun handleGet(path: String, output: OutputStream) {
        if (path == "/" || path == "/index.html") {
            sendHtmlInterface(output)
        } else if (path.startsWith("/download/")) {
            val filePath = path.substring(10)
            downloadFile(filePath, output)
        } else if (path == "/list") {
            listFiles("", output)
        } else if (path.startsWith("/list/")) {
            val folder = path.substring(6)
            listFiles(folder, output)
        } else if (path.startsWith("/search?")) {
            val query = path.substring(8).split("=").getOrNull(1)?.let {
                URLDecoder.decode(it, "UTF-8")
            } ?: ""
            searchFiles(query, output)
        } else if (path.startsWith("/createfolder")) {
            sendResponse(output, 405, "Use POST method", "text/plain")
        } else {
            sendResponse(output, 404, "Not Found", "text/plain")
        }
    }

    private fun handlePost(path: String, input: BufferedReader, headers: Map<String, String>, output: OutputStream) {
        if (path == "/upload") {
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            uploadFile(input.readText(contentLength), output)
        } else if (path == "/upload-chunk") {
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            uploadChunk(input.readText(contentLength), output)
        } else if (path == "/complete-upload") {
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            completeChunkedUpload(input.readText(contentLength), output)
        } else if (path == "/createfolder") {
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            createFolder(input.readText(contentLength), output)
        } else {
            sendResponse(output, 404, "Not Found", "text/plain")
        }
    }

    private fun handleDelete(path: String, output: OutputStream) {
        if (path.startsWith("/delete/")) {
            val filePath = path.substring(8)
            deleteFile(filePath, output)
        } else if (path.startsWith("/deletefolder/")) {
            val folderPath = path.substring(14)
            deleteFolder(folderPath, output)
        } else {
            sendResponse(output, 404, "Not Found", "text/plain")
        }
    }

    private fun BufferedReader.readText(length: Int): String {
        val buffer = CharArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return String(buffer, 0, totalRead)
    }

    private fun sendHtmlInterface(output: OutputStream) {
        val html = """
            
        """.trimIndent()

        sendResponse(output, 200, html, "text/html")
    }

    private fun listFiles(folder: String, output: OutputStream) {
        val currentDir = if (folder.isEmpty()) {
            storageDir
        } else {
            File(storageDir, folder)
        }

        if (!currentDir.exists() || !currentDir.isDirectory) {
            sendResponse(output, 404, """{"folders":[],"files":[]}""", "application/json")
            return
        }

        val folders = mutableListOf<Map<String, String>>()
        val files = mutableListOf<Map<String, String>>()

        currentDir.listFiles()?.forEach { item ->
            if (item.isDirectory) {
                val folderPath = if (folder.isEmpty()) item.name else "$folder/${item.name}"
                folders.add(mapOf(
                    "name" to item.name,
                    "path" to folderPath
                ))
            } else {
                val filePath = if (folder.isEmpty()) item.name else "$folder/${item.name}"
                files.add(mapOf(
                    "name" to item.name,
                    "path" to filePath,
                    "size" to formatFileSize(item.length()),
                    "date" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastModified()))
                ))
            }
        }

        // Sort folders and files alphabetically
        val sortedFolders = folders.sortedBy { it["name"] }
        val sortedFiles = files.sortedBy { it["name"] }

        val foldersJson = sortedFolders.joinToString(",", "[", "]") { folder ->
            """{"name":"${folder["name"]}","path":"${folder["path"]}"}"""
        }

        val filesJson = sortedFiles.joinToString(",", "[", "]") { file ->
            """{"name":"${file["name"]}","path":"${file["path"]}","size":"${file["size"]}","date":"${file["date"]}"}"""
        }

        val json = """{"folders":$foldersJson,"files":$filesJson}"""
        sendResponse(output, 200, json, "application/json")
    }

    private fun createFolder(body: String, output: OutputStream) {
        try {
            val params = body.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }

            val path = params["path"] ?: throw Exception("No path specified")
            val folder = File(storageDir, path)

            if (folder.exists()) {
                sendResponse(output, 400, "Folder already exists", "text/plain")
                return
            }

            if (folder.mkdirs()) {
                sendResponse(output, 200, "Folder created successfully", "text/plain")
            } else {
                sendResponse(output, 500, "Failed to create folder", "text/plain")
            }
        } catch (e: Exception) {
            sendResponse(output, 500, "Error: ${e.message}", "text/plain")
        }
    }

    private fun uploadFile(body: String, output: OutputStream) {
        try {
            val params = body.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }

            val filename = params["filename"] ?: throw Exception("No filename")
            val content = params["content"] ?: throw Exception("No content")

            val decodedContent = Base64.decode(content, Base64.DEFAULT)

            val targetFile = File(storageDir, filename)

            // Create parent directories if needed
            targetFile.parentFile?.mkdirs()

            targetFile.writeBytes(decodedContent)

            sendResponse(output, 200, "File uploaded successfully", "text/plain")
        } catch (e: Exception) {
            sendResponse(output, 500, "Upload failed: ${e.message}", "text/plain")
        }
    }

    private fun uploadChunk(body: String, output: OutputStream) {
        try {
            val params = body.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }

            val uploadId = params["uploadId"] ?: throw Exception("No upload ID")
            val chunkIndex = params["chunkIndex"]?.toIntOrNull() ?: throw Exception("No chunk index")
            val content = params["content"] ?: throw Exception("No content")

            val decodedContent = Base64.decode(content, Base64.DEFAULT)

            // Create temp directory for chunks
            val tempDir = File(storageDir, ".chunks/$uploadId")
            tempDir.mkdirs()

            // Save chunk
            val chunkFile = File(tempDir, "chunk_$chunkIndex")
            chunkFile.writeBytes(decodedContent)

            sendResponse(output, 200, "Chunk $chunkIndex uploaded successfully", "text/plain")
        } catch (e: Exception) {
            Log.e("HttpServer", "Chunk upload error", e)
            sendResponse(output, 500, "Chunk upload failed: ${e.message}", "text/plain")
        }
    }

    private fun completeChunkedUpload(body: String, output: OutputStream) {
        try {
            val params = body.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }

            val uploadId = params["uploadId"] ?: throw Exception("No upload ID")
            val filename = params["filename"] ?: throw Exception("No filename")
            val totalChunks = params["totalChunks"]?.toIntOrNull() ?: throw Exception("No total chunks")

            val tempDir = File(storageDir, ".chunks/$uploadId")

            if (!tempDir.exists()) {
                throw Exception("Upload session not found")
            }

            // Combine chunks
            val targetFile = File(storageDir, filename)
            targetFile.parentFile?.mkdirs()

            FileOutputStream(targetFile).use { outputStream ->
                for (i in 0 until totalChunks) {
                    val chunkFile = File(tempDir, "chunk_$i")
                    if (!chunkFile.exists()) {
                        throw Exception("Missing chunk $i")
                    }
                    chunkFile.inputStream().use { it.copyTo(outputStream) }
                }
            }

            // Clean up chunks
            tempDir.deleteRecursively()

            // Clean up empty parent directory
            val chunksDir = File(storageDir, ".chunks")
            if (chunksDir.listFiles()?.isEmpty() == true) {
                chunksDir.delete()
            }

            sendResponse(output, 200, "File uploaded successfully (${formatFileSize(targetFile.length())})", "text/plain")
        } catch (e: Exception) {
            Log.e("HttpServer", "Complete upload error", e)
            sendResponse(output, 500, "Failed to complete upload: ${e.message}", "text/plain")
        }
    }

    private fun downloadFile(filePath: String, output: OutputStream) {
        val targetFile = File(storageDir, filePath)
        if (!targetFile.exists() || !targetFile.isFile) {
            sendResponse(output, 404, "File not found", "text/plain")
            return
        }

        val filename = targetFile.name
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"$filename\"\r\n" +
                "Content-Length: ${targetFile.length()}\r\n" +
                "\r\n"
        output.write(response.toByteArray())
        targetFile.inputStream().use { it.copyTo(output) }
        output.flush()
    }

    private fun deleteFile(filePath: String, output: OutputStream) {
        val targetFile = File(storageDir, filePath)
        if (targetFile.exists() && targetFile.isFile && targetFile.delete()) {
            sendResponse(output, 200, "File deleted", "text/plain")
        } else {
            sendResponse(output, 404, "File not found", "text/plain")
        }
    }

    private fun deleteFolder(folderPath: String, output: OutputStream) {
        val targetFolder = File(storageDir, folderPath)
        if (targetFolder.exists() && targetFolder.isDirectory) {
            if (targetFolder.deleteRecursively()) {
                sendResponse(output, 200, "Folder deleted", "text/plain")
            } else {
                sendResponse(output, 500, "Failed to delete folder", "text/plain")
            }
        } else {
            sendResponse(output, 404, "Folder not found", "text/plain")
        }
    }

    private fun searchFiles(query: String, output: OutputStream) {
        if (query.isEmpty()) {
            sendResponse(output, 400, """{"error":"Empty search query"}""", "application/json")
            return
        }

        val results = mutableListOf<Map<String, String>>()
        searchRecursively(storageDir, "", query.lowercase(), results)

        val json = results.joinToString(",", "[", "]") { file ->
            """{"name":"${file["name"]}","path":"${file["path"]}","folder":"${file["folder"]}","size":"${file["size"]}","date":"${file["date"]}"}"""
        }

        sendResponse(output, 200, json, "application/json")
    }

    private fun searchRecursively(dir: File, currentPath: String, query: String, results: MutableList<Map<String, String>>) {
        dir.listFiles()?.forEach { item ->
            if (item.isFile) {
                if (item.name.lowercase().contains(query)) {
                    val filePath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
                    results.add(mapOf(
                        "name" to item.name,
                        "path" to filePath,
                        "folder" to currentPath,
                        "size" to formatFileSize(item.length()),
                        "date" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastModified()))
                    ))
                }
            } else if (item.isDirectory) {
                val newPath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
                searchRecursively(item, newPath, query, results)
            }
        }
    }

    private fun sendResponse(output: OutputStream, code: Int, body: String, contentType: String) {
        val response = "HTTP/1.1 $code OK\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n" +
                "\r\n" +
                body
        output.write(response.toByteArray())
        output.flush()
    }

    private fun formatFileSize(size: Long): String {
        val kb = size / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$size bytes"
        }
    }

    // Method for relay client to process requests directly
    fun handleRequestDirect(method: String, path: String, body: String): HttpResponse {
        return try {
            val outputStream = ByteArrayOutputStream()

            when (method) {
                "GET" -> handleGet(path, outputStream)
                "POST" -> {
                    val fakeReader = BufferedReader(StringReader(body))
                    handlePost(path, fakeReader, mapOf("content-length" to body.length.toString()), outputStream)
                }
                "DELETE" -> handleDelete(path, outputStream)
                else -> sendResponse(outputStream, 405, "Method Not Allowed", "text/plain")
            }

            // Parse the response
            val responseBytes = outputStream.toByteArray()
            val responseString = String(responseBytes)

            // Extract status code
            val statusLine = responseString.substring(0, responseString.indexOf("\r\n"))
            val statusCode = statusLine.split(" ")[1].toIntOrNull() ?: 200

            // Extract headers
            val headersEnd = responseString.indexOf("\r\n\r\n")
            val headersSection = responseString.substring(0, headersEnd)
            val headers = mutableMapOf<String, String>()

            headersSection.lines().drop(1).forEach { line ->
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0]] = parts[1]
                }
            }

            // Extract body
            val bodyStart = headersEnd + 4
            val isBinary = headers["Content-Type"]?.contains("octet-stream") == true

            if (isBinary) {
                val bodyBytes = responseBytes.copyOfRange(bodyStart, responseBytes.size)
                HttpResponse(statusCode, headers, "", bodyBytes, true)
            } else {
                val bodyString = responseString.substring(bodyStart)
                HttpResponse(statusCode, headers, bodyString, ByteArray(0), false)
            }
        } catch (e: Exception) {
            Log.e("HttpServer", "Error handling direct request", e)
            HttpResponse(500, mapOf(), "Internal Server Error", ByteArray(0), false)
        }
    }

    data class HttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val bodyString: String = "",
        val bodyBytes: ByteArray = ByteArray(0),
        val isBinary: Boolean = false
    )
}