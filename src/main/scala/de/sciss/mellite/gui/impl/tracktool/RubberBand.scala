/*
 *  RubberBand.scala
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
package tracktool

import java.awt.event.{KeyEvent, KeyListener, MouseEvent}
import javax.swing.event.MouseInputAdapter

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.TrackTool.{DragEnd, DragCancel, DragRubber}
import de.sciss.span.Span

// XXX TODO --- DRY with Dragging
trait RubberBand[S <: Sys[S], A] {
  _: RegionLike[S, A] =>

  final protected def mkRubber(e: MouseEvent, hitTrack: Int, pos: Long): Unit =
    new Rubber(e, firstTrack = hitTrack, firstPos = pos)

  private[this] class Rubber(val firstEvent: MouseEvent, val firstTrack: Int, val firstPos: Long)
    extends MouseInputAdapter with KeyListener {

    private var started         = false
    private var _currentEvent   = firstEvent
    private var _currentTrack   = firstTrack
    private var _currentPos     = firstPos

    def currentEvent  = _currentEvent
    def currentTrack  = _currentTrack
    def currentPos    = _currentPos

    // ---- constructor ----
    {
      val comp = firstEvent.getComponent
      comp.addMouseListener(this)
      comp.addMouseMotionListener(this)
      comp.requestFocus() // (why? needed to receive key events?)
    }

    override def mouseReleased(e: MouseEvent): Unit = {
      unregister()
      if (started) dispatch(DragEnd)
    }

    private def unregister(): Unit = {
      val comp = firstEvent.getComponent
      comp.removeMouseListener      (this)
      comp.removeMouseMotionListener(this)
      comp.removeKeyListener        (this)
    }

    private def calcCurrent(e: MouseEvent): Unit = {
      _currentEvent = e
      _currentPos   = canvas.screenToFrame(e.getX).toLong
      _currentTrack = canvas.screenToTrack(e.getY - firstEvent.getY) + canvas.screenToTrack(firstEvent.getY)
    }

    override def mouseDragged(e: MouseEvent): Unit = {
      calcCurrent(e)
      if (!started) {
        started = currentEvent.getPoint.distanceSq(firstEvent.getPoint) > 16
        if (!started) return
        e.getComponent.addKeyListener(this)
        // dispatch(DragBegin)
      }

      val dStart  = math.min(firstPos, currentPos)
      val dStop   = math.max(dStart + BasicRegion.MinDur, math.max(firstPos, currentPos))
      val dTrkIdx = math.min(firstTrack, currentTrack)
      val dTrkH   = math.max(firstTrack, currentTrack) - dTrkIdx + 1

      val rubber  = DragRubber(trackIndex = dTrkIdx, trackHeight = dTrkH, span = Span(dStart, dStop))
      dispatch(rubber)

      val regions = canvas.findRegions(rubber).toSet
      val selMod  = canvas.selectionModel
      // XXX TODO --- not very efficient
      val toRemove  = selMod.iterator.filter(!regions.contains(_))
      val toAdd     = regions        .filter(!selMod .contains(_))
      toRemove.foreach(selMod -= _)
      toAdd   .foreach(selMod += _)
    }

    def keyPressed(e: KeyEvent): Unit =
      if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
        unregister()
        dispatch(DragCancel)
      }

    def keyTyped   (e: KeyEvent) = ()
    def keyReleased(e: KeyEvent) = ()
  }
}
