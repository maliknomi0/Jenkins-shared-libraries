def call(Map config = [:]) {
    String imageName = (config.imageName ?: '').trim()
    String imageTag = (config.imageTag ?: 'latest').trim()
    String credentialsId = (config.credentialsId ?: config.credentials ?: 'docker-hub-credentials').toString()
    String registry = (config.registry ?: '').trim()
    boolean pushLatest = config.containsKey('pushLatest') ? (config.pushLatest as boolean) : (imageTag != 'latest')
    boolean logout = config.containsKey('logout') ? (config.logout as boolean) : true
    List additionalTags = (config.additionalTags ?: []) as List

    if (!imageName) {
        error("docker_push: 'imageName' is required")
    }

    List<String> tagsToPush = ["${imageName}:${imageTag}"]
    if (pushLatest && imageTag != 'latest') {
        tagsToPush << "${imageName}:latest"
    }
    additionalTags.findAll { it != null && it.toString().trim() }.each { tag ->
        String resolved = "${imageName}:${tag.toString().trim()}"
        if (!tagsToPush.contains(resolved)) {
            tagsToPush << resolved
        }
    }

    echo "Pushing Docker image tags: ${tagsToPush.join(', ')}"

    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'DOCKER_USERNAME',
        passwordVariable: 'DOCKER_PASSWORD'
    )]) {
        List<String> scriptLines = []
        scriptLines << 'set -eu'

        String loginTarget = registry ? " ${shellQuote(registry)}" : ''
        scriptLines << "echo \"\$DOCKER_PASSWORD\" | docker login -u \"\$DOCKER_USERNAME\" --password-stdin${loginTarget}"

        tagsToPush.each { imageRef ->
            scriptLines << "docker push ${shellQuote(imageRef)}"
        }

        if (logout) {
            if (registry) {
                scriptLines << "docker logout ${shellQuote(registry)} || true"
            } else {
                scriptLines << 'docker logout || true'
            }
        }

        sh(label: 'Docker push', script: scriptLines.join('\n'))
    }
}

private String shellQuote(def value) {
    String s = value == null ? '' : value.toString()
    return "'${s.replace("'", "'\"'\"'")}'"
}
