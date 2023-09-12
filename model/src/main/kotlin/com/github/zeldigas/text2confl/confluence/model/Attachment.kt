package com.github.zeldigas.text2confl.confluence.model

import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.io.path.inputStream

data class Attachment(
    val attachmentName: String, val linkName: String, val resourceLocation: Path
) {

    companion object {
        fun fromLink(name: String, location: Path): Attachment {
            return Attachment(normalizeName(name), name, location)
        }

        private fun normalizeName(name: String): String {
            return name.replace("../", "__").replace("./", "")
                .replace("/", "_")
        }
    }

    val hash: String by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        resourceLocation.inputStream().use {
            val byteArray = ByteArray(4096)
            val digestInputStream = DigestInputStream(it, md)
            while (digestInputStream.read(byteArray) != -1) {
            }
        }
        toBase64(md.digest())
    }
}