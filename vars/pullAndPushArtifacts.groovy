def pullAndPushArtifacts(
    String sourceUrl, String sourceRepo, String sourceArtifactPath, 
    String targetUrl, String targetRepo, String targetArtifactPath, 
    String sourceCredentialsId, String targetCredentialsId
) {
    def sourceArtifactory = Artifactory.server(sourceUrl)
    def targetArtifactory = Artifactory.server(targetUrl)

    // Define download specification for source Artifactory
    def downloadSpec = """{
        "files": [
            {
                "pattern": "${sourceRepo}/${sourceArtifactPath}",
                "target": "download/"
            }
        ]
    }"""
    
    // Define upload specification for target Artifactory
    def uploadSpec = """{
        "files": [
            {
                "pattern": "download/${sourceArtifactPath}",
                "target": "${targetRepo}/${targetArtifactPath}"
            }
        ]
    }"""

    try {
        // Download artifacts from source Artifactory
        def downloadResponse = sourceArtifactory.download(downloadSpec, sourceCredentialsId)
        if (downloadResponse) {
            echo "Successfully downloaded artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
        } else {
            error "Failed to download artifacts from ${sourceUrl}/${sourceRepo}/${sourceArtifactPath}"
        }

        // Upload artifacts to target Artifactory
        def uploadResponse = targetArtifactory.upload(uploadSpec, targetCredentialsId)
        if (uploadResponse) {
            echo "Successfully uploaded artifacts to ${targetUrl}/${targetRepo}/${targetArtifactPath}"
        } else {
            error "Failed to upload artifacts to ${targetUrl}/${targetRepo}/${targetArtifactPath}"
        }
    } catch (Exception e) {
        error "An error occurred: ${e.message}"
    }
}
