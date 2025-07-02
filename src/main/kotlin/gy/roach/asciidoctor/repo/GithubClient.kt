package gy.roach.asciidoctor.repo

import gy.roach.asciidoctor.config.GitHubConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.time.LocalDateTime
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Client for interacting with GitHub repositories.
 * Handles cloning and updating repositories using Personal Access Tokens.
 */
@Component
class GithubClient(
    private val githubConfig: GitHubConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        if (githubConfig.disableSslValidation) {
            disableSslCertificateValidation()
            logger.warn("SSL certificate validation has been disabled. This should only be used in trusted environments.")
        }
        
        // Create base directories if they don't exist
        createDirectoryIfNotExists(githubConfig.stagingBaseDir)
        createDirectoryIfNotExists(githubConfig.webBaseDir)
    }

    /**
     * Data class representing the result of a GitHub repository operation.
     */
    data class GitOperationResult(
        val sourceDirectory: String,
        val targetDirectory: String,
        val filesProcessed: Int,
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val success: Boolean = true,
        val errorMessage: String? = null
    )
    
    /**
     * Exception thrown when GitHub API operations fail.
     */
    class GithubApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * Process a GitHub repository by cloning or updating it.
     * 
     * @param repoName The name of the repository
     * @param pat Personal Access Token for authentication
     * @param name The name to use for the local directory
     * @param gitUrl The URL of the Git repository
     * @param branch The branch to checkout
     * @return GitOperationResult containing information about the operation
     */
    fun processRepository(
        repoName: String,
        pat: String,
        name: String,
        gitUrl: String,
        branch: String
    ): GitOperationResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Create source and target directories
            val sourceDir = Paths.get(githubConfig.stagingBaseDir, name).toString()
            val targetDir = Paths.get(githubConfig.webBaseDir, name).toString()
            
            logger.info("Processing repository: $repoName to $sourceDir")
            
            // Create directories if they don't exist
            createDirectoryIfNotExists(sourceDir)
            createDirectoryIfNotExists(targetDir)
            
            // Check if the directory is already a git repository
            val isExistingRepo = File(sourceDir, ".git").exists()
            
            val credentialsProvider = UsernamePasswordCredentialsProvider("token", pat)
            
            val filesProcessed = if (isExistingRepo) {
                // Update existing repository
                updateRepository(sourceDir, branch, credentialsProvider)
            } else {
                // Clone new repository
                cloneRepository(gitUrl, sourceDir, branch, credentialsProvider)
            }
            
            val duration = System.currentTimeMillis() - startTime
            logger.info("Repository $repoName processed successfully in ${duration}ms. Files processed: $filesProcessed")
            
            return GitOperationResult(
                sourceDirectory = sourceDir,
                targetDirectory = targetDir,
                filesProcessed = filesProcessed
            )
        } catch (e: Exception) {
            val errorMessage = "Failed to process repository $repoName: ${e.message}"
            logger.error(errorMessage, e)
            throw GithubApiException(errorMessage, e)
        }
    }
    
    /**
     * Clone a repository to the specified directory.
     */
    private fun cloneRepository(
        gitUrl: String,
        directory: String,
        branch: String,
        credentialsProvider: UsernamePasswordCredentialsProvider
    ): Int {
        try {
            logger.info("Cloning repository from $gitUrl to $directory, branch: $branch")
            
            val git = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(File(directory))
                .setBranch(branch)
                .setCredentialsProvider(credentialsProvider)
                .call()
            
            // Count files in the repository
            val filesCount = countFiles(directory)
            
            git.close()
            
            return filesCount
        } catch (e: GitAPIException) {
            logger.error("Failed to clone repository", e)
            throw GithubApiException("Failed to clone repository: ${e.message}", e)
        }
    }
    
    /**
     * Update an existing repository.
     */
    private fun updateRepository(
        directory: String,
        branch: String,
        credentialsProvider: UsernamePasswordCredentialsProvider
    ): Int {
        try {
            logger.info("Updating repository in $directory, branch: $branch")
            
            val git = Git.open(File(directory))
            
            // Checkout the specified branch
            git.checkout()
                .setName(branch)
                .call()
            
            // Pull the latest changes
            git.pull()
                .setCredentialsProvider(credentialsProvider)
                .call()
            
            // Count files in the repository
            val filesCount = countFiles(directory)
            
            git.close()
            
            return filesCount
        } catch (e: GitAPIException) {
            logger.error("Failed to update repository", e)
            throw GithubApiException("Failed to update repository: ${e.message}", e)
        }
    }
    
    /**
     * Count the number of files in a directory (excluding .git directory).
     */
    private fun countFiles(directory: String): Int {
        return File(directory)
            .walkTopDown()
            .filter { it.isFile }
            .filter { !it.path.contains("/.git/") }
            .count()
    }
    
    /**
     * Create a directory if it doesn't exist.
     */
    private fun createDirectoryIfNotExists(directory: String) {
        val dir = File(directory)
        if (!dir.exists()) {
            logger.info("Creating directory: $directory")
            if (!dir.mkdirs()) {
                throw GithubApiException("Failed to create directory: $directory")
            }
        }
    }
    
    /**
     * Disable SSL certificate validation.
     * This should only be used in trusted environments.
     */
    private fun disableSslCertificateValidation() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            SSLContext.setDefault(sc)
        } catch (e: Exception) {
            logger.error("Failed to disable SSL validation", e)
        }
    }
}