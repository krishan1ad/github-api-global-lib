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

                // Ensure the local directory structure exists
                def localDir = "${tempDir}/${artifactDir}".replaceAll('/+', '/')
                sh """
                    mkdir -p "${localDir}"
                """

                // Download the artifact
                def downloadCmd = """
                    curl -sSf -u "\${SOURCE_USER}:\${SOURCE_PASSWORD}" -o "${localFile}" "${sourceArtifactUrl}" 2> ${tempDir}/download_error.log
                """
                echo "Downloading artifact with command: ${downloadCmd}"
                sh(downloadCmd)

                // Check if the file exists and is not empty
                if (fileExists(localFile) && sh(script: "test -s '${localFile}'", returnStatus: true) == 0) {
                    echo "Artifact downloaded successfully: ${localFile}"

                    // Ensure the target directory structure exists on the remote server
                    def targetDirUrl = "${targetUrl}/${targetRepo}/${artifactDir}".replaceAll('/+', '/')
                    def mkdirTargetDirCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -X MKCOL "${targetDirUrl}/" 2> ${tempDir}/mkdir_error.log
                    """
                    echo "Creating target directory with command: ${mkdirTargetDirCmd}"
                    sh(mkdirTargetDirCmd)

                    // Print directory creation errors if any
                    if (fileExists("${tempDir}/mkdir_error.log")) {
                        echo "Directory creation errors:"
                        sh "cat ${tempDir}/mkdir_error.log"
                    }

                    // Upload the artifact
                    def uploadCmd = """
                        curl -sSf -u "\${TARGET_USER}:\${TARGET_PASSWORD}" -T "${localFile}" "${targetArtifactUrl}" 2> ${tempDir}/upload_error.log
                    """
                    echo "Uploading artifact with command: ${uploadCmd}"
                    def uploadStatus = sh(script: uploadCmd, returnStatus: true)

                    // Print upload errors if any
                    if (fileExists("${tempDir}/upload_error.log")) {
                        echo "Upload errors:"
                        sh "cat ${tempDir}/upload_error.log"
                    }

                    if (uploadStatus == 0) {
                        echo "Artifact migrated successfully: ${artifactPath}"
                    } else {
                        echo "Failed to migrate artifact: ${artifactPath}"
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
