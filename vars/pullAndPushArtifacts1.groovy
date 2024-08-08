import groovy.json.JsonOutput

def call(
    String sourceUrl, String targetUrl, String repoPath, 
    String sourceCredentialsId, String targetCredentialsId
) {
    // Initialize JFrog Artifactory servers
    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(targetUrl)

    // Use the credentials from the source credentials ID
    withCredentials([usernamePassword(credentialsId: sourceCredentialsId, usernameVariable: 'SOURCE_USER', passwordVariable: 'SOURCE_PASSWORD')]) {
        // Define download specification for source Artifactory as a JSON string
        def downloadSpecMap = [
            "files": [
                [
                    "pattern": "${repoPath}",
                    "target": "download/"
                ]
            ]
        ]
        def downloadSpecJson = JsonOutput.toJson(downloadSpecMap)

        try {
            // Download artifacts from source Artifactory
            def downloadResponse = sourceArtifactory.download(downloadSpecJson)
            if (downloadResponse) {
                echo "Successfully downloaded artifacts from ${sourceUrl}/${repoPath}"
            } else {
                error "Failed to download artifacts from ${sourceUrl}/${repoPath}"
            }
        } catch (Exception e) {
            error "An error occurred during download: ${e.message}"
        }
    }

    // Use the credentials from the target credentials ID
    withCredentials([usernamePassword(credentialsId: targetCredentialsId, usernameVariable: 'TARGET_USER', passwordVariable: 'TARGET_PASSWORD')]) {
        // Define upload specification for target Artifactory as a JSON string
        def uploadSpecMap = [
            "files": [
                [
                    "pattern": "download/${repoPath}",
                    "target": "${repoPath}"
                ]
            ]
        ]
        def uploadSpecJson = JsonOutput.toJson(uploadSpecMap)

        try {
            // Upload artifacts to target Artifactory
            def uploadResponse = targetArtifactory.upload(uploadSpecJson)
            if (uploadResponse) {
                echo "Successfully uploaded artifacts to ${targetUrl}/${repoPath}"
            } else {
                error "Failed to upload artifacts to ${targetUrl}/${repoPath}"
            }
        } catch (Exception e) {
            error "An error occurred during upload: ${e.message}"
        }
    }
}
