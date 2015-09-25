package com.idyria.osi.aib.appserv

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.aib.core.compiler.EmbeddedCompiler
import com.idyria.osi.tea.logging.TLogSource
import com.idyria.osi.tea.thread.ThreadLanguage
import java.nio.file.Files
import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import com.idyria.osi.aib.core.dependencies.maven.model.Project
import org.eclipse.aether.repository.WorkspaceRepository
import org.eclipse.aether.repository.WorkspaceReader
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.resolution.ArtifactResult

/**
 * The application wrapper finds applications from a location, and takes care of the external classloader view
 */
class ApplicationWrapper(var location: File) extends TLogSource with ThreadLanguage {

    // Compiler
    //--------------------

    //-- Watcher
    var runtimeCompiler = new FolderWatcher(location)

    //-- Create Folder watcher
    //var compiler = new EmbeddedCompiler

    // set output
    //var compilerOutput = new File(location, "target"+File.separator+"classes")
    //compilerOutput.mkdirs()
    //compiler.settings2.outputDirs.setSingleOutput(compilerOutput.getAbsolutePath)

    // Dependencies Resolver
    //------------------
    var artifactsResolver = new AetherResolver

    // Internal Classloader
    //----------------------------
    var classloader = URLClassLoader.newInstance(Array(runtimeCompiler.compilerOutput.toURI().toURL()), Thread.currentThread().getContextClassLoader)

    /**
     * The dependencies list holds the Aether Artifacts resolved as beeing
     * dependencies for this wrapper
     */
    var dependencies = List[ArtifactResult]()
    
    // Applications 
    //-------------------
    var applications = List[AIBApplication]()

    // Gradle Connector
    //-------------------------------
    //var gradleConnector: GradleConnector = null

    // Init: Find and create Applications
    //--------------
    def init = {
        
        // Prepare Classloader
        //----------------
        updateClassLoader

        // Launch folder watcher compiler
        //--------------

        // Add new paths to compiler
        var generatedSources = new File(runtimeCompiler.compilerOutput.getParentFile, "generated-sources" + File.separator + "scala")
        //println(s"Testing generatedSources: "+generatedSources.getAbsolutePath)
        generatedSources.exists match {
            case true =>
                runtimeCompiler.addSourceFolder(generatedSources)
            case _ =>
        }
        
        // Dont's start per default
        //runtimeCompiler.start

        // Look for applications
        //-------------------
        //this.updateApplications
        /*this.applications.foreach {
            app => app.appInit()
        }*/
        /*var apps = this.scanFor(classOf[AIBApplication])
        apps.foreach {
            app => 
                println(s"** Application: $app")
        }*/

        // Prepare gradle
        //-------------
        /*gradleConnector = GradleConnector.newConnector()
      .forProjectDirectory(location)*/

        // Create Default application, or use provided class
        //--------------------------

    }

    def updateClassLoader = {
        
        // Re-Create classloader 
        //-------------------

        //-- Check for dependencies informations for compilation/runtime
        this.dependencies = new File(this.location, "pom.xml") match {
            case f if (f.exists()) =>

                //-- Build model
                var project = Project(f.toURI().toURL())
                
                //-- Resolve dependencies for classpath
                //this.artifactsResolver.resolveDependencies(project.groupId, project.artifactId, project.version,false)
                /*var cp = this.artifactsResolver.resolveDependenciesToClasspath(project.groupId, project.artifactId, project.version,true)

                cp*/
                List[ArtifactResult]()
            case _ =>
                List[ArtifactResult]()
                //URLClassLoader.newInstance(Array(runtimeCompiler.compilerOutput.toURI().toURL()), this.classloader.getParent)
        }
        
        //-- Create CL
        var newClassloader = URLClassLoader.newInstance((this.artifactsResolver.dependenciesToClassPathURLS(this.dependencies) :+ runtimeCompiler.compilerOutput.toURI().toURL()).toArray, this.classloader.getParent)
       
        //-- Replace
        this.classloader = null
        sys.runtime.gc
        this.classloader = newClassloader
        
        //-- Update in compiler
        this.runtimeCompiler.updateClassLoader(this.classloader)
        
    }
    
