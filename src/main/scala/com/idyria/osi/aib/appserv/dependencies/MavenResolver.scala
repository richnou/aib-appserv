package com.idyria.osi.aib.appserv.dependencies

import java.io.File
import java.net.URL

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactDescriptorRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils

import com.idyria.osi.tea.logging.TLogSource

class AetherConfiguration {

    def newRepositorySystem: RepositorySystem = {

        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
        var locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(classOf[RepositoryConnectorFactory], classOf[BasicRepositoryConnectorFactory]);
        locator.addService(classOf[TransporterFactory], classOf[FileTransporterFactory]);
        locator.addService(classOf[TransporterFactory], classOf[HttpTransporterFactory]);

        /*locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
        {
            @Override
            public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
            {
                exception.printStackTrace();
            }
        } );*/

        return locator.getService(classOf[RepositorySystem]);

        //return org.eclipse.aether.examples.manual.ManualRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.guice.GuiceRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.sisu.SisuRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.plexus.PlexusRepositorySystemFactory.newRepositorySystem();
    }

    def newRepositorySystemSession(system: RepositorySystem): DefaultRepositorySystemSession = {
        var session = MavenRepositorySystemUtils.newSession();

        //var localRepo = new LocalRepository("target/local-repo");
        var localRepo = new LocalRepository(sys.props("user.home")+File.separator+".m2"+File.separator+"repository");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        // session.setTransferListener( new ConsoleTransferListener() );
        // session.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }

    def newRepositories(system: RepositorySystem, session: RepositorySystemSession): List[RemoteRepository] = {
        return List[RemoteRepository](newCentralRepository);
    }

    def newCentralRepository: RemoteRepository = {

        var b = new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/")
        b.build
    }

}

class AetherResolver(val config: AetherConfiguration = new AetherConfiguration) extends TLogSource {

    var system = config.newRepositorySystem; 

    var session = config.newRepositorySystemSession(system);

    // Get Deps
    //------------------------
    def getDependencies(groupId: String, artifactId: String, version: String, scope: String): List[Dependency] = {

        this.getDependencies(groupId, artifactId, version).filter(d => d.getScope == scope)
    }

    def getDependencies(groupId: String, artifactId: String, version: String): List[Dependency] = {

        this.getDependencies(new DefaultArtifact(s"$groupId:$artifactId:$version"))

    }

    def getDependencies(artifact: Artifact): List[Dependency] = {

        var descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(config.newRepositories(system, session));

        var descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        descriptorResult.getDependencies().toList
    }

    // Transistive Resolution
    //-------------------------------

    def resolveDependencies(groupId: String, artifactId: String, version: String, scope: String, head: Boolean ): List[ArtifactResult] = {

        this.resolveDependencies(new DefaultArtifact(s"$groupId:$artifactId:$version"), scope, head)
    }

    def resolveDependencies(groupId: String, artifactId: String, version: String, head: Boolean ): List[ArtifactResult] = {

        this.resolveDependencies(groupId, artifactId, version, "compile", head)

    }

    /**
     * Resolve dependencies
     *
     *
     */
    def resolveDependencies(artifact: Artifact, scope: String, head: Boolean): List[ArtifactResult] = {

        logFine(s"Resolving dependencies for $artifact")

        //-- Prepare filter
        var classpathFilter = DependencyFilterUtils.classpathFilter(scope);

        //-- If no Head -> Get its dependencies list, and resolve for each
        head match {
            case true =>
                var collectRequest = new CollectRequest();
                collectRequest.setRoot(new Dependency(artifact, scope));
                collectRequest.setRepositories(config.newRepositories(system, session));

                var dependencyRequest = new DependencyRequest(collectRequest, classpathFilter);

                var artifactResults =
                    system.resolveDependencies(session, dependencyRequest).getArtifactResults();

                artifactResults.toList
            case false => 
                
                this.getDependencies(artifact).map {
                    dep => 
                        this.resolveDependencies(dep.getArtifact, scope, true)
                }.flatten
                

        }

    }

    // Classpath building helpers
    //----------------------------

    def resolveDependenciesToClasspath(groupId: String, artifactId: String, version: String, head: Boolean): List[URL] = {

        dependenciesToClassPathURLS(this.resolveDependencies(groupId, artifactId, version, JavaScopes.COMPILE,head))
    }
    
   

    def resolveDependenciesToClasspath(artifact: Artifact, scope: String, head: Boolean): List[URL] = {

        dependenciesToClassPathURLS(this.resolveDependencies(artifact, scope,head))
    }

    /**
     * Converts a dependency resolution path to a viable classpath, by
     * transforming pom.xml paths to actual build output (for Workspace resolved
     * artifacts for example)
     *
     * It also tries to remove duplicates
     *
     *
     */
    def dependenciesToClassPathURLS(artifacts: List[ArtifactResult]): List[URL] = {

        // Remove duplicates (Map by groupId:artifactId, and use only the first one
        //-------------------------
        var onlyFirst = artifacts.groupBy { r => s"${r.getArtifact.getGroupId}:${r.getArtifact.getArtifactId}" }.mapValues { f => f.head }.values.toList

        // Map paths
        //--------------------------
        onlyFirst.map {
            case result if (result.getArtifact.getFile.getName() == "pom.xml") =>
                logFine(s"---Transforming file $result")
                //result.getArtifact.setFile(new File(result.getArtifact.getFile().getParentFile,"target/classes"))
                //result
                new File(result.getArtifact.getFile().getParentFile, "target/classes").toURI().toURL()
            case result => result.getArtifact.getFile.toURI().toURL()
        }
    }

}

object MavenResolver {

    // Create Settings
    //------------------

    //var settings = cli = new MavenCli();

    // Initialise Aether
    //--------------------
    var config = new AetherConfiguration
    var system = config.newRepositorySystem;

    var session = config.newRepositorySystemSession(system);

    def resolveDependencies(groupId: String, artifactId: String, version: String, scope: String): List[Dependency] = {

        this.resolveDependencies(groupId, artifactId, version).filter(d => d.getScope == scope)
    }

    def resolveDependencies(groupId: String, artifactId: String, version: String): List[Dependency] = {

        this.resolveDependencies(new DefaultArtifact(s"$groupId:$artifactId:$version"))

    }

    def resolveDependencies(artifact: Artifact): List[Dependency] = {

        var descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(config.newRepositories(system, session));

        var descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        descriptorResult.getDependencies().toList
    }

}