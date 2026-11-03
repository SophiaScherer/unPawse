# App-specific R8 rules. Libraries ship their own consumer rules; add one here only for
# reflection this app does itself, with a comment saying what reads it.

# ML Kit resolves its detectors through a Firebase-components registry keyed by class. Under R8
# full mode the registry came back null and the camera crashed on open, so keep that wiring intact.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
