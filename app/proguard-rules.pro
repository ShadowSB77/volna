# Guava (тянется через Media3) ссылается на аннотации, которых нет в рантайме
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Сервис плеера создаётся системой по имени
-keep class com.volna.player.player.PlaybackService { *; }
-keep class com.volna.player.data.DownloadService { *; }
