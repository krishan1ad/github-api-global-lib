def call(Map params) {
    def sourceUrl = params.sourceUrl
    def sourceRepo = params.sourceRepo
    def sourceUsername = params.sourceUsername
    def sourcePassword = params.sourcePassword
    def targetUrl = params.targetUrl
    def targetRepo = params.targetRepo
    def targetUsername = params.targetUsername
    def targetPassword = params.targetPassword
    def tempDir = params.tempDir ?: '/tmp/artifacts'

    if (!sourceUrl || !sourceRepo || !sourceUsername || !sourcePassword ||
        !targetUrl || !targetRepo || !targetUsername || !targetPassword) {
        error "Missing required parameters for artifact migration."
    }

    try {
        // Create and clear temporary directory
        sh """
            mkdir -p "${tempDir}"
            rm -rf "${tempDir}/*"
        """

        // Fetch list of artifacts
        def artifactsJson = sh(script: """
            curl -sS -u "${sourceUsername}:${sourcePassword}" "${sourceUrl}/api/storage/${sourceRepo}/?list&deep=1" | jq -r '.["files"] | .[]?.uri'
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
                curl -sSf -u "${sourceUsername}:${sourcePassword}" -X GET "${sourceArtifactUrl}" -o "${tempDir}/${artifactName}"
            """

            if (fileExists("${tempDir}/${artifactName}")) {
                // Upload the artifact
                def uploadStatus = sh(script: """
                    curl -sSf -u "${targetUsername}:${targetPassword}" -X PUT "${targetArtifactUrl}" -T "${tempDir}/${artifactName}"
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
