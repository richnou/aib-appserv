package appserv.examples.gui

class A extends B with D {
  
  def test =  {
    println(s"Hello world 3 -> "+getClass.isInstanceOf[D])
    test2
  }
  
}

trait D {
  
  def test2 = {
    println(s"Hello world from test 2 -> ")
  }
  
}