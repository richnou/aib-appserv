package com.idyria.osi.aib.appserv

import java.io.File

import com.idyria.osi.ooxoo.core.buffers.structural.io.sax.StAXIOBuffer
import com.idyria.osi.ooxoo.model.out.markdown.MDProducer
import com.idyria.osi.ooxoo.model.out.scala.ScalaProducer
import com.idyria.osi.vui.core.VBuilder
import com.idyria.osi.vui.javafx.JavaFXRun
import com.idyria.osi.vui.lib.gridbuilder.GridBuilder

/**
 * Global Serv will load some applications from locations and produce some informations
 */

class AIBAppServ extends GridBuilder {

  //-- Configurations
  var applicationConfig = Config()

  //-- Application Wrappers for all locations 
  var applicationWrappers = List[ApplicationWrapper]()

  //-- local aib bus 
  var aib = com.idyria.osi.aib.core.bus.aib.getBus

  def start = {
    println(s"Welcome to AIB App serv")

    //-- Vars 
    var location = new File("").getAbsoluteFile.getCanonicalFile // Location
    var configFile = new File(location, "appserv.xml")

    // Loading config file
    //---------------------- 

    // Defaults:
    applicationConfig.gui = false

    configFile.exists() match {
      case true =>
        applicationConfig.appendBuffer(StAXIOBuffer(configFile.toURI.toURL()))
        applicationConfig.lastBuffer.streamIn
      case false =>
    }

    // Load first applications
    //----------------
    updateApplicationWrappers

    // Init/Start app wrappers 
    //---------------------
    this.applicationWrappers.foreach { app => app.init; app.start }

    // Starting GUI
    //-----------------------
    println(s"Starting in GUI Mode")

    //sys.exit()
    if (applicationConfig.gui.data) {

      JavaFXRun.onJavaFX {

        frame {
          f =>
            f title ("AIB Applications")
            //f.size(1024, 768)
            f.size(100, 100)

            f <= grid {

              // Top: Global Menu
              //--------------
              "GlobalMenu" row subgrid {

              }

              // Main View: Left list, Right: UI panels
              "Main View" row subgrid {

                var leftMenu = panel {
                  p =>
                    p layout = vbox

                }

                var right = subgrid {

                  // Labels
                  "-" row { label("location") | (label("actions") using (expandWidth, spread)) }

                  this.applicationWrappers.foreach {
                    appWrapper =>

                      //-- Prepare UI elements
                      var initButton = button("Init") {
                        b =>
                          b.onClickFork {
                            appWrapper.init
                          }
                      }
                      var startButton = button("Start")
                      startButton.onClickFork {
                        appWrapper.start
                      }
                      var stopButton = button("Stop")
                      stopButton.onClickFork {
                        appWrapper.stop
                      }
                      var restartButton = button("Restart")
                      restartButton.onClickFork {
                        appWrapper.restart
                      }

                      //-- UI 
                      "-" row {
                        label(appWrapper.location.getPath) | initButton | startButton | restartButton | stopButton
                      }

                  }

                  // Add for each application
                  /* applicationConfig.applications.foreach {
                  appLocation =>

                    //-- Prepare application
                    var appWrapper = new ApplicationWrapper(new File(appLocation))

                    

                }*/

                }
                "-" row {
                  right
                }
              }

            }

            f.show

        }

      }
    }

  }

  /**
   * Go through config, and find application wrappers
   */
  def updateApplicationWrappers = {
 
    applicationConfig.applications.map(new File(_).getAbsoluteFile.getCanonicalFile).foreach {

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
        this.applicationWrappers = this.applicationWrappers :+ new ApplicationWrapper(f)

    }

  }

}

object AIBAppServ extends App with VBuilder with GridBuilder {

  println(s"App serv test")

  // Create app serv 
  var appServ = new AIBAppServ

  //-- Prepare test application
  var appWrapper = new ApplicationWrapper(new File("src/examples/gui"))
  appWrapper.init

  /*var loc = new File("src/examples/gui")
  
  var applicationWrapper = new ApplicationWrapper(loc)
  applicationWrapper.init
  applicationWrapper.start*/

}