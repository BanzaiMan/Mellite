/*
 *  MuteImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package tracktool

import javax.swing.undo.UndoableEdit

import de.sciss.lucre.expr.{Expr, Boolean => BooleanEx}
import de.sciss.lucre.stm
import de.sciss.lucre.synth.expr.ExprImplicits
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.synth.proc.{BooleanElem, ObjKeys, Obj}
import java.awt.{Point, Toolkit}
import java.awt.event.MouseEvent
import de.sciss.span.SpanLike
import de.sciss.mellite.gui.TrackTool.Mute
import de.sciss.lucre.synth.Sys
import org.scalautils.TypeCheckedTripleEquals

object MuteImpl {
  private lazy val cursor = {
    val tk  = Toolkit.getDefaultToolkit
    // val img = tk.createImage(Mellite.getClass.getResource("cursor-mute.png"))
    val img = ToolsImpl.getImage("cursor-mute.png")
    tk.createCustomCursor(img, new Point(4, 4), "Mute")
  }
}
final class MuteImpl[S <: Sys[S]](protected val canvas: TimelineProcCanvas[S])
  extends RegionImpl[S, Mute] {

  def defaultCursor = MuteImpl.cursor
  val name          = "Mute"
  val icon          = ToolsImpl.getIcon("mute")

  // ProcActions.toggleMute(obj)

  protected def commitObj(mute: Mute)(span: Expr[S, SpanLike], obj: Obj[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    val imp = ExprImplicits[S]
    import imp._
    val newMute: Expr[S, Boolean] = obj.attr[BooleanElem](ObjKeys.attrMute) match {
      // XXX TODO: BooleanEx should have `not` operator
      case Some(Expr.Var(vr)) => val vOld = vr().value; !vOld
      case other => !other.exists(_.value)
    }
    import TypeCheckedTripleEquals._
    val newMuteOpt = if (newMute === BooleanEx.newConst[S](false)) None else Some(newMute)
    import BooleanEx.serializer
    val edit = EditAttrMap.expr(s"Adjust $name", obj, ObjKeys.attrMute, newMuteOpt) { ex =>
      BooleanElem(BooleanEx.newVar(ex))
    }
    Some(edit)
  }

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: TimelineObjView[S]): Unit = region match {
    case hm: TimelineObjView.HasMute => dispatch(TrackTool.Adjust(Mute(!hm.muted)))
    case _ =>
  }

  //  dispatch(TrackTool.Adjust(Mute(!region.muted)))
}
