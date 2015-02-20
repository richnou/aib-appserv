package com.idyria.osi.aib.appserv

import java.io.File
import java.net.URL
import java.net.URLClassLoader
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.resolution.ArtifactResult
import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import com.idyria.osi.aib.appserv.dependencies.EclipseWorkspaceReader
import com.idyria.osi.aib.appserv.dependencies.EclipseWorkspaceReader
import com.idyria.osi.aib.appserv.prebuild.PrebuildLoader
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.aib.core.compiler.FileCompileError
import com.idyria.osi.ooxoo.core.buffers.extras.listeners.DataChangeInterceptorTrait
import com.idyria.osi.ooxoo.core.buffers.structural.io.sax.StAXIOBuffer
import com.idyria.osi.ooxoo.model.out.markdown.MDProducer
import com.idyria.osi.ooxoo.model.out.scala.ScalaProducer
import com.idyria.osi.tea.thread.PhaserUtils
import com.idyria.osi.vui.core.VBuilder
import com.idyria.osi.vui.javafx.JavaFXRun
import com.idyria.osi.vui.lib.gridbuilder.GridBuilder
import org.eclipse.aether.artifact.DefaultArtifact

/**
 * Global Serv will load some applications from locations and produce some informations
 */

class AIBAppServ extends GridBuilder with DataChangeInterceptorTrait with PhaserUtils {

    //-- Configurations
    var applicationConfig = Config()

    //-- Application Wrappers for all locations 
    var applicationWrappers = List[ApplicationWrapper]()
    
    //-- List of prebuild applications
    var prebuildApplications = List[AIBApplication]();

    //-- local aib bus 
    var aib: com.idyria.osi.aib.core.bus.aib = _

    //-- Common artifact resolver
    var artifactsResolver = new AetherResolver

