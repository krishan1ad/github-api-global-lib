for (artifact in artifacts) {
    def artifactPath = artifact.replaceFirst("^/${sourceRepo}/", '') // Remove leading repo path
    def artifactName = artifactPath.split('/').last()
    def artifactDir = artifactPath - "/${artifactName}"
    def localFile = "${tempDir}/${artifactPath.replaceAll('//', '/')}"

    // Ensure the local directory structure exists
    sh """
        mkdir -p "${tempDir}/${artifactDir.replaceAll('//', '/').trim()}"
    """

    // Construct URLs
    def sourceArtifactUrl = "${sourceUrl}/${sourceRepo}/${artifactPath.replaceAll('//', '/')}"
    def targetArtifactUrl = "${targetUrl}/${targetRepo}/${artifactPath.replaceAll('//', '/')}"
    
    echo "Downloading from ${sourceArtifactUrl} to ${localFile}"
    
    // Download the artifact
    def downloadResult = sh(script: """
        curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -o "${localFile}" "${sourceArtifactUrl}" || echo "Download failed with exit code \$?"
    """, returnStdout: true).trim()

    if (downloadResult.contains("Download failed")) {
        error "Failed to download artifact: ${artifactPath}. Response: ${downloadResult}"
    }

    if (fileExists(localFile)) {
        // Ensure the target directory structure exists on the remote server
        def targetDir = "${targetUrl}/${targetRepo}/${artifactDir.replaceAll('//', '/').trim()}"
        sh """
            curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X MKCOL "${targetDir}/" || true
        """

        // Upload the artifact
        def uploadStatus = sh(script: """
            curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -T "${localFile}" "${targetArtifactUrl}"
        """, returnStatus: true)

        if (uploadStatus == 0) {
            echo "Artifact migrated successfully: ${artifactPath}"
        } else {
            echo "Failed to migrate artifact: ${artifactPath}"
        }

        // Clean up the temporary file
        sh "rm -f '${localFile}'"
    } else {
        error "Failed to download artifact: ${artifactPath}"
    }
}
