/*
 *  ElementView.scala
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

import javax.swing.Icon

import de.sciss.desktop
import de.sciss.lucre.event.Observable
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Color, Workspace}

import scala.language.higherKinds

object ObjView {
  /** Standard `AttrMap` key whose value is of type `Color.Obj`. */
  final val attrColor         = "color"

  final val categPrimitives   = "Primitives"
  final val categResources    = "Resources"
  final val categComposition  = "Composition"
  // final val categSound        = "Sound"
  final val categOrganisation = "Organisation"
  final val categMisc         = "Miscellaneous"

  trait Factory {
    def prefix    : String
    def humanName : String
    def icon      : Icon
    def tpe       : Obj.Type

    type Config[S <: stm.Sys[S]]

    type E[~ <: stm.Sys[~]] <: Obj[~]

    /** Whether it is possible to create an instance of the object via a GUI dialog. */
    def hasMakeDialog: Boolean

    /** Provides an optional initial configuration for the make-new-instance dialog. */
    def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit cursor: stm.Cursor[S]): Unit

    def category: String

    /** Creates an object from a configuration.
      * The reason that the result type is not `Obj.T[S, E]` is
      * that we allow the returning of a number of auxiliary other objects as well.
      */
    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]]
  }

  trait Update[S <: stm.Sys[S]] {
    def view: ObjView[S]
  }
  final case class Repaint[S <: stm.Sys[S]](view: ObjView[S]) extends Update[S]
}
trait ObjView[S <: stm.Sys[S]]
  extends Disposable[S#Tx]
  with Observable[S#Tx, ObjView.Update[S]] /* Model[ObjView.Update[S]] */ {

  def factory: ObjView.Factory
  def humanName: String

  /** The contents of the `"name"` attribute of the object. This is directly
    * set by the table tree view. The object view itself must only make sure that
    * an initial value is provided.
    */
  var nameOption: Option[String]

  var colorOption: Option[Color]

  /** Convenience method that returns an "unnamed" string if no name is set. */
  def name: String = nameOption.getOrElse(TimelineObjView.Unnamed)

  /** A view must provide an icon for the user interface. It should have a dimension of 32 x 32 and ideally
    * be drawn as vector graphics in order to look good when applying scaling.
    */
  def icon  : Icon

  def objH: stm.Source[S#Tx, Obj[S]]

  /** The view must store a handle to its underlying model. */
  def obj(implicit tx: S#Tx): Obj[S]

  /** Whether a dedicated view/editor window exists for this type of object. */
  def isViewable: Boolean

  /** If the object is viewable, this method is invoked when the user pressed the eye button.
    * The method should return an appropriate view for this object, or `None` if no editor or viewer window
    * can be produced.
    */
  def openView(parent: Option[Window[S]])
              (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]]
}