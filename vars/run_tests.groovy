def call() {
    call([:])
}

def call(String command) {
    call(command: command)
}

def call(Map config) {
    String command = config.command?.toString()?.trim()
    List commands = (config.commands ?: []) as List
    boolean failIfMissing = config.failIfMissing as boolean
    String label = (config.label ?: 'Run tests').toString()

    List<String> resolvedCommands = []
    if (command) {
        resolvedCommands << command
    }
    commands.findAll { it != null && it.toString().trim() }.each {
        resolvedCommands << it.toString().trim()
    }

    if (resolvedCommands.isEmpty()) {
        if (failIfMissing) {
            error("run_tests: no test command provided (use 'command' or 'commands')")
        }
        echo "run_tests: no test command provided, skipping"
        return
    }

    echo "Running ${resolvedCommands.size()} test command(s)"
    sh(label: label, script: resolvedCommands.join('\n'))
}
