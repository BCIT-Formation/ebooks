# APK Build Guide

Guide complet pour générer l'APK EbookReader avec plusieurs méthodes.

## Structure

```
apk/                    # Directory where generated APKs are stored
scripts/
  build-apk.sh         # Main multi-method build script
  apk-manager.sh       # Manage APK storage
  build-apk-docker.sh  # Legacy Docker build (deprecated)
```

## Quick Start

### Build with Auto-Detection (Recommended)

```bash
# Build with default version (v1.0.0, code=1)
./scripts/build-apk.sh

# Build with custom version
./scripts/build-apk.sh 1.2.3

# Build with custom version and code
./scripts/build-apk.sh 1.2.3 42
```

The script will automatically try these methods in order:
1. **Gradle (local)** - Fastest if you have SDK installed
2. **Docker** - Self-contained, reproducible, no SDK needed
3. **Clean Gradle** - Fresh build if caching causes issues

### Build with Specific Method

```bash
# Force Gradle
./scripts/build-apk.sh 1.2.3 42 gradle

# Force Docker
./scripts/build-apk.sh 1.2.3 42 docker

# Force clean Gradle (always cleans first)
./scripts/build-apk.sh 1.2.3 42 clean
```

## Managing APKs

Use the APK manager to view and clean stored APKs:

```bash
# List all stored APKs
./scripts/apk-manager.sh list

# Show latest APK
./scripts/apk-manager.sh latest

# Show storage usage
./scripts/apk-manager.sh size

# Clean APKs older than 7 days
./scripts/apk-manager.sh clean
```

## Build Methods

### 1. Local Gradle (Fastest)

**Requirements:**
- Java 17+ installed
- Android SDK installed (`$ANDROID_HOME` set)
- ~2GB free disk space

**Pros:**
- Fastest incremental builds
- Full control over build process
- Better IDE integration

**Cons:**
- Requires SDK setup
- Sensitive to environment issues

**Usage:**
```bash
./scripts/build-apk.sh 1.2.3 42 gradle
```

### 2. Docker (Recommended for CI/Reproducibility)

**Requirements:**
- Docker installed and running
- ~3GB free disk space
- First build takes longer (SDK download)

**Pros:**
- No SDK setup needed
- Reproducible builds across machines
- Isolated environment
- Works in CI/CD pipelines

**Cons:**
- Slower for first build
- Requires Docker daemon running

**Usage:**
```bash
./scripts/build-apk.sh 1.2.3 42 docker
```

### 3. Clean Gradle (Troubleshooting)

**Requirements:**
- Same as Local Gradle
- ~2GB free disk space

**Use when:**
- Gradle cache is corrupted
- Getting build errors with regular build
- Need fresh start

**Usage:**
```bash
./scripts/build-apk.sh 1.2.3 42 clean
```

## GitHub Actions

### Manual Trigger

Go to **Actions** > **Manual Release APK** (`release.yml`) > **Run workflow**

Options:
- `version_name`: e.g., "1.2.3" (will become v1.2.3)

The debug APK is also built and uploaded as an artifact by the regular CI
workflow (`ci.yml`) on every push and pull request.

## Troubleshooting

### "APK not found"

1. **Check prerequisites:**
   ```bash
   java -version              # Should show Java 17+
   gradle --version           # Or ./gradlew --version
   echo $ANDROID_HOME         # Should point to SDK
   ```

2. **Check for build errors:**
   - Review full output of `./scripts/build-apk.sh`
   - Look for Java/Gradle errors
   - Check disk space: `df -h`

3. **Try different method:**
   ```bash
   ./scripts/build-apk.sh 1.2.3 42 clean  # Try clean build
   ./scripts/build-apk.sh 1.2.3 42 docker # Try Docker
   ```

### "Docker not running"

```bash
# Start Docker daemon
docker daemon

# Or restart Docker
sudo systemctl restart docker

# Verify
docker ps
```

### "Gradle build failed"

1. **Clear cache:**
   ```bash
   ./gradlew clean
   ```

2. **Check Java compatibility:**
   ```bash
   ./gradlew --version
   java --version
   ```

3. **Check SDK components:**
   ```bash
   sdkmanager --list
   ```

### "No space left on device"

Clean build artifacts:
```bash
./gradlew clean
docker system prune -a  # If using Docker
rm -rf ~/.gradle/caches
```

## Output Location

All built APKs are stored in `apk/` directory:

```
apk/
├── EbookReader-1.0.0.apk
├── EbookReader-1.2.3.apk
└── ...
```

View latest:
```bash
./scripts/apk-manager.sh latest
```

## Installation

After building, transfer APK to Android device:

1. **Via adb (if connected):**
   ```bash
   adb install apk/EbookReader-1.2.3.apk
   ```

2. **Manually:**
   - Copy APK to USB drive or cloud storage
   - Transfer to Android device
   - Open file manager, navigate to APK
   - Tap to install
   - Allow "Install from unknown sources" if prompted

## CI/CD Integration

The GitHub Actions workflows automatically:

1. Build a debug APK on every push/PR (`ci.yml`, 30-day artifact)
2. Build and publish a signed release APK when `auto-release.yml` or
   `release.yml` runs (90-day artifact + GitHub Release)

View builds:
- **Actions tab** > **CI** (debug) or **Auto Release** / **Manual Release APK** (release)
- Download artifact from run details
- Or download from GitHub Releases

## Advanced

### Custom Build Arguments

Pass additional Gradle properties:

```bash
export VERSION_CODE=99
export VERSION_NAME=v2.0.0
./gradlew assembleRelease
```

### Docker Build Customization

```bash
docker build \
  --build-arg VERSION_CODE=99 \
  --build-arg VERSION_NAME=v2.0.0 \
  --build-arg GRADLE_OPTS="-Xmx3072m" \
  -t ebook-reader:custom \
  .
```

### Keep Build Artifacts

APKs are automatically kept in `apk/` directory.
To archive all builds:

```bash
mkdir -p releases
mv apk/*.apk releases/
```

## Performance Tips

1. **Use incremental builds (Gradle method):**
   - Don't use `clean` unless necessary
   - Keep cache between builds

2. **Pre-warm SDK cache:**
   ```bash
   ./gradlew help  # Downloads plugins
   ```

3. **Use Docker for CI:**
   - Reproducible, isolated builds
   - No SDK setup overhead
   - Works everywhere Docker runs

## See Also

- [CLAUDE.md](./CLAUDE.md) - Project architecture and conventions
- [Dockerfile](./Dockerfile) - Docker build configuration
- [.github/workflows/release.yml](./.github/workflows/release.yml) - GitHub Actions release workflow
