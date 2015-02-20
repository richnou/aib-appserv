package com.idyria.osi.aib.appserv.prebuild

import com.idyria.osi.aib.appserv.AIBApplication
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.artifact.Artifact
import com.idyria.osi.aib.appserv.dependencies.AetherResolver
import java.net.URLClassLoader
import java.util.concurrent.Semaphore

/**
 * Uses its provided classpath to load some AIBApplications
 *
 * This class will load its own classloader if an artifact is provided
 *
 */
class PrebuildLoader(ar: AetherResolver) extends AIBApplication {

    this.artifactsResolver = ar

    var dependencies = List[ArtifactResult]()

    var mainArtifact: Option[Artifact] = None

    /**
     * The main classes to be loaded
     */
    var applicationClasses = List[String]()

    var initSemaphore = new Semaphore(0)

    // Lifecyle
    //--------------

    def doInit = {

        // update Classloader
        //--------------------
        println(s"Prebuild loader with main artifact: $mainArtifact")

        initSemaphore.drainPermits()
        
        mainArtifact match {
            case Some(artifact) =>

                //-- Get Dependencies
                this.dependencies = this.artifactsResolver.resolveDependencies(artifact, "compile", true)

                //-- Convert to classpath and set classloader
                this.classloader = URLClassLoader.newInstance(this.artifactsResolver.dependenciesToClassPathURLS(this.dependencies).toArray)

            case None =>
        }

        //-- Reset Applications
        this.applicationClasses.foreach {
            appClassStr =>
                try {

                    println(s"Loading app: $appClassStr")

                    // Load
                    var appClass = this.classloader.loadClass(appClassStr)

                    // check
                    if (!classOf[AIBApplication].isAssignableFrom(appClass)) {
                        throw new RuntimeException(s"Cannot load AIB Application wich does not derive from type ${classOf[AIBApplication]}: $appClass")
                    }

                    // Init 
                    var app = appClass.newInstance().asInstanceOf[AIBApplication]
                    app.appInit()

                    // Save
                    this.childApplications = this.childApplications :+ app

                } catch {
                    case e: Throwable =>
                        e.printStackTrace()
                }

        }

        // Signal
        initSemaphore.release()

    }

    def doStart = {

        initSemaphore.acquire()
        initSemaphore.release()
        println(s"In do start")
    
            //-- Start all

            this.childApplications.foreach {
                app =>

                    println(s"Starting app: ${app.getClass}")

                    app.appStart(false)
            }
        

    }

    def doStop = {

        this.childApplications.foreach {
            app =>
                try {
                    app.appStop(false)
                } catch {
                    case e: Throwable => e.printStackTrace()
                }
        }

    }

    // Application Loading
    //-----------------------

    def loadApplication(appClass: String) = {

        println(s"[PBL] Load app: $appClass")
        this.applicationClasses = this.applicationClasses :+ appClass
    }

}