package com.xuesui.englishapp.modelprobe.core.storage

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).hex()
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun hashParts(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update("${bytes.size}:".toByteArray(Charsets.US_ASCII)); digest.update(bytes)
    }
    return digest.digest().hex()
}
internal fun InputStream.boundedText(limit: Int): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (output.size() <= limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (n < 0) break
        output.write(buffer, 0, n)
    }
    val bytes = output.toByteArray()
    require(bytes.size <= limit) { "文本文件过大（上限 $limit 字节）" }
    return bytes.toString(Charsets.UTF_8)
}
internal fun File.readJson(limit: Int = 2_000_000): JSONObject = inputStream().use { JSONObject(it.boundedText(limit)) }
internal fun atomicJson(file: File, json: JSONObject) {
    file.parentFile!!.mkdirs()
    val temp = File(file.parentFile, ".${file.name}.${java.util.UUID.randomUUID()}.new")
    try {
        FileOutputStream(temp).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { temp.delete() }
}
internal fun child(root: File, name: String): File {
    require(name.matches(Regex("[A-Za-z0-9_.-]+")) && name != "." && name != "..")
    val file = File(root, name)
    require(file.canonicalFile.parentFile == root.canonicalFile) { "私有文件路径无效" }
    return file
}
internal fun removeOwned(root: File, file: File) {
    require(file.canonicalFile.parentFile == root.canonicalFile) { "拒绝越界清理" }
    require(!Files.isSymbolicLink(file.toPath())) { "拒绝清理符号链接" }
    if (file.isDirectory) file.listFiles()?.forEach { removeOwned(file, it) }
    check(!file.exists() || file.delete()) { "无法清理私有临时文件" }
}
