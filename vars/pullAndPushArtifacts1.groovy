def call(
    String sourceUrl, String sourceRepo, String sourceArtifactPath, 
    String targetUrl, String sourceCredentialsId, String targetCredentialsId
) {
    // Initialize JFrog Artifactory servers
    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(targetUrl)

    // Define download specification for source Artifactory as JSON string
    def downloadSpecMap = [
        "files": [
            [
                "pattern": "${sourceRepo}/${sourceArtifactPath}",
                "target": "download/"
            ]
        ]
    ]
    def downloadSpecJson = groovy.json.JsonOutput.toJson(downloadSpecMap)

    // Define upload specification for target Artifactory as JSON string
    def uploadSpecMap = [
        "files": [
            [
                "pattern": "download/${sourceArtifactPath}",
                "target": "${sourceRepo}/${sourceArtifactPath}"
            ]
        ]
    ]
    def uploadSpecJson = groovy.json.JsonOutput.toJson(uploadSpecMap)

    try {
        // Download artifacts from source Artifactory
        withCredentials([usernamePassword(credentialsId: sourceCredentialsId, passwordVariable: 'SOURCE_PASSWORD', usernameVariable: 'SOURCE_USERNAME')]) {
            def downloadResponse = sourceArtifactory.download(downloadSpecJson)
            if (downloadResponse) {
                echo "Successfully downloaded artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
            } else {
                error "Failed to download artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
            }
        }

        // Upload artifacts to target Artifactory
        withCredentials([usernamePassword(credentialsId: targetCredentialsId, passwordVariable: 'TARGET_PASSWORD', usernameVariable: 'TARGET_USERNAME')]) {
            def uploadResponse = targetArtifactory.upload(uploadSpecJson)
            if (uploadResponse) {
                echo "Successfully uploaded artifacts to ${targetUrl}/${sourceRepo}/${sourceArtifactPath}"
            } else {
                error "Failed to upload artifacts to ${targetUrl}/${sourceRepo}/${sourceArtifactPath}"
            }
        }
    } catch (Exception e) {
        error "An error occurred: ${e.message}"
    }
}
