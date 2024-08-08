def call(Map params) {
    def sourceUrl = 'https://repository.mpa.com'  // Updated to HTTPS
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
            // Create and clear temporary directory
            sh """
                mkdir -p "${tempDir}"
                rm -rf "${tempDir}/*"
            """

            // Fetch list of artifacts
            def artifactsJson = sh(script: """
                curl -sS -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" "${sourceUrl}/api/storage/${sourceRepo}/?list&deep=1" | jq -r '.["files"] | .[]?.uri'
            """, returnStdout: true).trim()

            if (!artifactsJson) {
                error "Failed to fetch artifacts from source repository."
            }

            def artifacts = artifactsJson.split('\n')

            for (artifact in artifacts) {
                def artifactName = artifact.split('/').last()
                def sourceArtifactUrl = "${sourceUrl}/${sourceRepo}/${artifactName}"
                def targetArtifactUrl = "${targetUrl}/${targetRepo}/${artifactName}"

                // Download the artifact
                sh """
                    curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -X GET "${sourceArtifactUrl}" -o "${tempDir}/${artifactName}"
                """

                if (fileExists("${tempDir}/${artifactName}")) {
                    // Upload the artifact
                    def uploadStatus = sh(script: """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X PUT "${targetArtifactUrl}" -T "${tempDir}/${artifactName}"
                    """, returnStatus: true)

                    if (uploadStatus == 0) {
                        echo "Artifact migrated successfully: ${artifactName}"
                    } else {
                        echo "Failed to migrate artifact: ${artifactName}"
                    }

                    // Clean up the temporary file
                    sh "rm '${tempDir}/${artifactName}'"
                } else {
                    error "Failed to download artifact: ${artifactName}"
                }
            }

            echo "Migration process completed."

        } catch (Exception e) {
            error "Migration process failed: ${e.message}"
        }
    }
}
