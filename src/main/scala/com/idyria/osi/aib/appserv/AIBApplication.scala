/**
 *
 */
package com.idyria.osi.aib.appserv

import java.io.File
import com.idyria.osi.aib.core.compiler.EmbeddedCompiler
import java.net.URLClassLoader
import java.util.concurrent.Semaphore
import com.idyria.osi.aib.core.bus.aib

/**
 *
 * Application features:
 *
 *  - Location
 *  - Own Classloader
 *  - own aib bus
 *  - Pom Model to load extra stuff
 *
 * @author zm4632
 *
 */
trait AIBApplication {

  /**
   * base location
   */
  var location: File = _

  /**
   * A name: Default to class name
   */
  var name = getClass.getSimpleName

  /**
   *  The main thread
   */
  var thread: Thread = null

  /**
   * The Thread classloader
   */
  var classloader: URLClassLoader = null

  /**
   * A Stop signal to healp application nicely close
   */
  var stopSignal = new Semaphore(0)

  var wrapper : ApplicationWrapper = null
  
  /**
   * An aib bus for the application
   * @warning Only set during application startup, with the correct classloader
   */
  //var aib: aib = new com.idyria.osi.aib.core.bus.aib()
  
  // Lyfecycle
  //----------------
  var (initClosures,startClosures,stopClosures) = (List[( Unit => Unit)](),List[( Unit => Unit)](),List[( Unit => Unit)]())
  
  //def onInit(cl: => Unit) = initClosures = initClosures :+ { i : Unit =>  cl}
  
  def onInit(cl: => Unit) = {
    
    aib.registerClosure { ev: String =>
      ev match {
        case "init" => cl
        case _ => 
      }
    }
    
  }
  
  /**
   * Checks and loads stuff from location
   */
  def appInit() = {
    
    // DO this first!
    doInit
    
    // Signal 
    aib ! "init"
    
    
    //initClosures.foreach{cl =>  cl()}
  }

  def onStart(cl: => Unit) = {
    
    aib.registerClosure { ev: String =>
      ev match {
        case "start" => cl
        case _ => 
      }
    }
    
  }
  
  /**
   * Startup
   */
  def appStart() = {
    
   

    
    // Call app start 
    //--------------------
    doStart
    
     // Signal 
    aib ! "start"
    
    //startClosures.foreach{cl =>  cl()}
  }

  // Stopping
  //-----------------------
  def onStop(cl: => Unit) = {
    
    aib.registerClosure { ev: String =>
      ev match {
        case "stop" => cl
        case _ => 
      }
    }
    
  }
  
  /**
   * Stop
   */
  def appStop = {

    // call do Stop 
    doStop

    
    // Signal 
    aib ! "stop"

    
  }

  def doInit
  def doStart
  def doStop

}
object AIBApplication {

}