    def start = {
        println(s"Welcome to AIB App serv")

        //-- Vars 
        var location = new File("").getAbsoluteFile.getCanonicalFile // Location
        var configFile = new File(location, "appserv.xml")
        this.aib = com.idyria.osi.aib.core.bus.aib.getBus

        // Loading config file
        //---------------------- 

        // Defaults:
        applicationConfig.gui = true

        configFile.exists() match {
            case true =>
                applicationConfig.appendBuffer(StAXIOBuffer(configFile.toURI.toURL()))
                applicationConfig.lastBuffer.streamIn
            case false =>
        }

        // Load first applications
        //----------------
        updateApplicationWrappers

        // Starting GUI
        //-----------------------

        //sys.exit()
        if (applicationConfig.gui.data) {

            println(s"Starting in GUI Mode")

            JavaFXRun.onJavaFX {

                frame {
                    f =>
                        f title ("AIB Applications")
                        f.size(1024, 768)
                        //f.size(100, 100)

                        f <= grid {

                            // Top: Global Menu
                            //--------------
                            "GlobalMenu" row subgrid {

                            }

                            // Main View: Left list, Right: UI panels
                            var tabPanel = tabpane {
                                tp =>

                            }
                            "Main View" row tabPanel(expand)

                            // Update UI on application
                            //--------------------------
                            
                            //-- Normal application
                            aib.registerClosure {
                                app : AIBApplication => 
                                    onUIThread {
                                        println(s"Added application wrapper")
                                        //tabPanel <=// ApplicationUI(app)
                                    }
                            }
                            
                            aib.registerClosure {
                                appWrapper: ApplicationWrapper =>
                                    onUIThread {
                                        println(s"Added application wrapper")
                                        tabPanel <= grid {
                                            currentGrid.name = appWrapper.location.getName

                                            // Components
                                            //-----------------
                                            var appTable = table[AIBApplication] {
                                                t =>
                                            }

                                            var dependenciesTable = table[ArtifactResult] {
                                                t =>
                                            }

                                            // Monitoring
                                            //--------------------
                                            phaserTaskOnArrive(appWrapper.runtimeCompiler.updated) {
                                                println(s"Updated")

                                                //-- Update applications
                                                //--------------------
                                                appWrapper.updateApplications
                                                // Update UIS
                                                onUIThread {
                                                    appTable.clear
                                                    appWrapper.applications.foreach(appTable.add(_))
                                                }

                                                println(s"Available apps: ${appWrapper.applications}")

                                                //-- Start the ones which are supposed to be started
                                                appWrapper.applications.filter(_.configuration.autoRestart.data).foreach {
                                                    app =>
                                                        app.appStart()
                                                }

                                                //-- Update dependencies
                                                onUIThread {
                                                    dependenciesTable.clear
                                                    appWrapper.dependencies.foreach(dependenciesTable.add(_))

                                                }
                                            }

                                            // Prepare components
                                            //-------------------------------
                                            "Control" row {

                                                var refresh = button("Refresh apps") {
                                                    b =>
                                                        b.onClickFork {

                                                            //appWrapper.restart
                                                            appWrapper.updateApplications

                                                            // Update UIS
                                                            onUIThread {

                                                                appTable.clear
                                                                appWrapper.applications.foreach(appTable.add(_))
                                                                println(s"Available apps: ${appWrapper.applications}")
                                                                appWrapper.applications.foreach {
                                                                    // case app if (app.)
                                                                    app =>
                                                                        println(s"Full Restart: " + app.configuration.autoRestart)

                                                                }
                                                            }

                                                        }
                                                }

                                                // State 
                                                var state = label("State:")
                                                appWrapper.runtimeCompiler.cleanStatus.data.booleanValue() match {
                                                    case true => state.setText("State: Clean")
                                                    case false => state.setText("State: Not Clean")
                                                }
                                                appWrapper.runtimeCompiler.cleanStatus - interceptData { b: Boolean =>
                                                    b match {
                                                        case true => state.setText("State: Clean")
                                                        case false => state.setText("State: Not Clean")
                                                    }

                                                }

                                                state | refresh
                                            }

                                            "Dependencies" row {

                                                titledPane("Dependencies") {
                                                    p =>
                                                        p(spread, expand)
                                                        p <= dependenciesTable {
                                                            t =>
                                                                t.column("Group") {
                                                                    c =>
                                                                        c.content {
                                                                            art => art.getArtifact.getGroupId
                                                                        }
                                                                }

                                                                t.column("Artifact") {
                                                                    c =>
                                                                        c.content {
                                                                            art => art.getArtifact.getArtifactId
                                                                        }
                                                                }

                                                                t.column("Version") {
                                                                    c =>
                                                                        c.content {
                                                                            art => art.getArtifact.getVersion
                                                                        }
                                                                }

                                                                t.column("File") {
                                                                    c =>
                                                                        c.content {
                                                                            art => art.getArtifact.getFile
                                                                        }
                                                                }

                                                        }
                                                }

                                            }

                                            "Applications" row {

                                                //-- Auto restart all

                                                //-- Application
                                                appTable {
                                                    t =>
                                                        t.name = "apps"
                                                        t.column("Application") {
                                                            c =>
                                                                c.content {
                                                                    app => app.name
                                                                }

                                                        }
                                                        t.column("Auto-Restart") {
                                                            c =>

                                                                c.content {
                                                                    app =>
                                                                        // Create Check box, and bind with app config
                                                                        checkBox("") {
                                                                            b =>
                                                                                b.checkedProperty.set(app.configuration.autoRestart.toBool)
                                                                                //b.setEnabled(app.configuration.autoRestart.toBool)
                                                                                app.configuration.autoRestart - b.checkedProperty
                                                                        }
                                                                }
                                                        }
                                                        t.column("Actions") {
                                                            c =>
                                                                c.content {
                                                                    app =>
                                                                        panel {
                                                                            p =>
                                                                                p.layout = hbox
                                                                                p <= button("Init") {
                                                                                    b => b.onClickFork(app.appInit())
                                                                                }
                                                                                p <= button("Start") {
                                                                                    b =>
                                                                                        b.onClickFork(app.appStart())
                                                                                }
                                                                                p <= button("Stop") {
                                                                                    b => b.onClickFork(app.appStop())
                                                                                }
                                                                        }

                                                                    /*button("Init") {
                                                                            b =>
                                                                        } */
                                                                }

                                                        }

                                                        //appWrapper.findApplications
                                                        appWrapper.applications.foreach(t.add(_))
                                                }(spread, expandWidth)

                                            }

                                            "Problems" row subgrid {

                                                var errorsArea = textArea
                                                "-" row {
                                                    table[FileCompileError] {
                                                        t =>
                                                            t.name = "errors"
                                                            t.column("File") {
                                                                c =>
                                                                    c.content {
                                                                        error =>
                                                                            error.message
                                                                    }

                                                            }

                                                            appWrapper.runtimeCompiler.pendingBatches.map { b => b.compileErrors }.flatten.foreach {
                                                                error => t.add(error)
                                                            }
                                                    }(expand)
                                                }

                                                "-" row errorsArea

                                            }

                                            "Text output" row {
                                                textArea(expand)

                                            }
                                        }
                                    }

                            }

                        }

                        // On UI Shutdown, close all
                        //----------
                        f.onClose {
                            prebuildApplications.foreach {
                                app => 
                                    println(s"Shutting down")
                                    app.appStop(true)
                                    app.appShutdown(true)
                            }
                        }
                        
                        f.show

                }

            }
        }

        // Init/Start app wrappers 
        //---------------------
        println(s"Done start ui")
        this.applicationWrappers.foreach { appWrapper =>

            appWrapper.artifactsResolver = artifactsResolver
            appWrapper.init;
            aib.send(appWrapper)
        }

    }

