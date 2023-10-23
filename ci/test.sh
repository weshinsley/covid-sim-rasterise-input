mkdir -p build
javac -classpath .:libs/junit-platform-console-standalone-1.9.2.jar -d build src/rasterise/*.java
java -jar libs/junit-platform-console-standalone-1.9.2.jar --class-path build --scan-class-path
