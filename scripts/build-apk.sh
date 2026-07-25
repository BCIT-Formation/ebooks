#!/bin/bash
# Multi-method APK builder for ReadIt
#
# This script attempts to build the APK using multiple methods:
# 1. Gradle local build
# 2. Docker build (if Docker is available)
# 3. Gradle with clean and fresh build
#
# Usage:
#   ./scripts/build-apk.sh [version_name] [version_code] [method]
#
# Examples:
#   ./scripts/build-apk.sh                    # Default: v1.0.0, code=1, auto-detect method
#   ./scripts/build-apk.sh 1.2.3              # v1.2.3, code=1
#   ./scripts/build-apk.sh 1.2.3 42           # v1.2.3, code=42
#   ./scripts/build-apk.sh 1.2.3 42 gradle    # Force Gradle method
#   ./scripts/build-apk.sh 1.2.3 42 docker    # Force Docker method

set -euo pipefail

VERSION_NAME="${1:-1.0.0}"
VERSION_CODE="${2:-1}"
BUILD_METHOD="${3:-auto}"
APK_STORAGE="./apk"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Ensure version name starts with 'v'
if [[ ! "$VERSION_NAME" =~ ^v ]]; then
    VERSION_NAME="v${VERSION_NAME}"
fi

log_info() { echo -e "${BLUE}ℹ️  $*${NC}"; }
log_success() { echo -e "${GREEN}✅ $*${NC}"; }
log_warning() { echo -e "${YELLOW}⚠️  $*${NC}"; }
log_error() { echo -e "${RED}❌ $*${NC}"; }

# Create APK storage directory
mkdir -p "$APK_STORAGE"

print_header() {
    echo -e "${BLUE}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ReadIt APK Builder"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${NC}"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v gradle &> /dev/null && [ ! -f "./gradlew" ]; then
        log_error "Gradle not found. Please install Gradle or ensure gradlew exists."
        return 1
    fi

    if [ ! -f "app/build.gradle.kts" ]; then
        log_error "app/build.gradle.kts not found. Are you in the project root?"
        return 1
    fi

    log_success "Prerequisites OK"
    return 0
}

# Method 1: Build with local Gradle
build_gradle() {
    log_info "Building APK using local Gradle..."

    if ! command -v java &> /dev/null; then
        log_error "Java not found. Gradle build requires Java 17+."
        return 1
    fi

    local gradle_cmd="./gradlew"
    if ! [ -x "$gradle_cmd" ]; then
        log_warning "gradlew not executable, fixing permissions..."
        chmod +x "$gradle_cmd"
    fi

    log_info "Running: $gradle_cmd assembleRelease"
    if $gradle_cmd assembleRelease \
        --no-daemon \
        --build-cache \
        -PVERSION_CODE="$VERSION_CODE" \
        -PVERSION_NAME="$VERSION_NAME" 2>&1; then

        if find app/build/outputs/apk/release -name "*.apk" -quit | grep -q .; then
            log_success "Gradle build completed successfully"
            copy_apk_from_gradle
            return 0
        else
            log_error "Gradle build succeeded but APK not found in output"
            return 1
        fi
    else
        log_error "Gradle build failed"
        return 1
    fi
}

# Method 2: Docker build
build_docker() {
    log_info "Building APK using Docker..."

    if ! command -v docker &> /dev/null; then
        log_warning "Docker not available"
        return 1
    fi

    if ! docker ps &> /dev/null; then
        log_warning "Docker daemon not running"
        return 1
    fi

    if [ ! -f "Dockerfile" ]; then
        log_warning "Dockerfile not found"
        return 1
    fi

    log_info "Building Docker image..."
    if ! docker build \
        --build-arg VERSION_CODE="$VERSION_CODE" \
        --build-arg VERSION_NAME="$VERSION_NAME" \
        -t readit:latest \
        -f Dockerfile \
        . 2>&1; then
        log_error "Docker build failed"
        return 1
    fi

    log_info "Extracting APK from Docker image..."
    local temp_container
    temp_container=$(docker run -d readit:latest sleep 999)
    trap "docker rm -f $temp_container > /dev/null 2>&1" RETURN

    if docker cp "$temp_container":/out/. "$APK_STORAGE/" 2>&1; then
        docker rm -f "$temp_container" > /dev/null 2>&1
        log_success "Docker build completed successfully"
        return 0
    else
        log_error "Failed to extract APK from Docker container"
        docker rm -f "$temp_container" > /dev/null 2>&1
        return 1
    fi
}

