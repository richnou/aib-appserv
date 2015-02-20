package com.idyria.osi.aib.appserv

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import scala.collection.JavaConversions._
import com.idyria.osi.aib.core.compiler.EmbeddedCompiler
import com.idyria.osi.tea.thread.ThreadLanguage
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.AbstractFile
import java.nio.file.WatchKey
import java.nio.file.StandardCopyOption
import java.io.FileReader
import scala.io.Source
import com.idyria.osi.aib.core.compiler.FileCompileError
import java.util.concurrent.Semaphore
import com.idyria.osi.tea.logging.TLogSource
import com.idyria.osi.ooxoo.core.buffers.datatypes.BooleanBuffer
import java.util.concurrent.Phaser
import com.idyria.osi.aib.core.compiler.EmbeddedCompiler

class FolderWatcher(var location: File) extends ThreadLanguage with TLogSource {

    // Location
    //-------------------

    // Source location
    var locationPath = FileSystems.getDefault().getPath(location.getAbsolutePath)

    // Main output path
    var outputPath = locationPath.resolve(List("aib-target").mkString(File.separator))

    //var buildOutputPath = locationPath.resolve(List("aib-target").mkString(File.separator))
    var compilerOutputPath = outputPath.resolve(List("classes").mkString(File.separator))

    // File patterns
    //-----------------
    var ignorePatterns = List(""".*\.ignore\..*""".r)
    var sourcePatterns = List(""".*\.scala$""".r)

    // Watched Directories
    //------------------
    var watchedDirectories = List[(Path, WatchKey)]()

    // Compiler
    //--------------------
    var compiler = new EmbeddedCompiler

    // set output
    var compilerOutput = compilerOutputPath.toFile()
    compilerOutput.mkdirs()
    compiler.settings2.outputDirs.setSingleOutput(compilerOutput.getAbsolutePath)

    /**
     * Change the global output path
     */
    def changeOutputPath(file: File) = {

        outputPath = FileSystems.getDefault().getPath(file.getAbsolutePath)
        compilerOutputPath = outputPath.resolve(List("classes").mkString(File.separator))
        compilerOutput = compilerOutputPath.toFile()
        compilerOutput.mkdirs()
        compiler.settings2.outputDirs.setSingleOutput(compilerOutput.getAbsolutePath)

    }

    def updateClassLoader(cl: ClassLoader) = {
        this.watcherThread.setContextClassLoader(cl)
        var old = Thread.currentThread().getContextClassLoader
        Thread.currentThread().setContextClassLoader(cl)
        this.compiler = new EmbeddedCompiler
        this.compiler.settings2.outputDirs.setSingleOutput(compilerOutput.getAbsolutePath)
        Thread.currentThread().setContextClassLoader(old)
        
    }

    class CompileBatch(var files: List[File]) {

        def this(f: File) = this(List(f))

        //-- Errors
        var compileErrors = scala.collection.mutable.MutableList[FileCompileError]()

        def compile = {

            compileErrors.clear()

            compiler.compileFiles(files) match {
                case Some(error) =>

                    compileErrors += error
                    Some(error)

                case None =>

                    // Clear errors
                    compileErrors.clear()
                    //compileErrors = compileErrors.filterNot { e => e.file.getAbsolutePath.equals(f.getAbsolutePath) }
                    None

            }
        }

        def hasErrors = !compileErrors.isEmpty

    }

    var pendingBatches = scala.collection.mutable.MutableList[CompileBatch]()

    //-- Updated signal
    var updated = new Phaser
    var cleanStatus = BooleanBuffer()
    cleanStatus = false

    // Source folders
    //-------------------------
    def addSourceFolder(file: File) = {

        // to path
        var sourcePath = FileSystems.getDefault().getPath(file.getAbsolutePath)

        // register
        //-------------
        sourcePath.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE)
        var initCompile = List[File]()
        var waltkStream = Files.walk(sourcePath)
        var it = waltkStream.iterator()
        while (it.hasNext()) {
            var path = it.next()

            var relativePath = locationPath.relativize(path)
            logFine[FolderWatcher](s"path : $relativePath // ${outputPath}")

            path match {

                //-- Source File
                case f if (sourcePatterns.find(r => r.findFirstIn(f.toFile.getAbsolutePath) != None) != None) =>
                    initCompile = initCompile :+ f.toFile

                //-- Ignores  : compiler output, ignore
                //case f if (f.startsWith(buildOutputPath)) =>
                case f if (f.startsWith(outputPath)) => //logFine[FolderWatcher](s"Ignore: $relativePath")
                case f if (ignorePatterns.find(r => r.findFirstIn(f.toFile.getAbsolutePath) != None) != None) =>

                //-- Register Folder
                case f if (f.toFile.isDirectory()) =>

                    var key = path.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
                    watchedDirectories = watchedDirectories :+ (path, key)

                //-- Other  : Copy to output
                case f =>

                    // Get relative path from base 
                    var relativePath = this.locationPath.relativize(f)
                    var outputPath = this.compilerOutputPath.resolve(relativePath)
                    logFine[FolderWatcher](s"Copying to: ${this.compilerOutputPath.resolve(relativePath)}")
                    outputPath.toFile().getParentFile.mkdirs()
                    Files.copy(f, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

            }

        }

        // Pre-Compilation
        //--------------------
        logFine[FolderWatcher](s"CP out: $compilerOutputPath")
        logFine[FolderWatcher]("Init compile CP: " + compiler.settings2.classpathURLs)
        logFine[FolderWatcher](s"Init compile with: $initCompile")
        var batch = new CompileBatch(initCompile)
        pendingBatches += batch
        //compileBatches
    }

