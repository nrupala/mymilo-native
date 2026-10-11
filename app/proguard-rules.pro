# ProGuard / R8 rules — release builds (v0.6.0+)

# Gson-reflected model + DTO classes: field names are the wire format.
-keep class org.aimlds.mymilo.network.** { *; }
-keep class org.aimlds.mymilo.data.** { *; }

# Reflection metadata Gson/Retrofit rely on.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Retrofit / OkHttp / Room / Coroutines ship their own consumer rules;
# the keeps above cover this app's reflective surface (Gson DTOs).

# On-device engine (v0.13.0): the native library calls these
# methods by their exact JNI names — renaming breaks the link.
-keep class org.aimlds.mymilo.local.LocalEngine { *; }
