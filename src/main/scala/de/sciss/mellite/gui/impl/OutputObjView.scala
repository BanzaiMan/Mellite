/*
 *  OutputObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2016 Hanns Holger Rutz. All rights reserved.
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

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Output, Workspace}

object OutputObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Output[~]
  val icon          = ObjViewImpl.raphaelIcon(raphael.Shapes.Export)
  val prefix        = "Output"
  def humanName     = prefix
  def tpe           = Output
  def category      = ObjView.categMisc
  def hasMakeDialog = false

  def mkListView[S <: Sys[S]](obj: Output[S])(implicit tx: S#Tx): OutputObjView[S] with ListObjView[S] = {
    val value = obj.key
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  type Config[S <: stm.Sys[S]] = Unit

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = ()

  def makeObj[S <: Sys[S]](config: Unit)(implicit tx: S#Tx): List[Obj[S]] = Nil

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Output[S]], val value: String)
    extends OutputObjView[S]
      with ListObjView[S]
      with ObjViewImpl    .Impl[S]
      with ListObjViewImpl.StringRenderer
      with ObjViewImpl    .NonViewable[S]
      with ListObjViewImpl.NonEditable[S] {

    override def obj(implicit tx: S#Tx) = objH()

    def factory = OutputObjView
  }
}
trait OutputObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Output[S]
}