    /**
     * Update internal applications list
     */
    def updateApplications = {

        updateClassLoader
        
      

        // Scan for applications and update internal map
        // --------------------------
        this.scanFor(classOf[AIBApplication]) foreach {
            case (file, appClass) =>

                // Look for equivalent application inside list
                var existingApp = this.applications.find { app => app.getClass.getCanonicalName == appClass.getCanonicalName }

                // Create new app
                //--------------

                //-- Prepare application classloader
                var appClassloader = URLClassLoader.newInstance(Array[URL](), this.classloader)
                //var appClassloader = this.classloader

                //-- Transfer previous bus to new classloader 
                //aib.transferBus(app.classloader,appClassloader)

                //-- Recreate class with new classloader
                Thread.currentThread().setContextClassLoader(appClassloader)
                var newapp = appClass.newInstance().asInstanceOf[AIBApplication]
                newapp.classloader = appClassloader
                newapp.location = this.location
                newapp.wrapper = this

                //newapp.startScheduler(appClassloader)
                
                // FIXME: Init app
                newapp
                //newapp.appInit()

                // Copy important informations from existing and remove exitings from list
                //--------------------
                existingApp match {
                    case Some(eApp) =>
                        
                      // FIXME Stop existing
                       // try {eApp.appStop()} catch { case e: Throwable => }
                        
                        newapp.configuration = eApp.configuration

                        this.applications = this.applications.filter(_ != eApp)
                    case None =>
                }

                // Update List
                //------------------
                this.applications = this.applications :+ newapp
        }
        sys.runtime.gc
        sys.runtime.gc

    }

    /**
     * Scan for Specific types in the output
     */
    def scanFor[T](baseClass: Class[T]): List[(File, Class[T])] = {

        // Prepare result
        var res = List[(File, Class[T])]()

        // Walk output
        var waltkStream = Files.walk(this.runtimeCompiler.compilerOutputPath)
        var it = waltkStream.iterator()
        while (it.hasNext()) {
            var path = it.next()

            // Get Full path
            var relativePath = this.runtimeCompiler.compilerOutputPath.relativize(path)
            var fullPath = this.runtimeCompiler.compilerOutputPath.resolve(relativePath)

            // Find Top Classes
            fullPath match {
                case p if (p.toFile().getName.matches("""[^$]*\.class""")) =>

                    logFine(s"*** Found Class: $relativePath")

                    // Load 
                    var cl = this.classloader.loadClass(relativePath.toString.replace("\\","/").replace("/", ".").replace(".class", ""))
                    if (baseClass.isAssignableFrom(cl)) {
                        res = res :+ (fullPath.toFile(), cl.asInstanceOf[Class[T]])
                    }

                case p =>
                // println(s"*** Found in output : $p")
            }

        }

        res

    }

    // Start: Start all application in their own threads
    //------------------
    /*  def findApplications = {

        location.listFiles().filter { f => f.getName.endsWith("app.scala") }.foreach {
            appClassFile =>

                // Read and add important imports
                //---------------
                println(s"Found application main class: " + appClassFile)
                var classContent = scala.io.Source.fromFile(appClassFile, "UTF-8").mkString

                classContent = classContent.replaceAll("(class|object)", "import " + classOf[AIBApplication].getPackage.getName + "._\n$0")

                /*  classContent = s"""
import ${classOf[Application].getPackage.getName}._

$classContent
"""*/
                //println(s"Compiling: " + classContent)

                // Compile
                //--------------
                //compiler.compile(classContent)
                //compiler.imain.compile(classContent)

                // Find Type 
                //---------------------
                var p = """(?s)(?:package\s+([\w\.]+).*)?(class|object)\s+([\w_-]+)\s+extends.+""".r
                var rType = p.findFirstMatchIn(classContent) match {
                    case Some(m) if (m.group(1) != null) => Some(m.group(1) + "." + m.group(3), m.group(2))
                    case Some(m) => Some(m.group(3), m.group(2))
                    case None =>
                        println(s"Could not find Type name")
                        None
                }

                // var rType = p.replaceAllIn(classContent, "$1$2")

                //println(s"Found type: $rType")

