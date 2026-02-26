def call(Map config = [:]) {
    String imageName = (config.imageName ?: '').trim()
    String deployPath = (config.deployPath ?: '').trim()
    String composeFile = (config.composeFile ?: 'docker-compose.yml').toString()
    String serviceName = (config.serviceName ?: 'backend').toString()
    int keepVersionedCount = (config.keepVersionedCount ?: 3) as int
    String imageUntil = (config.imageUntil ?: '168h').toString()
    String containerUntil = (config.containerUntil ?: '24h').toString()
    String builderUntil = (config.builderUntil ?: '168h').toString()
    List protectedTags = (config.protectedTags ?: ['latest', 'main-latest', 'previous']) as List

    if (!imageName) {
        error("docker_cleanup_vps: 'imageName' is required")
    }
    if (!deployPath) {
        error("docker_cleanup_vps: 'deployPath' is required")
    }
    if (keepVersionedCount < 0) {
        error("docker_cleanup_vps: 'keepVersionedCount' must be >= 0")
    }

    String protectedTagList = protectedTags
        .findAll { it != null && it.toString().trim() }
        .collect { it.toString().trim() }
        .join(' ')

    withEnv([
        "REPO=${imageName}",
        "DEPLOY_PATH=${deployPath}",
        "COMPOSE_FILE=${composeFile}",
        "SERVICE_NAME=${serviceName}",
        "KEEP_COUNT=${keepVersionedCount}",
        "IMAGE_UNTIL=${imageUntil}",
        "CONTAINER_UNTIL=${containerUntil}",
        "BUILDER_UNTIL=${builderUntil}",
        "PROTECTED_TAGS=${protectedTagList}"
    ]) {
        sh(label: 'Cleanup VPS Docker cache', script: '''#!/usr/bin/env bash
set -euo pipefail

cd "$DEPLOY_PATH"

echo "Disk usage before cleanup"
df -h /var || true
docker system df || true

current_service_ref=""
current_service_container_id="$(docker compose -f "$COMPOSE_FILE" ps -q "$SERVICE_NAME" 2>/dev/null || true)"
if [ -n "$current_service_container_id" ]; then
  current_service_ref="$(docker inspect --format '{{.Config.Image}}' "$current_service_container_id" 2>/dev/null || true)"
fi

echo "Current $SERVICE_NAME image in use: ${current_service_ref:-<unknown>}"
echo "Keeping latest $KEEP_COUNT version tags for $REPO plus protected tags: $PROTECTED_TAGS"

is_protected_tag() {
  local candidate="$1"
  for t in $PROTECTED_TAGS; do
    if [ "$candidate" = "$t" ]; then
      return 0
    fi
  done
  return 1
}

keep_versioned=0
while IFS= read -r image_ref; do
  [ -n "$image_ref" ] || continue
  tag="${image_ref##*:}"
  [ "$tag" != "<none>" ] || continue

  keep_reason=""
  if [ -n "$current_service_ref" ] && [ "$image_ref" = "$current_service_ref" ]; then
    keep_reason="running"
  elif is_protected_tag "$tag"; then
    keep_reason="protected-tag"
  elif [ "$keep_versioned" -lt "$KEEP_COUNT" ]; then
    keep_versioned=$((keep_versioned + 1))
    keep_reason="recent-version-$keep_versioned"
  fi

  if [ -n "$keep_reason" ]; then
    echo "KEEP   $image_ref ($keep_reason)"
    continue
  fi

  echo "REMOVE  $image_ref"
  docker image rm "$image_ref" >/dev/null 2>&1 || true
done < <(docker image ls "$REPO" --format '{{.Repository}}:{{.Tag}}' 2>/dev/null || true)

echo "Pruning stopped containers older than $CONTAINER_UNTIL"
docker container prune -f --filter "until=$CONTAINER_UNTIL" || true

echo "Pruning dangling images older than $IMAGE_UNTIL"
docker image prune -f --filter "until=$IMAGE_UNTIL" || true

echo "Pruning build cache older than $BUILDER_UNTIL"
docker builder prune -af --filter "until=$BUILDER_UNTIL" || true

echo "Disk usage after cleanup"
df -h /var || true
docker system df || true
''')
    }
}
