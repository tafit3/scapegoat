package com.sksamuel.scapegoat.inspections.unneccesary

import com.sksamuel.scapegoat.{ Inspection, InspectionContext, Inspector, Levels }

import scala.collection.mutable

/** @author Stephen Samuel */
class VarCouldBeVal extends Inspection("Var could be val", Levels.Warning) {

  def inspector(context: InspectionContext): Inspector = new Inspector(context) {
    override def postTyperTraverser = Some apply new context.Traverser {

      import context.global._

      private def unwrittenVars(tree: Tree, vars: mutable.HashMap[String, Tree]): List[(String, Tree)] = {
        tree match {
          case Block(stmt, expr) => containsUnwrittenVar(stmt :+ expr, vars)
          case _                 => containsUnwrittenVar(List(tree), vars)
        }
      }

      private def containsUnwrittenVar(trees: List[Tree], vars: mutable.HashMap[String, Tree]): List[(String, Tree)] = {
        // As we scan the tree, in `vars: HashMap[String, Tree]` we store an entry for each var
        // that we encounter. The key gives the name and the value gives the tree of the ValDef
        // that defines it. Whenever a var is written to, we remove its entry. What remains are
        // vars that are never written to (and the trees corresponding to the places where they
        // were defined).
        trees.foreach {
          case defn@ValDef(mods, name, _, _) if mods.isMutable =>
            vars.put(name.toString, defn)
          case Assign(lhs, _) =>
            if (lhs.symbol != null)
              vars.remove(lhs.symbol.name.toString)
          case DefDef(_, _, _, _, _, rhs) => unwrittenVars(rhs, vars)
          case block: Block               => unwrittenVars(block, vars)
          case ClassDef(_, _, _, Template(_, _, body)) =>
            containsUnwrittenVar(body, vars)
          case ModuleDef(_, _, Template(_, _, body)) => containsUnwrittenVar(body, vars)
          case If(cond, thenp, elsep) =>
            unwrittenVars(thenp, vars)
            unwrittenVars(elsep, vars)
          case tree =>
            containsUnwrittenVar(tree.children, vars)
        }
        vars.toList
      }

      private def containsUnwrittenVar(trees: List[Tree]): List[(String, Tree)] = {
        containsUnwrittenVar(trees, mutable.HashMap[String, Tree]())
      }

      override final def inspect(tree: Tree): Unit = {
        tree match {
          case d @ DefDef(_, _, _, _, _, Block(stmt, expr)) =>
            for ((unwritten, definitionTree) <- containsUnwrittenVar(stmt :+ expr)) {
              context.warn(
                definitionTree.pos,
                self,
                s"$unwritten is never written to, so could be a val: " + definitionTree.toString().take(200)
              )
            }
          case _ => continue(tree)
        }
      }
    }
  }
}
