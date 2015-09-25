package com.idyria.osi.aib.appserv.model

import com.idyria.osi.ooxoo.model.ModelBuilder
import com.idyria.osi.ooxoo.model.{producers,producer}
import com.idyria.osi.ooxoo.model.out.markdown.MDProducer
import com.idyria.osi.ooxoo.model.out.scala.ScalaProducer
import com.idyria.osi.ooxoo.core.buffers.structural.io.sax.STAXSyncTrait
import com.idyria.osi.tea.listeners.ListeningSupport

@producers(Array(
  new producer(value = classOf[ScalaProducer]),
  new producer(value = classOf[MDProducer])))
object AIBAppServModel extends ModelBuilder {

  "Config" is {
    withTrait(classOf[STAXSyncTrait].getCanonicalName)
    withTrait(classOf[ListeningSupport].getCanonicalName)

    "Gui" ofType "boolean"

    "Workspace" ofType "string"

    "Application" multiple {

      elementsStack.head.makeTraitAndUseCustomImplementation

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