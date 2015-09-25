package com.idyria.osi.aib.appserv

import java.net.URL
import java.net.URLClassLoader

/**
 * @author zm4632
 */
class AIBApplicationClassloader(arr:Array[URL],p:ClassLoader) extends URLClassLoader(arr,p) {
  
  def this(arr:Array[URL]) = this(arr,Thread.currentThread().getContextClassLoader)
  def this() = this(Array[URL](),Thread.currentThread().getContextClassLoader)
  
  override def addURL(u:URL) = super.addURL(u)
}