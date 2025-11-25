# Publishing Guide

Guide for publishing the Conferbot Android SDK to Maven Central.

## Prerequisites

1. **Maven Central Account**
   - Create account at https://central.sonatype.com
   - Verify domain ownership
   - Create namespace: `com.conferbot`

2. **GPG Key**
   ```bash
   gpg --gen-key
   gpg --list-keys
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

3. **Gradle Properties**
   Create `~/.gradle/gradle.properties`:
   ```properties
   signing.keyId=YOUR_KEY_ID
   signing.password=YOUR_GPG_PASSWORD
   signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg

   ossrhUsername=YOUR_SONATYPE_USERNAME
   ossrhPassword=YOUR_SONATYPE_PASSWORD
   ```

## Publishing Configuration

### 1. Update `build.gradle`

Add publishing plugin to `conferbot/build.gradle`:

```gradle
plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
    id 'maven-publish'
    id 'signing'
}

// ... existing config ...

afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release

                groupId = 'com.conferbot'
                artifactId = 'android-sdk'
                version = '1.0.0'

                pom {
                    name = 'Conferbot Android SDK'
                    description = 'Native Android SDK for Conferbot chat integration'
                    url = 'https://github.com/conferbot/android-sdk'

                    licenses {
                        license {
                            name = 'Proprietary'
                            url = 'https://conferbot.com/license'
                        }
                    }

                    developers {
                        developer {
                            id = 'conferbot'
                            name = 'Conferbot Team'
                            email = 'dev@conferbot.com'
                        }
                    }

                    scm {
                        connection = 'scm:git:git://github.com/conferbot/android-sdk.git'
                        developerConnection = 'scm:git:ssh://github.com/conferbot/android-sdk.git'
                        url = 'https://github.com/conferbot/android-sdk'
                    }
                }
            }
        }

        repositories {
            maven {
                name = "OSSRH"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = ossrhUsername
                    password = ossrhPassword
                }
            }
        }
    }

    signing {
        sign publishing.publications.release
    }
}
```

### 2. Root `build.gradle`

```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0"
    }
}

plugins {
    id 'io.github.gradle-nexus.publish-plugin' version '1.3.0'
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username = ossrhUsername
            password = ossrhPassword
        }
    }
}
```

## Build and Publish

### 1. Update Version

Update version in:
- `gradle.properties`: `VERSION_NAME=1.0.0`
- `conferbot/build.gradle`: `version = '1.0.0'`
- `CHANGELOG.md`: Add release notes

### 2. Build Release

```bash
# Clean build
./gradlew clean

# Build release
./gradlew :conferbot:assembleRelease

# Generate AAR
./gradlew :conferbot:bundleReleaseAar

# Generate Javadoc
./gradlew :conferbot:dokkaHtml
```

### 3. Publish to Maven Local (Testing)

```bash
./gradlew publishToMavenLocal
```

Test in another project:
```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation 'com.conferbot:android-sdk:1.0.0'
}
```

### 4. Publish to Maven Central

```bash
# Publish
./gradlew publishReleasePublicationToOSSRHRepository

# Or using nexus plugin
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

### 5. Verify Staging Repository

1. Go to https://s01.oss.sonatype.org
2. Login with credentials
3. Go to "Staging Repositories"
4. Find `comconferbot-XXXX`
5. Click "Close"
6. Wait for validation
7. Click "Release"

### 6. Sync to Maven Central

After release, wait 2-4 hours for sync to Maven Central.
Check: https://central.sonatype.com/artifact/com.conferbot/android-sdk

## JitPack Publishing (Alternative)

For simpler publishing via JitPack:

### 1. Create GitHub Release

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

### 2. Create Release on GitHub

Go to GitHub → Releases → Create new release
- Tag: v1.0.0
- Title: v1.0.0
- Description: Release notes from CHANGELOG.md

### 3. JitPack Build

JitPack will automatically build from the tag.
Check build status: https://jitpack.io/#conferbot/android-sdk

