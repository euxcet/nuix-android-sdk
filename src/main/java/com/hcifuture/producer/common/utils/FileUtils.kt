package com.hcifuture.producer.common.utils

import java.io.File

class FileUtils {
    companion object {
        fun loadVisibleFile(path: File): List<File> {
            val files = mutableListOf<File>()
            val subFiles = path.listFiles() ?: return files
            for (file in subFiles) {
                if (!file.isHidden) {
                    if (file.isDirectory) {
                        files.addAll(loadVisibleFile(file))
                    } else {
                        files.add(file)
                    }
                }
            }
            return files
        }
    }
}
