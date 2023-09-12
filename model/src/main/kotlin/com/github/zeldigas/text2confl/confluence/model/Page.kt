package com.github.zeldigas.text2confl.confluence.model

import java.nio.file.Path

data class Page(
    val content: PageContent,
    val source: Path,
    val children: List<Page>
) {
    val title: String
        get() = content.header.title
    val properties: Map<String, Any>
        get() = content.header.pageProperties
    val virtual: Boolean
        get() {
            val virtualAttr: Any = content.header.attributes["_virtual_"] ?: return false
            return when (virtualAttr) {
                is Boolean -> virtualAttr
                is String -> virtualAttr.toBoolean()
                else -> false
            }
        }
}