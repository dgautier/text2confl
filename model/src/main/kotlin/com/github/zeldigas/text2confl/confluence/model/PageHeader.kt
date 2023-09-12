package com.github.zeldigas.text2confl.confluence.model

data class PageHeader(
    val title: String, val attributes: Map<String, Any?>,
    private val labelsKeys: List<String> = listOf("labels")
) {
    val pageProperties: Map<String, Any> = buildMap {
        val propertyMap = attributes["properties"]
        if (propertyMap is Map<*, *>) {
            putAll(propertyMap.filterValues { it != null }.map { (k, v) -> "$k" to v!! })
        }
        putAll(attributes.asSequence()
            .filter { (k, v) -> k.startsWith("property_") && v != null }
            .map { (k, v) -> k.substringAfter("property_") to v!! }
        )
    }

    val pageLabels: List<String>
        get() {
            val labels = labelsKeys.map { attributes[it] }.filterNotNull().firstOrNull()
            return when (labels) {
                is List<*> -> labels.map { it.toString() }
                is String -> labels.split(",").map { it.trim() }
                else -> emptyList()
            }
        }
}