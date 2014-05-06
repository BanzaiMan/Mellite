/*
 *  FolderViewImpl.scala
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
package document

import de.sciss.synth.proc.{ProcKeys, Folder, ExprImplicits, Artifact, Obj, ArtifactLocationElem, FolderElem, StringElem}
import swing.{ScrollPane, Component}
import scala.collection.{JavaConversions, breakOut}
import collection.immutable.{IndexedSeq => Vec}
import de.sciss.lucre.stm.{Disposable, Cursor, IdentifierMap}
import de.sciss.model.impl.ModelImpl
import scala.util.control.NonFatal
import javax.swing.{DropMode, JComponent, TransferHandler}
import java.awt.datatransfer.{DataFlavor, Transferable}
import javax.swing.TransferHandler.TransferSupport
import de.sciss.treetable.{TreeTableSelectionChanged, TreeTableCellRenderer, TreeColumnModel, AbstractTreeModel, TreeTable}
import de.sciss.treetable.TreeTableCellRenderer.{State, TreeState}
import java.io.File
import de.sciss.lucre.{data, stm}
import de.sciss.synth.io.AudioFile
import scala.util.Try
import de.sciss.model.Change
import de.sciss.lucre.swing._
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.synth.proc
import proc.Implicits._
import de.sciss.lucre.event.Sys
import de.sciss.synth.proc.Folder.Update
import de.sciss.lucre.swing.TreeTableView.ModelUpdate
import de.sciss.lucre.data.Iterator

object FolderViewImpl {
  private final val DEBUG = false

  def apply[S <: Sys[S]](document: File, root: Folder[S])
                        (implicit tx: S#Tx, cursor: Cursor[S]): FolderView[S] = {
    val _doc    = document
    val _cursor = cursor

    implicit val folderSer = Folder.serializer[S]

    new Impl[S] {
      val cursor    = _cursor
      val mapViews  = tx.newInMemoryIDMap[ObjView[S]]  // folder IDs to renderers
      val document  = _doc
      val treeView  = TreeTableView(root, TTHandler)

      deferTx {
        guiInit()
      }
    }
  }

  private abstract class Impl[S <: Sys[S]]
    extends ComponentHolder[Component] with FolderView[S] with ModelImpl[FolderView.Update[S]] {
    view =>

    type Node   = ObjView.Renderer[S]
    // type Branch = ObjView.FolderLike[S]
    // type Path   = TreeTable.Path[Branch]

    protected implicit def cursor: Cursor[S]
    protected def document: File // Document[S]

    protected object TTHandler
      extends TreeTableView.Handler[S, Obj[S], Folder[S], Folder.Update[S], ObjView[S]] {

      type Data = ObjView[S]

      def branchOption(node: Obj[S]): Option[Folder[S]] = ???

      def children(branch: Folder[S])(implicit tx: S#Tx): de.sciss.lucre.data.Iterator[S#Tx, Obj[S]] = ???

      def update(update: Update[S])(implicit tx: S#Tx): Vec[ModelUpdate[Obj[S], Folder[S]]] = ???

      def renderer(view: TreeTableView[S, Obj[S], Folder[S], Data], data: Data, row: Int, column: Int,
                   state: State): Component = ???

      lazy val columns: TreeColumnModel[Data] = {
        val colName = new TreeColumnModel.Column[Data, String]("Name") {
          def apply(node: Data): String = ??? // node.name

          def update(data: Data, value: String): Unit =
          ???
//            data match {
//              case v: ObjView[S] if value != v.name =>
//                cursor.step { implicit tx =>
//                  v.obj().attr.name = value
//                }
//              case _ =>
//            }

          def isEditable(data: Data) = data match {
            case b: ObjView[S] => true
            case _ => false // i.e. Root
          }
        }

        val colValue = new TreeColumnModel.Column[Data, Any]("Value") {
          def apply(node: Data): Any = ??? // node.value

          def update(node: Data, value: Any): Unit =
            ??? // cursor.step { implicit tx => node.tryUpdate(value) }

          def isEditable(data: Data) = data.isEditable
        }

        new TreeColumnModel.Tuple2[Data, String, Any](colName, colValue) {
          def getParent(node: Data): Option[Data] = ??? // node.parent
        }
      }

      def data(node: Obj[S])(implicit tx: S#Tx): Data = ???
    }

    protected def treeView: TreeTableView[S, Obj[S], Folder[S], ObjView[S]]

//        def updateName(n: String): Unit =
//          deferTx {
//            v.name = n
//            _model.elemUpdated(v)
//          }
//
//          case Obj.AttrRemoved(ProcKeys.attrName, _)                             => updateName("<Unnamed>")
//          case Obj.AttrAdded  (ProcKeys.attrName, s: StringElem[S])              => updateName(s.peer.value)
//          case Obj.AttrChange (ProcKeys.attrName, _, Change(_, newName: String)) => updateName(newName)

    def dispose()(implicit tx: S#Tx): Unit = {
      treeView.dispose()
    }

    protected def guiInit(): Unit = {
      val t = treeView.treeTable
      t.rootVisible = false
      t.renderer    = new TreeTableCellRenderer {
        private val component = TreeTableCellRenderer.Default
        def getRendererComponent(treeTable: TreeTable[_, _], value: Any, row: Int, column: Int,
                                 state: TreeTableCellRenderer.State): Component = {
          val value1 = if (value != {}) value else null
          val res = component.getRendererComponent(treeTable, value1, row = row, column = column, state = state)
          if (row >= 0) state.tree match {
            case Some(TreeState(false, true)) =>
              // println(s"row = $row, col = $column")
              try {
                val node = t.getNode(row)
                component.icon = ??? // node.icon
              } catch {
                case NonFatal(_) => // XXX TODO -- currently NPE problems; seems renderer is called before tree expansion with node missing
              }
            case _ =>
          }
          res // component
        }
      }
    //      val tabCM = t.peer.getColumnModel
    //      tabCM.getColumn(0).setPreferredWidth(176)
    //      tabCM.getColumn(1).setPreferredWidth(256)

      t.listenTo(t.selection)
      t.reactions += {
        case e: TreeTableSelectionChanged[_, _] =>  // this crappy untyped event doesn't help us at all
          // println(s"selection: $e")
          dispatch(FolderView.SelectionChanged(view, selection))
        // case e => println(s"other: $e")
      }
      t.showsRootHandles  = true
      // t.expandPath(TreeTable.Path(_model.root))
      t.dragEnabled       = true
      t.dropMode          = DropMode.ON_OR_INSERT_ROWS
      t.peer.setTransferHandler(new TransferHandler {
        // ---- export ----

        override def getSourceActions(c: JComponent): Int =
          TransferHandler.COPY | TransferHandler.MOVE // dragging only works when MOVE is included. Why?

      //        override def createTransferable(c: JComponent): Transferable = {
      //          val sel   = selection
      //          val tSel  = DragAndDrop.Transferable(FolderView.selectionFlavor) {
      //            new FolderView.SelectionDnDData(document, selection)
      //          }
      //          // except for the general selection flavour, see if there is more specific types
      //          // (current Int and Code are supported)
      //          sel.headOption match {
      //            case Some((_, elemView: ObjView.Int[S])) =>
      //              val elem  = elemView.obj
      //              val tElem = DragAndDrop.Transferable(timeline.DnD.flavor) {
      //                timeline.DnD.IntDrag[S](document, elem)
      //              }
      //              DragAndDrop.Transferable.seq(tSel, tElem)
      //
      //            case Some((_, elemView: ObjView.Code[S])) /* if elemView.value.id == Code.SynthGraph.id */ =>
      //              val elem  = elemView.obj
      //              val tElem = DragAndDrop.Transferable(timeline.DnD.flavor) {
      //                timeline.DnD.CodeDrag[S](document, elem)
      //              }
      //              DragAndDrop.Transferable.seq(tSel, tElem)
      //
      //            case _ => tSel
      //          }
      //        }

        // ---- import ----
        override def canImport(support: TransferSupport): Boolean =
          t.dropLocation match {
            case Some(tdl) =>
              // println(s"last = ${tdl.path.last}; column ${tdl.column}; isLeaf? ${t.treeModel.isLeaf(tdl.path.last)}")
              val locOk = tdl.index >= 0 || (tdl.column == 0 && (tdl.path.last match {
                case _: ObjView.Folder[_] => true
                case _                    => false
              }))

              if (locOk) {
                // println("Supported flavours:")
                // support.getDataFlavors.foreach(println)

                // println(s"File? ${support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)}")
                // println(s"Action = ${support.getUserDropAction}")

                support   .isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                  (support.isDataFlavorSupported(FolderView.selectionFlavor   ) &&
                   support.getUserDropAction == TransferHandler.MOVE)

              } else {
                false
              }

            case _ => false
          }