# Method 3: Clean Gradle build
build_gradle_clean() {
    log_info "Building APK with clean Gradle build (fresh start)..."

    local gradle_cmd="./gradlew"
    if ! [ -x "$gradle_cmd" ]; then
        chmod +x "$gradle_cmd"
    fi

    log_info "Cleaning previous builds..."
    if ! $gradle_cmd clean --no-daemon 2>&1; then
        log_warning "Clean step had issues, continuing anyway..."
    fi

    log_info "Running fresh build..."
    if $gradle_cmd assembleRelease \
        --no-daemon \
        -PVERSION_CODE="$VERSION_CODE" \
        -PVERSION_NAME="$VERSION_NAME" 2>&1; then

        if find app/build/outputs/apk/release -name "*.apk" -quit | grep -q .; then
            log_success "Clean Gradle build completed successfully"
            copy_apk_from_gradle
            return 0
        else
            log_error "APK not found after clean build"
            return 1
        fi
    else
        log_error "Clean Gradle build failed"
        return 1
    fi
}

# Copy APK from Gradle output to storage
copy_apk_from_gradle() {
    local apk_filename="ReadIt-${VERSION_NAME#v}.apk"
    local source_apk
    source_apk=$(find app/build/outputs/apk/release -name "*.apk" -type f | head -1)

    if [ -n "$source_apk" ]; then
        cp "$source_apk" "$APK_STORAGE/$apk_filename"
        log_success "APK copied to $APK_STORAGE/$apk_filename"
    fi
}

# Try all methods in order
try_all_methods() {
    log_info "Auto-detecting best build method..."

    log_info "Attempt 1: Local Gradle build"
    if build_gradle; then
        return 0
    fi

    log_info "Attempt 2: Docker build"
    if build_docker; then
        return 0
    fi

    log_info "Attempt 3: Clean Gradle build"
    if build_gradle_clean; then
        return 0
    fi

    return 1
}

# Verify APK and show results
verify_and_report() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    local apk_files
    apk_files=$(find "$APK_STORAGE" -maxdepth 1 -name "*.apk" -type f)

    if [ -n "$apk_files" ]; then
        log_success "APK(s) generated successfully!"
        echo ""
        echo "📁 Generated APK(s):"
        echo "$apk_files" | while read -r apk; do
            local size
            size=$(ls -lh "$apk" | awk '{print $5}')
            echo "   • $(basename "$apk") ($size)"
        done
        echo ""
        echo "📍 Location: $APK_STORAGE/"
        echo ""
        echo "💡 Next steps:"
        echo "   1. Transfer APK to Android device"
        echo "   2. Enable 'Install from unknown sources' in Settings"
        echo "   3. Open APK and follow install prompt"
        echo ""
        return 0
    else
        log_error "No APK found in $APK_STORAGE/"
        echo ""
        echo "Troubleshooting tips:"
        echo "   • Check Java installation: java -version"
        echo "   • Check Android SDK: \$ANDROID_HOME"
        echo "   • Check Docker: docker --version && docker ps"
        echo "   • Review build logs above for specific errors"
        echo ""
        return 1
    fi
}

# Main execution
main() {
    print_header

    echo "📋 Configuration:"
    echo "   Version: $VERSION_NAME"
    echo "   Code: $VERSION_CODE"
    echo "   Method: $BUILD_METHOD"
    echo "   Storage: $APK_STORAGE"
    echo ""

    if ! check_prerequisites; then
        log_error "Prerequisites check failed"
        return 1
    fi

    case "$BUILD_METHOD" in
        gradle)
            if build_gradle; then
                verify_and_report
                return $?
            else
                log_error "Gradle build failed"
                return 1
            fi
            ;;
        docker)
            if build_docker; then
                verify_and_report
                return $?
            else
                log_error "Docker build failed"
                return 1
            fi
            ;;
        clean)
            if build_gradle_clean; then
                verify_and_report
                return $?
            else
                log_error "Clean build failed"
                return 1
            fi
            ;;
        auto|*)
            if try_all_methods; then
                verify_and_report
                return $?
            else
                verify_and_report
                return 1
            fi
            ;;
    esac
}

main "$@"
