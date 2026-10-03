package com.xuesui.englishapp.modelprobe.core.storage

import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import java.io.File
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedRun(val id: String, val report: JSONObject, val directory: File) {
    val status: String get() = report.getString("status")
    fun artifact(role: String): File {
        val files = report.getJSONArray("artifacts")
        val entry = (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("role") == role }
        return child(directory, entry.getString("name"))
    }
}

/** Generic task artifacts: this layer does not know speakers, PCM or inference parameters. */
internal class RunStore(private val root: File, private val cache: File) {
    init {
        root.mkdirs(); cache.mkdirs()
        synchronized(draftLock) {
            root.listFiles()?.filter { it.name.startsWith(".pending-") && it.canonicalPath !in activeDrafts }?.forEach { removeOwned(root, it) }
        }
    }
    inner class Draft internal constructor(val id: String, val directory: File) : Closeable {
        private var committed = false
        fun file(name: String) = child(directory, name)
        fun commit(report: JSONObject, artifacts: Map<String, String>, cacheKey: String? = null): SavedRun {
            val array = JSONArray()
            artifacts.forEach { (role, name) ->
                val file = file(name)
                require(file.isFile) { "结果文件缺失" }
                array.put(JSONObject().put("role", role).put("name", name).put("bytes", file.length()).put("sha256", fileHash(file)))
            }
            report.put("reportSchemaVersion", 1).put("runId", id).put("artifacts", array)
            atomicJson(file("report.json"), report)
            val target = child(root, id)
            Files.move(directory.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            committed = true
            synchronized(draftLock) { activeDrafts.remove(directory.canonicalPath) }
            if (cacheKey != null) {
                require(cacheKey.matches(Regex("[0-9a-f]{64}")))
                // A cache-index failure must not invalidate the saved WAV/report pair.
                runCatching { atomicJson(child(cache, "$cacheKey.json"), JSONObject().put("runId", id)) }
            }
            return SavedRun(id, report, target)
        }
        override fun close() {
            synchronized(draftLock) {
                try { if (!committed && directory.exists()) removeOwned(root, directory) }
                finally { activeDrafts.remove(directory.canonicalPath) }
            }
        }
    }
    fun begin(): Draft = synchronized(draftLock) {
        val id = UUID.randomUUID().toString()
        val directory = child(root, ".pending-$id").also { check(it.mkdir()) }
        activeDrafts.add(directory.canonicalPath)
        Draft(id, directory)
    }
    fun read(id: String, verify: Boolean = true): SavedRun {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        val dir = child(root, id)
        val report = child(dir, "report.json").readJson(8_000_000)
        require(report.getInt("reportSchemaVersion") == 1 && report.getString("runId") == id) { "结果记录版本或ID不匹配" }
        val run = SavedRun(id, report, dir)
        if (verify) {
            val artifacts = report.getJSONArray("artifacts")
            repeat(artifacts.length()) { i ->
                val spec = artifacts.getJSONObject(i); val file = child(dir, spec.getString("name"))
                require(file.isFile && file.length() == spec.getLong("bytes") && fileHash(file) == spec.getString("sha256")) { "结果文件已损坏，不能播放/导出/缓存复用" }
            }
        }
        return run
    }
    fun history(include: (SavedRun) -> Boolean = { true }): List<SavedRun> = root.listFiles().orEmpty().asSequence()
        .filter { it.name.matches(Regex("[a-f0-9-]{36}")) }
        .mapNotNull { runCatching { read(it.name, false) }.getOrNull() }.filter(include)
        .sortedByDescending { it.report.optLong("createdAtEpochMillis") }.take(100).toList()
    fun cached(key: String): SavedRun? {
        require(key.matches(Regex("[0-9a-f]{64}")))
        val pointer = child(cache, "$key.json")
        if (!pointer.isFile) return null
        return runCatching { read(pointer.readJson(4096).getString("runId")) }.getOrElse { pointer.delete(); null }
    }
    fun clearCache() { cache.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}\\.json")) }?.forEach { removeOwned(cache, it) } }
    companion object {
        private val draftLock = Any()
        private val activeDrafts = mutableSetOf<String>()
        fun fileHash(file: File, token: WorkToken? = null): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { token?.check(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().hex()
        }
    }
}

internal class SettingsStore(private val directory: File) {
    init { directory.mkdirs() }
    private fun file(model: String, engineVersion: String) = child(directory, hashParts(listOf(model, engineVersion)) + ".json")
    private fun profile(model: String) = child(directory, hashParts(listOf(model)) + ".active.json")
    fun load(model: String, engineVersion: String, schema: Int): JSONObject? {
        val file = file(model, engineVersion)
        if (!file.isFile) {
            val previous = profile(model)
            require(!previous.isFile || previous.readJson(4096).getString("engineVersion") == engineVersion) { "引擎版本已变更，使用默认参数；旧设置保留，确认新设置后保存" }
            return null
        }
        val json = file.readJson(32768)
        require(json.getInt("schemaVersion") == schema && json.getString("modelId") == model && json.getString("engineVersion") == engineVersion) { "设置版本不兼容，已恢复默认；旧文件保留，点击保存后覆盖" }
        return json.getJSONObject("parameters")
    }
    fun save(model: String, engineVersion: String, schema: Int, parameters: JSONObject) {
        atomicJson(file(model, engineVersion), JSONObject().put("schemaVersion", schema).put("modelId", model)
            .put("engineVersion", engineVersion).put("parameters", parameters))
        atomicJson(profile(model), JSONObject().put("engineVersion", engineVersion))
    }
}
