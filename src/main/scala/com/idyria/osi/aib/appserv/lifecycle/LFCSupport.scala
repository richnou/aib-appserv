package com.idyria.osi.aib.appserv.lifecycle

import com.idyria.osi.tea.listeners.ListeningSupport
import java.util.concurrent.Phaser
import java.util.concurrent.Semaphore

/**
 * @author zm4632
 */
trait LFCSupport extends ListeningSupport {

  val statesHandlers = scala.collection.mutable.Map[String, scala.collection.mutable.ListBuffer[() => Unit]]()
  var currentState: Option[String] = None
  val lfcSemaphore = new Semaphore(0)
  
  def applyState(str: String) = {
    statesHandlers.get(str) match {
      case Some(handlers) =>
        handlers.foreach {
          h => h()
        }
      case None =>
      //throw new RuntimeException(s"Cannot apply state $str to ${getClass.getName}, no handlers defined")
    }
    this.currentState = Some(str)
    lfcSemaphore.release(lfcSemaphore.getQueueLength)
    //this.lfcPhaser.register()
    //this.lfcPhaser.arriveAndDeregister()
    this.@->("state.updated")
  }

  def registerStateHandler(str: String)(h: => Unit) = {

    var handlers = this.statesHandlers.getOrElseUpdate(str, new scala.collection.mutable.ListBuffer[() => Unit])
    handlers += { () => h }
  }
  
  /**
   * Blocks until the provided state has been reached
   */
  def waitForState(expectedState:String) = {
    
    currentState match {
      case Some(st) if(st==expectedState) =>  
      case _ => 
        var wait = true
         //var phase = lfcPhaser.register()
        while(wait) {
          //phase = lfcPhaser.awaitAdvance(phase+1)
          lfcSemaphore.acquire()
         // println(s"Arrived: $currentState")
          this.currentState match {
            case Some(st) if(st==expectedState) =>  wait = false
            case _ => 
          }
        }
       
      
    }
    
    
  }

}
trait LFCDefinition {

  var states = List[String]()

  def defineState(name: String) = {
    states = states :+ name
  }

  def moveToState(lifecyclable: LFCSupport, targetState: String) = {

    // Get index of target state and current State 
    var targetStateIndex = this.states.indexOf(targetState)
    var currentStateIndex = lifecyclable.currentState match {
      case Some(current) => this.states.indexOf(current)
      case None => -1
    }
    println(s"Current: $currentStateIndex")
    // If target is before current, just jump to it 
    (targetStateIndex - currentStateIndex) match {
      case r if (r <= 0) =>
          lifecyclable.applyState(states(targetStateIndex))
          
        
      case r =>
        
        // Scroll to target state, and execute all the states which are higher than current state
        (0 to targetStateIndex) foreach {
          case i if (i > currentStateIndex) =>
            lifecyclable.applyState(states(i))
            currentStateIndex = i
          case _ =>
        }
    }

  }

}