                rType match {
                    case Some((typeName, objectClass)) =>
                        println(s"Foung new type: $typeName")

                        objectClass match {
                            case "class" =>
                                println(s"-> Class")

                                // Try to load
                                try {
                                    var appClass = classloader.loadClass(typeName)

                                    // Check type 
                                    classOf[AIBApplication].isAssignableFrom(appClass) match {
                                        case true =>
                                            println(s"Application class is of the correct type")

                                            // Record
                                            //--------------
                                            var application = appClass.newInstance().asInstanceOf[AIBApplication]
                                            application.location = location

                                            // Record
                                            //---------------
                                            applications = applications :+ application

                                        case false =>
                                            println(s"Application class is not of the correct type")
                                    }
                                } catch {
                                    case e: Throwable =>
                                }

                            case "object" =>
                                println(s"-> Object")

                            // Get value
                            /* compiler.imain.valueOfTerm(typeName) match {
                  case None =>

                    throw new RuntimeException("Nothing compiled: " + compiler.interpreterOutput.getBuffer().toString())

                  case Some(wwwview) =>
                  //wwwview.asInstanceOf[WWWView]
                }*/
                            //var objectApp = compiler.imain.eval(typeName)
                            /*compiler.imain.definedTypes.foreach {
                  x => println(s"Term: $x")
                }*/
                        }

                    case _ =>
                        logWarn(s"Compiled $appClassFile did not yield a new type ")
                }

            // End of init -> init all apps
            //-------------------
            //applications.foreach(_.appInit)

            // Gradle Init, get dependencies
            //---------------------------

            //gradleConnector.connect()

        }

    }*/

    /* def start = {

        // Add new paths to compiler
        //----------------
        var generatedSources = new File(location, "target" + File.separator + "generated-sources")
        println(s"Testing generatedSources: " + generatedSources.getAbsolutePath)
        generatedSources.exists match {
            case true =>
                runtimeCompiler.addSourceFolder(generatedSources)
            case _ =>
        }

        // Recreate Classloader
        // - Get AIB Bus for current classloader
        // - Recreate classloader and transfer bus
        // - 

        // Re-Create classloader 
        this.classloader = null
        //sys.runtime.gc
        this.classloader = URLClassLoader.newInstance(Array(runtimeCompiler.compilerOutput.toURI().toURL()), Thread.currentThread().getContextClassLoader)

        // Re-Create applications
        var normalClassloader = Thread.currentThread().getContextClassLoader
        try {
            applications = applications.map {
                app =>

                    //-- Prepare application classloader
                    // var appClassloader = URLClassLoader.newInstance(Array[URL](), this.classloader)
                    var appClassloader = this.classloader

                    //-- Transfer previous bus to new classloader 
                    //aib.transferBus(app.classloader,appClassloader)

                    //-- Recreate class with new classloader
                    Thread.currentThread().setContextClassLoader(appClassloader)
                    var newapp = this.classloader.loadClass(app.getClass.getCanonicalName).newInstance().asInstanceOf[AIBApplication]
                    newapp.classloader = appClassloader
                    newapp.location = this.location
                    newapp.wrapper = this
                    newapp
            }
        } finally {
            Thread.currentThread.setContextClassLoader(normalClassloader)
        }
        sys.runtime.gc
        sys.runtime.gc

        //println(s"Applications:")
        // For all applications: Create thread and start it
        applications.foreach {
            application =>

                // println(s"Application: $application")

                // Create Thread
                //--------------------
                var appThread = createThread {

                    // Create application
                    //var app = this.classloader.loadClass(name)

                    // Create Bus 
                    /*application.bus match {
            case null => application.bus = aib.getBus
            case _ =>
          }*/

                    // Init 
                    application.appInit

                    // Start
                    //application.appStart
                }

                // Set classloader and start
                //-----------
                //application.classloader = URLClassLoader.newInstance(Array[URL](), this.classloader)
                application.thread = appThread
                appThread.setContextClassLoader(application.classloader)
                appThread.start()

        }

    }
*/

    // Stop: Kill all
    //------------------------
    def stop = {

        applications.filter { a => a.thread != null }.foreach {
            application =>

                // Call on stop
                //----------
                println(s"Stopping application:")
                
                // FIXME Stop
                //application.appStop()

                // Join thread, kill it after timeout
                //----------
                application.thread.join(2000)
                if (application.thread.isAlive) {
                    println(s"Force application kill")
                    application.thread.interrupt
                }
        }
    }

    // Restart
    //-----------------
    def restart = {
        //stop
        //start
    }
}