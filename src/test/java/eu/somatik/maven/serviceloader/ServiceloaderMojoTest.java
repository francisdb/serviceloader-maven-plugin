/*
 * Copyright (C) 2020 Francis De Brabandere <info@somatik.eu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.somatik.maven.serviceloader;


import com.google.common.collect.Sets;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Test;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceloaderMojoTest {

    @Test
    public void testListCompiledClasses() throws Exception {
	BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        List<String> list = mojo.listCompiledClasses(new File("target/test-classes"));
        assertEquals(9, list.size());
        assertTrue("missing class", list.contains("com.bar.Bar"));
        assertTrue("missing class", list.contains("com.foo.AbstractFoo"));
        assertTrue("missing class", list.contains("com.foo.FooImpl"));
        assertTrue("missing class", list.contains("com.foo.FooImpl2"));
        assertTrue("missing class", list.contains("com.foo.bar.Hello"));
        assertTrue("missing class", list.contains("com.baz.BazExt"));
        assertTrue("missing class", list.contains("com.baz.BazExt2"));
    }

    @Test
    public void testMojoWithExcludes() throws MojoExecutionException, IllegalAccessException, IOException {
        BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[]{"com.foo.AbstractFoo"});
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        ReflectionUtils.setVariableValueInObject(mojo, "excludes", new String[]{"*2*"});
        mojo.execute();

        File serviceFile = new File("target/test-classes/META-INF/services/com.foo.AbstractFoo");

        String serviceFileContents = FileUtils.fileRead(serviceFile);
        assertEquals("com.foo.FooImpl\n", serviceFileContents);
    }

    @Test
    public void testMojoWithIncludes() throws MojoExecutionException, IllegalAccessException, IOException {
        BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[]{"com.foo.AbstractFoo"});
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        ReflectionUtils.setVariableValueInObject(mojo, "includes", new String[]{"*2"});
        mojo.execute();

        File serviceFile = new File("target/test-classes/META-INF/services/com.foo.AbstractFoo");

        String serviceFileContents = FileUtils.fileRead(serviceFile);
        assertEquals("com.foo.FooImpl2\n", serviceFileContents);
    }

    /**
     * See https://github.com/francisdb/serviceloader-maven-plugin/issues/4
     */
    @Test
    public void testMojoWithAbstractClass() throws MojoExecutionException, IllegalAccessException, IOException {
	BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[]{"com.foo.AbstractFoo"});
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        mojo.execute();

        File serviceFile = new File("target/test-classes/META-INF/services/com.foo.AbstractFoo");

        String serviceFileContents = FileUtils.fileRead(serviceFile);
        Set<String> classNames = Sets.newHashSet(serviceFileContents.trim().split("\n"));
        assertEquals(Sets.newHashSet("com.foo.FooImpl", "com.foo.FooImpl2"), classNames);
    }

    /**
     * See https://github.com/francisdb/serviceloader-maven-plugin/issues/11
     */
    @Test
    public void testMojoWithConcreteClass() throws MojoExecutionException, IllegalAccessException, IOException {
        BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[]{"com.baz.Baz"});
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        mojo.execute();

        File serviceFile = new File("target/test-classes/META-INF/services/com.baz.Baz");

        String serviceFileContents = FileUtils.fileRead(serviceFile);
        Set<String> classNames = Sets.newHashSet(serviceFileContents.trim().split("\n"));
        assertEquals(Sets.newHashSet("com.baz.BazExt", "com.baz.BazExt2"), classNames);
    }

    @Test
    public void testNotFailOnMissingClass() throws MojoExecutionException, IllegalAccessException, IOException {
        BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[] { "com.baz.MissingService" });
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        ReflectionUtils.setVariableValueInObject(mojo, "failOnMissingServiceClass", false);
        mojo.execute();

        File serviceFile = new File("target/test-classes/META-INF/services/com.baz.MissingService");
        assertFalse(serviceFile.exists());
    }

    @Test(expected = MojoExecutionException.class)
    public void testFailOnMissingClass() throws MojoExecutionException, IllegalAccessException, IOException {
        BuildContext buildContext = new DefaultBuildContext();
        ServiceloaderMojo mojo = new ServiceloaderMojo();
        mojo.setBuildContext(buildContext);
        ReflectionUtils.setVariableValueInObject(mojo, "services", new String[] { "com.baz.MissingService" });
        ReflectionUtils.setVariableValueInObject(mojo, "compileClasspath", Collections.<String>emptyList());
        ReflectionUtils.setVariableValueInObject(mojo, "classFolder", new File("target/test-classes"));
        ReflectionUtils.setVariableValueInObject(mojo, "outputDirectory", new File("target/test-classes/META-INF/services"));
        ReflectionUtils.setVariableValueInObject(mojo, "failOnMissingServiceClass", true);
        mojo.execute();
    }
}
