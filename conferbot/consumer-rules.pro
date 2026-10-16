# Consumer ProGuard rules for Conferbot SDK

# Keep all public SDK classes and methods
-keep public class com.conferbot.sdk.** { public *; }

# Keep data models
-keep class com.conferbot.sdk.models.** { *; }

# Keep Socket.IO
-keep class io.socket.** { *; }

# Firebase is optional (compileOnly): an app without it never loads
# ConferbotNotificationService, so R8 must not fail on its missing superclass.
-dontwarn com.google.firebase.**
