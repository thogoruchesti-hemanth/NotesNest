# ---------------------------------------------------------
# General R8 / ProGuard rules
# ---------------------------------------------------------

# Keep line numbers & signatures for crash reports and reflection
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------
# App Data Models & Room Entities
# ---------------------------------------------------------
# Keep entities and DAOs to avoid reflection issues with Room database schemas
-keep class com.example.NotesNest.databases.entities.** { *; }
-keep class com.example.NotesNest.databases.daos.** { *; }

# Keep models used by Firebase Realtime Database
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}

# Keep Enum values for reflection
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------
# Suppress harmless missing-class warnings from dependencies
# ---------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.apache.http.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
