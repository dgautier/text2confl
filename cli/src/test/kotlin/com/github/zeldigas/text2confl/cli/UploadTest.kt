package com.github.zeldigas.text2confl.cli

import assertk.all
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.zeldigas.confclient.ConfluenceClient
import com.github.zeldigas.confclient.ConfluenceClientConfig
import com.github.zeldigas.confclient.PasswordAuth
import com.github.zeldigas.confclient.TokenAuth
import com.github.zeldigas.text2confl.cli.config.ConverterConfig
import com.github.zeldigas.text2confl.cli.config.DirectoryConfig
import com.github.zeldigas.text2confl.cli.config.EditorVersion
import com.github.zeldigas.text2confl.cli.config.UploadConfig
import com.github.zeldigas.text2confl.convert.Converter
import com.github.zeldigas.text2confl.convert.Page
import io.ktor.http.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path


@ExtendWith(MockKExtension::class)
internal class UploadTest(
    @MockK private val serviceProvider: ServiceProvider,
    @MockK private val contentUploader: ContentUploader,
    @MockK private val confluenceClient: ConfluenceClient,
    @MockK private val converter: Converter
) {
    private val commnad = Upload()
    private val parentContext = Context.build(commnad) {}

    @BeforeEach
    internal fun setUp() {
        every { serviceProvider.createConverter(any(), any()) } returns converter
        every { serviceProvider.createConfluenceClient(any()) } returns confluenceClient
        every { serviceProvider.createUploader(confluenceClient, any(), any()) } returns contentUploader
        parentContext.obj = serviceProvider
    }

    @Test
    internal fun `All data from command line args`(@TempDir tempDir: Path) {
        val result = mockk<List<Page>>()
        every { converter.convertDir(tempDir) } returns result
        coEvery { contentUploader.uploadPages(result, "TR", "1234") } just Runs

        commnad.parse(
            listOf(
                "--confluence-url", "https://test.atlassian.net/wiki",
                "--space", "TR",
                "--user", "test",
                "--password", "test",
                "--parent-id", "1234",
                "--docs", tempDir.toString()
            ),
            parentContext
        )

        verify {
            serviceProvider.createConfluenceClient(
                ConfluenceClientConfig(
                    Url("https://test.atlassian.net/wiki"),
                    false,
                    PasswordAuth("test", "test")
                )
            )
        }
        verify { serviceProvider.createConverter("TR", ConverterConfig("", "", EditorVersion.V2)) }
        verify {
            serviceProvider.createUploader(
                confluenceClient, UploadConfig(
                    "TR", false, "Automated upload by text2confl", true, ChangeDetector.HASH
                ),
                ConverterConfig("", "", EditorVersion.V2)
            )
        }
    }

    @Test
    internal fun `Data from yaml config file`(@TempDir tempDir: Path) {
        val directoryConfig = sampleConfig()
        writeToFile(tempDir.resolve(".text2confl.yml"), directoryConfig)

        val result = mockk<List<Page>>()
        every { converter.convertDir(tempDir) } returns result
        coEvery { contentUploader.uploadPages(result, "TR", "1234") } just Runs

        commnad.parse(
            listOf(
                "--oauth-token", "token",
                "--message", "custom upload message",
                "--docs", tempDir.toString()
            ),
            parentContext
        )

        verify {
            serviceProvider.createConfluenceClient(
                ConfluenceClientConfig(
                    Url(directoryConfig.server!!),
                    directoryConfig.skipSsl,
                    TokenAuth("token")
                )
            )
        }
        val converterConfig = ConverterConfig(directoryConfig.titlePrefix, directoryConfig.titlePostfix, directoryConfig.editorVersion!!)
        verify { serviceProvider.createConverter(directoryConfig.space!!, converterConfig) }
        verify {
            serviceProvider.createUploader(
                confluenceClient, UploadConfig(
                    directoryConfig.space!!, directoryConfig.removeOrphans, "custom upload message", directoryConfig.notifyWatchers, directoryConfig.modificationCheck
                ),
                converterConfig
            )
        }
    }

    @Test
    internal fun `Any credential type must be specified`(@TempDir tempDir: Path) {
        assertThat { commnad.parse(
            listOf(
                "--space", "TR",
                "--confluence-url", "https://wiki.example.org",
                "--docs", tempDir.toString()
            ),
            parentContext
        )
        }.isFailure().isInstanceOf(PrintMessage::class).all {
            transform { it.error }.isTrue()
            hasMessage("Either access token or username/password should be specified")
        }
    }

    @Test
    internal fun `Space is required`(@TempDir tempDir: Path) {
        assertThat { commnad.parse(
            listOf(
                "--docs", tempDir.toString()
            ),
            parentContext
        )
        }.isFailure().isInstanceOf(PrintMessage::class).all {
            transform { it.error }.isTrue()
            hasMessage("Space is not specified. Use `--space` option or `space` in config file")
        }
    }

    @Test
    internal fun `Confluence url is required`(@TempDir tempDir: Path) {
        assertThat { commnad.parse(
            listOf(
                "--space", "TR",
                "--docs", tempDir.toString()
            ),
            parentContext
        )
        }.isFailure().isInstanceOf(PrintMessage::class).all {
            transform { it.error }.isTrue()
            hasMessage("Confluence url is not specified. Use `--confluence-url` option or `server` in config file")
        }
    }

    @Test
    internal fun `Resolution of page by title`(@TempDir tempDir: Path) {
        val result = mockk<List<Page>>()
        every { converter.convertDir(tempDir) } returns result
        coEvery { confluenceClient.getPage("TR", "Test page").id } returns "1234"
        coEvery { contentUploader.uploadPages(result, "TR", "1234") } just Runs
        commnad.parse(
            listOf(
                "--confluence-url", "https://test.atlassian.net/wiki",
                "--space", "TR",
                "--oauth-token", "test",
                "--parent", "Test page",
                "--docs", tempDir.toString()
            ),
            parentContext
        )

        coVerify {
            contentUploader.uploadPages(result, "TR", "1234")
        }
    }

    @Test
    internal fun `Using home page if not specified`(@TempDir tempDir: Path) {
        val result = mockk<List<Page>>()
        every { converter.convertDir(tempDir) } returns result
        coEvery { confluenceClient.describeSpace("TR", listOf("homepage")).homepage?.id } returns "1234"
        coEvery { contentUploader.uploadPages(result, "TR", "1234") } just Runs
        commnad.parse(
            listOf(
                "--confluence-url", "https://test.atlassian.net/wiki",
                "--space", "TR",
                "--oauth-token", "test",
                "--docs", tempDir.toString()
            ),
            parentContext
        )

        coVerify {
            contentUploader.uploadPages(result, "TR", "1234")
        }

    }

    private fun sampleConfig(): DirectoryConfig {
        return DirectoryConfig(
            server = "https://test.atlassian.net/wiki",
            skipSsl = true,
            space = "TR",
            defaultParentId = "1234",
            removeOrphans = true,
            notifyWatchers = false,
            titlePrefix = "Prefix: ",
            titlePostfix = " - Postfix",
            editorVersion = EditorVersion.V1,
            modificationCheck = ChangeDetector.CONTENT
        )
    }

    private fun writeToFile(dest: Path, config: DirectoryConfig) {
        val mapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()
        mapper.writeValue(dest.toFile(), config)
    }
}