# Android SDK Testing

## Connection Test

The `test_connection.kt` file tests the connection to the Conferbot embed server.

### Prerequisites

- Kotlin compiler installed (`kotlinc`)
- OR Android Studio with Kotlin plugin
- OR JDK with Kotlin support
- Embed server running on `localhost:8001`

### Run the Test

**Option 1: Using kotlinc (compile and run)**
```bash
kotlinc test_connection.kt -include-runtime -d test_connection.jar
java -jar test_connection.jar
```

**Option 2: Using Android Studio**
1. Right-click on `test_connection.kt`
2. Select "Run 'test_connection'"

**Option 3: Add to Gradle (recommended)**

Add to `build.gradle`:
```gradle
task runConnectionTest(type: JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    main = 'com.conferbot.test.ConnectionTestKt'
}
```

Then run:
```bash
./gradlew runConnectionTest
```

### Expected Output

**Success:**
```
🚀 Starting Conferbot Android SDK Connection Test

Configuration:
  Socket URL: http://localhost:8001
  API Key: test_api_key
  Bot ID: test_bot_id

📡 Testing REST API endpoint...
✅ REST API connection successful!
   Status Code: 200
   Response: {...}

✅ Test completed successfully!
   REST API endpoint is working correctly.
```

**Failure (server not running):**
```
❌ Connection error: Connection refused
   Make sure embed server is running on port 8001
```

## Unit Tests

Run the Android unit tests with:
```bash
./gradlew test
```

Or in Android Studio:
- Right-click on the test folder
- Select "Run Tests"

## Instrumented Tests

For tests that require Android emulator/device:
```bash
./gradlew connectedAndroidTest
```
