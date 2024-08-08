def call(Map params) {
    def sourceUrl = 'https://repository.mpsa.com'
    def targetUrl = 'http://192.168.1.148:8081/artifactory'
    def sourceRepo = params.sourceRepo
    def sourceCredentialsId = params.sourceCredentialsId
    def targetRepo = params.targetRepo
    def targetCredentialsId = params.targetCredentialsId
    def tempDir = "${env.WORKSPACE}/artifacts"  // Use Jenkins workspace directory for temp files

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
                def targetDirUrl = "${targetUrl}/${targetRepo}/${artifactDir}".replaceAll('/+', '/')

                // Check if the target file already exists
                def fileExistsCmd = """
                    curl -sI -u "\${TARGET_USER}:\${TARGET_PASSWORD}" "${targetArtifactUrl}" | grep -q "HTTP/1.1 200 OK"
                """
                def fileExists = sh(script: fileExistsCmd, returnStatus: true) == 0

                if (fileExists) {
                    echo "Skipping migration for existing artifact: ${artifactPath}"
                    continue
                }

                // Ensure the local directory structure exists
                def localDir = "${tempDir}/${artifactDir}".replaceAll('/+', '/')
                sh """
                    mkdir -p "${localDir}"
                """

                // Download the artifact
                def downloadCmd = """
                    curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -o "${localFile}" "${sourceArtifactUrl}"
                """
                echo "Downloading artifact with command: ${downloadCmd}"
                def downloadStatus = sh(script: downloadCmd, returnStatus: true)

                if (downloadStatus != 0) {
                    error "Failed to download artifact from ${sourceArtifactUrl}"
                }

                // Check if the file exists and is not empty
                if (fileExists(localFile) && sh(script: "test -s '${localFile}'", returnStatus: true) == 0) {
                    echo "Artifact downloaded successfully: ${localFile}"

                    // Ensure the target directory structure exists on the remote server
                    def mkdirTargetDirCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X MKCOL "${targetDirUrl}/" 2>/dev/null || true
                    """
                    echo "Creating target directory with command: ${mkdirTargetDirCmd}"
                    sh(mkdirTargetDirCmd)

                    // Upload the artifact
                    def uploadCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -T "${localFile}" "${targetArtifactUrl}"
                    """
                    echo "Uploading artifact with command: ${uploadCmd}"
                    def uploadStatus = sh(script: uploadCmd, returnStatus: true)

                    if (uploadStatus == 0) {
                        echo "Artifact migrated successfully: ${artifactPath}"
                    } else {
                        error "Failed to upload artifact to ${targetArtifactUrl}"
                    }

                    // Clean up the temporary file
                    sh "rm '${localFile}'"
                } else {
                    error "Failed to download or the downloaded file is empty: ${artifactPath}"
                }
            }

            echo "Migration process completed."

        } catch (Exception e) {
            error "Migration process failed: ${e.message}"
        }
    }
}
