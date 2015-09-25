package com.idyria.osi.aib.appserv

import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import java.net.URL
import com.idyria.osi.vui.lib.gridbuilder.GridBuilder
import com.idyria.osi.vui.javafx.JavaFXRun
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.vui.core.VBuilder
import com.idyria.osi.aib.appserv.prebuild.PrebuildLoader
import com.idyria.osi.ooxoo.core.buffers.extras.listeners.DataChangeInterceptorTrait
import com.idyria.osi.tea.thread.PhaserUtils
import com.idyria.osi.tea.logging.TLog
import org.apache.maven.artifact.Artifact
import java.io.File
import org.eclipse.aether.artifact.DefaultArtifact
import eu.hansolo.enzo.notification.Notification.Notifier
import javafx.geometry.Pos
import javafx.stage.Stage
import com.idyria.osi.aib.core.compiler.FileCompileError
import org.eclipse.aether.resolution.ArtifactResult
import com.idyria.osi.aib.appserv.dependencies.EclipseWorkspaceReader
import com.idyria.osi.aib.appserv.model.ConfigApplication
import com.idyria.osi.aib.appserv.model.Config
import javafx.scene.control.ProgressIndicator
import com.idyria.osi.vui.javafx.JavaFXNodeDelegate
import org.controlsfx.control.decoration.StyleClassDecoration
import org.controlsfx.control.decoration.Decorator
import javafx.scene.control.TextField
import com.idyria.osi.aib.appserv.lifecycle.LFCButtons
import com.idyria.osi.aib.appserv.apps.GUIApplication

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
    // var location = new File("").getAbsoluteFile.getCanonicalFile // Location
    //var configFile = new File(location, "appserv.xml")
    this.aib = com.idyria.osi.aib.core.bus.aib.getBus

    // Loading config file
    //---------------------- 

    // Defaults:
    applicationConfig.gui = true

    /*configFile.exists() match {
      case true =>
        applicationConfig.fromFile(configFile)
      case false =>
        applicationConfig.staxPreviousFile = Some(configFile)
    }*/

    // Application list configuation
    // - try to instanciate the applications 
    //-----------------------
    aib.registerClosure {
      config: ConfigApplication =>

        // Record 
        applicationConfig.applications += config
        applicationConfig.@->("sync")

        // Bootstrap 
        config.appserver = AIBAppServ.this
        config.bootstrap
    }

    // Application Deploy
    //---------------------
    aib.registerClosure {
      application: AIBApplication =>

        println(s"Got AIBApplication")

        // Synchronise state 
        application.syncInitialState
    }

    // Setup Workspace
    //----------------------
    if (applicationConfig.workspace != null) {
      var workspaceLocation = new File(applicationConfig.workspace.toString())
      println(s"WS Loc: $workspaceLocation")

      artifactsResolver.session.setWorkspaceReader(new EclipseWorkspaceReader(workspaceLocation))

    }

    // Load first applications
    //----------------
    //updateApplicationWrappers

    // Starting GUI /home/rleys/git/adl/h2dl-indesign/pom.xml
    //-----------------------

    //sys.exit()
    if (applicationConfig.gui.data) {

      println(s"Starting in GUI Mode")

      JavaFXRun.onJavaFX {
        try {

          frame {
            f =>
              f title ("AIB Applications")
              f.size(1024, 768)
              //f.size(100, 100)

              onVUIError {
                case e: Throwable =>
                  e.printStackTrace()
                  Notifier.setPopupLocation(f.base.asInstanceOf[Stage], Pos.BOTTOM_RIGHT)
                  Notifier.INSTANCE.notifyError(e.getClass.getSimpleName, e.getMessage)
              }

              f <= grid {

                var mainInfoTable = table[ConfigApplication]

                // Top: Global Menu
                //--------------
                "GlobalMenu" row {

                  subgrid {

                    "Workspace" row {
                      var l = label("Workspace:")
                      var ti = textInput {
                        input =>
                          input(expand)
                          input.setText(applicationConfig.workspace)

                          input.onEnterKey {
                            new File(input.getText.trim) match {
                              case f if (f.exists()) =>
                                artifactsResolver.session.setWorkspaceReader(new EclipseWorkspaceReader(f))
                                applicationConfig.workspace = f.getAbsolutePath
                                applicationConfig.@->("sync")
                              case _ =>
                            }
                          }

                      }
                      Decorator.addDecoration(ti.base.asInstanceOf[TextField], new StyleClassDecoration("warning"))
                      /*var pi =  new ProgressIndicator(0);
                      pi.sett*/

                      l | ti
                    }

                    "Applications" row {

                      mainInfoTable {
                        t =>
                          t(expand, spread)
                          t.column("ID") {
                            c =>
                              c.content { app => "-" }
                          }
                          t.column("Path") {
                            c =>
                              c.content { app => app.path }
                          }
                          t.column("Artifact") {
                            c =>
                              c.content { app => app.artifact.id }
                          }

                          applicationConfig.applications.foreach(t.add(_))

                          aib.registerClosure { ca: ConfigApplication => t.add(ca) }
                      }
                    }
                    "Add" row {
                      label("Add application:") | textInput {
                        ti =>
                          ti(expandWidth)
                          ti.onEnterKey {

                            // Try to find the format provided
                            //--------------
                            ti.getText.trim() match {

                              //-- Strange

                              //-- Maven POM
                              case fs if (new File(fs).isDirectory()) =>

                                println(s"Found Application")
                                var ca = new ConfigApplication()
                                ca.path = fs

                                aib.send(ca)

                              case fs => throw new RuntimeException(s"Application String: $fs does not match any supported format")
                            }

                          }
                      }

                    }

                  }
                }
                // EOF Global Top

                // Main View: Left list, Right: UI panels
                var tabPanel = tabpane {
                  tp =>
                    tp(expand)

                }
                "Main View" row tabPanel(expand)

                //----------------------------------------------------
                // Update UI on  Standard application
                //----------------------------------------------------

                //-- Normal application
                aib.registerClosure {
                  app: AIBApplication =>

                    onUIThread {
                      println(s"Added application ")
                      //tabPanel <=// ApplicationUI(app)

                      //-- Comps
                      var childAppsTable = table[AIBApplication]

                      //-- Monitor
                      app.updated - interceptData {
                        b: Boolean =>

                          childAppsTable.clear
                          app.childApplications.foreach(childAppsTable.add(_))

                      }

                      //-- UI
                      //tabPanel(expand)
                      var addedTab = (tabPanel <= grid {

                        currentGrid.name = app.name.toString()
                        currentGrid(expand)
                        currentGrid.group(expand)

                        // Local Application State
                        //-----------------------------
                        "control" row {

                          var l = label(s"Current State: " + app.currentState.get)
                          var ctrl = new LFCButtons(app, AIBApplication)

                          app.on("state.updated") {
                            l.setText(s"Current State: " + app.currentState.get)
                          }

                          l(expandWidth) | ctrl

                        }

                        // Custom UI ? 
                        //-------------------
                        "custom" row {
                         app.childApplications.collectFirst {
                            case app: GUIApplication =>
                              app.ui

                          } match {
                            case Some(ui) => 
                              ui(expandWidth,spread)
                              ui
                            case None =>
                          }
                          
                        }

                        // Table with List for Child Applications
                        //-------------------
                        "table" row {
                          childAppsTable {
                            t =>
                              t(expand, spread, expandWidth)
                              t.column("Application") {
                                c =>
                                  c.content {
                                    app => app.name
                                  }

                              }
                              
                               t.column("Classloader") {
                                c =>
                                  c.content {
                                    app =>
                                      s"${app.classloader.getClass.getSimpleName} (${app.classloader.hashCode()})"
                                  }
                               }

                              t.column("Actions") {
                                c =>
                                  c.content {
                                    app =>
                                      panel {
                                        p =>
                                          p.layout = hbox

                                          p <= new LFCButtons(app, AIBApplication)

                                        /*p <= button("Init") {
                                            b =>
                                              //b.onClickFork(app.appInit())
                                          }
                                          p <= button("Start") {
                                            b =>
                                              //b.onClickFork(app.appStart())
                                          }
                                          p <= button("Stop") {
                                            b => 
                                              //b.onClickFork(app.appStop())
                                          }*/
                                      }

                                    /*button("Init") {
                                                                            b =>
                                                                        } */
                                  }

                              }

                              // Add applications
                              app.childApplications.foreach(t.add(_))
                          }
                        }

                      })
                      //tab(expand)
                      // EOF Tab Pane

                    }
                  // EOF ONUITHREAD
                }
                // EOF Closure

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
                            //app.appStart()
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
                                            b =>
                                            //b.onClickFork(app.appInit())
                                          }
                                          p <= button("Start") {
                                            b =>
                                            //b.onClickFork(app.appStart())
                                          }
                                          p <= button("Stop") {
                                            b =>
                                            //b.onClickFork(app.appStop())
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
                  //app.appStop(true)
                  //app.appShutdown(true)
                }
              }
              // EOF Frame 
              f.show

          }

        } catch {
          case e: Throwable => println(s"Caught error")
        }

      }
      // EOF JFX

    }

    // Init/Start app wrappers 
    //---------------------

    //this.updateApplicationWrappers
    println(s"Done start ui")

    this.applicationConfig.applications.toParArray.foreach {
      appConfig =>

        appConfig.bootstrap
    }

    /*this.applicationWrappers.foreach { appWrapper =>

      appWrapper.artifactsResolver = artifactsResolver
      appWrapper.init;
      aib.send(appWrapper)
    }

    this.prebuildApplications.foreach {
      app => aib.send(app)
    }*/

  }

  /* /**
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
      case appConfig if (appConfig.artifact != null && appConfig.artifact.id != null) =>

        // Resolve
        //loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), appConfig.artifact.applicationClasses.map(_.data).toList)
        loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), List(appConfig.artifact.applicationClass.data))

      case _ =>
    }

  }

  // Single applications using Prebuild loader
  //---------------

  def loadPrebuildApplication(artifact: Artifact, appClasses: List[String]) = {

    //-- Create Loader with CP
    println(s"Create preloader with : " + appClasses)
    var loader = new PrebuildLoader(this.artifactsResolver)
    loader.mainArtifact = Some(artifact)
    prebuildApplications = prebuildApplications :+ loader
    appClasses.foreach(loader.loadApplication(_))
    loader.appInit(false)
    loader.appStart(false)

  }

  def loadPrebuildApplication(classpath: List[URL], appClasses: List[String]) = {

    //-- Create Loader with CP
    var loader = new PrebuildLoader(this.artifactsResolver)
    loader.classloader = URLClassLoader.newInstance(classpath.toArray)
    prebuildApplications = prebuildApplications :+ loader

    //-- Start application
    appClasses.foreach(loader.loadApplication(_))
    loader.appInit(false)
    loader.appStart(false))

  }*/

}

object AIBAppServ extends App with VBuilder with GridBuilder {

  println(s"App serv test")

  //TLog.setLevel(classOf[AIBAppServ],TLog.Level.FULL)

  TLog.setLevel(classOf[ApplicationWrapper], TLog.Level.FULL)
  TLog.setLevel(classOf[aib], TLog.Level.FULL)
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
  //appServ.artifactsResolver.session.setWorkspaceReader(new EclipseWorkspaceReader(new File("/home/rleys/eclipse-workspaces/test-optimized/")))

  appServ.start

  /*var loc = new File("src/examples/gui")
  
  var applicationWrapper = new ApplicationWrapper(loc)
  applicationWrapper.init
  applicationWrapper.start*/

}