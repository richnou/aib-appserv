package com.idyria.osi.aib.appserv.model

import com.idyria.osi.aib.appserv.ApplicationWrapper
import java.io.File
import org.eclipse.aether.artifact.DefaultArtifact
import com.idyria.osi.aib.core.bus.aib
import com.idyria.osi.aib.appserv.AIBAppServ

/**
 * @author zm4632
 */
class ConfigApplication extends ConfigApplicationTrait {

  var appserver : AIBAppServ = _
  
  /**
   * Analyse Element content to try to determine what kind of Application to create
   */
  def bootstrap = {
    assert(appserver!=null,"Application Server Must be Provided")
    
    this match {
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
          //case f if (this.applicationWrappers.find { w => w.location.equals(f) } != None) =>

          //-- Ok
          case f =>
            var wrapper = new ApplicationWrapper(f)
            
            appserver.aib send wrapper
            
            //this.applicationWrappers = this.applicationWrappers :+ wrapper
          //wrapper.init
        }

      // Artifact
      //-------------------------------
      case appConfig if (appConfig.artifact != null && appConfig.artifact.id != null) =>

        // Resolve
        //loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), appConfig.artifact.applicationClasses.map(_.data).toList)
        
        //loadPrebuildApplication(new DefaultArtifact(s"${appConfig.artifact.id}"), List(appConfig.artifact.applicationClass.data))

      case _ =>
    }

  }

}