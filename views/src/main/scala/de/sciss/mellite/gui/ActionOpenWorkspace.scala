/*
 *  ActionOpenWorkspace.scala
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

import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

import de.sciss.desktop
import de.sciss.desktop.{KeyStrokes, OptionPane, FileDialog, Menu, RecentFiles}
import de.sciss.file._
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.lucre.swing.defer
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.SoundProcesses

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, blocking}
import scala.language.existentials
import scala.swing.event.Key
import scala.swing.{Action, Dialog}
import scala.util.{Failure, Success}

object ActionOpenWorkspace extends Action("Open...") {
  import KeyStrokes._

  private val _recent = RecentFiles(Application.userPrefs("recent-docs")) { folder =>
    perform(folder)
  }

  accelerator = Some(menu1 + Key.O)

  private def fullTitle = "Open Workspace"

  // XXX TODO: should be in another place
  def openGUI[S <: Sys[S]](doc: Workspace[S]): Unit = {
    recentFiles.add(doc.folder)
    Application.documentHandler.addDocument(doc)
    doc match {
      case cf: Workspace.Confluent =>
        implicit val workspace: Workspace.Confluent  = cf
        implicit val cursor = workspace.system.durable
        GUI.atomic[proc.Durable, Unit](fullTitle, s"Opening cursor window for '${doc.folder.base}'") {
          implicit tx => DocumentCursorsFrame(cf)
        }
      case eph: Workspace.Ephemeral =>
        implicit val workspace  = eph
        implicit val cursor     = eph.cursor
        val nameView = ExprView.const[proc.Durable, String](doc.folder.base)
        GUI.atomic[proc.Durable, Unit](fullTitle, s"Opening root elements window for '${doc.folder.base}'") {
          implicit tx => FolderFrame[proc.Durable](name = nameView, isWorkspaceRoot = true)
        }
    }
  }

  def recentFiles: RecentFiles  = _recent
  def recentMenu : Menu.Group   = _recent.menu

  // private def isWebLaF = javax.swing.UIManager.getLookAndFeel.getName == "WebLookAndFeel"

  def apply(): Unit = {
    val dlg = FileDialog.open(title = fullTitle)
    // if (!isWebLaF) {
      // filter currently doesn't work with WebLaF
      dlg.setFilter { f => f.isDirectory && f.ext.toLowerCase == Workspace.ext}
    // }
    dlg.show(None).foreach(perform)
  }

  private def openView[S <: Sys[S]](doc: Workspace[S]): Unit = ()
// MMM
//    DocumentViewHandler.instance(doc).collectFirst {
//      case dcv: DocumentCursorsView => dcv.window
//    } .foreach(_.front())

  def perform(folder: File): Unit =
    Application.documentHandler.documents.find(_.folder == folder).fold(doOpen(folder)) { doc =>

      val doc1 = doc.asInstanceOf[Workspace[S] forSome { type S <: Sys[S] }]
      openView(doc1)
    }

  private def doOpen(folder: File): Unit = {
    import SoundProcesses.executionContext
    val config          = BerkeleyDB.Config()
    config.lockTimeout  = Duration(Prefs.dbLockTimeout.getOrElse(Prefs.defaultDbLockTimeout), TimeUnit.MILLISECONDS)
    val fut: Future[WorkspaceLike] = Future(blocking(Workspace.read(folder, config)))

    var opt: OptionPane[Unit] = null
    desktop.Util.delay(1000) {
      if (!fut.isCompleted) {
        opt = OptionPane.message(message = s"Reading '$folder'…")
        opt.show(None, "Open Workspace")
      }
    }
    fut.onComplete { tr =>
      defer {
        if (opt != null) {
          val w = SwingUtilities.getWindowAncestor(opt.peer)
          if (w != null) w.dispose()
        }
        tr match {
          case Success(cf : Workspace.Confluent) => openGUI(cf )
          case Success(dur: Workspace.Ephemeral) => openGUI(dur)
          case Failure(e) =>
            Dialog.showMessage(
              message     = s"Unable to create new workspace ${folder.path}\n\n${GUI.formatException(e)}",
              title       = fullTitle,
              messageType = Dialog.Message.Error
            )
          // case _ =>
        }
      }
    }
  }
}