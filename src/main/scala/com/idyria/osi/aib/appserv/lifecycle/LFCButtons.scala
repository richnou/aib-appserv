package com.idyria.osi.aib.appserv.lifecycle

import org.controlsfx.control.SegmentedButton
import com.idyria.osi.vui.core.VBuilder
import com.idyria.osi.vui.core.stdlib.node.SGCustomNode
import com.idyria.osi.vui.javafx.JavaFXRun
import javafx.scene.control.ToggleButton
import com.idyria.osi.vui.javafx.JavaFXNodeDelegate
import scala.collection.JavaConversions._
import com.idyria.osi.tea.thread.ThreadLanguage

/**
 * @author zm4632
 */
class LFCButtons(val lifecyclable : LFCSupport,val states : LFCDefinition) extends SGCustomNode with VBuilder  {
  
  
  def createUI = panel {
    p => 
      
      /*
       * ToggleButton b1 = new ToggleButton("day");
 ToggleButton b2 = new ToggleButton("week");
 ToggleButton b3 = new ToggleButton("month");
 ToggleButton b4 = new ToggleButton("year");
       
 SegmentedButton segmentedButton = new SegmentedButton();    
 segmentedButton.getButtons().addAll(b1, b2, b3, b4);
       */
      
      // Create buttons
      var buttons = states.states.map {
        stateName => 
          
          var tb = new ToggleButton(stateName)
          tb.setUserData(stateName)
          JavaFXNodeDelegate(tb).onClicked  {
            
            states.moveToState(lifecyclable, tb.getText)
            
          }
          
          tb
      }
      
      var segButton = new SegmentedButton()
      segButton.getButtons.addAll(buttons)
      
      // Pre select the correct one 
      lifecyclable.currentState match {
        case Some(currentState) =>
          
          buttons.find { tb => tb.getText==currentState } match {
            case Some(button) =>
               button.setSelected(true)
            case None => 
          }
          
          
        case None => 
      }
      
      // Update buttons on state update 
      lifecyclable.on("state.updated") {
        buttons.find { b => b.getUserData.toString() == lifecyclable.currentState.get.toString() } match {
          case Some(button) =>
            button.setSelected(true)
          case None => 
        }
      }
      
      p <= JavaFXNodeDelegate(segButton)
      
  }
}

object LFCButtons extends App with VBuilder with ThreadLanguage {
  
  // Create dummy thing
  object TestDefinition extends LFCDefinition {
    defineState("idle")
    defineState("init")
    defineState("start")
    defineState("stop")
    defineState("restart")
  }
  
  class TestTarget extends LFCSupport {
    
    registerStateHandler("init") {
      println(s"Init")
    }
    registerStateHandler("start") {
      println(s"Start")
    }
    registerStateHandler("stop") {
      println(s"Stop")
    }
    
  }
  var target = new TestTarget
  target.applyState("idle")
  
  
  var th = fork {
    
    println(s"Waitign for state")
    target.waitForState("stop")
      println(s"got stop")
  }
  Thread.sleep(500)
  println(s"goign to stop ")
  target.applyState("start")
  
  
  Thread.sleep(500)
  sys.exit 
  
  JavaFXRun.onJavaFX {
    
    frame {
      f => 
        f.size(500, 500)
        
        f <= new LFCButtons(target,TestDefinition)
        
        
        
        f.show
    }
    
  }
  
}