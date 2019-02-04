package cio

trait GC { self: Interpreter =>
  def startGC(): Unit
}

trait NoopGC extends GC { self: Interpreter =>
  def startGC(): Unit = {}
}
