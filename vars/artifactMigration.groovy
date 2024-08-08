def call(Map params) {
    def sourceUrl = 'https://repository.mpsa.com'
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
            def fetchArtifactsCmd = """
                curl -sS -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" "${sourceUrl}/api/storage/${sourceRepo}/?list&deep=1" | jq -r '.files[]?.uri'
            """
            echo "Fetching artifacts with command: ${fetchArtifactsCmd}"

            def artifactsJson = sh(script: fetchArtifactsCmd, returnStdout: true).trim()

            if (!artifactsJson) {
                error "Failed to fetch artifacts from source repository or no artifacts found."
            }

            def artifacts = artifactsJson.split('\n')

            for (artifact in artifacts) {
                def artifactPath = artifact.replaceFirst("^/${sourceRepo}/", '')
                def artifactName = artifactPath.split('/').last()
                def artifactDir = artifactPath - "/${artifactName}"

                // Normalize paths
                def localFile = "${tempDir}/${artifactPath}".replaceAll('/+', '/')
                def sourceArtifactUrl = "${sourceUrl}/${sourceRepo}/${artifactPath}".replaceAll('/+', '/')
                def targetArtifactUrl = "${targetUrl}/${targetRepo}/${artifactPath}".replaceAll('/+', '/')

                // Ensure the local directory structure exists
                def localDir = "${tempDir}/${artifactDir}".replaceAll('/+', '/')
                sh """
                    mkdir -p "${localDir}"
                """

                // Download the artifact
                def downloadCmd = """
                    curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -o "${localFile}" "${sourceArtifactUrl}" 2> /tmp/download_error.log
                """
                echo "Downloading artifact with command: ${downloadCmd}"
                sh(downloadCmd)

                // Print download errors if any
                sh "cat /tmp/download_error.log || true"

                if (fileExists(localFile)) {
                    // Ensure the target directory structure exists on the remote server
                    def mkdirTargetDirCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X MKCOL "${targetUrl}/${targetRepo}/${artifactDir}/".replaceAll('/+', '/') 2> /tmp/mkdir_error.log
                    """
                    echo "Creating target directory with command: ${mkdirTargetDirCmd}"
                    sh(mkdirTargetDirCmd)

                    // Print directory creation errors if any
                    sh "cat /tmp/mkdir_error.log || true"

                    // Upload the artifact
                    def uploadCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -T "${localFile}" "${targetArtifactUrl}" 2> /tmp/upload_error.log
                    """
                    echo "Uploading artifact with command: ${uploadCmd}"
                    def uploadStatus = sh(script: uploadCmd, returnStatus: true)

                    // Print upload errors if any
                    sh "cat /tmp/upload_error.log || true"

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
