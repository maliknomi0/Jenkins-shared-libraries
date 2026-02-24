def call(Map config = [:]) {
    String mode = (config.mode ?: 'fs').toString().trim()
    String target = (config.target ?: '.').toString()
    String severity = (config.severity ?: '').toString().trim()
    String format = (config.format ?: '').toString().trim()
    String output = (config.output ?: '').toString().trim()
    boolean ignoreUnfixed = config.ignoreUnfixed as boolean
    Integer exitCode = config.containsKey('exitCode') ? (config.exitCode as Integer) : null
    List extraArgs = (config.extraArgs ?: []) as List

    if (!['fs', 'image', 'repo', 'config'].contains(mode)) {
        error("trivy_scan: unsupported mode '${mode}'")
    }

    List<String> cmd = ['trivy', mode]

    if (severity) {
        cmd.addAll(['--severity', severity])
    }
    if (format) {
        cmd.addAll(['--format', format])
    }
    if (output) {
        cmd.addAll(['--output', output])
    }
    if (ignoreUnfixed) {
        cmd << '--ignore-unfixed'
    }
    if (exitCode != null) {
        cmd.addAll(['--exit-code', exitCode.toString()])
    }

    extraArgs.findAll { it != null && it.toString().trim() }.each { arg ->
        cmd << arg.toString()
    }

    cmd << target

    echo "Running Trivy scan (${mode}) on ${target}"
    sh(label: 'Trivy scan', script: shellJoin(cmd))
}

private String shellJoin(List<String> args) {
    args.collect { shellQuote(it) }.join(' ')
}

private String shellQuote(def value) {
    String s = value == null ? '' : value.toString()
    return "'${s.replace("'", "'\"'\"'")}'"
}
