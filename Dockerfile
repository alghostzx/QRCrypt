FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_REV=11076708
ARG ANDROID_COMPILE_SDK=35
ARG ANDROID_BUILD_TOOLS=35.0.0
ARG GRADLE_VERSION=8.11.1

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/${ANDROID_BUILD_TOOLS}:${GRADLE_HOME}/bin"

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    unzip \
    zip \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK Command-Line Tools (headless, no Android Studio)
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_REV}_latest.zip" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -f /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && chmod -R a+rX "${ANDROID_HOME}"

# Install Gradle CLI (used for wrapper bootstrap / direct runs)
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
    && rm -f /tmp/gradle.zip \
    && chmod -R a+rX "${GRADLE_HOME}"

WORKDIR /workspace
