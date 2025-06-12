# Security Configuration Guide

## 🔒 Protecting Sensitive Information

This project requires sensitive configuration for Maven Central publishing. To keep your credentials secure:

### 📁 Local Configuration Setup

1. **Copy the example file:**
   ```bash
   cp gradle.properties.local.example gradle.properties.local
   ```

2. **Edit `gradle.properties.local` with your real credentials:**
   ```properties
   # Maven Central credentials
   ossrhUsername=your-sonatype-username
   ossrhPassword=your-sonatype-password-or-token
   
   # GPG signing configuration
   signing.gnupg.executable=path-to-your-gpg-executable
   signing.gnupg.keyName=your-gpg-key-id
   signing.gnupg.passphrase=your-gpg-key-password
   ```

3. **Verify the file is ignored:**
   ```bash
   git status
   # gradle.properties.local should NOT appear in the list
   ```

### ⚠️ Important Security Notes

- ✅ `gradle.properties.local` is already in `.gitignore`
- ✅ The main `gradle.properties` contains only non-sensitive project settings
- ❌ **NEVER** commit files containing real passwords or API keys
- 🔄 Use Sonatype User Tokens instead of passwords when possible

### 🚨 If You Accidentally Commit Secrets

If you accidentally commit sensitive information:

1. **Immediately change your passwords/tokens**
2. **Remove the sensitive data from Git history:**
   ```bash
   # Remove from current commit
   git reset --soft HEAD~1
   git reset gradle.properties
   
   # Or use git filter-branch for older commits
   git filter-branch --force --index-filter \
     'git rm --cached --ignore-unmatch gradle.properties' \
     --prune-empty --tag-name-filter cat -- --all
   ```
3. **Force push to update remote repository**
4. **Generate new credentials on Sonatype**

### 📋 Safe Sharing Checklist

Before pushing commits, verify:
- [ ] No passwords in `gradle.properties`
- [ ] No API keys in source code
- [ ] `gradle.properties.local` is not tracked by Git
- [ ] Build files reference properties correctly

### 🔗 Additional Resources

- [Sonatype User Tokens](https://central.sonatype.org/publish/generate-token/)
- [GPG Key Management](https://docs.github.com/en/authentication/managing-commit-signature-verification/generating-a-new-gpg-key)
- [Git Security Best Practices](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure) 