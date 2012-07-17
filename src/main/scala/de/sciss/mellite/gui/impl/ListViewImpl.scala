package de.sciss.mellite
package gui
package impl

import de.sciss.lucre.stm.{Disposable, Sys}
import de.sciss.lucre.expr.LinkedList
import swing.{ScrollPane, Component}
import javax.swing.DefaultListModel
import collection.immutable.{IndexedSeq => IIdxSeq}

object ListViewImpl {
   def apply[ S <: Sys[ S ], Elem ]( list: LinkedList[ S, Elem, _ ])( show: Elem => String )
                                   ( implicit tx: S#Tx ) : ListView[ S ] = {
      val view    = new Impl[ S ]
      val items   = list.iterator.toIndexedSeq
      guiFromTx {
         view.guiInit()
         view.addAll( items.map( show ))
      }
      val obs = list.changed.reactTx { implicit tx => {
         case LinkedList.Added(   _, idx, elem )   => guiFromTx( view.add( idx, show( elem )))
         case LinkedList.Removed( _, idx, elem )   => guiFromTx( view.remove( idx ))
         case LinkedList.Element( li, upd )        =>
            val ch = upd.foldLeft( Map.empty[ Int, String ]) { case (map0, (elem, _)) =>
               val idx = li.indexOf( elem )
               if( idx >= 0 ) {
                  map0 + (idx -> show( elem ))
               } else map0
            }
            guiFromTx {
               ch.foreach { case (idx, str) => view.update( idx, str )}
            }
      }}
      view.observer = obs
      view
   }

   private final class Impl[ S <: Sys[ S ]] extends ListView[ S ] {
      @volatile private var comp: Component = _
      private val mList  = new DefaultListModel
      var observer: Disposable[ S#Tx ] = _

      def component: Component = {
         val res = comp
         if( res == null ) sys.error( "Called component before GUI was initialized" )
         res
      }

      def guiInit() {
//         val rend = new DefaultListCellRenderer {
//            override def getListCellRendererComponent( c: JList, elem: Any, idx: Int, selected: Boolean, focused: Boolean ) : awt.Component = {
//               super.getListCellRendererComponent( c, showFun( elem.asInstanceOf[ Elem ]), idx, selected, focused )
//            }
//         }
         val ggList = new swing.ListView {
            peer.setModel( mList )
//            peer.setCellRenderer( rend )
         }
         comp = new ScrollPane( ggList )
      }

      def addAll( items: IIdxSeq[ String ]) {
         items.foreach( mList.addElement _ )
      }

      def add( idx: Int, item: String ) {
         mList.add( idx, item )
      }

      def remove( idx: Int ) {
         mList.remove( idx )
      }

      def update( idx: Int, item: String ) {
         mList.set( idx, item )
      }

      def dispose()( implicit tx: S#Tx ) {
         observer.dispose()
         guiFromTx( mList.clear() )
      }
   }
}
