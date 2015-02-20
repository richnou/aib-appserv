package com.idyria.osi.aib.appserv

import com.idyria.osi.ooxoo.model.ModelBuilder
import com.idyria.osi.ooxoo.model.producers
import com.idyria.osi.ooxoo.model.producer
import com.idyria.osi.ooxoo.model.out.markdown.MDProducer
import com.idyria.osi.ooxoo.model.out.scala.ScalaProducer

@producers(Array(
  new producer(value = classOf[ScalaProducer]),
  new producer(value = classOf[MDProducer])
))
object AIBAppServModel extends ModelBuilder {
  
  
    "Config" is {
      
      "Gui" ofType "boolean"
      
      "Application" multiple  {
          "Path" ofType "string"
          "Artifact" is {
              attribute("id")
              "ApplicationClass" ofType "string"
              
              //-- Optional: Detailed?
          }
      }
       
    }
    
    "ApplicationConfig" is {
        
        "AutoRestart" ofType "boolean" default "false"
        
    }
  
}