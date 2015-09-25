package com.idyria.osi.aib.appserv.apps

import com.idyria.osi.aib.appserv.AIBApplication
import com.idyria.osi.vui.core.VBuilder
import com.idyria.osi.vui.core.stdlib.node.SGCustomNode
import com.idyria.osi.vui.core.components.scenegraph.SGNode

/**
 * @author zm4632
 */
trait GUIApplication extends AIBApplication  {
  
  var _ui : Option[SGNode[Any]] = None
  
  def ui = _ui match {
    case Some(r) => r
    case None => 
      _ui = Some(this.createUI)
      _ui.get
  }
  
  def createUI : SGNode[Any]
  
}