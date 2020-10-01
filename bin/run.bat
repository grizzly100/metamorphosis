set MEDIA=%1

@echo off
REM BEGIN ENVIRONMENT VARIABLES

REM Java
set JAVA_HOME=C:\Program Files\Java\jdk-14

REM Maven Repository holding imported libraries
SET MAVEN_REPO=C:\Users\%USERNAME%\.m2\repository

REM Project directory
set PROJECT_HOME=C:\Dev\Projects\IDEA\metamorphosis
set PROJECT_LIB=%PROJECT_HOME%\lib
set CLASSES=%PROJECT_HOME%\target\classes

REM HEIF JNI wrapper - part of local project
set LIB_HEIF=%PROJECT_LIB%\heif-api\heif-api.jar

REM Maven libraries - SLF4J for logging and DREWNOAKES for metadata extraction
set LIB_SLF4J=%MAVEN_REPO%\org\slf4j\slf4j-api\1.7.24\slf4j-api-1.7.24.jar;%MAVEN_REPO%\org\slf4j\slf4j-simple\1.7.24\slf4j-simple-1.7.24.jar
set LIB_DREWNOAKES=%MAVEN_REPO%\com\drewnoakes\metadata-extractor\2.11.0\metadata-extractor-2.11.0.jar;%MAVEN_REPO%\com\adobe\xmp\xmpcore\5.1.3\xmpcore-5.1.3.jar

REM Create full classpath
set CP=%CLASSES%;%LIB_HEIF%;%LIB_SLF4J%;%LIB_DREWNOAKES%

REM END ENVIRONMENT VARIABLES
@echo on

REM Running FileRenamer
"%JAVA_HOME%\bin\java.exe" -Djava.library.path=%LIB%\heif-x64 -Dfile.encoding=UTF-8 -classpath %CP% org.grizzlytech.metamorphosis.FileRenamer %MEDIA%


