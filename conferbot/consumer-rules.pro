# Consumer ProGuard rules for Conferbot SDK

# Keep all public SDK classes and methods
-keep public class com.conferbot.sdk.** { public *; }

# Keep data models
-keep class com.conferbot.sdk.models.** { *; }

# Keep Socket.IO
-keep class io.socket.** { *; }
