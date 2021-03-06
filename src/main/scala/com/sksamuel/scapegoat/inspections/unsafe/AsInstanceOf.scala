package com.sksamuel.scapegoat.inspections.unsafe

import com.sksamuel.scapegoat._

class AsInstanceOf extends Inspection("Use of asInstanceOf", Levels.Warning) {

  def inspector(context: InspectionContext): Inspector = new Inspector(context) {
    override def postTyperTraverser = Some apply new context.Traverser {

      import context.global._

      override def inspect(tree: Tree): Unit = {
        tree match {
          // this will skip any uses of manifest etc
          case TypeApply(Select(qual, TermName("asInstanceOf")), _) if qual.toString != "classOf[java.lang.Class]" =>
            context.warn(tree.pos, self,
              "asInstanceOf used near " + tree.toString().take(500) + ". Consider using pattern matching.")
          case DefDef(modifiers, _, _, _, _, _) if modifiers.hasFlag(Flag.SYNTHETIC) => // no further
          case m @ Match(selector, cases) => // ignore selector and process cases
            cases.foreach(traverse)
          case _ => continue(tree)
        }
      }
    }
  }
}