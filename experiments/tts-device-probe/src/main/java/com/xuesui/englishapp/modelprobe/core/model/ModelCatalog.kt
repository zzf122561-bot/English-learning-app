package com.xuesui.englishapp.modelprobe.core.model

import com.xuesui.englishapp.modelprobe.core.storage.hashParts
import org.json.JSONObject

internal data class ModelFile(val role: String, val name: String, val assetPath: String, val sizeBytes: Long, val sha256: String)
internal data class ModelDescriptor(
    val modelId: String, val engineId: String, val engineVersion: String, val taskId: String,
    val revision: String, val license: String, val title: String,
    val runtime: String, val abi: String, val capabilities: Set<String>, val files: List<ModelFile>,
) {
    val totalBytes get() = files.sumOf { it.sizeBytes }
    val fingerprint get() = hashParts(listOf(modelId, revision) + files.sortedBy { it.role }.flatMap { listOf(it.role, it.name, it.sizeBytes.toString(), it.sha256) })
}

/** A manifest describes data only. It can never register code or arbitrary file paths. */
internal class ModelCatalog(val registered: List<ModelDescriptor>) {
    fun parseManifest(text: String): List<ModelDescriptor> {
        require(text.toByteArray().size <= 65536) { "模型清单过大" }
        val root = JSONObject(text)
        require(root.get("schemaVersion") is Number && root.get("schemaVersion").toString() == "1") { "不支持的模型清单版本" }
        val models = root.getJSONArray("models")
        require(models.length() in 1..registered.size) { "模型清单为空或包含未登记模型" }
        val seen = mutableSetOf<String>()
        return (0 until models.length()).map { index ->
            val item = models.getJSONObject(index)
            val id = item.getString("modelId")
            require(seen.add(id)) { "清单模型重复：$id" }
            val descriptor = registered.singleOrNull { it.modelId == id } ?: error("清单包含未登记模型：$id")
            require(item.getString("engineId") == descriptor.engineId && item.getString("taskId") == descriptor.taskId &&
                item.getString("revision") == descriptor.revision && item.getString("license") == descriptor.license) { "模型身份与受信任描述不一致" }
            val files = item.getJSONArray("files")
            require(files.length() == descriptor.files.size) { "模型文件组合不完整" }
            val roles = mutableSetOf<String>()
            repeat(files.length()) { n ->
                val value = files.getJSONObject(n)
                val role = value.getString("role")
                require(roles.add(role)) { "模型角色重复" }
                val expected = descriptor.files.singleOrNull { it.role == role } ?: error("未知模型角色")
                require(value.getString("name") == expected.name && value.getString("assetPath") == expected.assetPath &&
                    value.get("sizeBytes") is Number && value.get("sizeBytes").toString() == expected.sizeBytes.toString() && value.getString("sha256") == expected.sha256) { "模型 $role 的路径、长度或SHA256不匹配" }
            }
            descriptor
        }
    }
}
