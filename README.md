# Jenkins Shared Libraries

Reusable Jenkins Pipeline steps for common CI/CD tasks such as cloning repos, running tests, building/pushing Docker images, Trivy scanning, workspace cleanup, and build report generation.

## Structure

- `vars/clone.groovy`
- `vars/run_tests.groovy`
- `vars/docker_build.groovy`
- `vars/docker_push.groovy`
- `vars/trivy_scan.groovy`
- `vars/generate_reports.groovy`
- `vars/clean_ws.groovy`

## Setup (Jenkins)

1. Push this repository to your Git server.
2. In Jenkins, go to `Manage Jenkins` -> `System` -> `Global Trusted Pipeline Libraries`.
3. Add a library (example name: `shared`).
4. In your pipeline, load it with:

```groovy
@Library('shared') _
```

## Usage Example

```groovy
@Library('shared') _

pipeline {
  agent any

  stages {
    stage('Checkout') {
      steps {
        clone(url: 'https://github.com/example/app.git', branch: 'main')
      }
    }

    stage('Test') {
      steps {
        run_tests(commands: [
          'npm ci',
          'npm test'
        ])
      }
    }

    stage('Build Image') {
      steps {
        docker_build(
          imageName: 'myorg/myapp',
          imageTag: env.BUILD_NUMBER,
          buildArgs: [NODE_ENV: 'production'],
          tagLatest: true
        )
      }
    }

    stage('Security Scan') {
      steps {
        trivy_scan(mode: 'fs', target: '.', severity: 'HIGH,CRITICAL', exitCode: 1)
      }
    }

    stage('Push Image') {
      steps {
        docker_push(
          imageName: 'myorg/myapp',
          imageTag: env.BUILD_NUMBER,
          credentialsId: 'docker-hub-credentials',
          pushLatest: true
        )
      }
    }
  }

  post {
    always {
      generate_reports(
        projectName: 'myapp',
        imageName: 'myorg/myapp',
        imageTag: env.BUILD_NUMBER,
        metadata: [
          Environment: env.BRANCH_NAME ?: 'unknown'
        ]
      )
      clean_ws()
    }
  }
}
```

## Step Reference

### `clone`

Clone a Git repository.

```groovy
clone(url: 'https://github.com/org/repo.git', branch: 'main', credentialsId: 'git-creds')
```

Parameters:
- `url` (required)
- `branch` (default: `main`)
- `credentialsId` (optional)
- `changelog` (optional `true/false`)
- `poll` (optional `true/false`)

### `run_tests`

Run one or more test commands.

```groovy
run_tests(command: 'mvn test')
run_tests(commands: ['npm ci', 'npm test'])
```

Parameters:
- `command` (optional single command)
- `commands` (optional list of commands)
- `label` (optional shell step label)
- `failIfMissing` (default: `false`)

### `docker_build`

Build a Docker image with optional tags/build args.

```groovy
docker_build(
  imageName: 'myorg/service',
  imageTag: '123',
  dockerfile: 'Dockerfile',
  context: '.',
  tagLatest: true,
  buildArgs: [VERSION: '123']
)
```

Parameters:
- `imageName` (required)
- `imageTag` (default: `latest`)
- `dockerfile` (default: `Dockerfile`)
- `context` (default: `.`)
- `tagLatest` (default: `true` when `imageTag != 'latest'`)
- `pull`, `noCache` (optional booleans)
- `target`, `platform` (optional)
- `buildArgs`, `labels` (optional maps)
- `additionalTags` (optional list)

### `docker_push`

Log in and push Docker tags.

```groovy
docker_push(
  imageName: 'myorg/service',
  imageTag: '123',
  credentialsId: 'docker-hub-credentials',
  pushLatest: true
)
```

Parameters:
- `imageName` (required)
- `imageTag` (default: `latest`)
- `credentialsId` / `credentials` (default: `docker-hub-credentials`)
- `registry` (optional, e.g. `ghcr.io`)
- `pushLatest` (default: `true` when `imageTag != 'latest'`)
- `additionalTags` (optional list)
- `logout` (default: `true`)

### `trivy_scan`

Run a Trivy scan.

```groovy
trivy_scan(mode: 'fs', target: '.', severity: 'HIGH,CRITICAL', exitCode: 1)
```

Parameters:
- `mode` (`fs`, `image`, `repo`, `config`) default: `fs`
- `target` (default: `.`)
- `severity`, `format`, `output` (optional)
- `ignoreUnfixed` (optional boolean)
- `exitCode` (optional integer)
- `extraArgs` (optional list)

### `generate_reports`

Generate and archive a text build report.

```groovy
generate_reports(
  projectName: 'service',
  imageName: 'myorg/service',
  imageTag: env.BUILD_NUMBER
)
```

Parameters:
- `projectName` (default: `env.JOB_NAME`)
- `imageName`, `imageTag` (optional)
- `metadata` (optional map of extra lines)
- `reportPath` (default: `reports/build-report.txt`)

### `clean_ws`

Clean the Jenkins workspace using the Workspace Cleanup plugin.

```groovy
clean_ws()
clean_ws(deleteDirs: true, disableDeferredWipeout: true)
```

Parameters:
- Any `cleanWs(...)` options can be passed through.

## Notes

- `clean_ws` requires the Jenkins Workspace Cleanup plugin (`cleanWs` step).
- `docker_build` / `docker_push` require Docker to be available on the Jenkins agent.
- `trivy_scan` requires Trivy to be installed on the Jenkins agent.
