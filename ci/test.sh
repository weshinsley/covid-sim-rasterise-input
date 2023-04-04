mkdir build
javac -classpath .:ci/junit-jplatform-console-standalone-1.9.2.jar -d build src/rasterise/*.java
java -jar ci/junit-jplatform-console-standalone-1.9.2.jar --class-path build --scan-class-path
