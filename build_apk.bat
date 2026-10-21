@echo off
set JAVA_HOME=C:\jdk17_clean\jdk-17.0.20.1+1
set ANDROID_HOME=C:\Users\vladi\AppData\Local\Android\Sdk
set ANDROID_USER_HOME=C:\Users\vladi\.android
call "%USERPROFILE%\.gradle\wrapper\dists\paperscan\gradle-8.9\gradle-8.9\bin\gradle.bat" assembleDebug --stacktrace
pause
