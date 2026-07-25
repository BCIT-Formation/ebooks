#!/bin/bash
# Build ReadIt APK using Docker
#
# Usage:
#   ./scripts/build-apk-docker.sh [version_name] [version_code]
#
# Examples:
#   ./scripts/build-apk-docker.sh                    # v1.0.0, code=1
#   ./scripts/build-apk-docker.sh 1.2.3              # v1.2.3, code=1
#   ./scripts/build-apk-docker.sh 1.2.3 42           # v1.2.3, code=42
#

set -euo pipefail

VERSION_NAME="${1:-1.0.0}"
VERSION_CODE="${2:-1}"
OUTPUT_DIR="${OUTPUT_DIR:-.}/release"

# Ensure version name starts with 'v'
if [[ ! "$VERSION_NAME" =~ ^v ]]; then
    VERSION_NAME="v${VERSION_NAME}"
fi

echo "🐳 Building ReadIt APK with Docker"
echo "   Version: $VERSION_NAME"
echo "   Code: $VERSION_CODE"
echo "   Output: $OUTPUT_DIR"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build Docker image
echo "📦 Building Docker image..."
docker build \
    --build-arg VERSION_CODE="$VERSION_CODE" \
    --build-arg VERSION_NAME="$VERSION_NAME" \
    -t readit:latest \
    -f Dockerfile \
    .

# Extract APK from image
echo "🔧 Extracting APK..."
TEMP_CONTAINER=$(docker run -d readit:latest sleep 999)
trap "docker rm -f $TEMP_CONTAINER > /dev/null 2>&1" EXIT

docker cp "$TEMP_CONTAINER":/out/. "$OUTPUT_DIR/" || true

# Clean up container
docker rm -f "$TEMP_CONTAINER" > /dev/null 2>&1

# Verify APK exists
if find "$OUTPUT_DIR" -name "*.apk" -quit | grep -q .; then
    echo ""
    echo "✅ APK built successfully!"
    echo ""
    echo "📁 Output:"
    ls -lh "$OUTPUT_DIR"/*.apk
    echo ""
    echo "💡 Next steps:"
    echo "   1. Transfer APK to Android device"
    echo "   2. Enable 'Install from unknown sources' in Settings"
    echo "   3. Open APK and follow install prompt"
else
    echo "❌ APK not found in output directory"
    echo "Check Docker build logs above for errors"
    exit 1
fi
