# Build stage — compile APK
FROM eclipse-temurin:17-jdk-jammy as builder

ARG VERSION_CODE=1
ARG VERSION_NAME=1.0.0
ARG GRADLE_OPTS="-Xmx2048m"

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools \
    GRADLE_OPTS="${GRADLE_OPTS}" \
    GRADLE_USER_HOME=/root/.gradle

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    git \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -L -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools/* ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept Android licenses and install SDK components
RUN yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses && \
    ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
      "platforms;android-36" \
      "build-tools;36.0.0" \
      "platform-tools"

# Copy project
WORKDIR /app
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Build APK (unsigned by default)
RUN ./gradlew assembleRelease --no-daemon --build-cache \
    -PVERSION_CODE="${VERSION_CODE}" \
    -PVERSION_NAME="${VERSION_NAME}" || \
    # Fallback: try clean build if first attempt fails
    (./gradlew clean && ./gradlew assembleRelease --no-daemon \
    -PVERSION_CODE="${VERSION_CODE}" \
    -PVERSION_NAME="${VERSION_NAME}")

# Export stage — extract built APK
FROM alpine:latest

COPY --from=builder /app/app/build/outputs/apk/release /out

RUN ls -lh /out/ || exit 1

CMD ["sh", "-c", "ls -lh /out/"]