    /**
     * Go through config, and find application wrappers
     */
    def updateApplicationWrappers = {

        applicationConfig.applications.foreach {

            // File
            //--------------
            case configApp if (configApp.path != null) =>

                new File(configApp.path) match {
                    //-- Invalid paths 
                    case f if (!f.exists()) =>

                        logWarn(s"Application path: $f does not exist")

                    //-- Not folders
                    case f if (!f.isDirectory()) =>

                        logWarn(s"Application path: $f is not a directory")

                    // -- Ok, but already in list 
                    case f if (this.applicationWrappers.find { w => w.location.equals(f) } != None) =>

                    //-- Ok and not in list
                    case f =>
                        var wrapper = new ApplicationWrapper(f)
                        this.applicationWrappers = this.applicationWrappers :+ wrapper
                    //wrapper.init
                }

            // Artifact
            //-------------------------------
            case appConfig if(appConfig.artifact!=null && appConfig.artifact.id!=null) =>
                
                // Resolve
                //loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), appConfig.artifact.applicationClasses.map(_.data).toList)
                loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), List(appConfig.artifact.applicationClass.data)) 
                
            case _ => 
        }

        

    }
    
    // Single applications using Prebuild loader
    //---------------
    
    def loadPrebuildApplication(artifact: Artifact,appClasses:List[String]) = {
        
        //-- Create Loader with CP
        println(s"Create preloader with : "+appClasses)
        var loader = new PrebuildLoader(this.artifactsResolver)
        loader.mainArtifact = Some(artifact) 
        prebuildApplications = prebuildApplications :+ loader
        appClasses.foreach(loader.loadApplication(_))
        loader.appInit(false)
        loader.appStart(false)
        
    }
    
    def loadPrebuildApplication(classpath: List[URL],appClasses:List[String]) = {
        
        //-- Create Loader with CP
        var loader = new PrebuildLoader(this.artifactsResolver)
        loader.classloader = URLClassLoader.newInstance(classpath.toArray)
        prebuildApplications = prebuildApplications :+ loader
        
        //-- Start application
        appClasses.foreach(loader.loadApplication(_))
        loader.appInit(false)
        loader.appStart(false)
        
        
        
    }

}

object AIBAppServ extends App with VBuilder with GridBuilder {

    println(s"App serv test")

    //TLog.setLevel(classOf[aib], TLog.Level.FULL)
    // TLog.setLevel(classOf[AetherResolver], TLog.Level.FULL)
    // TLog.setLevel(classOf[EclipseWorkspaceReader], TLog.Level.FULL)

    // Create app serv 
    var appServ = new AIBAppServ

    //-- Prepare test application
    //var appWrapper = new ApplicationWrapper(new File("src/examples/gui"))
    //appWrapper.init

    //-- Args processing
    args.zipWithIndex.collect {
        case (arg, index) if (arg == "--application") => index
    }.foreach {
        index =>
            //appServ.applicationConfig.applications += args(index + 1)
    }

    // Register Eclipse
    //---------------------
    appServ.artifactsResolver.session.setWorkspaceReader(new EclipseWorkspaceReader(new File("/home/rleys/eclipse-workspaces/test-optimized/")))

    appServ.start

    /*var loc = new File("src/examples/gui")
  
  var applicationWrapper = new ApplicationWrapper(loc)
  applicationWrapper.init
  applicationWrapper.start*/

}