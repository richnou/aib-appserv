package com.idyria.osi.aib.appserv.dependencies

import org.eclipse.aether.repository.WorkspaceRepository
import org.eclipse.aether.repository.WorkspaceReader
import com.idyria.osi.tea.logging.TLogSource
import java.io.File
import java.io.FileInputStream
import java.net.URL
import com.idyria.osi.tea.io.TeaIOUtils
import org.eclipse.aether.artifact.Artifact
import com.idyria.osi.aib.core.dependencies.maven.model.Project

import scala.collection.JavaConversions._

/**
 * Just an Aether Worskspace Repository and reader
 */
class EclipseWorkspaceReader(val workspaceLocation: File, val workspaceRepository: WorkspaceRepository = new WorkspaceRepository("eclipse")) extends WorkspaceReader with TLogSource {

    var projectsLocation = new File(workspaceLocation, ".metadata/.plugins/org.eclipse.core.resources/.projects")

    logFine(s"Eclipse workspace: $workspaceLocation")

    // Projects availability cache
    //------------
    var projectsLocations = projectsLocation.listFiles().filter(folder => new File(folder, ".location").exists()).map(new File(_, ".location")).map {
        location =>

            //-- Get source (file is binary, so search for delimiters)
            var locationContent = TeaIOUtils.swallow(new FileInputStream(location))
            var uri = locationContent.toList.dropWhile { _ != 'U'.toByte }.takeWhile { b => b != 0x00 }.map(_.toChar).mkString

            //locationContent.
            //var locationContent = scala.io.Source.fromFile(location, "UTF-8").mkString
            var search = """URI//([\w :/_.-]+)""".r

            //-- Find URI
            search.findFirstMatchIn(uri) match {
                case Some(result) =>
                    logFine(s"Found lcoation: " + result.group(1))
                    Some(new File(new URL(result.group(1)).getFile))
                case None =>
                    logFine(s"Location not  found for : " + location)
                    None
            }

    }.filter(_ != None).map { _.get }

    // Find Artifacts
    //-----------------
    var projectArtifacts = projectsLocations.map(new File(_, "pom.xml")).filter(_.exists()).map {
        pomFile =>

            //-- Parse
            var project = Project(pomFile.toURI().toURL())

            logFine(s"--- found ${project.artifactId},${project.groupId},${project.version}")
            //-- Prepare Artifact
            (pomFile, project)
    }

    def findArtifact(artifact: Artifact): File = {

        logFine(s"looking in workspace for $artifact")

        this.projectArtifacts.find {
            case (file, art) => artifact.getGroupId == art.groupId.toString() &&
                artifact.getArtifactId == art.artifactId.toString() &&
                artifact.getVersion == art.version.toString()
        } match {
            case Some((file, art)) =>
                logFine(s"Got it: $file")
                //new File(file.getParentFile,"target/classes")
                artifact.setFile(new File(file.getParentFile, "target/classes"))

                file
            //new File(file.getParentFile,"target/classes")
            case None => null
        }

    }

    def findVersions(artifact: Artifact): java.util.List[String] = {

        this.projectArtifacts.filter {
            case (file, art) => artifact.getGroupId == art.groupId.toString() &&
                artifact.getArtifactId == art.artifactId.toString()

        }.map {
            case (file, art) => art.version.toString()
        }.toList

        //List[String]()
    }

    def getRepository(): WorkspaceRepository = workspaceRepository

}
