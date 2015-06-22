package de.sciss.mellite.gui.impl.document

import de.sciss.desktop
import de.sciss.desktop.{DocumentHandler => DH}
import de.sciss.lucre.swing.defer
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{DocumentHandler, Mellite, Workspace}
import de.sciss.model.impl.ModelImpl
import org.scalautils.TypeCheckedTripleEquals

import scala.language.existentials

/** We are bridging between the transactional and non-EDT `mellite.DocumentHandler` and
  * the GUI-based `de.sciss.desktop.DocumentHandler`. This is a bit ugly. In theory it
  * should be fine to call into either, as this bridge is backed up by the peer
  * `mellite.DocumentHandler.instance`.
  */
class DocumentHandlerImpl extends desktop.DocumentHandler with ModelImpl[DH.Update[Mellite.Document]] {
  type Document = Mellite.Document

  private def peer = DocumentHandler.instance

  private def add[S <: Sys[S]](doc: Workspace[S]): Unit =
    doc.cursor.step { implicit tx => peer.addDocument(doc) }

  def addDocument(doc: Document): Unit = add(doc.asInstanceOf[Workspace[~] forSome {type ~ <: Sys[~]}])

  def removeDocument(doc: Document): Unit = doc.close() // throw new UnsupportedOperationException()

  def documents: Iterator[Document] = peer.allDocuments

  private var _active = Option.empty[Document]

  def activeDocument: Option[Document] = _active

  def activeDocument_=(value: Option[Document]): Unit =
    if (_active != value) {
      _active = value
      value.foreach { doc => dispatch(DH.Activated(doc)) }
    }

  peer.addListener {
    case DocumentHandler.Opened(doc) => defer {
      dispatch(DH.Added(doc.asInstanceOf[Workspace[~] forSome {type ~ <: Sys[~]}]))
    }
    case DocumentHandler.Closed(doc) => defer {
      import TypeCheckedTripleEquals._
      val docC = doc.asInstanceOf[Workspace[~] forSome {type ~ <: Sys[~]}]
      if (activeDocument === Some(docC)) activeDocument = None
      dispatch(DH.Removed(docC))
    }
  }
}
