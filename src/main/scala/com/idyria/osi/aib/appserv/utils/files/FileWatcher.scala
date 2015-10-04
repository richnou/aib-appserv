package com.idyria.osi.aib.appserv.utils.files

import com.idyria.osi.tea.thread.ThreadLanguage
import com.idyria.osi.tea.logging.TLogSource
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.StandardCopyOption
import java.nio.file.WatchKey
import java.nio.file.WatchEvent
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConversions._

/**
 * @author zm4632
 */
class FileWatcher extends ThreadLanguage with TLogSource {

  var baseDirectories = Map[WatchKey, File]()
  var changeListeners = Map[String, List[() => Any]]()

  def start = {
    println(s"////////////// Startin watcher")
    watcherThread.start()
  }

  def stop = {

  }

  def onFileChange(f: File)(cl: => Any) = {

    f match {
      case f if (f.isDirectory()) => new RuntimeException("Cannot Watch File Change on Directory")
      case f =>

        // to path
        var sourcePath = FileSystems.getDefault().getPath(f.getAbsolutePath)
        var directoryPath = FileSystems.getDefault().getPath(f.getParentFile.getAbsolutePath)

        // Register if necessary
        this.baseDirectories.find { case (key, file) => file.getAbsolutePath == f.getParentFile.getAbsolutePath } match {
          case Some(entry) =>
          case None =>
            var watchKey = directoryPath.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
            this.baseDirectories = this.baseDirectories + (watchKey ->  f.getParentFile.getAbsoluteFile)
        }

        // Save listener
        var listeners = changeListeners.get(f.getAbsolutePath) match {
          case Some(listeners) => listeners
          case None => List()
        }
        listeners = listeners :+ { () => cl }
        this.changeListeners = this.changeListeners.updated(f.getAbsolutePath, listeners)

        println(s"///////////// Recorded event for "+f.getAbsolutePath)
      // 
    }

  }

  // Watcher Thread
  //--------------------
  var watcher = FileSystems.getDefault().newWatchService();
  var watcherThread = createThread {

    var stop = false
    while (!stop) {

      // Get Key
      var key = watcher.take()

      try {
        /* key.pollEvents().foreach {
          e => e.
        }*/
        
        //println(s"///////////// Got Key ")
        key.pollEvents().filter { ev => ev.kind() != StandardWatchEventKinds.OVERFLOW } foreach {

          case e: WatchEvent[Path] if (e.kind() == StandardWatchEventKinds.ENTRY_MODIFY) =>

            // Get Path of directory
            var directoryFile = this.baseDirectories.get(key).get
            
            // Get Path of file
            var filePath = directoryFile.toPath().resolve(e.context()).toAbsolutePath().toFile().getAbsolutePath
            //var filePath = directoryFile.toPath().resolve(e.context()).toAbsolutePath().toFile().getAbsolutePath
          
            //var filePath = 
            //println(s"///////////// Got Modify event for "+filePath+ "// "+e.context().toAbsolutePath().toFile().getAbsolutePath+"//"+directoryFile.toPath())
            
            // Get and run listeners
            changeListeners.get(filePath) match {
              case Some(listeners) =>
                listeners.foreach {
                  l => 
                   // println(s"Running event")
                    l()
                }
              case None =>
            }
            
          case e => 
            
            println(s"///////////// Got Event ")
        }

      } finally {
        //-- invalid key
        key.reset()
      }

    }

  }
  watcherThread.setDaemon(true)

}