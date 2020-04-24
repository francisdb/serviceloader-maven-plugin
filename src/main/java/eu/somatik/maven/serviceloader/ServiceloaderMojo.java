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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.SelectorUtils;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Goal that generates the services files
 *
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresProject = true)
public class ServiceloaderMojo extends AbstractMojo {


    @Component
    private BuildContext buildContext;

    /**
     * <i>Maven Internal</i>: Project to interact with.
     *
     */
    @Parameter(property="project", required = true, readonly = true)
    protected MavenProject project;

    /**
     */
    @Parameter(defaultValue ="${project.build.directory}/classes", required = true, readonly = true)
    private File classFolder;

    /**
     */
    @Parameter(defaultValue ="${project.compileClasspathElements}", required = true, readonly = true)
    private List<String> compileClasspath;

    /**
     * The service interfaces to generate service files for
     *
     */
    @Parameter
    private String[] services;
    
    @Parameter
    private String[] includes;
    
    @Parameter
    private String[] excludes;
    
    @Parameter(defaultValue ="true")
    private boolean failOnMissingServiceClass;

    public MavenProject getProject() {
        return project;
    }

    public String[] getServices() {
        return services;
    }

    private File getClassFolder() {
        return classFolder;
    }

    public List<String> getCompileClasspath() {
        return compileClasspath;
    }

    public void setBuildContext(BuildContext buildContext) {
        this.buildContext = buildContext;
    }

