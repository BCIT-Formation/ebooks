#!/bin/bash
# setup.sh — Build & install EbookReader APK (one-shot, no questions)
#
# Builds the APK either with Docker (reproducible, no local SDK needed) or with
# a local Gradle build (no Docker needed). By default it auto-detects what is
# available: Docker is used when its daemon is running, otherwise it falls back
# to a local Gradle build.
#
# Usage:
#   ./setup.sh                       # Build v1.0.0 (auto: docker or local)
#   ./setup.sh 1.2.3                 # Build v1.2.3
#   ./setup.sh 1.2.3 42              # Build v1.2.3, code=42
#   BUILD=local ./setup.sh 1.2.3     # Force a local Gradle build (no Docker)
#   BUILD=docker ./setup.sh 1.2.3    # Force a Docker build
#   DEBUG=1 ./setup.sh 1.2.3         # Verbose (show all build output)
#
# Environment:
#   BUILD   auto (default) | docker | local
#   DEBUG   0 (default) | 1     show full build output + diagnostics
#
# Speed & size:
#   • Docker builds 'assembleRelease' — minified + resource-shrunk (R8 full mode
#     is on by default in AGP 8.4), i.e. the lean, light APK.
#   • Local builds 'assembleDebug' via the Gradle daemon + build/configuration
#     cache (see gradle.properties) for the fastest incremental rebuilds; the
#     debug APK installs alongside release thanks to the '.debug' suffix.
#

set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

VERSION_NAME="${1:-1.0.0}"
VERSION_CODE="${2:-1}"
OUTPUT_DIR="${HOME}/.ebooks-apk"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEBUG="${DEBUG:-0}"
BUILD="${BUILD:-auto}"

# Ensure version name starts with 'v'
if [[ ! "$VERSION_NAME" =~ ^v ]]; then
    VERSION_NAME="v${VERSION_NAME}"
fi

# Helper: log with timestamp
log_info() {
    echo "[$(date '+%H:%M:%S')] ℹ️  $*"
}

log_error() {
    echo "[$(date '+%H:%M:%S')] ❌ $*" >&2
}

log_success() {
    echo "[$(date '+%H:%M:%S')] ✅ $*"
}

# ─────────────────────────────────────────────────────────────────────────────
# Setup
# ─────────────────────────────────────────────────────────────────────────────

log_info "Creating output directory: $OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cd "$SCRIPT_DIR"

if [ "$DEBUG" = "1" ]; then
    log_info "DEBUG mode enabled"
    log_info "Version: $VERSION_NAME"
    log_info "Code: $VERSION_CODE"
    log_info "Build method (requested): $BUILD"
    log_info "Script directory: $SCRIPT_DIR"
    log_info "Output directory: $OUTPUT_DIR"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Build method selection
# ─────────────────────────────────────────────────────────────────────────────

docker_available() {
    command -v docker &> /dev/null && docker ps &> /dev/null
}

# Resolve "auto" to a concrete method.
if [ "$BUILD" = "auto" ]; then
    if docker_available; then
        BUILD="docker"
    else
        BUILD="local"
        log_info "Docker daemon not available — falling back to a local Gradle build."
    fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# Android SDK detection (local builds only)
# ─────────────────────────────────────────────────────────────────────────────

