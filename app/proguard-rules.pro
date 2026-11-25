# R8 rules for the app's own code. The libraries (kotlinx.serialization, Retrofit, OkHttp, Room, Hilt, WorkManager
# and hilt-work, Glance, CameraX, Coil, Tink, Play services) ship consumer rules that R8 merges automatically; only
# what those rules miss belongs here.

# Glance stores an ActionCallback by class name (actionRunCallback<T>()) and creates it later through its no-arg
# constructor by reflection. Glance's own rule keeps the class but not that constructor, so R8 full mode strips it
# and tapping a widget action (add water, dose taken, dose snooze) would crash.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