    /**
     * The main entry point for this Mojo.
     *
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        if (skipProject()) {
            getLog().info("POM project detected; skipping");
        }else {
            URLClassLoader classLoader = new URLClassLoader(generateClassPathUrls());
            List<Class<?>> interfaceClasses = loadServiceClasses(classLoader);
            Map<String, List<String>> serviceImplementations = findImplementations(classLoader, interfaceClasses);
            writeServiceFiles(serviceImplementations);
        }
    }

    /**
     * Writes the output for the service files to disk
     *
     * @param serviceImplementations
     * @throws MojoExecutionException
     */
    private void writeServiceFiles(
            Map<String, List<String>> serviceImplementations)
            throws MojoExecutionException {
        // TODO give the user an option to write them to the source folder or
        // any other folder?
        File parentFolder = new File(getClassFolder(), "META-INF" + File.separator + "services");
        if (!parentFolder.exists()) {
            parentFolder.mkdirs();
        }
        for (Entry<String, List<String>> interfaceClassName : serviceImplementations.entrySet()) {
            File serviceFile = new File(parentFolder, interfaceClassName.getKey());
            getLog().info("Generating service file " + serviceFile.getAbsolutePath());
            FileWriter writer = null;
            try {
                writer = new FileWriter(serviceFile);
                for (String implementationClassName : interfaceClassName.getValue()) {
                    getLog().info("  + " + implementationClassName);
                    writer.write(implementationClassName);
                    writer.write('\n');
                }
                buildContext.refresh(serviceFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Error creating file " + serviceFile, e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        getLog().error(e);
                    }
                }
            }
        }
    }

    /**
     * Loads all interfaces using the provided ClassLoader
     *
     * @param loader the classloader
     * @return thi List of Interface classes
     * @throws MojoExecutionException
     *             is the interfaces are not interfaces or can not be found on
     *             the classpath
     */
    private List<Class<?>> loadServiceClasses(ClassLoader loader)
            throws MojoExecutionException {
        List<Class<?>> serviceClasses = new ArrayList<Class<?>>();
        for (String serviceClassName : getServices()) {
            try {
                Class<?> serviceClass = loader.loadClass(serviceClassName);
                serviceClasses.add(serviceClass);
            } catch (ClassNotFoundException ex) {
                if (failOnMissingServiceClass) {
                    throw new MojoExecutionException("Could not load class: " + serviceClassName, ex);
                } else {
                    getLog().info("Skipping missing service class: " + serviceClassName);
                }
            }
        }
        return serviceClasses;
    }

    /**
     * Finds all implementations of interfaces in a folder
     *
     * @param loader
     * @param interfaceClasses
     * @return
     * @throws MojoExecutionException
     */
    private Map<String, List<String>> findImplementations(ClassLoader loader,
                                                          List<Class<?>> interfaceClasses) throws MojoExecutionException {
        Map<String, List<String>> serviceImplementations = new HashMap<String, List<String>>();
        for (Class<?> interfaceClass: interfaceClasses) {
            serviceImplementations.put(interfaceClass.getName(), new ArrayList<String>());
        }
        getLog().info("Scanning generated classes for implementations...");
        File classFolder = getClassFolder();
        List<String> classNames = listCompiledClasses(classFolder);
        // List<String> classNames = listCompiledClassesRegex( classFolder );
        for (String className : classNames) {
            try {
                if(getLog().isDebugEnabled()){
                    getLog().debug("checking class: " + className);
                }
                Class<?> cls = loader.loadClass(className);
                int mods = cls.getModifiers();
                if (!cls.isAnonymousClass() && !cls.isInterface()
                        && !cls.isEnum() && !Modifier.isAbstract(mods)
                        && Modifier.isPublic(mods)) {
                    for (Class<?> interfaceCls : interfaceClasses) {
                        if (!interfaceCls.equals(cls) && interfaceCls.isAssignableFrom(cls)) {
                            
                            // if the includes section isn't empty, we need to respect the choice and only include the items that are shown there.
                            if (includes == null || includes.length == 0) {
                                serviceImplementations.get(interfaceCls.getName()).add(className);
                            } else {
                                for ( String include : includes )
                                {
                                    if(SelectorUtils.match(include, className)) {
                                        serviceImplementations.get(interfaceCls.getName()).add(className);
                                    }
                                }
                            }
                            
                        }
                    }
                }
            } catch (ClassNotFoundException e1) {
                getLog().warn(e1);
            } catch (NoClassDefFoundError e2) {
                getLog().warn(e2);
            }
            
        }
        
        // in the next iteration we start to process with the excludes
        if (excludes != null && excludes.length != 0) {
            Set<Entry<String,List<String>>> entrySet = serviceImplementations.entrySet();
            for ( Entry<String, List<String>> entry : entrySet )
            {
                classNames = entry.getValue();
                ListIterator<String> classNamesIter = classNames.listIterator();
                
                while ( classNamesIter.hasNext())
                {
                    String className = classNamesIter.next();
                    for ( String exclude : excludes )
                    {
                        if(SelectorUtils.match(exclude, className)) {
                          classNamesIter.remove();
                          break;
                        }
                    }
                }
            }
        }
        return serviceImplementations;
    }

    /**
     * Walks the classFolder and finds all classes
     *
     * @param classFolder the folder to scan for .class files
     * @return the list of available class names
     */
    List<String> listCompiledClasses(final File classFolder) {
        List<String> classNames = new ArrayList<String>();
        if (!classFolder.exists()) {
            getLog().info("Class folder does not exist; skipping scan");
            return classNames;
        }
        final String extension = ".class";
        final DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(classFolder);
        directoryScanner.setIncludes(new String[] { "**" + File.separator + "*" + extension });
        directoryScanner.scan();
        String[] files = directoryScanner.getIncludedFiles();

        String className;
        for (String file : files) {
            className = file.substring(0, file.length() - extension.length()).replace(File.separator, ".");
            classNames.add(className);
        }

        return classNames;
    }

    /**
     * Walks the classFolder and finds all .class files
     *
     * @param classFolder
     * @return the list of available class names
     */
    private List<String> listCompiledClassesRegex(File classFolder) {
        List<String> classNames = new ArrayList<String>();

        Stack<File> todo = new Stack<File>();
        todo.push(classFolder);
        String classFolderPath = classFolder.getAbsolutePath();
        getLog().info("ClassFolderPath=" + classFolderPath);

        Pattern pat = Pattern.compile(classFolderPath + File.separator + "(.*).class");
        File workDir;
        String name;
        while (!todo.isEmpty()) {
            workDir = todo.pop();
            for (File file : workDir.listFiles()) {
                if (file.isDirectory()) {
                    todo.push(file);
                } else {
                    if (file.getName().endsWith(".class")) {
                        name = file.getAbsolutePath();
                        name = pat.matcher(name).group(1);
                        name = name.replace(File.separator, ".");
                        getLog().debug("Found class: " + name);
                        classNames.add(name);
                    }
                }
            }

        }
        return classNames;
    }

    /**
     * Generates a URL[] with the project class path (can be used by a
     * URLClassLoader)
     *
     * @return the array of classpath URL's
     * @throws MojoExecutionException
     */
    private URL[] generateClassPathUrls() throws MojoExecutionException {
        List<URL> urls = new ArrayList<URL>();
        URL url;
        try {
            for (Object element : getCompileClasspath()) {
                String path = (String) element;
                if (path.endsWith(".jar")) {
                    url = new URL("jar:" + new File(path).toURI().toString() + "!/");
                } else {
                    url = new File(path).toURI().toURL();
                }
                urls.add(url);
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Could not set up classpath", e);
        }
        return urls.toArray(new URL[urls.size()]);
    }

    private boolean skipProject() {
        String packaging = null;
        if (project != null) {
            packaging = project.getPackaging();
        } else {
            getLog().warn("Project not set");
        }
        return packaging != null && packaging.equals("pom");
    }

}