detect_android_sdk() {
    # 1) Respect an already-exported SDK path.
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
        echo "$ANDROID_HOME"; return 0
    fi
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
        echo "$ANDROID_SDK_ROOT"; return 0
    fi
    # 2) Respect sdk.dir from local.properties.
    if [ -f "$SCRIPT_DIR/local.properties" ]; then
        local sdk_dir
        sdk_dir="$(grep -E '^sdk\.dir=' "$SCRIPT_DIR/local.properties" | head -1 | cut -d= -f2- || true)"
        if [ -n "$sdk_dir" ] && [ -d "$sdk_dir" ]; then
            echo "$sdk_dir"; return 0
        fi
    fi
    # 3) Probe common install locations.
    local candidate
    for candidate in \
        "$HOME/Android/Sdk" \
        "$HOME/Library/Android/sdk" \
        "$HOME/AppData/Local/Android/Sdk" \
        "/usr/lib/android-sdk" \
        "/opt/android-sdk"; do
        if [ -d "$candidate" ]; then
            echo "$candidate"; return 0
        fi
    done
    return 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Build — Docker
# ─────────────────────────────────────────────────────────────────────────────

build_docker() {
    echo "🐳 Building EbookReader APK ($VERSION_NAME) with Docker…"
    log_info "Docker build starting (this may take a few minutes)…"

    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        echo ""
        echo "📝 Install Docker from https://www.docker.com/ or build locally instead:"
        echo "   BUILD=local ./setup.sh ${VERSION_NAME#v}"
        echo ""
        exit 1
    fi

    if ! docker ps &> /dev/null; then
        log_error "Docker daemon is not running"
        echo ""
        echo "💡 Start Docker (Docker Desktop, or 'sudo systemctl start docker'),"
        echo "   or build locally without Docker:"
        echo "   BUILD=local ./setup.sh ${VERSION_NAME#v}"
        echo ""
        exit 1
    fi

    if [ "$DEBUG" = "1" ]; then
        docker build \
            --build-arg VERSION_CODE="$VERSION_CODE" \
            --build-arg VERSION_NAME="$VERSION_NAME" \
            -t ebook-reader:latest \
            -f Dockerfile \
            .
    else
        docker build \
            --build-arg VERSION_CODE="$VERSION_CODE" \
            --build-arg VERSION_NAME="$VERSION_NAME" \
            -t ebook-reader:latest \
            -f Dockerfile \
            . > /dev/null 2>&1
    fi

    log_success "Docker image built"

    log_info "Extracting APK from Docker container…"
    local temp_container
    temp_container=$(docker run -d ebook-reader:latest sleep 999)
    trap "docker rm -f $temp_container > /dev/null 2>&1" EXIT

    if [ "$DEBUG" = "1" ]; then
        log_info "Container ID: $temp_container"
    fi

    docker cp "$temp_container":/out/. "$OUTPUT_DIR/" 2>/dev/null || true
    log_success "APK extracted to $OUTPUT_DIR"

    log_info "Cleaning up container…"
    docker rm -f "$temp_container" > /dev/null 2>&1
    trap - EXIT
}

# ─────────────────────────────────────────────────────────────────────────────
# Build — Local Gradle (no Docker)
# ─────────────────────────────────────────────────────────────────────────────

build_local() {
    echo "🔨 Building EbookReader APK ($VERSION_NAME) with local Gradle…"

    if ! command -v java &> /dev/null; then
        log_error "Java (JDK 17+) is not installed — required for a local build."
        echo ""
        echo "📝 Install a JDK 17+ (e.g. 'sudo apt install openjdk-17-jdk' or Temurin),"
        echo "   or build with Docker instead:  BUILD=docker ./setup.sh ${VERSION_NAME#v}"
        echo ""
        exit 1
    fi

    local sdk_path
    if ! sdk_path="$(detect_android_sdk)"; then
        log_error "Android SDK not found — required for a local build."
        echo ""
        echo "📝 Point the script at an Android SDK using one of:"
        echo "   • export ANDROID_HOME=/path/to/Android/Sdk"
        echo "   • echo 'sdk.dir=/path/to/Android/Sdk' > local.properties"
        echo "   • Install Android Studio (bundles the SDK)"
        echo ""
        echo "🐳 Or build with Docker (no local SDK needed):"
        echo "   BUILD=docker ./setup.sh ${VERSION_NAME#v}"
        echo ""
        exit 1
    fi

    export ANDROID_HOME="$sdk_path"
    export ANDROID_SDK_ROOT="$sdk_path"
    log_info "Using Android SDK: $sdk_path"

    # Persist sdk.dir so subsequent Gradle invocations / Android Studio agree.
    if [ ! -f "$SCRIPT_DIR/local.properties" ]; then
        echo "sdk.dir=$sdk_path" > "$SCRIPT_DIR/local.properties"
        log_info "Wrote local.properties (sdk.dir=$sdk_path)"
    fi

    chmod +x "$SCRIPT_DIR/gradlew"

    local gradle_quiet=()
    if [ "$DEBUG" != "1" ]; then
        gradle_quiet=(--quiet)
    fi

    log_info "Running ./gradlew assembleDebug (this may take a few minutes)…"
    "$SCRIPT_DIR/gradlew" "${gradle_quiet[@]}" assembleDebug \
        -PVERSION_CODE="$VERSION_CODE" \
        -PVERSION_NAME="$VERSION_NAME"

    local built_apk="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "$built_apk" ]; then
        log_error "Gradle build finished but no APK at: $built_apk"
        exit 1
    fi

    local dest="$OUTPUT_DIR/EbookReader-${VERSION_NAME}-debug.apk"
    cp -f "$built_apk" "$dest"
    log_success "APK built and copied to $dest"
}

# ─────────────────────────────────────────────────────────────────────────────
# Build dispatch
# ─────────────────────────────────────────────────────────────────────────────

case "$BUILD" in
    docker) build_docker ;;
    local)  build_local ;;
    *)
        log_error "Unknown BUILD method: '$BUILD' (expected auto, docker, or local)"
        exit 1
        ;;
