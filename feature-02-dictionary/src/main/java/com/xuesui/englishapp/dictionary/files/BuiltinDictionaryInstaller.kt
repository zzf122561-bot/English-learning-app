package com.xuesui.englishapp.dictionary.files

import android.content.Context
import com.xuesui.englishapp.dictionary.data.DictionaryEntity
import com.xuesui.englishapp.dictionary.data.DictionaryRepository
import com.xuesui.englishapp.dictionary.data.DictionaryResourceEntity
import com.xuesui.englishapp.dictionary.data.DictionarySourceType
import com.xuesui.englishapp.dictionary.data.DictionaryStatus
import com.xuesui.englishapp.dictionary.engine.MdictSource
import com.xuesui.englishapp.dictionary.engine.PureKotlinMdictEngine
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

internal data class BuiltinAsset(val path: String, val sha256: String)
internal data class BuiltinDictionarySpec(
    val id: String,
    val displayName: String,
    val mdx: BuiltinAsset,
    val resources: List<BuiltinAsset>,
)
internal data class BuiltinManifest(val schemaVersion: Int, val dictionaries: List<BuiltinDictionarySpec>)
internal data class BuiltinInstallSummary(val installed: Int, val unchanged: Int, val errors: List<String>)

internal object BuiltinInstallationVerifier {
    fun validate(asset: BuiltinAsset) {
        require(!asset.path.startsWith('/') && !asset.path.contains('\\')) { "Unsafe asset path" }
        require(asset.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe asset path" }
        require(asset.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "Invalid SHA-256" }
    }

    fun matches(spec: BuiltinDictionarySpec, directory: File): Boolean =
        (listOf(spec.mdx) + spec.resources).all { asset ->
            val file = File(directory, asset.fileName())
            file.isFile && FileIntegrity.sha256(file).equals(asset.sha256, ignoreCase = true)
        }
}

private fun BuiltinAsset.fileName(): String = path.substringAfterLast('/')

internal class BuiltinDictionaryInstaller(
    private val context: Context,
    private val repository: DictionaryRepository,
) {
    suspend fun installAll(): BuiltinInstallSummary {
        val manifest = readManifest() ?: return BuiltinInstallSummary(0, 0, emptyList())
        require(manifest.schemaVersion == 1) { "Unsupported built-in manifest schema" }
        var installed = 0
        var unchanged = 0
        val errors = mutableListOf<String>()
        for (spec in manifest.dictionaries) {
            try {
                if (install(spec)) installed++ else unchanged++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errors += "${spec.id}: ${error.message ?: error::class.java.simpleName}"
            }
        }
        return BuiltinInstallSummary(installed, unchanged, errors)
    }

    internal fun readManifest(): BuiltinManifest? {
        val raw = try {
            context.assets.open(MANIFEST_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        val root = JSONObject(raw)
        val array = root.optJSONArray("dictionaries") ?: return BuiltinManifest(root.getInt("schemaVersion"), emptyList())
        val dictionaries = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val mdx = item.getJSONObject("mdx")
                val resources = item.optJSONArray("resources")
                add(
                    BuiltinDictionarySpec(
                        id = item.getString("id"),
                        displayName = item.getString("displayName"),
                        mdx = BuiltinAsset(mdx.getString("path"), mdx.getString("sha256")),
                        resources = buildList {
                            if (resources != null) for (resourceIndex in 0 until resources.length()) {
                                val resource = resources.getJSONObject(resourceIndex)
                                add(BuiltinAsset(resource.getString("path"), resource.getString("sha256")))
                            }
                        },
                    ),
                )
            }
        }
        return BuiltinManifest(root.getInt("schemaVersion"), dictionaries)
    }

    private suspend fun install(spec: BuiltinDictionarySpec): Boolean {
        require(spec.id.matches(Regex("[A-Za-z0-9._-]+"))) { "Unsafe built-in dictionary id" }
        BuiltinInstallationVerifier.validate(spec.mdx)
        spec.resources.forEach(BuiltinInstallationVerifier::validate)
        val current = repository.findById(spec.id)
        val duplicate = repository.findDuplicate(spec.mdx.sha256.lowercase(Locale.ROOT))
        require(duplicate == null || duplicate.id == spec.id) { "Built-in MDX hash is already registered" }
        if (current != null && BuiltinInstallationVerifier.matches(spec, File(current.privateMdxPath).parentFile!!)) {
            return false
        }

        val operationRoot = File(context.filesDir, "dictionaries/installing/${UUID.randomUUID()}")
        val target = File(context.filesDir, "dictionaries/builtin/${spec.id}")
        val promotion = AtomicDirectoryPromotion(target)
        try {
            operationRoot.mkdirs()
            val mdxFile = copyAsset(spec.mdx, operationRoot)
            val mddFiles = spec.resources.map { copyAsset(it, operationRoot) }
            val formatVersion = PureKotlinMdictEngine.open(MdictSource(mdxFile, mddFiles)).use {
                it.metadata.formatVersion
            }
            promotion.promote(operationRoot)
            val timestamp = System.currentTimeMillis()
            val finalMdx = File(target, mdxFile.name)
            val order = current?.displayOrder ?: repository.nextDisplayOrder()
            repository.replace(
                DictionaryEntity(
                    id = spec.id,
                    displayName = spec.displayName,
                    sourceType = DictionarySourceType.BUILTIN,
                    privateMdxPath = finalMdx.absolutePath,
                    mdxSha256 = spec.mdx.sha256.lowercase(Locale.ROOT),
                    enabled = current?.enabled ?: true,
                    displayOrder = order,
                    formatVersion = formatVersion,
                    runtimeStatus = if (mddFiles.isEmpty()) DictionaryStatus.RESOURCE_MISSING else DictionaryStatus.READY,
                    createdAt = current?.createdAt ?: timestamp,
                    updatedAt = timestamp,
                    fontLevel = current?.fontLevel ?: 5,
                ),
                spec.resources.mapIndexed { index, asset ->
                    DictionaryResourceEntity(
                        id = "${spec.id}.resource.$index",
                        dictionaryId = spec.id,
                        privateMddPath = File(target, asset.fileName()).absolutePath,
                        mddSha256 = asset.sha256.lowercase(Locale.ROOT),
                        partOrder = index,
                    )
                },
            )
            promotion.commit()
            return true
        } catch (error: Throwable) {
            promotion.rollback()
            if (operationRoot.exists()) operationRoot.deleteRecursively()
            throw error
        }
    }

    private fun copyAsset(asset: BuiltinAsset, directory: File): File {
        val target = File(directory, asset.fileName())
        val actual = context.assets.open("dictionaries/${asset.path}").use {
            FileIntegrity.copyAndHash(it, target)
        }
        require(actual.equals(asset.sha256, ignoreCase = true)) { "Built-in asset hash mismatch" }
        return target
    }

    companion object {
        const val MANIFEST_PATH = "dictionaries/manifest.json"
    }
}
