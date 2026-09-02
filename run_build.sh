#!/usr/bin/env bash
export ANDROID_HOME=/c/android-sdk
export JAVA_HOME="/c/Program Files/Java/jdk-22"
export PATH="$JAVA_HOME/bin:$PATH"
cd /c/coding/lyriq-widget || exit 1
{ ./build.sh; echo "BUILD_EXIT_CODE:$?"; } > build_log.txt 2>&1
