def call(Map params) {
    def sourceUrl = 'https://repository.mpsa.com'  // Updated to HTTPS
    def targetUrl = 'http://192.168.1.148:8081/artifactory'
    def sourceRepo = params.sourceRepo
    def sourceCredentialsId = params.sourceCredentialsId
    def targetRepo = params.targetRepo
    def targetCredentialsId = params.targetCredentialsId
    def tempDir = params.tempDir ?: '/tmp/artifacts'

    if (!sourceRepo || !sourceCredentialsId || !targetRepo || !targetCredentialsId) {
        error "Missing required parameters for artifact migration."
    }

    withCredentials([
        usernamePassword(credentialsId: sourceCredentialsId, usernameVariable: 'SOURCE_USER', passwordVariable: 'SOURCE_PASSWORD'),
        usernamePassword(credentialsId: targetCredentialsId, usernameVariable: 'TARGET_USER', passwordVariable: 'TARGET_PASSWORD')
    ]) {
        try {
            // Create and clear the temporary directory
            sh """
                mkdir -p "${tempDir}"
                rm -rf "${tempDir}/*"
            """

            // Fetch list of artifacts
            def artifactsJson = sh(script: """
                curl -sS -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" "${sourceUrl}/api/storage/${sourceRepo}/?list&deep=1" | jq -r '.files[]?.uri'
            """, returnStdout: true).trim()

            if (!artifactsJson) {
                error "Failed to fetch artifacts from source repository or no artifacts found."
            }

            def artifacts = artifactsJson.split('\n')

            for (artifact in artifacts) {
                def artifactPath = artifact.replaceFirst("^/${sourceRepo}/", '') // Remove leading repo path
                def artifactName = artifactPath.split('/').last()
                def artifactDir = artifactPath - "/${artifactName}"
                def localFile = "${tempDir}/${artifactPath}"
                def sourceArtifactUrl = "${sourceUrl}/${sourceRepo}/${artifactPath}"
                def targetArtifactUrl = "${targetUrl}/${targetRepo}/${artifactPath}"

                // Ensure the local directory structure exists
                sh """
                    mkdir -p "${tempDir}/${artifactDir}"
                """

                // Download the artifact
                sh """
                    curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -o "${localFile}" "${sourceArtifactUrl}" > /dev/null 2>&1
                """

                if (fileExists(localFile)) {
                    // Ensure the target directory structure exists on the remote server
                    sh """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X MKCOL "${targetUrl}/${targetRepo}/${artifactDir}/" > /dev/null 2>&1 || true
                    """

                    // Upload the artifact
                    def uploadStatus = sh(script: """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -T "${localFile}" "${targetArtifactUrl}" > /dev/null 2>&1
                    """, returnStatus: true)

                    if (uploadStatus == 0) {
                        echo "Artifact migrated successfully: ${artifactPath}"
                    } else {
                        echo "Failed to migrate artifact: ${artifactPath}"
                    }

                    // Clean up the temporary file
                    sh "rm '${localFile}'"
                } else {
                    error "Failed to download artifact: ${artifactPath}"
                }
            }

            echo "Migration process completed."

        } catch (Exception e) {
            error "Migration process failed: ${e.message}"
        }
    }
}