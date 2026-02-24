def call(Map config = [:]) {
    String projectName = (config.projectName ?: env.JOB_NAME ?: 'Project').toString()
    String imageName = (config.imageName ?: '').toString()
    String imageTag = (config.imageTag ?: '').toString()
    Map metadata = (config.metadata ?: [:]) as Map
    String reportPath = (config.reportPath ?: 'reports/build-report.txt').toString()

    echo "Generating build report..."

    String reportDir = reportPath.contains('/') ? reportPath.substring(0, reportPath.lastIndexOf('/')) : '.'
    sh(label: 'Prepare report directory', script: "mkdir -p ${shellQuote(reportDir)}")

    List<String> lines = [
        "===== ${projectName} Build Report =====",
        "Generated: ${new Date()}",
        "",
        "Build Number: ${env.BUILD_NUMBER ?: 'N/A'}",
        "Job Name: ${env.JOB_NAME ?: 'N/A'}",
        "Docker Image: ${imageName ?: 'N/A'}",
        "Image Tag: ${imageTag ?: 'N/A'}",
        "Build Status: ${currentBuild?.result ?: 'SUCCESS'}",
        "Build URL: ${env.BUILD_URL ?: 'N/A'}",
        "Git Branch: ${env.BRANCH_NAME ?: 'N/A'}",
        "Git Commit: ${env.GIT_COMMIT ?: 'N/A'}",
    ]

    metadata.each { key, value ->
        lines << "${key}: ${value}"
    }

    writeFile(file: reportPath, text: lines.join('\n') + '\n')
    archiveArtifacts(artifacts: reportPath, allowEmptyArchive: true)
}

private String shellQuote(def value) {
    String s = value == null ? '' : value.toString()
    return "'${s.replace("'", "'\"'\"'")}'"
}
