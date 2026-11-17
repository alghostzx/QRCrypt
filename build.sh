#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="qrcrypt-android-builder:1.0"

ensure_image() {
    if ! docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
        echo "[build.sh] Building Docker image ${IMAGE_NAME}..."
        docker build -t "${IMAGE_NAME}" "${SCRIPT_DIR}"
    fi
}

run_in_docker() {
    ensure_image
    mkdir -p "${SCRIPT_DIR}/.gradle-cache"
    docker run --rm \
        --user "$(id -u):$(id -g)" \
        -e "GRADLE_USER_HOME=/workspace/.gradle-cache" \
        -e "HOME=/tmp" \
        -v "${SCRIPT_DIR}:/workspace" \
        -w /workspace \
        "${IMAGE_NAME}" \
        "$@"
}

case "${1:-release}" in
    rebuild-image)
        docker build --no-cache -t "${IMAGE_NAME}" "${SCRIPT_DIR}"
        ;;
    shell)
        ensure_image
        mkdir -p "${SCRIPT_DIR}/.gradle-cache"
        docker run --rm -it \
            --user "$(id -u):$(id -g)" \
            -e "GRADLE_USER_HOME=/workspace/.gradle-cache" \
            -e "HOME=/tmp" \
            -v "${SCRIPT_DIR}:/workspace" \
            -w /workspace \
            "${IMAGE_NAME}" \
            bash
        ;;
    test)
        run_in_docker ./gradlew testDebugUnitTest testReleaseUnitTest --no-daemon
        ;;
    debug)
        run_in_docker ./gradlew assembleDebug --no-daemon
        ;;
    release)
        run_in_docker ./gradlew testReleaseUnitTest assembleRelease --no-daemon
        mkdir -p "${SCRIPT_DIR}/dist"
        cp -f "${SCRIPT_DIR}/app/build/outputs/apk/release/app-release.apk" "${SCRIPT_DIR}/dist/app-release.apk"
        echo "[build.sh] Signed APK copied to dist/app-release.apk"
        ;;
    verify)
        APK="${2:-${SCRIPT_DIR}/app/build/outputs/apk/release/app-release.apk}"
        REL_APK="${APK#${SCRIPT_DIR}/}"
        run_in_docker bash -euo pipefail -c '
            APK_PATH="'"${REL_APK}"'"
            echo "==> Verifying APK signature..."
            apksigner verify --verbose "$APK_PATH"
            echo "==> Checking APK permissions (must NOT contain INTERNET)..."
            PERMS="$(aapt2 dump permissions "$APK_PATH")"
            echo "$PERMS"
            if echo "$PERMS" | grep -q "android.permission.INTERNET"; then
                echo "ERROR: android.permission.INTERNET found in APK!" >&2
                exit 1
            fi
            echo "==> Checking 16KB page-size alignment..."
            zipalign -c -P 16 4 "$APK_PATH"
            echo "==> APK verification PASSED! Size: $(du -h "$APK_PATH" | cut -f1)"
        '
        ;;
    cmd)
        shift
        run_in_docker "$@"
        ;;
    *)
        run_in_docker ./gradlew "$@" --no-daemon
        ;;
esac
