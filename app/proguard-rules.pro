# minify 는 v1 에서 비활성(isMinifyEnabled = false)이다. 이 규칙은 v1.5 에서
# 축소·난독화를 켤 때 필요한 것을 미리 기록해 둔 것이다.

# ResizeModeDetector 가 ApplicationInfo.privateFlags 를 리플렉션으로 읽는다.
# 필드명이 난독화되면 조회가 실패하고 FALLBACK_UNRESIZEABLE_BIT 경로로만 떨어진다.
-keepclassmembers class android.content.pm.ApplicationInfo {
    int privateFlags;
}
