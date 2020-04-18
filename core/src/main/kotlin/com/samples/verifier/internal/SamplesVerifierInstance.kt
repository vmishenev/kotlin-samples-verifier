package com.samples.verifier.internal

import com.samples.verifier.*
import com.samples.verifier.internal.utils.ExecutionHelper
import com.samples.verifier.internal.utils.cloneRepository
import com.samples.verifier.internal.utils.processFile
import com.samples.verifier.model.ExecutionResult
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class SamplesVerifierInstance(compilerUrl: String, kotlinEnv: KotlinEnv) : SamplesVerifier {
    private val logger = LoggerFactory.getLogger("Samples Verifier")
    private val executionHelper = ExecutionHelper(compilerUrl, kotlinEnv)

    override fun collect(url: String, attributes: List<String>, type: FileType): Map<ExecutionResult, Code> {
        val results = hashMapOf<ExecutionResult, Code>()
        processRepository(url, attributes, type) { code ->
            val result = executionHelper.executeCode(code)
            results[result] = code
        }
        return results
    }

    override fun check(url: String, attributes: List<String>, type: FileType) {
        processRepository(url, attributes, type) { code ->
            val result = executionHelper.executeCode(code)
            val errors = result.errors.values.flatten()
            if (errors.isNotEmpty()) {
                logger.info("Code: \n${code}")
                logger.info("Errors: \n${errors.joinToString("\n")}")
                result.exception?.let { logger.info("Exception: \n${it.localizedMessage}") }
                    ?: logger.info("Output: \n${result.text}")
            } else if (result.exception != null) {
                logger.info("Code: \n${code}")
                logger.info("Exception: \n${result.exception.message}")
            }
        }
    }

    override fun <T> parse(
        url: String,
        attributes: List<String>,
        type: FileType,
        processResult: (List<Code>) -> List<T>
    ): Map<T, Code> {
        val codeSnippets = mutableListOf<Code>()
        processRepository(url, attributes, type) { code ->
            codeSnippets.add(code)
        }
        return processResult(codeSnippets).zip(codeSnippets).toMap()
    }

    private fun processRepository(
        url: String,
        attributes: List<String>,
        type: FileType,
        processResult: (Code) -> Unit
    ) {
        val dir = File(url.substringAfterLast('/').substringBeforeLast('.'))
        try {
            logger.info("Cloning repository...")
            cloneRepository(dir, url)
            processFiles(dir, attributes, type, processResult)
        } catch (e: GitException) {
            //TODO
            logger.error("${e.message}")
        } catch (e: IOException) {
            //TODO
            logger.error("${e.message}")
        } finally {
            if (dir.isDirectory) {
                FileUtils.deleteDirectory(dir)
            } else {
                dir.delete()
            }
        }
    }

    private fun processFiles(
        directory: File,
        attributes: List<String>,
        type: FileType,
        processResult: (Code) -> Unit
    ) {
        Files.walk(directory.toPath()).use {
            it.forEach { path: Path ->
                val file = path.toFile()
                when (type) {
                    FileType.MD -> {
                        if (file.extension == "md") {
                            logger.info("Processing ${file}...")
                            processFile(file, type, attributes, processResult)
                        }
                    }
                    FileType.HTML -> {
                        if (file.extension == "html") {
                            logger.info("Processing ${file}...")
                            processFile(file, type, attributes, processResult)
                        }
                    }
                }
            }
        }
    }
}