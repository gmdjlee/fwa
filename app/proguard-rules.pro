# minify 는 v1 에서 비활성(isMinifyEnabled = false)이다. 이 규칙은 v1.5 에서
# 축소·난독화를 켤 때 필요한 것을 미리 기록해 둔 것이다.

# ResizeModeDetector 가 ApplicationInfo.privateFlags 를 리플렉션으로 읽는다.
# 필드명이 난독화되면 조회가 실패하고 FALLBACK_UNRESIZEABLE_BIT 경로로만 떨어진다.
-keepclassmembers class android.content.pm.ApplicationInfo {
    int privateFlags;
}

# W2(F3+F4+S2+S3): Shizuku 가 ShellExecUserService 를 리플렉션으로 로드하고 destroy() 를
# 이름으로 호출한다(Shizuku 표준 UserService 규약). IShellExec.Stub 도 AIDL 생성 바이트코드라
# 마찬가지로 리플렉션 경로에 노출된다. 난독화되면 P4-1 팝업 경로 전체가 죽는다.
# (현재 isMinifyEnabled=false 라 무효 — M6 과 같은 취지의 v1.5 대비 사전 기록.)
-keep class dev.dj.foldwindow.service.ShellExecUserService { <init>(); public *; }
-keep interface dev.dj.foldwindow.IShellExec { *; }
-keep class dev.dj.foldwindow.IShellExec$* { *; }
