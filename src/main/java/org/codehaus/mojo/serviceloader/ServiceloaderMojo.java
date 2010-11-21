/*
The MIT License

Copyright (c) 2006, The Codehaus http://www.codehaus.org/

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package org.codehaus.mojo.serviceloader;

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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Goal that generates the servces files
 * 
 * @goal generate
 * @phase compile
 * @requiresDependencyResolution compile 
 */
public class ServiceloaderMojo
    extends AbstractMojo
{

    /**
     * <i>Maven Internal</i>: Project to interact with.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @parameter expression="${project.build.directory}/classes"
     * @required
     * @readonly
     */
    private File classFolder;

    /**
     * @parameter expression="${project.compileClasspathElements}"
     * @required
     * @readonly
     */
    private List<String> compileClasspath;

    /**
     * The service interfaces to generate service files for
     * 
     * @parameter
     * @required
     */
    private String[] services;

    public MavenProject getProject()
    {
        return project;
    }

    public String[] getServices()
    {
        return services;
    }

    private File getClassFolder()
    {
        return classFolder;
    }

    public List<String> getCompileClasspath()
    {
        return compileClasspath;
    }

    public void execute()
        throws MojoExecutionException
    {
        URLClassLoader classLoader = new URLClassLoader( generateClassPathUrls() );
        List<Class<?>> interfaceClasses = loadInterfaceClasses( classLoader );
        Map<String, List<String>> serviceImplementations = findImplementations( classLoader, interfaceClasses );
        writeServiceFiles( serviceImplementations );
    }

    /**
     * Writes the output for the service files to disk
     * 
     * @param serviceImplementations
     * @throws MojoExecutionException
     */
    private void writeServiceFiles( Map<String, List<String>> serviceImplementations )
        throws MojoExecutionException
    {
        // TODO give the user an option to write them to the source folder or any other folder?
        File parentFolder = new File( getClassFolder(), "META-INF" + File.separator + "services" );
        if ( !parentFolder.exists() )
        {
            parentFolder.mkdirs();
        }
        for ( Entry<String, List<String>> interfaceClassName : serviceImplementations.entrySet() )
        {
            File serviceFile = new File( parentFolder, interfaceClassName.getKey() );
            getLog().info( "Generating service file " + serviceFile.getAbsolutePath() );
            FileWriter writer = null;
            try
            {
                writer = new FileWriter( serviceFile );
                for ( String implementationClassName : interfaceClassName.getValue() )
                {
                    getLog().info( "  + " + implementationClassName );
                    writer.write( implementationClassName );
                    writer.write( '\n' );
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error creating file " + serviceFile, e );
            }
            finally
            {
                if ( writer != null )
                {
                    try
                    {
                        writer.close();
                    }
                    catch ( IOException e )
                    {
                        getLog().error( e );
                    }
                }
            }
        }
    }

    /**
     * Loads all interfaces using the provided ClassLoader
     * 
     * @param loader
     * @param interfaces
     * @return thi List of Interface classes
     * @throws MojoExecutionException is the interfaces are not interfaces or can not be found on the classpath
     */
    private List<Class<?>> loadInterfaceClasses( ClassLoader loader )
        throws MojoExecutionException
    {
        List<Class<?>> interfaceClasses = new ArrayList<Class<?>>();
        for ( String interfaceClassName : getServices() )
        {
            try
            {
                // Class.forName("oracle.jdbc.driver.OracleDriver", true, ucl);
                Class<?> interfaceClass = loader.loadClass( interfaceClassName );
                if ( !interfaceClass.isInterface() )
                {
                    throw new MojoExecutionException( "Class " + interfaceClassName + " is not an interface!" );
                }
                interfaceClasses.add( interfaceClass );
            }
            catch ( ClassNotFoundException e1 )
            {
                throw new MojoExecutionException( "Could not load interface class: " + interfaceClassName, e1 );
            }
        }
        return interfaceClasses;
    }

    /**
     * Finds all implementations of interfaces in a folder
     * 
     * @param loader
     * @param classFolder
     * @param interfaceClasses
     * @return
     * @throws MojoExecutionException
     */
    private Map<String, List<String>> findImplementations( ClassLoader loader, List<Class<?>> interfaceClasses )
        throws MojoExecutionException
    {
        Map<String, List<String>> serviceImplementations = new HashMap<String, List<String>>();
        for ( String interfaceClassName : getServices() )
        {
            serviceImplementations.put( interfaceClassName, new ArrayList<String>() );
        }
        getLog().info( "Scanning generated classes for implementations..." );
        File classFolder = getClassFolder();
        List<String> classNames = listCompiledClasses( classFolder );
        //List<String> classNames = listCompiledClassesRegex( classFolder );
        for ( String className : classNames )
        {
            try
            {
                Class<?> cls = loader.loadClass( className );
                int mods = cls.getModifiers();
                if ( !cls.isAnonymousClass() && !cls.isInterface() && !cls.isEnum()
                    && !Modifier.isAbstract( mods ) && Modifier.isPublic( mods ) )
                {
                    for ( Class<?> interfaceCls : interfaceClasses )
                    {
                        if ( interfaceCls.isAssignableFrom( cls ) )
                        {
                            serviceImplementations.get( interfaceCls.getName() ).add( className );
                        }
                    }
                }
            }
            catch ( ClassNotFoundException e1 )
            {
                getLog().error( e1 );
                throw new MojoExecutionException( "Could not load class: " + className, e1 );
            }
        }
        return serviceImplementations;
    }

    /**
     * Walks the classFolder and finds all .class files
     * 
     * @param classFolder
     * @return the list of available classe names
     */
    private List<String> listCompiledClasses( File classFolder )
    {
        final String extension = ".class";
        List<String> classNames = new ArrayList<String>();
        final DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir( classFolder );
        directoryScanner.setIncludes( new String[] { "**\\*" +extension } );
        directoryScanner.scan();
        String[] files = directoryScanner.getIncludedFiles();
        
        for(String file:files){
        	classNames.add(file.substring( 0, file.length() - extension.length()).replace( File.separator, "." ));
        }

        return classNames;
    }
    
    /**
     * Walks the classFolder and finds all .class files
     * 
     * @param classFolder
     * @return the list of available classe names
     */
    private List<String> listCompiledClassesRegex( File classFolder )
    {
        List<String> classNames = new ArrayList<String>();

        Stack<File> todo = new Stack<File>();
        todo.push( classFolder );
        String classFolderPath = classFolder.getAbsolutePath();
        getLog().info("ClassFolderPath=" + classFolderPath);
             
        Pattern pat = Pattern.compile(classFolderPath + File.separator + "(.*).class");
        File workDir;
        String name;
        while ( !todo.isEmpty() )
        {
            workDir = todo.pop();
            for ( File file : workDir.listFiles() )
            {
                if ( file.isDirectory() )
                {
                    todo.push( file );
                }
                else
                {
                    if ( file.getName().endsWith( ".class" ) )
                    {
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
     * Generates a URL[] with the project class path (can be used by a URLClassLoader)
     * 
     * @return the array of classpath URL's
     * @throws MojoExecutionException
     */
    private URL[] generateClassPathUrls()
        throws MojoExecutionException
    {
        List<URL> urls = new ArrayList<URL>();
        URL url;
        try
        {
            for ( Object element : getCompileClasspath() )
            {
                String path = (String) element;
                if ( path.endsWith( ".jar" ) )
                {
                	url = new URL( "jar:" + new File(path).toURI().toString() + "!/" );
                }
                else
                {
                	url = new File(path).toURI().toURL();
                }
                urls.add( url );
            }
        }
        catch ( MalformedURLException e )
        {
            throw new MojoExecutionException( "Could not set up classpath", e );
        }
        return urls.toArray( new URL[urls.size()] );
    }

}
