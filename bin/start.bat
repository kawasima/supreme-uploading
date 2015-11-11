@echo off

pushd %0\..\..


java -cp dist\supreme-uploading-0.1.0-SNAPSHOT.jar;"lib\*" clojure.main -m supreme-uploading.main

pause

