package com.github.zeldigas.text2confl.convert.asciidoc

import com.github.zeldigas.text2confl.convert.*
import com.vladsch.flexmark.util.sequence.Escaping
import org.asciidoctor.ast.Document
import java.nio.file.Path

class AsciidocFileConverter(private val asciidocParser: AsciidocParser) : FileConverter {

    constructor(config: AsciidoctorConfiguration): this(AsciidocParser(config))

    override fun readHeader(file: Path, context: HeaderReadingContext): PageHeader {
        val document = asciidocParser.parseDocumentHeader(file)

        return toHeader(file, document, context.titleTransformer)
    }

    private fun toHeader(file: Path, document: Document, titleTransformer: (Path, String) -> String): PageHeader {
        return PageHeader(titleTransformer(file, computeTitle(document, file)), document.attributes)
    }

    private fun computeTitle(doc: Document, file: Path): String =
        doc.attributes["title"]?.toString()
            ?: documentTitle(doc)
            ?: file.toFile().nameWithoutExtension

    private fun documentTitle(doc: Document): String? {
        val title = doc.structuredDoctitle?.combined ?: return null
        return Escaping.unescapeHtml(doc.attributes.entries.fold(title) { current, (key, value) -> current.replace("{$key}", value.toString()) })
    }

    override fun convert(file: Path, context: ConvertingContext): PageContent {
        val attachmentsRegistry = AttachmentsRegistry()
        val document = try {
            asciidocParser.parseDocument(file, createAttributes(file, context, attachmentsRegistry))
        }catch (ex: Exception) {
            throw ConversionFailedException(file, "Document parsing failed", ex)
        }

        val body = try {
            document.convert()
        } catch (ex: Exception) {
            throw ConversionFailedException(file, "Document conversion failed", ex)
        }

        return PageContent(
            toHeader(file, document, context.titleTransformer), body, attachmentsRegistry.collectedAttachments.values.toList()
        )
    }

    private fun createAttributes(file: Path, context: ConvertingContext, registry: AttachmentsRegistry) = AsciidocRenderingParameters(
        context.languageMapper,
        AsciidocReferenceProvider(file, context.referenceProvider),
        context.autotextFor(file),
        context.conversionParameters.addAutogeneratedNote,
        context.targetSpace,
        AsciidocAttachmentCollector(file, AttachmentCollector(context.referenceProvider, registry))
    )
}