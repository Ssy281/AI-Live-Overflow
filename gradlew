#!/bin/bash
# Gradle wrapper launcher
# Download and run Gradle if not already installed
export GRADLE_USER_HOME="${HOME}/.gradle"
exec gradle "$@"
