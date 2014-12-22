
package appserv.examples.gui

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.idyria.osi.vui.javafx._

import com.idyria.osi.aib.appserv._

class ExampleGUIApp extends AIBApplication with com.idyria.osi.vui.lib.gridbuilder.GridBuilder {

  def doInit = {

  }
  def doStart = {

    println(s"inside app")

    //launch(Array[String]())
    
    JavaFXRun.onJavaFX {
      frame {
        f => 
          f title("Hello world")
          f size(640,480)
          
          f <= grid {
            
            "-" row {
              label("Hello world") | (button("Click") {
                b => b.onClick {new appserv.examples.gui.A().test}
              })
            }
          }
          
          
          onStop {
            onUIThread(f.close)
          }
          f.show
      }
    }
    
    

  }
  def doStop = {

  }


}