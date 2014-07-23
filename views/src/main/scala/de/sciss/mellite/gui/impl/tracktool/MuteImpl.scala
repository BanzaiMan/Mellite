/*
 *  MuteImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
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

import de.sciss.synth.proc.{Obj, Proc}
import java.awt.{Point, Toolkit}
import java.awt.event.MouseEvent
import de.sciss.lucre.expr.Expr
import de.sciss.span.SpanLike
import de.sciss.mellite.gui.TrackTool.Mute
import de.sciss.lucre.synth.Sys

object MuteImpl {
  private lazy val cursor = {
    val tk  = Toolkit.getDefaultToolkit
    val img = tk.createImage(Mellite.getClass.getResource("cursor-mute.png"))
    tk.createCustomCursor(img, new Point(4, 4), "Mute")
  }
}
final class MuteImpl[S <: Sys[S]](protected val canvas: TimelineProcCanvas[S])
  extends RegionImpl[S, Mute] {

  def defaultCursor = MuteImpl.cursor
  val name          = "Mute"
  val icon          = ToolsImpl.getIcon("mute")

  protected def commitObj(mute: Mute)(span: Expr[S, SpanLike], obj: Obj[S])(implicit tx: S#Tx): Unit =
    ProcActions.toggleMute(obj)

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: TimelineObjView[S]): Unit = region match {
    case hm: TimelineObjView.HasMute => dispatch(TrackTool.Adjust(Mute(!hm.muted)))
    case _ =>
  }

  //  dispatch(TrackTool.Adjust(Mute(!region.muted)))
}