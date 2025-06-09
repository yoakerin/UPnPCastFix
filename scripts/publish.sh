#!/bin/bash

# UPnPCast Release Script
# Usage: ./scripts/publish.sh <version>

set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Error: Please provide version number"
    echo "Usage: ./scripts/publish.sh 1.0.0"
    exit 1
fi

echo "🚀 Publishing UPnPCast v$VERSION"

# Check if working directory is clean
if [ -n "$(git status --porcelain)" ]; then
    echo "❌ Working directory is not clean, please commit all changes first"
    exit 1
fi

# Update version
echo "📝 Updating version to $VERSION"
sed -i "s/VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties

# Run tests
echo "🧪 Running tests..."
./gradlew clean test lint

# Build release
echo "🔨 Building release..."
./gradlew assembleRelease

# Commit version changes
echo "💾 Committing version changes..."
git add gradle.properties
git commit -m "release: v$VERSION"

# Create tag
echo "🏷️ Creating tag..."
git tag -a "v$VERSION" -m "Release v$VERSION"

# Push to remote
echo "⬆️ Pushing to remote..."
git push origin main
git push origin "v$VERSION"

echo "✅ Release completed!" 