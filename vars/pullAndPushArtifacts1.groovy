import groovy.json.JsonOutput

def call(
    String sourceUrl, String repoPath, 
    String credentialsId
) {
    // Initialize JFrog Artifactory servers
    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(sourceUrl)

    // Use the credentials from the credentials ID
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASSWORD')]) {
        // Define download specification for source Artifactory as JSON string
        def downloadSpecMap = [
            "files": [
                [
                    "pattern": "${repoPath}",
                    "target": "download/"
                ]
            ]
        ]
        def downloadSpecJson = JsonOutput.toJson(downloadSpecMap)

        // Define upload specification for target Artifactory as JSON string
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
            // Download artifacts from source Artifactory
            def downloadResponse = sourceArtifactory.download(downloadSpecJson, [
                username: "${ARTIFACTORY_USER}",
                password: "${ARTIFACTORY_PASSWORD}"
            ])
            if (downloadResponse) {
                echo "Successfully downloaded artifacts from ${sourceUrl}/${repoPath}"
            } else {
                error "Failed to download artifacts from ${sourceUrl}/${repoPath}"
            }

            // Upload artifacts to target Artifactory
            def uploadResponse = targetArtifactory.upload(uploadSpecJson, [
                username: "${ARTIFACTORY_USER}",
                password: "${ARTIFACTORY_PASSWORD}"
            ])
            if (uploadResponse) {
                echo "Successfully uploaded artifacts to ${sourceUrl}/${repoPath}"
            } else {
                error "Failed to upload artifacts to ${sourceUrl}/${repoPath}"
            }
        } catch (Exception e) {
            error "An error occurred: ${e.message}"
        }
    }
}
