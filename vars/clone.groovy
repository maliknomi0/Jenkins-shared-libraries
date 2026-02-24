def call(String url, String branch = 'main') {
    return call(url: url, branch: branch)
}

def call(Map config = [:]) {
    String url = (config.url ?: '').trim()
    String branch = (config.branch ?: 'main').trim()

    if (!url) {
        error("clone: 'url' is required")
    }

    Map gitArgs = [
        url   : url,
        branch: branch,
    ]

    if (config.credentialsId) {
        gitArgs.credentialsId = config.credentialsId
    }

    if (config.changelog != null) {
        gitArgs.changelog = config.changelog as boolean
    }

    if (config.poll != null) {
        gitArgs.poll = config.poll as boolean
    }

    echo "Cloning ${url} (branch: ${branch})"
    git(gitArgs)
}
