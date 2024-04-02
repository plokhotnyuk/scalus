package scalus.uplc

import scalus.builtin.JVMPlatformSpecific

object Meaning {
    val defaultBuiltins = Meaning(eval.BuiltinCostModel.default)
}

class Meaning(builtinCostModel: eval.BuiltinCostModel)
    extends BuiltinsMeaning(builtinCostModel)
    with JVMPlatformSpecific {
    protected def log(msg: String): Unit = ()
}
