#!/bin/bash

# UPnPCast 发布脚本
# 用法: ./scripts/publish.sh <version>

set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "错误: 请提供版本号"
    echo "用法: ./scripts/publish.sh 1.0.0"
    exit 1
fi

echo "🚀 开始发布 UPnPCast v$VERSION"

# 检查工作目录是否干净
if [ -n "$(git status --porcelain)" ]; then
    echo "❌ 工作目录不干净，请先提交所有更改"
    exit 1
fi

# 更新版本号
echo "📝 更新版本号到 $VERSION"
sed -i "s/VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties
sed -i "s/version = \".*\"/version = \"$VERSION\"/" app/build.gradle.kts

# 运行测试
echo "🧪 运行测试..."
./gradlew clean test lint

# 构建 release 版本
echo "🔨 构建 release 版本..."
./gradlew assembleRelease

# 生成文档
echo "📚 生成文档..."
./gradlew dokkaHtml || true

# 提交版本更改
echo "💾 提交版本更改..."
git add gradle.properties app/build.gradle.kts
git commit -m "bump: 发布版本 v$VERSION"

# 创建标签
echo "🏷️ 创建标签..."
git tag -a "v$VERSION" -m "Release v$VERSION"

# 推送到远程
echo "⬆️ 推送到远程仓库..."
git push origin main
git push origin "v$VERSION"

# 发布到 GitHub Packages
echo "📦 发布到 GitHub Packages..."
./gradlew publish

echo "✅ 发布完成!"
echo "📋 接下来的步骤:"
echo "   1. 在 GitHub 上创建 Release"
echo "   2. 上传构建产物"
echo "   3. 更新文档网站"
echo "   4. 发布到 Maven Central (如果需要)" 