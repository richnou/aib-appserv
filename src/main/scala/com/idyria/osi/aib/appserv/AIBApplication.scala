/**
 *
 */
package com.idyria.osi.aib.appserv

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.Semaphore
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.ooxoo.core.buffers.structural.xelement
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.Callable
import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import java.util.concurrent.TimeUnit

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
    var classloader: URLClassLoader = null

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
    //var aib: aib = new com.idyria.osi.aib.core.bus.aib()

    // Scheduler
    //----------------

    var executor : ExecutorService = null

    /**
     * Starts the App scheduler on a classloader, to make sure the app has its own
     * classloader
     */
    def startScheduler(cl: URLClassLoader) = {

        // Create Thread
        //-------------------
        this.classloader = cl

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
    var (initClosures, startClosures, stopClosures) = (List[(Unit => Unit)](), List[(Unit => Unit)](), List[(Unit => Unit)]())

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
    }

    def doInit
    def doStart
    def doStop

}
object AIBApplication {

}