package com.hcifuture.producer.recorder

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class FileDatasetProvider @Inject constructor(
    @ApplicationContext val context: Context,
) {
    companion object {
        const val ROOT_DIR_NAME = "NuixDataset"
        const val ROOT_DATASET_NAME = "_ROOT"
    }
    private val root: File = File(context.externalCacheDir, ROOT_DIR_NAME)
    private val datasets: MutableList<FileDataset> = mutableListOf()

    init {
        if (!root.exists()) {
            root.mkdirs()
        }
        load()
    }

    private fun load() {
    }

    private fun createDataset(name: String, path: File): FileDataset {
        datasets.find { it.name == name}?.let { return it }
        val dataset = FileDataset(path, name)
        datasets.add(dataset)
        return dataset
    }

    fun createRoot(): FileDataset {
        return createDataset(ROOT_DATASET_NAME, root)
    }

    fun create(name: String): FileDataset {
        return createDataset(name, File(root, name))
    }

    fun get(name: String): FileDataset? =
        datasets.firstOrNull {
            it.name == name
        }
}