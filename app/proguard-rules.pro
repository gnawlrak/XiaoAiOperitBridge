# Xposed entry point
-keep class com.example.xiaoaioperit.HookEntry
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage

# ModuleStatus - used by HookEntry to detect module activation
-keep class com.example.xiaoaioperit.ModuleStatus { *; }