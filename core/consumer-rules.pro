# ---- Mark: R8 keep rules ----
# Applied automatically to every app that depends on :core (phone and watch),
# because the classes they protect live here.
#
# Everything below is either serialised by Gson (field names must survive) or
# reflected on at runtime. Without these, R8 renames fields and the app fails in
# ways that are hard to trace: commands execute but replies come back blank.

# Gson-serialised models: watch <-> phone transport and LLM request/response
-keep class com.example.mark.model.** { *; }
-keep class com.example.mark.network.** { *; }

# Enums used in when-branches and serialised by name
-keepclassmembers enum com.example.mark.router.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.example.mark.router.IntentType { *; }
-keep class com.example.mark.router.ReplyMode { *; }

# Sealed result/event hierarchies
-keep class com.example.mark.assistant.AssistantEvent { *; }
-keep class com.example.mark.assistant.AssistantEvent$* { *; }
-keep class com.example.mark.assistant.ToolResult { *; }
-keep class com.example.mark.assistant.ToolResult$* { *; }

# Gson generic type tokens
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit / OkHttp
-keepattributes Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Firebase / Play Services keep their own consumer rules; silence the noise
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**

# Keep our log tags readable when debugging a release build
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile