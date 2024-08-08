def call(
    String sourceRepo, String sourceArtifactPath
) {
    // Retrieve global environment variables
    def sourceUrl = env.SOURCE_URL
    def targetUrl = env.TARGET_URL
    def sourceCredentialsId = env.SOURCE_CREDENTIALS_ID
    def targetCredentialsId = env.TARGET_CREDENTIALS_ID

    // Initialize JFrog Artifactory servers
    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(targetUrl)

    // Define download specification for source Artifactory as JSON string
    def downloadSpecMap = [
        "files": [
            [
                "pattern": "${sourceRepo}/${sourceArtifactPath}/**", // Include all files and subfolders
                "target": "download/"
            ]
        ]
    ]
    def downloadSpecJson = groovy.json.JsonOutput.toJson(downloadSpecMap)

    // Define upload specification for target Artifactory as JSON string
    def uploadSpecMap = [
        "files": [
            [
                "pattern": "download/${sourceArtifactPath}/**", // Include all files and subfolders
                "target": "${sourceRepo}/${sourceArtifactPath}/"
            ]
        ]
    ]
    def uploadSpecJson = groovy.json.JsonOutput.toJson(uploadSpecMap)

    try {
        // Download artifacts from source Artifactory
        withCredentials([usernamePassword(credentialsId: sourceCredentialsId, passwordVariable: 'SOURCE_PASSWORD', usernameVariable: 'SOURCE_USERNAME')]) {
            echo "Downloading artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
            def downloadResponse = sourceArtifactory.download(downloadSpecJson)
            echo "Download response: ${downloadResponse}"
            if (downloadResponse) {
                echo "Successfully downloaded artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
            } else {
                error "Failed to download artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
            }
        }

        // Upload artifacts to target Artifactory
        withCredentials([usernamePassword(credentialsId: targetCredentialsId, passwordVariable: 'TARGET_PASSWORD', usernameVariable: 'TARGET_USERNAME')]) {
            echo "Uploading artifacts to ${targetUrl}/${sourceRepo}/${sourceArtifactPath}"
            def uploadResponse = targetArtifactory.upload(uploadSpecJson)
            echo "Upload response: ${uploadResponse}"
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
