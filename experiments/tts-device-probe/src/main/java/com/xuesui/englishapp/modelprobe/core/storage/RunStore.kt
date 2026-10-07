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
        return RunStore.privateChild(directory, entry.getString("name"))
    }
}

/** Generic task artifacts: this layer does not know speakers, PCM or inference parameters. */
internal class RunStore(private val root: File, private val cache: File,
    private val checkpoint: (String) -> Unit = {}) {
    private val anchor = root.absoluteFile.normalize().path
    private val cacheAnchor = cache.absoluteFile.normalize().path
    var maintenanceIssue: String? = null; private set
    init {
        require(!Files.isSymbolicLink(root.toPath()) && !Files.isSymbolicLink(cache.toPath())) { "拒绝符号链接结果目录" }
        root.mkdirs(); cache.mkdirs()
        synchronized(draftLock) {
            root.listFiles()?.filter { it.name.startsWith(".pending-") && it.canonicalPath !in activeDrafts }?.forEach { removeOwned(root, it) }
            recoverLocked()
        }
    }
    inner class Draft internal constructor(val id: String, val directory: File) : Closeable {
        private var committed = false
        fun file(name: String) = privateChild(directory, name)
        fun commit(report: JSONObject, artifacts: Map<String, String>, cacheKey: String? = null,
            publicationKey: String? = null): SavedRun = synchronized(draftLock) {
            checkRoots()
            require(!committed)
            if (publicationKey != null) {
                require(publicationKey.matches(KEY) && report.optString("retentionKey") == publicationKey)
                require(report.optString("status") == "success" && artifacts.containsKey("audio"))
                recoverLocked()
            }
            val array = JSONArray()
            artifacts.forEach { (role, name) ->
                val file = file(name)
                require(file.isFile) { "结果文件缺失" }
                array.put(JSONObject().put("role", role).put("name", name).put("bytes", file.length()).put("sha256", fileHash(file)))
            }
            report.put("reportSchemaVersion", 1).put("runId", id).put("artifacts", array)
            atomicJson(file("report.json"), report)
            // Journal the intent before the rename. Recovery can finish publication, or leave the
            // previous latest intact when the new directory never reached a complete commit.
            if (publicationKey != null) {
                val catalog = catalog()
                catalog.getJSONObject("pending").put(id, publicationKey)
                saveCatalog(catalog)
                checkpoint("before_commit")
            }
            val target = privateChild(root, id)
            Files.move(directory.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            committed = true
            synchronized(draftLock) { activeDrafts.remove(directory.canonicalPath) }
            if (publicationKey != null) {
                checkpoint("after_commit")
                publishLocked(id, publicationKey, catalog())
                checkpoint("after_activate")
                collectSafely()
            }
            if (cacheKey != null) {
                require(cacheKey.matches(Regex("[0-9a-f]{64}")))
                // A cache-index failure must not invalidate the saved WAV/report pair.
                runCatching { atomicJson(privateChild(cache, "$cacheKey.json"), JSONObject().put("runId", id)) }
            }
            SavedRun(id, report, target)
        }
        override fun close() {
            synchronized(draftLock) {
                try { if (!committed && directory.exists()) removeOwned(root, directory) }
                finally { activeDrafts.remove(directory.canonicalPath) }
            }
        }
    }
    fun begin(): Draft = synchronized(draftLock) {
        checkRoots()
        val id = UUID.randomUUID().toString()
        val directory = privateChild(root, ".pending-$id").also { check(it.mkdir()) }
        activeDrafts.add(directory.canonicalPath)
        Draft(id, directory)
    }
    fun read(id: String, verify: Boolean = true): SavedRun = synchronized(draftLock) {
        checkRoots(); require(id.matches(ID))
        val dir = privateChild(root, id)
        val report = privateChild(dir, "report.json").readJson(8_000_000)
        require(report.getInt("reportSchemaVersion") == 1 && report.getString("runId") == id) { "结果记录版本或ID不匹配" }
        val run = SavedRun(id, report, dir)
        if (verify) {
            val artifacts = report.getJSONArray("artifacts")
            repeat(artifacts.length()) { i ->
                val spec = artifacts.getJSONObject(i); val file = privateChild(dir, spec.getString("name"))
                require(file.isFile && file.length() == spec.getLong("bytes") && fileHash(file) == spec.getString("sha256")) { "结果文件已损坏，不能播放/导出/缓存复用" }
            }
        }
        run
    }
    fun history(limit: Int = 100, compact: Boolean = false, include: (SavedRun) -> Boolean = { true }): List<SavedRun> = synchronized(draftLock) {
        checkRoots()
        val garbage = catalog().getJSONArray("garbage").strings().toSet()
        directories().asSequence().filter { it.name !in garbage }
            .mapNotNull { runCatching { read(it.name, false).let { run -> if (compact) brief(run) else run } }.getOrNull() }.filter(include)
            .sortedByDescending { it.report.optLong("createdAtEpochMillis") }.take(limit).toList()
    }
    inner class Lease internal constructor(val run: SavedRun) : Closeable {
        private var closed = false
        override fun close() = synchronized(draftLock) {
            if (!closed) {
                closed = true
                val key = leaseKey(run.id)
                val count = (leases[key] ?: 1) - 1
                if (count == 0) leases.remove(key) else leases[key] = count
            }
        }
    }
    fun acquire(id: String, verify: Boolean = true): Lease = synchronized(draftLock) {
        val garbage = catalog().getJSONArray("garbage").strings().toSet()
        require(id !in garbage || recordsStrict().values.any { it.id !in garbage && id in children(it) }) { "该音频正在删除或已被新版替换" }
        val run = read(id, verify)
        val key = leaseKey(id); leases[key] = (leases[key] ?: 0) + 1
        Lease(run)
    }
    fun cachedLease(key: String): Lease? = synchronized(draftLock) {
        checkRoots(); require(key.matches(KEY))
        val pointer = privateChild(cache, "$key.json")
        if (!pointer.isFile) return@synchronized null
        runCatching { acquire(pointer.readJson(4096).getString("runId")) }.getOrElse { pointer.delete(); null }
    }
    fun cached(key: String): SavedRun? = cachedLease(key)?.use { it.run }
    fun clearCache() = synchronized(draftLock) {
        checkRoots()
        cache.listFiles()?.filter { it.name.matches(CACHE_FILE) }?.forEach { removeOwned(cache, privateChild(cache, it.name)) }
    }
    fun latest(key: String): SavedRun? = synchronized(draftLock) {
        require(key.matches(KEY))
        val id = catalog().getJSONObject("latest").optString(key)
        if (id.isEmpty()) null else runCatching { read(id, false) }.getOrNull()
    }
    /** Returns true only after the selected record's files have actually been removed. */
    fun delete(id: String): Boolean = synchronized(draftLock) {
        checkRoots(); require(id.matches(ID))
        if (!privateChild(root, id).exists()) return@synchronized true
        val run = read(id, false)
        checkFlatDirectory(run.directory)
        val catalog = catalog()
        val garbage = catalog.getJSONArray("garbage").strings().toMutableSet()
        val records = recordsStrict()
        fun mark(target: String) {
            if (!garbage.add(target)) return
            records[target]?.let { parent -> children(parent).filter { records[it]?.report?.optJSONObject("reading") != null }.forEach(::mark) }
            // Legacy queues may have died before writing their parent items array.
            records.values.filter { it.report.optJSONObject("reading")?.optString("sessionId") == target }
                .forEach { mark(it.id) }
        }
        mark(id)
        val latest = catalog.getJSONObject("latest")
        latest.keys().asSequence().toList().filter { latest.getString(it) == id }.forEach(latest::remove)
        catalog.put("garbage", JSONArray(garbage.toList()))
        saveCatalog(catalog)
        collectSafely()
        !privateChild(root, id).exists()
    }
    fun recover() = synchronized(draftLock) { recoverLocked() }
    private fun checkRoots() {
        require(root.absoluteFile.normalize().path == anchor && cache.absoluteFile.normalize().path == cacheAnchor)
        require(!Files.isSymbolicLink(root.toPath()) && !Files.isSymbolicLink(cache.toPath())) { "拒绝符号链接结果目录" }
        require(root.isDirectory && cache.isDirectory) { "私有结果目录不可用" }
    }
    private fun leaseKey(id: String) = root.canonicalPath + "/" + id
    private fun catalog(): JSONObject {
        checkRoots()
        val file = privateChild(root, "catalog.json")
        if (!file.exists()) return JSONObject().put("schemaVersion", 1).put("latest", JSONObject())
            .put("pending", JSONObject()).put("garbage", JSONArray())
        return file.readJson(8_000_000).also { value ->
            require(value.getInt("schemaVersion") == 1)
            listOf("latest", "pending").forEach { field ->
                val entries = value.getJSONObject(field)
                entries.keys().forEach { key ->
                    require(if (field == "latest") key.matches(KEY) && entries.getString(key).matches(ID)
                        else key.matches(ID) && entries.getString(key).matches(KEY)) { "结果索引无效，停止清理" }
                }
            }
            require(value.getJSONArray("garbage").strings().all { it.matches(ID) })
        }
    }
    private fun saveCatalog(value: JSONObject) = atomicJson(privateChild(root, "catalog.json"), value)
    private fun directories() = root.listFiles().orEmpty().filter { it.name.matches(ID) }
    private fun recordsStrict(): Map<String, SavedRun> = directories().associate { it.name to brief(read(it.name, false)) }
    private fun brief(run: SavedRun): SavedRun {
        // Keep management/reference metadata, not thousands of sampler entries per historical
        // fragment. Full reports stay on disk and are re-read/verified for playback and export.
        val value = JSONObject()
        listOf("reportSchemaVersion", "runId", "status", "taskId", "title", "createdAtEpochMillis", "reading", "items", "childRunIds",
            "retentionKey", "retention", "settingsKey", "modelFingerprint", "engineVersion", "audio", "artifacts", "requested",
            "plannedCount", "completedCount", "error").forEach { key -> if (run.report.has(key)) value.put(key, run.report.get(key)) }
        return run.copy(report = value)
    }
    private fun children(run: SavedRun): List<String> {
        val explicit = run.report.optJSONArray("childRunIds")
        val ids = if (explicit != null) explicit.strings() else if (run.report.optString("taskId") == "speech.reading.v1") {
            val items = run.report.optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull { i -> items.getJSONObject(i).optString("runId").takeIf { it.matches(ID) } }
        } else emptyList()
        require(ids.all { it.matches(ID) }) { "结果引用无效" }
        return ids
    }
    private fun publishLocked(id: String, key: String, value: JSONObject) {
        val run = read(id) // Complete report and artifact hashes must pass before switching latest.
        require(run.status == "success" && run.report.getString("retentionKey") == key && run.report.has("audio"))
        children(run).forEach { read(it) }
        val garbage = value.getJSONArray("garbage").strings().toMutableSet()
        val records = recordsStrict()
        fun mark(target: String) {
            if (!garbage.add(target)) return
            records[target]?.let { parent -> children(parent).filter { records[it]?.report?.optJSONObject("reading") != null }.forEach(::mark) }
        }
        records.values.filter { it.id != id && it.report.optString("retentionKey") == key }.forEach { mark(it.id) }
        // A v0.3 child survives process death even if the parent never committed. Its explicit
        // retention identity proves ownership; legacy children without it are never guessed.
        records.values.filter { candidate -> candidate.report.optJSONObject("reading")?.let { origin ->
            origin.optString("retentionKey") == key && origin.optString("sessionId") !in records
        } == true }.forEach { mark(it.id) }
        value.getJSONObject("latest").put(key, id)
        value.getJSONObject("pending").remove(id)
        value.put("garbage", JSONArray(garbage.toList()))
        checkpoint("before_activate")
        saveCatalog(value) // Latest + reclamation intent move together, never delete first.
    }
    private fun recoverLocked() {
        val value = catalog()
        val pending = value.getJSONObject("pending")
        pending.keys().asSequence().toList().forEach { id ->
            if (privateChild(root, id).exists()) publishLocked(id, pending.getString(id), catalog())
            else {
                val current = catalog(); current.getJSONObject("pending").remove(id); saveCatalog(current)
            }
        }
        collectSafely()
    }
    private fun collectSafely() {
        try { collectLocked(); maintenanceIssue = null }
        catch (failure: Exception) { maintenanceIssue = failure.message ?: "文件回收失败，将在下次操作重试" }
    }
    private fun checkFlatDirectory(directory: File) {
        privateChild(root, directory.name)
        directory.listFiles().orEmpty().forEach {
            require(privateChild(directory, it.name).isFile) { "结果目录含未知子目录，拒绝清理" }
        }
    }
    private fun collectLocked() {
        val value = catalog()
        val garbage = value.getJSONArray("garbage").strings().toMutableSet()
        if (garbage.isEmpty()) return
        val records = recordsStrict() // Unknown/corrupt references fail closed; journal remains for retry.
        val live = mutableSetOf<String>()
        fun keep(id: String) { if (live.add(id)) records[id]?.let { children(it).forEach(::keep) } }
        records.keys.filter { it !in garbage || (leases[leaseKey(it)] ?: 0) > 0 }.forEach(::keep)
        val latest = value.getJSONObject("latest")
        latest.keys().forEach { keep(latest.getString(it)) }
        garbage.toList().filter { it !in live }.forEach { id ->
            val directory = privateChild(root, id)
            if (directory.exists()) {
                checkFlatDirectory(directory)
                checkpoint("before_remove")
                // Remove pointers first. After a crash this operation can safely run again.
                cache.listFiles().orEmpty().filter { it.name.matches(CACHE_FILE) }.forEach { entry ->
                    val file = privateChild(cache, entry.name)
                    val target = runCatching { file.readJson(4096).getString("runId") }.getOrNull()
                    if (target == null || target == id) removeOwned(cache, file)
                }
                // Rename to trash before removing individual files, so a crash never leaves a
                // half report in the committed namespace that prevents subsequent recovery.
                val trash = privateChild(root, ".trash-$id")
                Files.move(directory.toPath(), trash.toPath(), StandardCopyOption.ATOMIC_MOVE)
                removeOwned(root, trash)
            }
            val trash = privateChild(root, ".trash-$id")
            if (trash.exists()) { checkFlatDirectory(trash); removeOwned(root, trash) }
            garbage.remove(id)
        }
        value.put("garbage", JSONArray(garbage.toList())); saveCatalog(value)
    }
    companion object {
        private val draftLock = Any()
        private val activeDrafts = mutableSetOf<String>()
        private val leases = mutableMapOf<String, Int>()
        private val ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        private val KEY = Regex("[0-9a-f]{64}")
        private val CACHE_FILE = Regex("[0-9a-f]{64}\\.json")
        private fun JSONArray.strings() = (0 until length()).map { getString(it) }
        fun privateChild(root: File, name: String): File {
            require(!Files.isSymbolicLink(root.toPath())) { "拒绝符号链接目录" }
            return child(root, name).also { require(!Files.isSymbolicLink(it.toPath())) { "拒绝符号链接文件" } }
        }
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
    fun load(model: String, engineVersion: String, schema: Int, compatiblePrevious: Set<String> = emptySet()): JSONObject? {
        val file = file(model, engineVersion)
        if (!file.isFile) {
            val previous = profile(model)
            val previousVersion = if (previous.isFile) previous.readJson(4096).getString("engineVersion") else null
            // Explicit adapter-owned compatibility only; retain the old file until the user saves.
            if (previousVersion != null && previousVersion in compatiblePrevious)
                return load(model, previousVersion, schema)
            require(previousVersion == null || previousVersion == engineVersion) { "引擎版本已变更，使用默认参数；旧设置保留，确认新设置后保存" }
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
