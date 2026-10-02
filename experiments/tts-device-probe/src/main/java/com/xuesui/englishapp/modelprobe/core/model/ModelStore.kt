package com.xuesui.englishapp.modelprobe.core.model

import com.xuesui.englishapp.modelprobe.core.runtime.WorkToken
import com.xuesui.englishapp.modelprobe.core.storage.*
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal class ModelLease(val descriptor: ModelDescriptor, val files: Map<String, File>, private val release: () -> Unit) : Closeable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/** Immutable generations + atomic pointer: a failed replacement preserves the previous pair. */
internal class ModelStore(private val root: File, private val availableBytes: () -> Long = { root.usableSpace }) {
    private class Ownership { val lock = Any(); val leases = mutableMapOf<String, Int>() }
    companion object { private val owners = java.util.concurrent.ConcurrentHashMap<String, Ownership>() }
    // A finishing ViewModel may still be waiting for native exit when a new one opens.
    private val ownership = owners.computeIfAbsent(root.canonicalPath) { Ownership() }
    private val lock = ownership.lock
    private val leases get() = ownership.leases
    private val verified = mutableMapOf<String, List<Pair<Long, Long>>>()
    init { check(root.mkdirs() || root.isDirectory) }
    private fun home(d: ModelDescriptor) = child(root, d.modelId).also { it.mkdirs() }
    private fun active(d: ModelDescriptor): File? {
        val home = home(d)
        val pointer = child(home, "active.json")
        if (!pointer.isFile) return null
        val json = pointer.readJson(4096)
        require(json.getString("fingerprint") == d.fingerprint) { "已安装模型版本与清单不同，请重新安装" }
        val generation = json.getString("generation")
        require(generation.matches(Regex("[a-f0-9-]{36}"))) { "模型安装记录损坏" }
        return child(home, generation).also { require(it.isDirectory) { "模型目录缺失，请重新安装" } }
    }
    fun present(d: ModelDescriptor): Boolean = synchronized(lock) {
        val directory = active(d) ?: return false
        d.files.forEach { spec ->
            val file = child(directory, spec.name)
            require(file.isFile) { "${spec.role} 私有副本缺失，请重新安装" }
            require(file.length() == spec.sizeBytes) { "${spec.role} 长度不匹配，请重新安装" }
        }
        true
    }
    private fun unleased(d: ModelDescriptor) { check((leases[d.modelId] ?: 0) == 0) { "模型正在使用，请先卸载" } }
    private fun stamp(d: ModelDescriptor, dir: File) = d.files.map { child(dir, it.name).let { f -> f.length() to f.lastModified() } }
    private fun cleanup(d: ModelDescriptor, keep: File?) {
        val dir = home(d)
        dir.listFiles()?.filter { it != keep && (it.name.startsWith(".pending-") || it.name.matches(Regex("[a-f0-9-]{36}"))) }
            ?.forEach { removeOwned(dir, it) }
    }
    fun install(d: ModelDescriptor, token: WorkToken, open: (ModelFile) -> InputStream, progress: (Long, Long) -> Unit) = synchronized(lock) {
        unleased(d)
        token.check()
        val dir = home(d)
        val old = runCatching { active(d) }.getOrNull()
        cleanup(d, old)
        require(availableBytes() >= d.totalBytes + 64L * 1024 * 1024) { "可用空间不足：私有副本需 ${d.totalBytes} 字节，另预留64MiB" }
        val id = UUID.randomUUID().toString()
        val stage = child(dir, ".pending-$id").also { check(it.mkdir()) }
        var candidate: File = stage
        var committed = false
        var completed = 0L
        try {
            d.files.forEach { spec ->
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                open(spec).use { input -> FileOutputStream(child(stage, spec.name)).use { out ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        token.check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        bytes += count
                        require(bytes <= spec.sizeBytes) { "${spec.role} 文件长度超出清单" }
                        out.write(buffer, 0, count); digest.update(buffer, 0, count)
                        completed += count; progress(completed, d.totalBytes)
                    }
                    out.fd.sync()
                } }
                require(bytes == spec.sizeBytes) { "${spec.role} 文件缺失或不完整：$bytes / ${spec.sizeBytes} 字节" }
                require(digest.digest().hex() == spec.sha256) { "${spec.role} SHA256错误，未安装" }
            }
            token.check()
            val complete = child(dir, id)
            Files.move(stage.toPath(), complete.toPath(), StandardCopyOption.ATOMIC_MOVE)
            candidate = complete
            token.check()
            atomicJson(child(dir, "active.json"), JSONObject().put("schemaVersion", 1).put("generation", id).put("fingerprint", d.fingerprint))
            committed = true
            verified[complete.path] = stamp(d, complete)
            // The pointer switch is the commit point; later cancellation does not undo a valid installation.
            runCatching { cleanup(d, complete) } // A valid commit remains valid if old-generation cleanup fails.
        } finally { if (!committed && candidate.exists()) removeOwned(dir, candidate) }
    }
    fun acquire(d: ModelDescriptor, token: WorkToken, forceVerify: Boolean = false, progress: (Long, Long) -> Unit = { _, _ -> }): ModelLease = synchronized(lock) {
        token.check()
        val dir = active(d) ?: error("模型尚未安装")
        val state = stamp(d, dir)
        if (forceVerify || verified[dir.path] != state) {
            verified.remove(dir.path)
            var completed = 0L
            d.files.forEach { spec ->
                val file = child(dir, spec.name)
                require(file.isFile && file.length() == spec.sizeBytes) { "${spec.role} 文件缺失或长度错误" }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        token.check(); val n = input.read(buffer); if (n < 0) break
                        digest.update(buffer, 0, n); completed += n; progress(completed, d.totalBytes)
                    }
                }
                require(digest.digest().hex() == spec.sha256) { "${spec.role} SHA256错误，请重新安装" }
            }
            token.check(); verified[dir.path] = state
        }
        leases[d.modelId] = (leases[d.modelId] ?: 0) + 1
        ModelLease(d, d.files.associate { it.role to child(dir, it.name) }) {
            synchronized(lock) { leases[d.modelId] = (leases[d.modelId] ?: 1) - 1 }
        }
    }
    fun delete(d: ModelDescriptor) = synchronized(lock) {
        unleased(d)
        val dir = home(d)
        removeOwned(root, dir)
        verified.keys.removeAll { it.startsWith(dir.path + File.separator) }
    }
}
