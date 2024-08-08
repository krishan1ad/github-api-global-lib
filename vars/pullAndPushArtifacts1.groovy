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

    // List contents of the download directory to verify folder structure
    echo "Listing contents of the download directory:"
    sh 'ls -R download/'

    // Discover files and construct upload specifications
    def fileUploadSpecs = []
    def targetBasePath = "${sourceRepo}/${sourceArtifactPath}/"

    // Find all files in the download directory and process them
    def fileList = sh(script: 'find download/ -type f', returnStdout: true).trim().split('\n')

    fileList.each { filePath ->
        // Construct relative path for upload
        def relativePath = filePath.replaceFirst('^download/', '')
        def targetPath = "${targetBasePath}${relativePath}".replaceFirst('^'+sourceArtifactPath+'/', '')

        // Define upload specification for each file
        fileUploadSpecs.add([
            "pattern": filePath,
            "target": targetPath
        ])
    }

    // Convert upload specifications to JSON
    def uploadSpecMap = [
        "files": fileUploadSpecs
    ]
    def uploadSpecJson = groovy.json.JsonOutput.toJson(uploadSpecMap)

    echo "Upload specification: ${uploadSpecJson}"

    // Upload artifacts to target Artifactory
    withCredentials([usernamePassword(credentialsId: targetCredentialsId, passwordVariable: 'TARGET_PASSWORD', usernameVariable: 'TARGET_USERNAME')]) {
        echo "Uploading artifacts to ${targetUrl}"
        def uploadResponse = targetArtifactory.upload(uploadSpecJson)
        echo "Upload response: ${uploadResponse}"
        if (uploadResponse) {
            echo "Successfully uploaded artifacts to ${targetUrl}"
        } else {
            error "Failed to upload artifacts to ${targetUrl}"
        }
    }
}
