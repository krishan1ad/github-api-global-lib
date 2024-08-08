def call(
    String sourceRepo, String sourceArtifactPath, 
    String targetRepo
) {
    def sourceUrl = env.SOURCE_URL
    def targetUrl = env.TARGET_URL
    def sourceCredentialsId = env.SOURCE_CREDENTIALS_ID
    def targetCredentialsId = env.TARGET_CREDENTIALS_ID

    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(targetUrl)

    def downloadSpecMap = [
        "files": [
            [
                "pattern": "${sourceRepo}/${sourceArtifactPath}",
                "target": "download/"
            ]
        ]
    ]
    def downloadSpecJson = groovy.json.JsonOutput.toJson(downloadSpecMap)

    def uploadSpecMap = [
        "files": [
            [
                "pattern": "download/${sourceArtifactPath}",
                "target": "${targetRepo}/${sourceArtifactPath}"
            ]
        ]
    ]
    def uploadSpecJson = groovy.json.JsonOutput.toJson(uploadSpecMap)

    int retryCount = 3
    int retryDelay = 30 // seconds

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

        // Upload artifacts to target Artifactory with retry
        boolean uploadSuccessful = false
        for (int i = 0; i < retryCount; i++) {
            try {
                withCredentials([usernamePassword(credentialsId: targetCredentialsId, passwordVariable: 'TARGET_PASSWORD', usernameVariable: 'TARGET_USERNAME')]) {
                    def uploadResponse = targetArtifactory.upload(uploadSpecJson)
                    if (uploadResponse) {
                        echo "Successfully uploaded artifacts to ${targetUrl}/${targetRepo}/${sourceArtifactPath}"
                        uploadSuccessful = true
                        break
                    } else {
                        echo "Failed to upload artifacts. Retrying in ${retryDelay} seconds..."
                    }
                }
            } catch (Exception e) {
                echo "An error occurred during upload: ${e.message}. Retrying in ${retryDelay} seconds..."
            }
            sleep retryDelay
        }

        if (!uploadSuccessful) {
            error "Failed to upload artifacts to ${targetUrl}/${targetRepo}/${sourceArtifactPath} after ${retryCount} attempts"
        }
    } catch (Exception e) {
        error "An error occurred: ${e.message}"
    }
}
