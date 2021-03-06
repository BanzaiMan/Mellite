/*
 *  ViewHandlerImpl.scala
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
package document

import de.sciss.desktop.Desktop
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing._
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.Workspace

import scala.concurrent.stm.TMap

object ViewHandlerImpl {
  import DocumentHandler.Document
  import DocumentViewHandler.WorkspaceWindow  // MMM

  def instance: DocumentViewHandler = impl

  private lazy val impl = new Impl

  // MMM
  // def mkWindow[S <: Sys[S]](doc: Workspace[S])(implicit tx: S#Tx): WorkspaceWindow[S] = impl.mkWindow(doc)

  private final class Impl extends DocumentViewHandler with ModelImpl[DocumentViewHandler.Update] {
    override def toString = "DocumentViewHandler"

    private val map     = TMap  .empty[Document, WorkspaceWindow[_]]
    private var _active = Option.empty[Document]

    Desktop.addListener {
      case Desktop.OpenFiles(_, files) =>
        // println(s"TODO: $open; EDT? ${java.awt.EventQueue.isDispatchThread}")
        files.foreach { f =>
          ActionOpenWorkspace.perform(f)
        }
    }

    def activeDocument = _active
    def activeDocument_=[S <: Sys[S]](value: Option[Workspace[S]]): Unit = {
      requireEDT()
      if (_active != value) {
        _active = value
        value.foreach { doc =>
          dispatch(DocumentViewHandler.Activated(doc))
        }
      }
    }

    def getWindow[S <: Sys[S]](doc: Workspace[S]): Option[WorkspaceWindow[S]] = {
      requireEDT()
      map.single.get(doc).asInstanceOf[Option[WorkspaceWindow[S]]]
    }

    // MMM
    //    def mkWindow[S <: Sys[S]](doc: Workspace[S])(implicit tx: S#Tx): WorkspaceWindow[S] =
    //      map.get(doc)(tx.peer).asInstanceOf[Option[WorkspaceWindow[S]]].getOrElse {
    //        val w = WorkspaceWindow(doc)
    //        map.put(doc, w)(tx.peer)
    //        doc.addDependent(new Disposable[S#Tx] {
    //          def dispose()(implicit tx: S#Tx): Unit = deferTx {
    //            logInfo(s"Remove view map entry for ${doc.folder.name}")
    //            map.single.remove(doc)
    //            if (_active == Some(doc)) activeDocument = None
    //          }
    //        })
    //        w
    //      }
  }
}