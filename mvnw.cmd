@ECHO OFF
setlocal
set WRAPPER_DIR=%~dp0\.mvn\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar
set WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_JAR%" (
  IF EXIST "%WRAPPER_DIR%" (echo.) ELSE (mkdir "%WRAPPER_DIR%")
  for /f "tokens=2* delims==" %%A in ('findstr /R "^wrapperUrl=" "%WRAPPER_PROPS%"') do set URL=%%B
  where curl >nul 2>nul
  IF %ERRORLEVEL% EQU 0 (
    echo Downloading Maven Wrapper jar from %URL%
    curl -fsSL "%URL%" -o "%WRAPPER_JAR%"
  ) ELSE (
    where wget >nul 2>nul
    IF %ERRORLEVEL% EQU 0 (
      echo Downloading Maven Wrapper jar from %URL%
      wget -q "%URL%" -O "%WRAPPER_JAR%"
    ) ELSE (
      echo Error: curl or wget is required to download maven-wrapper.jar
      exit /b 1
    )
  )
)

set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD=%JAVA_HOME%\bin\java

"%JAVA_CMD%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
