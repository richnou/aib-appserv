/**
 *
 */
package com.idyria.osi.aib.appserv

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory

import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import com.idyria.osi.aib.appserv.lifecycle.LFCDefinition
import com.idyria.osi.aib.appserv.lifecycle.LFCSupport
import com.idyria.osi.aib.appserv.model.ApplicationConfig
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.ooxoo.core.buffers.datatypes.BooleanBuffer
import com.idyria.osi.ooxoo.core.buffers.datatypes.BooleanBuffer.convertBoolToBooleanBuffer

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
trait AIBApplication extends LFCSupport {

  /**
   * base location
   */
  var location: File = _

  /**
   * A name: Default to class name
   */
  var name = getClass.getSimpleName

  /**
   * Configuration
   */
  var configuration = ApplicationConfig()

  /**
   *  The main thread for this app
   */
  var thread: Thread = null

  /**
   * The Thread classloader
   */
  var classloader: ClassLoader = null

  /**
   * A Stop signal to healp application nicely close
   */
  var stopSignal = new Semaphore(0)

  /**
   * Reference to the parent wrapper
   */
  var wrapper: ApplicationWrapper = null

  /**
   * A resolve to resolve dependencies and libraries
   */
  var artifactsResolver = new AetherResolver

  /**
   * Children applications
   */
  var childApplications = List[AIBApplication]()

  /**
   * An aib bus for the application
   * @warning Only set during application startup, with the correct classloader
   */
  var aibBus: aib = _

  // Sync signals for monitoring
  //--------------------

  var updated: BooleanBuffer = false

  // Scheduler
  //----------------

  var executor: ExecutorService = null

  /**
   * Starts the App scheduler on a classloader, to make sure the app has its own
   * classloader
   */
  def setClassloader(cl: ClassLoader) = {

    // Create Thread
    //-------------------
    var oldClassloader = this.classloader
    this.classloader = cl

    // Reset executor
    //-----------
    this.executor match {
      case null =>
      case e => e.shutdownNow()
    }

    this.executor = Executors.newCachedThreadPool(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        var t = new Thread(r)
        t.setDaemon(true)
        t.setContextClassLoader(classloader)
        t
      }
    })

    // Change AIB
    this.aibBus = aib.transferBus(oldClassloader)

    /*this.invokeInAppAndWait {
      this.aibBus = aib.switchToClassLoader(this.aibBus)
    }*/

    //aib.

    /*this.thread = new Thread()
      this.thread.setContextClassLoader(cl)
      this.thread.setDaemon(true)
      this.classloader = cl*/

  }

 

  /**
   * Runs a closure in the App domain, meaning on an app thread
   * Waits for execution
   */
  def invokeInAppAndWait(cl: => Any): Any = {

    var result: Any = null
    //println(s"Submiting action to App executor")
    var future = this.executor.submit(new Callable[Any] {
      def call: Any = {
        cl
      }
    })
    //println(s"Done, will wait to be done")
    future.get

  }

  def invokeInApp(wait: Boolean = true)(cl: => Any): Any = {

    var result: Any = null
    //println(s"Submiting action to App executor")
    var future = this.executor.submit(new Callable[Any] {
      def call: Any = {
        cl
      }
    })

    wait match {
      case true => future.get
      case false => //future.get(1, TimeUnit.NANOSECONDS)
    }

    //println(s"Done, will wait to be done")
    //future.

  }

  // Lyfecycle
  //----------------

  var initialState: Option[String] = None

  def syncInitialState = {
    this.initialState match {
      case None =>
      case Some(state) => AIBApplication.moveToState(this, state)
    }

  }

  // The State is always common and extern, but the handlers have to be handled in the app context
  override def registerStateHandler(str: String)(h: => Unit) = {
    super.registerStateHandler(str) {
      invokeInAppAndWait {
        h
      }
    }
  }

  /**
   * Apply State to self and children
   */
  override def applyState(str: String) = {
    super.applyState(str)

    synchronized {
      this.childApplications.foreach {
        app => AIBApplication.moveToState(app, str)
      }
    }

  }

  def onInit(cl: => Unit) = {
    this.registerStateHandler("init") {
      cl
    }
  }

  def onStart(cl: => Unit) = {
    this.registerStateHandler("start") {
      cl
    }
  }
  def onStop(cl: => Unit) = {
    this.registerStateHandler("stop") {
      cl
    }
  }

  def onShutdown(cl: => Unit) = {
    this.registerStateHandler("shutdown") {
      cl
    }
  }

  // Common Init stuff
  //-------------------
  this.currentState = Some("idle")
  
  this.setClassloader(new AIBApplicationClassloader())

  super.registerStateHandler("preload") {

    println(s"Preload")

    // Stat common stuff
    //--------------
    /*this.executor = Executors.newCachedThreadPool(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        var t = new Thread(r)
        t.setDaemon(true)
        t.setContextClassLoader(classloader)
        t
      }
    })*/

  }

  /* var (initClosures, startClosures, stopClosures) = (List[(Unit => Unit)](), List[(Unit => Unit)](), List[(Unit => Unit)]())

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
  def appInit(wait: Boolean = true) = {

    // Stat common stuff
    //--------------
    this.executor = Executors.newCachedThreadPool(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        var t = new Thread(r)
        t.setDaemon(true)
        t.setContextClassLoader(classloader)
        t
      }
    })

    invokeInApp(wait) {

      // Dependency stuff
      //

      // App Init
      ///----------------

      // DO this first!
      doInit

      // Signal 
      aib ! "init"
    }

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
  def appStart(wait: Boolean = true) = {

    invokeInApp(wait) {
      // Call app start 
      //--------------------
      doStart

      // Signal 
      aib ! "start"

    }

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
  def appStop(wait: Boolean = true) = {

    // Stop Common Stuff

    invokeInApp(wait) {

      // call do Stop 
      doStop

      // Signal 
      aib ! "stop"

    }

  }

  /**
   * After shutdown, you must reinit
   */
  def appShutdown(wait: Boolean = true) = {

    invokeInApp(wait) {

      // call do Stop 
      //doStop

      // Signal 
      aib ! "shutdown"

    }

    // Close common stuff
    //--------------
    this.executor.shutdownNow()

  }

  def appRestart = {
    appStop()
    appStart()
  }*/

  /*def doInit
  def doStart
  def doStop*/

  // Children Application Management 
  //------------------------
  def addChildApplication(app: AIBApplication) = {
    this.childApplications = this.childApplications :+ app

    println(s"** Added, Syncing with state " + this.currentState)
    
    synchronized {
      // Synchronisze state with parent
      app.initialState = this.currentState
      app.syncInitialState
    }
    this.updated.set(true)
    println(s"**** Signaling on: "+this.hashCode())
    
    this.@->("child.added", app)
    //this.@->("child.added")
  }

}
object AIBApplication extends LFCDefinition {
  this.defineState("idle")
  this.defineState("preload")
  this.defineState("init")
  this.defineState("start")
  this.defineState("stop")

}