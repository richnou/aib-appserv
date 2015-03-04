

package com.idyria.osi.aib.appserv.dependencies

import org.scalatest.FunSuite
import org.eclipse.aether.repository.WorkspaceRepository
import org.eclipse.aether.repository.WorkspaceReader
import java.io.File
import org.eclipse.aether.artifact.Artifact
import com.idyria.osi.tea.io.TeaIOUtils
import java.io.FileInputStream
import scala.collection.JavaConversions._
import java.net.URL
import com.idyria.osi.aib.core.dependencies.maven.model.Project
import com.idyria.osi.tea.logging.TLog

class DependenciesResolutionTest extends FunSuite {

    test("Dependencies resolution") {

        var resolver = new AetherResolver
        var res = resolver.resolveDependencies("org.eclipse.aether", "aether-impl", "1.0.0.v20140518", "compile", true)

        res.foreach {
            dep =>
                println(s"Dep: " + dep.toString())
            // println(s"Dep: "+MavenResolver.resolveDependencies(dep.getArtifact))
        }

    }
    
  

    test("Eclispe workspace resolution") {

        var eclipseWorkspaceReader = new EclipseWorkspaceReader(new File("src/test/resources/eclipse-workspace"))
        var resolver = new AetherResolver
        resolver.session.setWorkspaceReader(eclipseWorkspaceReader)
        
        //-- Check projects locations
        assertResult(2)(eclipseWorkspaceReader.projectsLocations.size)
        
        /*
        

        //TLog.setLevel(classOf[AetherResolver], TLog.Level.FULL)
        //TLog.setLevel(classOf[EclipseWorkspaceReader], TLog.Level.FULL)
        
        resolver.resolveDependenciesToClasspath("kit.ipe.adl", "design-platform", "0.0.1-SNAPSHOT", true).foreach {
            dep =>

                println(s"Dep-> $dep ")
        }
        resolver.resolveDependenciesToClasspath("com.idyria.osi.vui", "vui-core", "1.1.1-SNAPSHOT", true).foreach {
            dep =>

                println(s"Dep-> $dep ")
        }*/

        /*    resolver.resolveDependencies("org.eclipse.aether", "aether-impl", "1.0.0.v20140518", "compile").foreach { 
            dep => 
                
                println(s"ADep-> $dep")
        }*/

    }

}