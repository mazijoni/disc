@echo off
@rem ##########################################################################
@rem Gradle wrapper for Windows
@rem ##########################################################################
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

@setlocal
set DEFAULT_JVM_OPTS="-Xmx3g" "-Xms512m"

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
