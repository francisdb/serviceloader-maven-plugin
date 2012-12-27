This maven plugin generates services files for the ServiceLoader introduced in Java 6 :
http://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html

for example:

    <build>
      <plugins>
        <plugin>
          <groupId>eu.somatik.serviceloader-maven-plugin</groupId>
          <artifactId>serviceloader-maven-plugin</artifactId>
          <version>1.0.1</version>
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

this will generate these files:

* META-INF/services/com.foo.Dictionary
* META-INF/services/com.foo.Operation

by scanning the generated classes and finding all non-abstract/non-interface implementations of the service interfaces. The plugin itself has no Java 6 dependency

A example project is provided and can be run like this:

    $ mvn2 clean install
    ...
    [INFO] Generating service file .../example/target/classes/META-INF/services/eu.somatik.serviceloader.Operation
    [INFO]   + eu.somatik.serviceloader.SimpleOperation
    ...
    
    $ java -jar target/example-1.0-SNAPSHOT.jar
    Found service implementation: eu.somatik.serviceloader.SimpleOperation@579a19fd
    Hello world

The old project path for reference: 
http://jira.codehaus.org/browse/MOJO-1272?focusedCommentId=242147#action_242147
