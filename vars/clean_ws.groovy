def call(Map config = [:]) {
    Map options = [
        cleanWhenNotBuilt      : true,
        deleteDirs             : true,
        disableDeferredWipeout : true,
        notFailBuild           : true,
    ] + config

    echo "Cleaning workspace"
    cleanWs(options)
}
