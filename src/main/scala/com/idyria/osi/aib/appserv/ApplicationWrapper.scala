package com.idyria.osi.aib.appserv

import java.io.File
import java.net.URL
import java.net.URLClassLoader

import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.aib.core.compiler.EmbeddedCompiler
import com.idyria.osi.tea.logging.TLogSource
import com.idyria.osi.tea.thread.ThreadLanguage

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

  // Internal Classloader
  //----------------------------
  var classloader = URLClassLoader.newInstance(Array(runtimeCompiler.compilerOutput.toURI().toURL()), Thread.currentThread().getContextClassLoader)

  // Applications 
  //-------------------
  var applications = List[AIBApplication]()

  // Gradle Connector
  //-------------------------------
  //var gradleConnector: GradleConnector = null

  // Init: Find and create Applications
  //--------------
  def init = {

    // Launch folder watcher compiler
    //--------------
    
    // Add new paths to compiler
    var generatedSources = new File(runtimeCompiler.compilerOutput.getParentFile,"generated-sources"+File.separator+"scala")
    println(s"Testing generatedSources: "+generatedSources.getAbsolutePath)
    generatedSources.exists match {
      case true =>
        runtimeCompiler.addSourceFolder(generatedSources)
      case _ => 
    }
    runtimeCompiler.start

    // Prepare gradle
    //-------------
    /*gradleConnector = GradleConnector.newConnector()
      .forProjectDirectory(location)*/

    // Create Default application, or use provided class
    //--------------------------
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
          case Some(m) if (m.group(1)!=null) => Some(m.group(1) + "." + m.group(3), m.group(2))
          case Some(m)  => Some( m.group(3), m.group(2))
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
  }

  // Start: Start all application in their own threads
  //------------------
  def start = {

    // Add new paths to compiler
    //----------------
    var generatedSources = new File(location,"target"+File.separator+"generated-sources")
    println(s"Testing generatedSources: "+generatedSources.getAbsolutePath)
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
          var appClassloader =  this.classloader

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
          application.appStart
        }

        // Set classloader and start
        //-----------
        //application.classloader = URLClassLoader.newInstance(Array[URL](), this.classloader)
        application.thread = appThread
        appThread.setContextClassLoader(application.classloader)
        appThread.start()

    }

  }

  // Stop: Kill all
  //------------------------
  def stop = {

    applications.filter { a => a.thread != null }.foreach {
      application =>

        // Call on stop
        //----------
        println(s"Stopping application:")
        application.appStop

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
    stop
    start
  }
}