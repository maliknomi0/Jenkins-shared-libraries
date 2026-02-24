def call(Map config = [:]) {
    String imageName = (config.imageName ?: '').trim()
    String imageTag = (config.imageTag ?: 'latest').trim()
    String dockerfile = (config.dockerfile ?: 'Dockerfile').toString()
    String context = (config.context ?: '.').toString()
    boolean tagLatest = config.containsKey('tagLatest') ? (config.tagLatest as boolean) : (imageTag != 'latest')
    boolean pull = config.pull as boolean
    boolean noCache = config.noCache as boolean
    String target = config.target?.toString()
    String platform = config.platform?.toString()
    Map buildArgs = (config.buildArgs ?: [:]) as Map
    Map labels = (config.labels ?: [:]) as Map
    List additionalTags = (config.additionalTags ?: []) as List

    if (!imageName) {
        error("docker_build: 'imageName' is required")
    }

    List<String> cmd = ['docker', 'build']

    if (pull) {
        cmd << '--pull'
    }
    if (noCache) {
        cmd << '--no-cache'
    }
    if (platform) {
        cmd.addAll(['--platform', platform])
    }
    if (target) {
        cmd.addAll(['--target', target])
    }

    buildArgs.each { key, value ->
        if (key == null || key.toString().trim().isEmpty()) {
            error("docker_build: buildArgs contains an empty key")
        }
        String argValue = value == null ? '' : value.toString()
        cmd.addAll(['--build-arg', "${key}=${argValue}"])
    }

    labels.each { key, value ->
        if (key == null || key.toString().trim().isEmpty()) {
            error("docker_build: labels contains an empty key")
        }
        String labelValue = value == null ? '' : value.toString()
        cmd.addAll(['--label', "${key}=${labelValue}"])
    }

    cmd.addAll(['-t', "${imageName}:${imageTag}"])

    if (tagLatest && imageTag != 'latest') {
        cmd.addAll(['-t', "${imageName}:latest"])
    }

    additionalTags.findAll { it != null && it.toString().trim() }.each { tag ->
        cmd.addAll(['-t', "${imageName}:${tag.toString().trim()}"])
    }

    cmd.addAll(['-f', dockerfile, context])

    echo "Building Docker image ${imageName}:${imageTag}"
    sh(label: 'Docker build', script: shellJoin(cmd))
}

private String shellJoin(List<String> args) {
    args.collect { shellQuote(it) }.join(' ')
}

private String shellQuote(def value) {
    String s = value == null ? '' : value.toString()
    return "'${s.replace("'", "'\"'\"'")}'"
}
