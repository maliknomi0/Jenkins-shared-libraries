def call(Map config = [:]) {
    List mounts = (config.mounts ?: []) as List
    if (mounts.isEmpty()) {
        error("prepare_bind_mounts: 'mounts' is required (list of [host, container] maps)")
    }

    List normalizedMounts = []
    mounts.eachWithIndex { item, idx ->
        if (!(item instanceof Map)) {
            error("prepare_bind_mounts: mounts[${idx}] must be a map")
        }

        String host = item.host?.toString()?.trim()
        String container = item.container?.toString()?.trim()

        if (!host) {
            error("prepare_bind_mounts: mounts[${idx}].host is required")
        }
        if (!container) {
            error("prepare_bind_mounts: mounts[${idx}].container is required")
        }

        normalizedMounts << [host: host, container: container]
    }

    int uid = config.containsKey('uid') ? config.uid.toString().toInteger() : 65532
    int gid = config.containsKey('gid') ? config.gid.toString().toInteger() : 65532
    boolean allowSudo = config.containsKey('allowSudo') ? (config.allowSudo as boolean) : true
    boolean validateWithDocker = config.containsKey('validateWithDocker') ? (config.validateWithDocker as boolean) : true
    String dirMode = (config.dirMode ?: '775').toString().trim()
    String fileMode = (config.fileMode ?: '664').toString().trim()
    String label = (config.label ?: 'Prepare bind mounts').toString()
    String validationEntrypoint = (config.validationEntrypoint ?: '/nodejs/bin/node').toString()

    String image = (config.image ?: '').toString().trim()
    if (!image) {
        String imageName = (config.imageName ?: '').toString().trim()
        String imageTag = (config.imageTag ?: '').toString().trim()
        if (imageName && imageTag) {
            image = "${imageName}:${imageTag}"
        }
    }

    if (validateWithDocker && !image) {
        error("prepare_bind_mounts: 'image' or ('imageName' + 'imageTag') is required when validateWithDocker=true")
    }

    List hostDirs = normalizedMounts.collect { it.host.toString() }
    String hostDirsArgs = hostDirs.collect { shellQuote(it) }.join(' ')
    String owner = "${uid}:${gid}"

    List scriptLines = []
    scriptLines << 'set -eu'
    scriptLines << "mkdir -p ${hostDirsArgs}"
    scriptLines << ''
    scriptLines << "# Ensure host bind mounts are writable by runtime user ${owner}"
    scriptLines << 'if [ "$(id -u)" -eq 0 ]; then'
    scriptLines << "  chown -R ${owner} ${hostDirsArgs}"
    normalizedMounts.each { mount ->
        scriptLines << "  find ${shellQuote(mount.host)} -type d -exec chmod ${shellQuote(dirMode)} {} \\;"
        scriptLines << "  find ${shellQuote(mount.host)} -type f -exec chmod ${shellQuote(fileMode)} {} \\;"
    }

    if (allowSudo) {
        scriptLines << 'elif command -v sudo >/dev/null 2>&1 && sudo -n true >/dev/null 2>&1; then'
        scriptLines << "  sudo chown -R ${owner} ${hostDirsArgs}"
        normalizedMounts.each { mount ->
            scriptLines << "  sudo find ${shellQuote(mount.host)} -type d -exec chmod ${shellQuote(dirMode)} {} \\;"
            scriptLines << "  sudo find ${shellQuote(mount.host)} -type f -exec chmod ${shellQuote(fileMode)} {} \\;"
        }
        scriptLines << 'else'
        scriptLines << '  echo "Skipping ownership fix (not root and passwordless sudo unavailable); validating write access..."'
        scriptLines << 'fi'
    } else {
        scriptLines << 'else'
        scriptLines << '  echo "Skipping ownership fix (allowSudo=false and not root); validating write access..."'
        scriptLines << 'fi'
    }

    if (validateWithDocker) {
        String validationJs = buildValidationScript(normalizedMounts.collect { it.container.toString() })
        List dockerCmd = ['docker', 'run', '--rm', '--user', owner]
        normalizedMounts.each { mount ->
            dockerCmd.addAll(['-v', "${mount.host}:${mount.container}"])
        }
        dockerCmd.addAll([
            '--entrypoint', validationEntrypoint,
            image,
            '-e', validationJs,
        ])

        scriptLines << ''
        scriptLines << '# Validate bind mounts using the same runtime uid/gid as the app container'
        scriptLines << shellJoin(dockerCmd)
    }

    echo "Preparing ${normalizedMounts.size()} bind mount(s) for runtime user ${owner}"
    sh(label: label, script: scriptLines.join('\n'))
}

private String buildValidationScript(List containerPaths) {
    String jsArray = '[' + containerPaths.collect { toJsString(it) }.join(',') + ']'
    return "const fs=require('fs');for(const d of ${jsArray}){fs.mkdirSync(d,{recursive:true});const p=d+'/.permcheck';fs.writeFileSync(p,'ok');fs.unlinkSync(p);}"
}

private String toJsString(def value) {
    String s = value == null ? '' : value.toString()
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'
}

private String shellJoin(List args) {
    args.collect { shellQuote(it) }.join(' ')
}

private String shellQuote(def value) {
    String s = value == null ? '' : value.toString()
    return "'${s.replace("'", "'\"'\"'")}'"
}