//        // XXX TODO: not sure whether removal should be in exportDone or something
//        private def insertData(sel: FolderView.Selection[S], newParentView: Branch, idx: Int): Boolean = {
//          // println(s"insert into $parent at index $idx")
//
//          def isNested(pv: Branch, cv: Branch): Boolean =
//            pv == cv || pv.children.collect {
//              case pcv: ObjView.Folder[S] => pcv
//            }.exists(isNested(_, cv))
//
//          // make sure we are not moving a folder within itself (it will magically disappear :)
//          val sel1 = sel.filter {
//            case (_, cv: ObjView.Folder[S]) if isNested(cv, newParentView) => false
//            case _ => true
//          }
//
//          // if we move children within the same folder, adjust the insertion index by
//          // decrementing it for any child which is above the insertion index, because
//          // we will first remove all children, then re-insert them.
//          val idx0 = if (idx >= 0) idx else newParentView.children.size
//          val idx1 = idx0 - sel1.count {
//            case (_ :+ `newParentView`, cv) => newParentView.children.indexOf(cv) <= idx0
//            case _ => false
//          }
//          // println(s"idx0 $idx0 idx1 $idx1")
//
//          cursor.step { implicit tx =>
//            val tup = sel1.map {
//              case (_ :+ pv, cv) => pv.folder() -> cv.obj()
//            }
//
//            val newParent = newParentView.folder()
//            tup             .foreach { case  (oldParent, c)       => oldParent.remove(            c) }
//            tup.zipWithIndex.foreach { case ((_        , c), off) => newParent.insert(idx1 + off, c) }
//          }
//
//          true
//        }
//
//        private def importSelection(support: TransferSupport, parent: ObjView.FolderLike[S], index: Int): Boolean = {
//          val data = support.getTransferable.getTransferData(FolderView.selectionFlavor)
//            .asInstanceOf[FolderView.SelectionDnDData[S]]
//          if (data.document == document) {
//            val sel = data.selection
//            insertData(sel, parent, index)
//          } else {
//            false
//          }
//        }
//
//        private def importFiles(support: TransferSupport, parent: ObjView.FolderLike[S], index: Int): Boolean = {
//          import JavaConversions._
//          val data  = support.getTransferable.getTransferData(DataFlavor.javaFileListFlavor)
//            .asInstanceOf[java.util.List[File]].toList
//          val tup   = data.flatMap { f =>
//            Try(AudioFile.readSpec(f)).toOption.map(f -> _)
//          }
//          val trip  = tup.flatMap { case (f, spec) =>
//            findLocation(f).map { loc => (f, spec, loc) }
//          }
//
//          if (trip.nonEmpty) {
//            cursor.step { implicit tx =>
//              trip.foreach {
//                case (f, spec, locS) =>
//                  val loc = locS()
//                  loc.elem.peer.modifiableOption.foreach { locM =>
//                    ObjectActions.addAudioFile(parent.folder(), index, locM, f, spec)
//                  }
//              }
//            }
//            true
//
//          } else {
//            false
//          }
//        }

        override def importData(support: TransferSupport): Boolean =
        ???
