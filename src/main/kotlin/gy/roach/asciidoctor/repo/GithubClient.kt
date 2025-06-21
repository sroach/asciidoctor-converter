package gy.roach.asciidoctor.repo

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * Enum representing the status of a file in a GitHub repository
 */
enum class FileStatus {
    MODIFIED, NEW, DELETED, MOVED
}

/**
 * Data class representing a file with its content and status
 */
data class FileContent(
    val content: String,
    val status: FileStatus,
    val path: String,
    val sha: String
)

/**
 * Service for interacting with GitHub API
 */
@Service
class GithubClient(private val restTemplate: RestTemplate = RestTemplate()) {

    private val logger = LoggerFactory.getLogger(GithubClient::class.java)

    @Value("\${github.api.base-url}")
    private lateinit var baseUrl: String

    @Value("\${github.api.token:}")
    private lateinit var token: String

    /**
     * Exception thrown when a GitHub API error occurs
     */
    class GithubApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /**
     * Get content of a file from GitHub repository
     * 
     * @param repo GitHub repository in format "owner/repo"
     * @param branch Branch name
     * @param path Path to the file
     * @return FileContent object containing the file content and status
     * @throws GithubApiException if an error occurs while retrieving the content
     */
    fun getContent(repo: String, branch: String, path: String): FileContent {
        val url = "$baseUrl/repos/$repo/contents/$path?ref=$branch"

        val headers = HttpHeaders()
        if (token.isNotBlank()) {
            headers.set("Authorization", "token $token")
        }

        try {
            logger.debug("Fetching content from GitHub: $url")

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                Map::class.java
            )

            val responseBody = response.body as Map<*, *>

            // Check if the response is a file (not a directory)
            if (responseBody["type"] != "file") {
                throw GithubApiException("Path does not point to a file: $path")
            }

            val content = try {
                Base64.getDecoder().decode(responseBody["content"].toString()).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.warn("Failed to decode content for file: $path", e)
                "" // Return empty content if decoding fails
            }

            val sha = responseBody["sha"].toString()

            // Determine file status by checking commit history
            val status = try {
                determineFileStatus(repo, branch, path, sha)
            } catch (e: Exception) {
                logger.warn("Failed to determine file status for: $path", e)
                FileStatus.MODIFIED // Default to MODIFIED if status determination fails
            }

            return FileContent(content, status, path, sha)
        } catch (e: HttpClientErrorException) {
            when (e.statusCode) {
                HttpStatus.NOT_FOUND -> {
                    logger.warn("File not found: $path in $repo/$branch")
                    // Check if the file was deleted
                    try {
                        val deletedStatus = checkIfFileWasDeleted(repo, branch, path)
                        if (deletedStatus) {
                            return FileContent("", FileStatus.DELETED, path, "")
                        }
                    } catch (ex: Exception) {
                        logger.warn("Failed to check if file was deleted: $path", ex)
                    }
                    throw GithubApiException("File not found: $path in $repo/$branch", e)
                }
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> {
                    logger.error("Authentication error accessing GitHub API: ${e.message}")
                    throw GithubApiException("Authentication error accessing GitHub API", e)
                }
                else -> {
                    logger.error("GitHub API error: ${e.message}")
                    throw GithubApiException("GitHub API error: ${e.statusCode} - ${e.message}", e)
                }
            }
        } catch (e: RestClientException) {
            logger.error("REST client error: ${e.message}")
            throw GithubApiException("Error communicating with GitHub API", e)
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}")
            throw GithubApiException("Unexpected error retrieving content from GitHub", e)
        }
    }

    /**
     * Check if a file was deleted by looking at recent commits
     * 
     * @param repo GitHub repository in format "owner/repo"
     * @param branch Branch name
     * @param path Path to the file
     * @return true if the file was deleted, false otherwise
     */
    private fun checkIfFileWasDeleted(repo: String, branch: String, path: String): Boolean {
        val commitsUrl = "$baseUrl/repos/$repo/commits?sha=$branch&path=$path"

        val headers = HttpHeaders()
        if (token.isNotBlank()) {
            headers.set("Authorization", "token $token")
        }

        try {
            val response = restTemplate.exchange(
                commitsUrl,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                List::class.java
            )

            val commits = response.body as List<*>

            if (commits.isNotEmpty()) {
                val latestCommit = commits.first() as Map<*, *>
                val commitSha = latestCommit["sha"].toString()
                val commitUrl = "$baseUrl/repos/$repo/commits/$commitSha"

                val commitResponse = restTemplate.exchange(
                    commitUrl,
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    Map::class.java
                )

                val commitDetails = commitResponse.body as Map<*, *>
                val files = commitDetails["files"] as List<*>

                for (file in files) {
                    val fileData = file as Map<*, *>
                    if (fileData["status"] == "removed" && fileData["filename"] == path) {
                        return true
                    }
                }
            }

            return false
        } catch (e: Exception) {
            logger.warn("Error checking if file was deleted: $path", e)
            return false
        }
    }

    /**
     * Determine the status of a file by checking its commit history
     * 
     * @param repo GitHub repository in format "owner/repo"
     * @param branch Branch name
     * @param path Path to the file
     * @param currentSha Current SHA of the file
     * @return FileStatus enum value representing the status of the file
     */
    private fun determineFileStatus(repo: String, branch: String, path: String, currentSha: String): FileStatus {
        val url = "$baseUrl/repos/$repo/commits?path=$path&sha=$branch"

        val headers = HttpHeaders()
        if (token.isNotBlank()) {
            headers.set("Authorization", "token $token")
        }

        try {
            logger.debug("Fetching commit history for file: $path in $repo/$branch")

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                List::class.java
            )

            val commits = response.body as List<*>

            if (commits.isEmpty()) {
                logger.debug("No commit history found for file: $path, assuming it's new")
                return FileStatus.NEW
            }

            // Check if the file was recently added
            if (commits.size == 1) {
                logger.debug("File has only one commit: $path, assuming it's new")
                return FileStatus.NEW
            }

            try {
                // Check the most recent commit to see if the file was deleted
                val latestCommit = commits.first() as Map<*, *>
                val latestCommitUrl = "$baseUrl/repos/$repo/commits/${latestCommit["sha"]}"

                val latestCommitResponse = restTemplate.exchange(
                    latestCommitUrl,
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    Map::class.java
                )

                val latestCommitData = latestCommitResponse.body as Map<*, *>
                val latestFiles = latestCommitData["files"] as? List<*> ?: emptyList<Any>()

                for (file in latestFiles) {
                    val fileData = file as? Map<*, *> ?: continue
                    if (fileData["status"] == "removed" && fileData["filename"] == path) {
                        logger.debug("File was deleted in the latest commit: $path")
                        return FileStatus.DELETED
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error checking latest commit for file: $path", e)
                // Continue to check other statuses
            }

            try {
                // Check if the file was moved by looking at the first commit
                val firstCommit = commits.lastOrNull() as? Map<*, *> ?: return FileStatus.MODIFIED
                val firstCommitUrl = "$baseUrl/repos/$repo/commits/${firstCommit["sha"]}"

                val firstCommitResponse = restTemplate.exchange(
                    firstCommitUrl,
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    Map::class.java
                )

                val firstCommitData = firstCommitResponse.body as Map<*, *>
                val files = firstCommitData["files"] as? List<*> ?: emptyList<Any>()

                for (file in files) {
                    val fileData = file as? Map<*, *> ?: continue
                    if (fileData["status"] == "renamed" && fileData["filename"] == path) {
                        logger.debug("File was moved: $path")
                        return FileStatus.MOVED
                    }
                }
            } catch (e: Exception) {
                logger.warn("Error checking first commit for file: $path", e)
                // Continue to default status
            }

            // If not new, deleted, or moved, it's modified
            logger.debug("File is modified: $path")
            return FileStatus.MODIFIED
        } catch (e: HttpClientErrorException) {
            logger.warn("HTTP error determining file status: ${e.statusCode} - ${e.message}")
            // Default to MODIFIED if we can't determine the status
            return FileStatus.MODIFIED
        } catch (e: Exception) {
            logger.warn("Error determining file status: ${e.message}")
            // Default to MODIFIED if we can't determine the status
            return FileStatus.MODIFIED
        }
    }

    /**
     * Get list of files in a directory from GitHub repository
     * 
     * @param repo GitHub repository in format "owner/repo"
     * @param branch Branch name
     * @param path Path to the directory
     * @return List of FileContent objects
     * @throws GithubApiException if an error occurs while retrieving the directory content
     */
    fun getDirectoryContent(repo: String, branch: String, path: String): List<FileContent> {
        val url = "$baseUrl/repos/$repo/contents/$path?ref=$branch"

        val headers = HttpHeaders()
        if (token.isNotBlank()) {
            headers.set("Authorization", "token $token")
        }

        try {
            logger.debug("Fetching directory content from GitHub: $url")

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                Any::class.java
            )

            val result = mutableListOf<FileContent>()

            // Handle both single file and directory responses
            when (val responseBody = response.body) {
                is Map<*, *> -> {
                    // Single file response
                    val fileType = responseBody["type"]?.toString()
                    if (fileType == "file") {
                        try {
                            val fileContent = getContent(repo, branch, path)
                            result.add(fileContent)
                        } catch (e: Exception) {
                            logger.warn("Error getting content for file: $path", e)
                        }
                    } else {
                        logger.warn("Path is not a file or directory: $path, type: $fileType")
                        throw GithubApiException("Path is not a file or directory: $path")
                    }
                }
                is List<*> -> {
                    // Directory response
                    val files = responseBody as List<*>

                    // Get existing files
                    for (file in files) {
                        val fileData = file as? Map<*, *> ?: continue
                        val filePath = fileData["path"]?.toString() ?: continue
                        val fileType = fileData["type"]?.toString() ?: continue

                        if (fileType == "file") {
                            try {
                                val fileContent = getContent(repo, branch, filePath)
                                result.add(fileContent)
                            } catch (e: Exception) {
                                logger.warn("Error getting content for file: $filePath", e)
                                // Continue with other files
                            }
                        }
                    }
                }
                else -> {
                    logger.error("Unexpected response type: ${responseBody?.javaClass}")
                    throw GithubApiException("Unexpected response type from GitHub API")
                }
            }

            // Check for deleted files by looking at recent commits
            try {
                val deletedFiles = getDeletedFiles(repo, branch, path)
                result.addAll(deletedFiles)
            } catch (e: Exception) {
                logger.warn("Error getting deleted files for directory: $path", e)
                // Continue with existing files
            }

            return result
        } catch (e: HttpClientErrorException) {
            when (e.statusCode) {
                HttpStatus.NOT_FOUND -> {
                    logger.warn("Directory not found: $path in $repo/$branch")
                    throw GithubApiException("Directory not found: $path in $repo/$branch", e)
                }
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> {
                    logger.error("Authentication error accessing GitHub API: ${e.message}")
                    throw GithubApiException("Authentication error accessing GitHub API", e)
                }
                else -> {
                    logger.error("GitHub API error: ${e.message}")
                    throw GithubApiException("GitHub API error: ${e.statusCode} - ${e.message}", e)
                }
            }
        } catch (e: RestClientException) {
            logger.error("REST client error: ${e.message}")
            throw GithubApiException("Error communicating with GitHub API", e)
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}")
            throw GithubApiException("Unexpected error retrieving directory content from GitHub", e)
        }
    }

    /**
     * Get list of deleted files in a directory from GitHub repository
     * 
     * @param repo GitHub repository in format "owner/repo"
     * @param branch Branch name
     * @param path Path to the directory
     * @return List of FileContent objects representing deleted files
     */
    private fun getDeletedFiles(repo: String, branch: String, path: String): List<FileContent> {
        val commitsUrl = "$baseUrl/repos/$repo/commits?sha=$branch&path=$path"

        val headers = HttpHeaders()
        if (token.isNotBlank()) {
            headers.set("Authorization", "token $token")
        }

        try {
            logger.debug("Fetching commit history for directory: $path in $repo/$branch")

            val response = restTemplate.exchange(
                commitsUrl,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                List::class.java
            )

            val commits = response.body as? List<*> ?: return emptyList()
            val result = mutableListOf<FileContent>()

            // Check recent commits for deleted files
            for (commit in commits.take(10)) { // Limit to 10 recent commits for performance
                try {
                    val commitData = commit as? Map<*, *> ?: continue
                    val commitSha = commitData["sha"]?.toString() ?: continue
                    val commitUrl = "$baseUrl/repos/$repo/commits/$commitSha"

                    logger.debug("Checking commit: $commitSha for deleted files")

                    val commitResponse = restTemplate.exchange(
                        commitUrl,
                        HttpMethod.GET,
                        HttpEntity<String>(headers),
                        Map::class.java
                    )

                    val commitDetails = commitResponse.body as? Map<*, *> ?: continue
                    val files = commitDetails["files"] as? List<*> ?: continue

                    for (file in files) {
                        try {
                            val fileData = file as? Map<*, *> ?: continue
                            val filePath = fileData["filename"]?.toString() ?: continue
                            val status = fileData["status"]?.toString() ?: continue

                            // Only consider files in the specified directory
                            if (status == "removed" && filePath.startsWith(path)) {
                                logger.debug("Found deleted file: $filePath in commit: $commitSha")

                                // Create a FileContent object for the deleted file
                                val deletedFile = FileContent(
                                    content = "", // Empty content for deleted files
                                    status = FileStatus.DELETED,
                                    path = filePath,
                                    sha = fileData["sha"]?.toString() ?: ""
                                )

                                // Add to result if not already present
                                if (result.none { it.path == filePath }) {
                                    result.add(deletedFile)
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Error processing file in commit: $commitSha", e)
                            // Continue with other files
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Error processing commit: ${commit.toString().take(10)}...", e)
                    // Continue with other commits
                }
            }

            logger.debug("Found ${result.size} deleted files in directory: $path")
            return result
        } catch (e: HttpClientErrorException) {
            logger.warn("HTTP error getting deleted files: ${e.statusCode} - ${e.message}")
            return emptyList() // Return empty list instead of failing
        } catch (e: Exception) {
            logger.warn("Error getting deleted files: ${e.message}")
            return emptyList() // Return empty list instead of failing
        }
    }
}
