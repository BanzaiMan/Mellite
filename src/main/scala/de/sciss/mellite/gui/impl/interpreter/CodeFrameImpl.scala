/*
 *  CodeFrameImpl.scala
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
package interpreter

import javax.swing.event.{AncestorEvent, AncestorListener}
import javax.swing.undo.UndoableEdit

import de.sciss.desktop.impl.UndoManagerImpl
import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.fscape.lucre.FScape
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{IDPeek, Obj}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.{CellView, View, defer, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.impl.ActionImpl
import de.sciss.synth.proc.{Action, Code, Proc, SynthGraphObj, Workspace}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.{Component, Orientation, ProgressBar, SplitPane}

object CodeFrameImpl {
  // ---- adapter for editing a Proc's source ----

  def proc[S <: Sys[S]](obj: Proc[S])
                       (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                        compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = mkSource(obj = obj, codeID = Code.SynthGraph.id, key = Proc.attrSource,
      init = {
        val txt     = ProcActions.extractSource(obj.graph.value)
        val comment = if (txt.isEmpty)
            "SoundProcesses graph function source code"
          else
            "source code automatically extracted"
        s"// $comment\n\n$txt"
      })
    
    val codeEx0 = codeObj
    val objH    = tx.newHandle(obj)
    val code0   = codeEx0.value match {
      case cs: Code.SynthGraph => cs
      case other => sys.error(s"Proc source code does not produce SynthGraph: ${other.contextName}")
    }

    val handler = new CodeView.Handler[S, Unit, SynthGraph] {
      def in() = ()

      def save(in: Unit, out: SynthGraph)(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        implicit val sgTpe = SynthGraphObj
        EditVar.Expr[S, SynthGraph, SynthGraphObj]("Change SynthGraph", obj.graph, SynthGraphObj.newConst[S](out))
      }

      def dispose()(implicit tx: S#Tx) = ()
    }

    implicit val undo = new UndoManagerImpl
    val rightView = OutputsView[S](obj) // SCANS

    // XXX TODO --- bottom could have play/power button similar to ensemble; even a meter
    make(obj, codeObj, code0, Some(handler), bottom = Nil, rightViewOpt = Some(rightView))
  }

  // ---- adapter for editing a Action's source ----

  def action[S <: Sys[S]](obj: Action[S])
                         (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                          compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = mkSource(obj = obj, codeID = Code.Action.id, key = Action.attrSource,
      init = "// Action source code\n\n")

    val codeEx0 = codeObj
    val code0   = codeEx0.value match {
      case cs: Code.Action => cs
      case other => sys.error(s"Action source code does not produce plain function: ${other.contextName}")
    }

    val (handlerOpt, bottom) = obj match {
      case Action.Var(vr) =>
        val varH  = tx.newHandle(vr)
        val objH  = tx.newHandle(obj)
        val handler = new CodeView.Handler[S, String, Array[Byte]] {
          def in(): String = cursor.step { implicit tx =>
            val id = tx.newID()
            val cnt = IDPeek(id)
            s"Action$cnt"
          }

          def save(in: String, out: Array[Byte])(implicit tx: S#Tx): UndoableEdit = {
            val obj = varH()
            val value = ActionImpl.newConst[S](name = in, jar = out)
            EditVar[S, Action[S], Action.Var[S]](name = "Change Action Body", expr = obj, value = value)
          }

          def dispose()(implicit tx: S#Tx) = ()
        }

        val viewExecute = View.wrap[S] {
          val actionExecute = swing.Action(null) {
            cursor.step { implicit tx =>
              val obj = objH()
              val universe = Action.Universe(obj, workspace)
              obj.execute(universe)
            }
          }
          GUI.toolButton(actionExecute, raphael.Shapes.Bolt, tooltip = "Run body")
        }

        (Some(handler), viewExecute :: Nil)

      case _ => (None, Nil)
    }

    implicit val undo = new UndoManagerImpl
    make(obj, codeObj, code0, handlerOpt, bottom = bottom, rightViewOpt = None)
  }

  // ---- adapter for editing an FScape's source ----

  def fscape[S <: Sys[S]](obj: FScape[S])
                         (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                          compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = mkSource(obj = obj, codeID = FScape.Code.id, key = FScape.attrSource,
      init = "// FScape graph function source code\n\n")

    val codeEx0 = codeObj
    val objH    = tx.newHandle(obj)
    val code0   = codeEx0.value match {
      case cs: FScape.Code => cs
      case other => sys.error(s"FScape source code does not produce fscape.Graph: ${other.contextName}")
    }

    import de.sciss.fscape.Graph
    import de.sciss.fscape.lucre.GraphObj
    import de.sciss.fscape.stream.Control

    val handler = new CodeView.Handler[S, Unit, Graph] {
      def in(): Unit = ()

      def save(in: Unit, out: Graph)(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        implicit val tpe = GraphObj
        EditVar.Expr[S, Graph, GraphObj]("Change FScape Graph", obj.graph, GraphObj.newConst[S](out))
      }

      def dispose()(implicit tx: S#Tx) = ()
    }

    val renderRef = Ref(Option.empty[FScape.Rendering[S]])

    lazy val ggProgress: ProgressBar = new ProgressBar {
      max = 160
    }

    val viewProgress = View.wrap[S](ggProgress)

    lazy val actionCancel: swing.Action = new swing.Action(null) {
      def apply(): Unit = cursor.step { implicit tx =>
        renderRef.swap(None)(tx.peer).foreach(_.cancel())
      }
      enabled = false
    }

    val viewCancel = View.wrap[S] {
      GUI.toolButton(actionCancel, raphael.Shapes.Cross, tooltip = "Abort Rendering")
    }

    // XXX TODO --- should use custom view so we can cancel upon `dispose`
    val viewRender = View.wrap[S] {
      val actionRender = new swing.Action("Render") { self =>
        def apply(): Unit = cursor.step { implicit tx =>
          if (renderRef.get(tx.peer).isEmpty) {
            val obj       = objH()
            val config    = Control.Config()
             config.progressReporter = { report =>
               defer {
                 ggProgress.value = (report.total * ggProgress.max).toInt
               }
             }
            // config.blockSize
            // config.nodeBufferSize
            // config.executionContext
            // config.seed

            def finished()(implicit tx: S#Tx): Unit = {
              renderRef.set(None)(tx.peer)
              deferTx {
                actionCancel.enabled  = false
                self.enabled          = true
              }
            }

            val rendering = obj.run(config)
            /* val obs = */ rendering.reactNow { implicit tx => {
              case FScape.Rendering.Success => finished()
              case FScape.Rendering.Failure(FScape.Rendering.Cancelled()) => finished()
              case FScape.Rendering.Failure(ex) =>
                finished()
                deferTx(ex.printStackTrace())
              case FScape.Rendering.Running =>
                deferTx {
                  actionCancel.enabled = true
                  self        .enabled = false
                }
            }}
            renderRef.set(Some(rendering))(tx.peer)
          }
        }
      }
      GUI.toolButton(actionRender, Shapes.Sparks)
    }

    val bottom = viewProgress :: viewCancel :: viewRender :: Nil

    implicit val undo = new UndoManagerImpl
    make(obj, codeObj, code0, Some(handler), bottom = bottom, rightViewOpt = None)
  }

  // ---- general constructor ----

  def apply[S <: Sys[S]](obj: Code.Obj[S], bottom: ISeq[View[S]])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         compiler: Code.Compiler): CodeFrame[S] = {
    val _codeEx = obj
    val _code   = _codeEx.value
    implicit val undo = new UndoManagerImpl
    make[S, _code.In, _code.Out](obj, obj, _code, None, bottom = bottom, rightViewOpt = None)
  }
  
  private def make[S <: Sys[S], In0, Out0](pObj: Obj[S], obj: Code.Obj[S], code0: Code { type In = In0; type Out = Out0 },
                                handler: Option[CodeView.Handler[S, In0, Out0]], bottom: ISeq[View[S]],
                                rightViewOpt: Option[View[S]])
                               (implicit tx: S#Tx, ws: Workspace[S], csr: stm.Cursor[S],
                                undoMgr: UndoManager, compiler: Code.Compiler): CodeFrame[S] = {
    // val _name   = /* title getOrElse */ obj.attr.name
    val codeView  = CodeView(obj, code0, bottom = bottom)(handler)
    val view      = rightViewOpt.fold[View[S]](codeView) { bottomView =>
      new View.Editable[S] with ViewHasWorkspace[S] {
        val undoManager = undoMgr
        val cursor      = csr
        val workspace   = ws

        lazy val component: Component = {
          val res = new SplitPane(Orientation.Vertical, codeView.component, bottomView.component)
          res.oneTouchExpandable  = true
          res.resizeWeight        = 1.0
          // cf. https://stackoverflow.com/questions/4934499
          res.peer.addAncestorListener(new AncestorListener {
            def ancestorAdded  (e: AncestorEvent): Unit = res.dividerLocation = 1.0
            def ancestorRemoved(e: AncestorEvent): Unit = ()
            def ancestorMoved  (e: AncestorEvent): Unit = ()
          })
          res
        }

        def dispose()(implicit tx: S#Tx): Unit = {
          codeView  .dispose()
          bottomView.dispose()
        }
      }
    }
    val _name = AttrCellView.name(pObj)
    val res = new FrameImpl(codeView = codeView, view = view, name = _name, contextName = code0.contextName)
    res.init()
    res
  }

  // ---- util ----

  private def mkSource[S <: Sys[S]](obj: Obj[S], codeID: Int, key: String, init: => String)
                                   (implicit tx: S#Tx): Code.Obj[S] = {
    // if there is no source code attached,
    // create a new code object and add it to the attribute map.
    // let's just do that without undo manager
    val codeObj = obj.attr.get(key) match {
      case Some(c: Code.Obj[S]) => c
      case _ =>
        val source  = init
        val code    = Code(codeID, source)
        val c       = Code.Obj.newVar(Code.Obj.newConst[S](code))
        obj.attr.put(key, c)
        c
    }
    codeObj
  }

  // ---- frame impl ----

  private final class FrameImpl[S <: Sys[S]](val codeView: CodeView[S], val view: View[S],
                                             name: CellView[S#Tx, String], contextName: String)
    extends WindowImpl[S](name.map(n => s"$n : $contextName Code")) with CodeFrame[S] {

    override protected def checkClose(): Boolean = {
      if (codeView.isCompiling) {
        // ggStatus.text = "busy!"
        return false
      }

      !codeView.dirty || {
        val message = "The code has been edited.\nDo you want to save the changes?"
        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
          messageType = OptionPane.Message.Warning)
        opt.title = s"Close - $title"
        opt.show(Some(window)) match {
          case OptionPane.Result.No => true
          case OptionPane.Result.Yes =>
            /* val fut = */ codeView.save()
            true

          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
            false
        }
      }
    }

    // override def style = Window.Auxiliary
  }
}