# GeckoView ships its required consumer rules; keep application entry points used by WorkManager.
-keep class ge.usltd.btu_classroom.core.sync.**Worker { public <init>(...); }
