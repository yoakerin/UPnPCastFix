# Contributing to UPnPCast

Thank you for your interest in contributing to UPnPCast! We welcome contributions from the community.

## 🚀 Ways to Contribute

- 🐛 **Report bugs** using our [bug report template](https://github.com/yinnho/UPnPCast/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D+)
- ✨ **Request features** using our [feature request template](https://github.com/yinnho/UPnPCast/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D+)
- 📖 **Improve documentation**
- 🧪 **Add tests**
- 💻 **Submit code improvements**

## 🛠️ Development Setup

### Prerequisites
- **JDK 17** or higher
- **Android Studio** (latest stable version)
- **Git**

### Local Setup
```bash
# Clone the repository
git clone https://github.com/yinnho/UPnPCast.git
cd UPnPCast

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the demo app
./gradlew :app-demo:installDebug
```

## 📋 Development Guidelines

### Code Style
- Follow **Kotlin coding conventions**
- Use **meaningful variable and function names**
- Add **KDoc comments** for public APIs
- Keep functions **small and focused**

### Testing
- Write unit tests for new features
- Ensure all tests pass before submitting
- Test on different Android versions (API 24+)
- Test with various DLNA devices when possible

### Commit Messages
Use conventional commit format:
```
type(scope): description

Examples:
feat(api): add new device discovery method
fix(cast): resolve playback state synchronization
docs(readme): update installation instructions
test(discovery): add SSDP discovery tests
```

## 🔄 Pull Request Process

### 1. Create a Feature Branch
```bash
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes
- Follow the coding guidelines above
- Add tests for new functionality
- Update documentation if needed

### 3. Test Your Changes
```bash
# Run all tests
./gradlew test

# Run lint checks
./gradlew lint

# Test with the demo app
./gradlew :app-demo:installDebug
```

### 4. Submit Pull Request
- Push your branch to GitHub
- Create a Pull Request with a clear description
- Link any related issues
- Wait for code review

### PR Requirements
- ✅ All tests must pass
- ✅ Code must pass lint checks
- ✅ Include appropriate tests
- ✅ Update documentation if needed
- ✅ Get approval from maintainers

## 🐛 Bug Reports

When reporting bugs, please include:
- **Clear description** of the issue
- **Steps to reproduce** the problem
- **Expected vs actual behavior**
- **Environment details** (device, Android version, library version)
- **Logs or error messages**
- **Code sample** if applicable

## ✨ Feature Requests

For feature requests, please provide:
- **Problem description** and use case
- **Proposed solution** with examples
- **API design suggestions** (if applicable)
- **Priority level** and **contribution willingness**

## 📱 Testing on Devices

### DLNA Device Testing
We test compatibility with:
- Xiaomi TV
- Samsung Smart TV
- LG Smart TV
- Sony Bravia TV
- Android TV boxes

### Manual Testing Checklist
- [ ] Device discovery works
- [ ] Media casting starts successfully
- [ ] Playback controls (play/pause/stop) work
- [ ] Volume control functions
- [ ] Error handling is appropriate

## 📞 Getting Help

- 📖 Check the [documentation](README.md)
- 🎯 Look at the [demo app](app-demo/)
- 💬 Start a [discussion](https://github.com/yinnho/UPnPCast/discussions)
- 🐛 Search existing [issues](https://github.com/yinnho/UPnPCast/issues)

## 📜 License

By contributing, you agree that your contributions will be licensed under the MIT License.

## 🙏 Recognition

Contributors will be acknowledged in our release notes and README. Thank you for making UPnPCast better! 