esac

# ─────────────────────────────────────────────────────────────────────────────
# Verify & Report
# ─────────────────────────────────────────────────────────────────────────────

log_info "Verifying APK…"
APK_FILE=$(find "$OUTPUT_DIR" -name "*.apk" -type f -print -quit 2>/dev/null || echo "")

if [ -z "$APK_FILE" ]; then
    log_error "APK not found in $OUTPUT_DIR"
    log_error "Build may have failed. Run with DEBUG=1 for more info:"
    log_error "  DEBUG=1 ./setup.sh ${VERSION_NAME#v}"
    echo ""
    echo "Files in $OUTPUT_DIR:"
    ls -lah "$OUTPUT_DIR" 2>/dev/null || echo "  (directory empty)"
    exit 1
fi

log_success "APK found: $(basename "$APK_FILE")"

# ─────────────────────────────────────────────────────────────────────────────
# Install
# ─────────────────────────────────────────────────────────────────────────────

if ! command -v adb &> /dev/null; then
    log_info "adb not found — skipping device install (manual install available below)."
else
    log_info "Checking for connected Android devices…"
    DEVICES=$(adb devices 2>/dev/null | grep -v "List" | grep "device$" | wc -l || echo "0")

    if [ "$DEVICES" -gt 0 ]; then
        echo "📱 Android device detected ($DEVICES device(s)) — installing…"
        if [ "$DEBUG" = "1" ]; then
            adb devices
        fi

        if adb install -r "$APK_FILE"; then
            log_success "APK installed on device"
        else
            echo "⚠️  Install failed (check device is unlocked and developer mode enabled)"
        fi
    else
        log_info "No Android device detected (that's OK, manual install available)"
    fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────

APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
APK_NAME=$(basename "$APK_FILE")

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ BUILD COMPLETE ($BUILD)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📦 APK Name:     $APK_NAME"
echo "📊 Size:         $APK_SIZE"
echo "📂 Full Path:    $APK_FILE"
echo "💾 Folder:       $OUTPUT_DIR"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📲 INSTALL OPTIONS:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "1️⃣  Command line (if device connected):"
echo "    adb install -r \"$APK_FILE\""
echo ""
echo "2️⃣  File manager (copy to phone):"
echo "    $APK_FILE"
echo ""
echo "3️⃣  Open directly on file manager:"
echo "    file://$APK_FILE"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$DEBUG" = "1" ]; then
    echo ""
    log_info "Directory contents:"
    ls -lah "$OUTPUT_DIR"
fi
