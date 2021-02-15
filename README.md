[![Build Status](https://travis-ci.org/francisdb/serviceloader-maven-plugin.svg?branch=master)](https://travis-ci.org/francisdb/serviceloader-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/eu.somatik.serviceloader-maven-plugin/serviceloader-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/eu.somatik.serviceloader-maven-plugin/serviceloader-maven-plugin)

This maven plugin generates services files for the ServiceLoader introduced in Java 6 :
https://docs.oracle.com/javase/9/docs/api/java/util/ServiceLoader.html

# Use

for example:
```xml
<build>
  <plugins>
    <plugin>
      <groupId>eu.somatik.serviceloader-maven-plugin</groupId>
      <artifactId>serviceloader-maven-plugin</artifactId>
      <version>1.3.1</version>
      <configuration>
        <services>
          <param>com.foo.Dictionary</param>
          <param>com.foo.Operation</param>
        </services>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

this will generate these files:

* META-INF/services/com.foo.Dictionary
* META-INF/services/com.foo.Operation

by scanning the generated classes and finding all non-abstract/non-interface implementations of the service interfaces. The plugin itself has no Java 6 dependency

# Excludes / includes

Additionally it is possible to filter implementation classes via includes and excludes section in the configuration. The class name notation is the same as for the services section.

for example:
```xml
<build>
  <plugins>
    <plugin>
      <groupId>eu.somatik.serviceloader-maven-plugin</groupId>
      <artifactId>serviceloader-maven-plugin</artifactId>
      <version>1.3.1</version>
      <configuration>
        <services>
          <param>com.foo.Dictionary</param>
          <param>com.foo.Operation</param>
        </services>
        <includes>
          <include>*.RightClass*</include>
        </includes>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

This should add only implementation classes that begin with RightClass*.

# Missing Service Classes

The default action when a service class is missing is to fail the build.
If you want to ignore this service, you can use the `failOnMissingServiceClass` option (`true` by default).

for example:
```xml
<build>
  <plugins>
    <plugin>
      <groupId>eu.somatik.serviceloader-maven-plugin</groupId>
      <artifactId>serviceloader-maven-plugin</artifactId>
      <version>1.3.1</version>
      <configuration>
      	<failOnMissingServiceClass>false</failOnMissingServiceClass>
        <services>
          <param>com.foo.MissingService</param>
        </services>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

# Example

A example project is provided and can be run like this:

    $ mvn clean install
    ...
    [INFO] Generating service file .../example/target/classes/META-INF/services/eu.somatik.serviceloader.Operation
    [INFO]   + eu.somatik.serviceloader.SimpleOperation
    ...
    
    $ java -jar target/example-1.0-SNAPSHOT.jar
    Found service implementation: eu.somatik.serviceloader.SimpleOperation@579a19fd
    Hello world

# Release

see http://central.sonatype.org/pages/apache-maven.html#performing-a-release-deployment-with-the-maven-release-plugin

*Note: do `export GPG_TTY=$(tty)` first if you have `gpg: signing failed: Inappropriate ioctl for device` errors*

```
mvn -P release release:clean release:prepare
mvn -P release release:perform
```
