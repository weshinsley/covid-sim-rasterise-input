md build
javac -classpath .;libs/junit-platform-console-standalone-1.9.2.jar;tomlj-1.1.0-all.jar -d build src/com/mrc/rasterise/*.java src/com/mrc/rasterise/population/*.java src/com/mrc/rasterise/shapefile/*.java
java -jar libs/junit-platform-console-standalone-1.9.2.jar --class-path build --scan-class-path