    // Compiler method with handlers
    //-------------------------

    /**
     * Kickoff
     */
    def start = {

        // base folder is in watcher
        //locationPath.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE)

        compilerOutputPath.toFile().mkdirs()

        // Walk tree
        //---------------
        addSourceFolder(locationPath.toFile())
        /*
    /*logFine[FolderWatcher](s"Absolute file: ${initCompile(0).getAbsoluteFile.isFile()}")
    logFine[FolderWatcher](s"Absolute file: ${AbstractFile.getFile(initCompile(0).getAbsoluteFile)}")*/
    compiler.imain.compileSourcesSeq(initCompile.map(f => new BatchSourceFile(AbstractFile.getFile(f.getAbsoluteFile))).toSeq) match {
      case true =>
      case false =>
    }*/

        // Start
        //--------------
        watcherThread.start()

    }

    /**
     * Compile source file, and try to return some errors
     */
    def compileSource(f: File): Unit = {

        //-- look for a pending batch with this file
        pendingBatches.find { batch => batch.files.contains(f) } match {
            case Some(batch) =>
                batch.compile
                compileBatches
            case None =>
                pendingBatches += new CompileBatch(f)
                compileBatches
        }

        /*
      compiler.compileFile(f) match {
        case Some(error) => 
          
          compileErrors += error
          Some(error)
          
        case None => 
          
          // Clear errors for file
          compileErrors = compileErrors.filterNot { e => e.file.getAbsolutePath.equals(f.getAbsolutePath) }
          None
          
      }*/

    }

    /**
     * A
     */
    def compileBatches = {

        logFine[FolderWatcher](s"compile batches")
        /**
         * Compile the batches
         */
        pendingBatches.toList.foreach {
            batch =>

                batch.compile

                // Clean old batches, try to compile errors ones 
                batch.hasErrors match {
                    case true =>
                    case false => pendingBatches = pendingBatches.filterNot(_ == batch)
                }

        }

        // Infos
        pendingBatches.foreach {
            batch =>
                logFine[FolderWatcher](s"**** Batch: " + batch.files)
                batch.compileErrors.foreach {
                    err => logError[FolderWatcher](s"****** -> error: ${err.file} , ${err.message}")
                }

        }

        // If no batches anymore, then signal updated 
        if (pendingBatches.length == 0) {

            //updated.release(updated.getQueueLength)

            cleanStatus.set(true)
        } else {
            cleanStatus.set(false)
            println(s"Compiler clean status: " + cleanStatus.getNextBuffer)
        }

    }

    // Watcher Thread
    //--------------------
    var watcher = FileSystems.getDefault().newWatchService();
    var watcherThread = createThread {

        // Registrer to updated phaser
        updated.register()


        // Make an init compile
        compileBatches
        updated.arrive()

        var stop = false
        while (!stop) {
            try {

                // Get Key
                var key = watcher.take()

                // Get associated directory
                var directory = watchedDirectories.collectFirst { case (dpath, dkey) if (dkey == key) => dpath }.get

                try {

                    // Loop over events
                    key.pollEvents().filter { ev => ev.kind() != StandardWatchEventKinds.OVERFLOW }.foreach {

                        //-- New Folder
                        case event: WatchEvent[Path] if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && directory.resolve(event.context()).toFile().isDirectory()) =>

                            var filePath = directory.resolve(event.context())
                            logFine[FolderWatcher](s"New Folder: $filePath")

                            var newkey = filePath.register(this.watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
                            watchedDirectories = watchedDirectories :+ (filePath, newkey)

                        //-- New/Changed File
                        case event: WatchEvent[Path] =>

                            //-- Get File 

                            var filePath = directory.resolve(event.context())
                            var file = filePath.toFile()
                            logFine[FolderWatcher](s"Added/Changed: $file // ${event.context().getParent}")

                            file match {

                                //-- Source file
                                case f if (sourcePatterns.find(r => r.findFirstIn(f.getAbsolutePath) != None) != None) =>

                                    logFine[FolderWatcher](s"Compiling")
                                    compileSource(f)

                                //-- Ignored files
                                //case f if (filePath.startsWith(buildOutputPath)) =>
                                case f if (filePath.startsWith(outputPath)) =>
                                case f if (ignorePatterns.find(r => r.findFirstIn(f.getAbsolutePath) != None) != None) =>

                                //-- Other files, copy to output
                                case f =>

                                    // Get relative path from base 
                                    var relativePath = this.locationPath.relativize(filePath)
                                    var outputPath = this.compilerOutputPath.resolve(relativePath)
                                    // logFine[FolderWatcher](s"Copying to: ${this.compilerOutputPath.resolve(relativePath)}")
                                    outputPath.toFile().getParentFile.mkdirs()
                                    Files.copy(filePath, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)

                            }

                        case _ =>

                    }

                } finally {
                    //-- invalid key
                    key.reset()
                }

                // End
                try {

                    updated.arrive()
                } catch {
                    case e: IllegalStateException =>
                }

            } catch {
                case e: InterruptedException => stop = true
            }

        }

    }
    watcherThread.setDaemon(true)

    // Init: Walk the folder tree, search for sources, register folders for watching
    //-------------------

}