//          t.dropLocation match {
//            case Some(tdl) =>
//              tdl.path.last match {
//                case parent: ObjView.FolderLike[S] =>
//                  val idx = tdl.index
//                  if      (support.isDataFlavorSupported(FolderView.selectionFlavor   ))
//                    importSelection(support, parent, idx)
//                  else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
//                    importFiles    (support, parent, idx)
//                  else false
//
//                case _ => false
//              }
//
//            case _ => false
//          }
      })

      component     = treeView.component
    }

    def selection: FolderView.Selection[S] = treeView.selection

    def insertionPoint(implicit tx: S#Tx): (Folder[S], Int) = treeView.insertionPoint

    def locations: Vec[ObjView.ArtifactLocation[S]] = selection.flatMap { nodeView =>
      nodeView.renderData match {
        case view: ObjView.ArtifactLocation[S] => Some(view)
        case _ => None
      }
    } (breakOut)

    def findLocation(f: File): Option[stm.Source[S#Tx, Obj.T[S, ArtifactLocationElem]]] = {
      val locsOk = locations.flatMap { view =>
        try {
          Artifact.relativize(view.directory, f)
          Some(view)
        } catch {
          case NonFatal(_) => None
        }
      } .headOption

      locsOk match {
        case Some(loc)  => Some(loc.obj)
        case _          =>
          val parent = selection.flatMap { nodeView =>
            nodeView.renderData match {
              case f: ObjView.Folder[S] => Some(f.obj)
              case _ => None
            }
          } .headOption
          ActionArtifactLocation.query(treeView.root, file = f, folder = parent) // , window = Some(comp))
      }
    }

    //    object PathExtractor {
    //      def unapply(path: Seq[Node]): Option[(Vec[ObjView.FolderLike[S]], ObjView[S])] =
    //        path match {
    //          case init :+ (last: ObjView[S]) =>
    //            val pre: Vec[ObjView.FolderLike[S]] = init.map({
    //              case g: ObjView.FolderLike[S] => g
    //              case _ => return None
    //            })(breakOut)
    //            Some((/* _root +: */ pre, last))
    //          case _ => None
    //        }
    //    }
  }
}