### 4. Usage

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.conferbot:android-sdk:1.0.0'
}
```

## Version Management

### Semantic Versioning

Follow SemVer: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking API changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

Examples:
- `1.0.0` - Initial release
- `1.0.1` - Bug fix
- `1.1.0` - New feature
- `2.0.0` - Breaking change

### Version Code

For AAR version code:
```gradle
android {
    defaultConfig {
        versionCode 100 // 1.0.0
        versionName "1.0.0"
    }
}
```

Version code formula: `MAJOR * 10000 + MINOR * 100 + PATCH`
- 1.0.0 = 10000
- 1.2.3 = 10203
- 2.0.0 = 20000

## Release Checklist

- [ ] Update version in `gradle.properties`
- [ ] Update version in `build.gradle`
- [ ] Update `CHANGELOG.md` with release notes
- [ ] Update `README.md` if needed
- [ ] Run all tests: `./gradlew test`
- [ ] Run lint: `./gradlew lint`
- [ ] Build release: `./gradlew assembleRelease`
- [ ] Test in example app
- [ ] Create git tag: `git tag v1.0.0`
- [ ] Push tag: `git push origin v1.0.0`
- [ ] Publish to Maven Central
- [ ] Create GitHub Release with notes
- [ ] Update documentation website
- [ ] Announce release (blog, Twitter, etc.)

## Snapshot Releases

For development snapshots:

### 1. Update Version

```gradle
version = '1.1.0-SNAPSHOT'
```

### 2. Publish Snapshot

```bash
./gradlew publishReleasePublicationToOSSRHRepository
```

Snapshot URL:
```
https://s01.oss.sonatype.org/content/repositories/snapshots/
```

### 3. Usage

```gradle
repositories {
    maven {
        url "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    }
}

dependencies {
    implementation 'com.conferbot:android-sdk:1.1.0-SNAPSHOT'
}
```

## Documentation Publishing

### KDoc/Dokka

Generate KDoc documentation:

```bash
./gradlew dokkaHtml
```

Output: `conferbot/build/dokka/html/index.html`

### GitHub Pages

1. Copy docs to `docs/` directory
2. Enable GitHub Pages in repo settings
3. Set source to `docs/` folder
4. Access at: https://conferbot.github.io/android-sdk/

### Read the Docs

1. Create `.readthedocs.yml`:
```yaml
version: 2

build:
  os: ubuntu-22.04
  tools:
    python: "3.11"

mkdocs:
  configuration: mkdocs.yml
```

2. Link GitHub repo to Read the Docs
3. Docs available at: https://conferbot-android-sdk.readthedocs.io/

## Artifact Verification

After publishing, verify:

### 1. Check Maven Central

```bash
curl https://repo1.maven.org/maven2/com/conferbot/android-sdk/1.0.0/
```

### 2. Download and Inspect

```bash
wget https://repo1.maven.org/maven2/com/conferbot/android-sdk/1.0.0/android-sdk-1.0.0.aar
unzip -l android-sdk-1.0.0.aar
```

### 3. Test Integration

Create new Android project and add dependency:

```gradle
dependencies {
    implementation 'com.conferbot:android-sdk:1.0.0'
}
```

## Troubleshooting

### Issue: GPG Signing Failed

**Solution**:
```bash
# Export secret key
gpg --export-secret-keys YOUR_EMAIL > secring.gpg

# Update gradle.properties
signing.secretKeyRingFile=/path/to/secring.gpg
```

### Issue: Upload Failed

**Solution**:
Check credentials in `~/.gradle/gradle.properties`

### Issue: Validation Failed

**Solution**:
Common issues:
- Missing POM information
- Missing Javadoc JAR
- Missing Sources JAR
- Invalid GPG signature

Add to `build.gradle`:
```gradle
tasks.register('androidSourcesJar', Jar) {
    archiveClassifier.set('sources')
    from android.sourceSets.main.java.srcDirs
}

tasks.register('androidJavadocsJar', Jar) {
    archiveClassifier.set('javadoc')
    from dokkaHtml.outputDirectory
}

publishing {
    publications {
        release(MavenPublication) {
            artifact androidSourcesJar
            artifact androidJavadocsJar
        }
    }
}
```

## Security

### Protect Credentials

Never commit:
- `gradle.properties` with credentials
- GPG keys
- Signing passwords

Use environment variables:
```gradle
ossrhUsername = System.getenv("OSSRH_USERNAME") ?: ""
ossrhPassword = System.getenv("OSSRH_PASSWORD") ?: ""
```

### GitHub Secrets

For CI/CD, add to GitHub Secrets:
- `OSSRH_USERNAME`
- `OSSRH_PASSWORD`
- `SIGNING_KEY_ID`
- `SIGNING_PASSWORD`
- `SIGNING_SECRET_KEY_RING_FILE` (base64 encoded)

## Automation (CI/CD)

### GitHub Actions

`.github/workflows/publish.yml`:
```yaml
name: Publish to Maven Central

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'adopt'

      - name: Decode GPG key
        run: |
          echo "${{ secrets.SIGNING_SECRET_KEY_RING_FILE }}" | base64 -d > secring.gpg

      - name: Publish
        run: ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
          SIGNING_SECRET_KEY_RING_FILE: secring.gpg
```

---

**Last Updated**: November 25, 2025
