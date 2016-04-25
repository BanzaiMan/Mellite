/*
 *  TimelineObjViewBasicImpl.scala
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
package timeline

import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.Curve

import scala.swing._

trait TimelineObjViewBasicImpl[S <: stm.Sys[S]] extends TimelineObjView[S] with ObjViewImpl.Impl[S] {
  var trackIndex  : Int = _
  var trackHeight : Int = _
  // var nameOption  : Option[String] = _
  var spanValue   : SpanLike = _
  var spanH       : stm.Source[S#Tx, SpanLikeObj[S]] = _

  protected var idH  : stm.Source[S#Tx, S#ID] = _

  def span(implicit tx: S#Tx) = spanH()
  def id  (implicit tx: S#Tx) = idH()

  def initAttrs(id: S#ID, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
    val attr      = obj.attr

    implicit val intTpe = IntObj
    val trackIdxView = AttrCellView[S, Int, IntObj](attr, TimelineObjView.attrTrackIndex)
    disposables ::= trackIdxView.react { implicit tx => opt =>
      deferTx {
        trackIndex = opt.getOrElse(0)
      }
      fire(ObjView.Repaint(this))
    }
    trackIndex   = trackIdxView().getOrElse(0)

    val trackHView = AttrCellView[S, Int, IntObj](attr, TimelineObjView.attrTrackHeight)
    disposables ::= trackHView.react { implicit tx => opt =>
      deferTx {
        trackHeight = opt.getOrElse(4)
      }
      fire(ObjView.Repaint(this))
    }
    trackHeight   = trackHView().getOrElse(4)

    spanH         = tx.newHandle(span)
    spanValue     = span.value
    idH           = tx.newHandle(id)
    initAttrs(obj)
  }

  protected def paintInner(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, x: Int, y: Int, w: Int, h: Int,
                           px1C: Int, px2C: Int, selected: Boolean): Unit = ()

  def paintBack(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit = {
    val selected        = tlv.selectionModel.contains(this)
    val canvas          = tlv.canvas
    val trackTools      = canvas.trackTools
    val regionViewMode  = trackTools.regionViewMode
    var start           = Long.MinValue
    var x1              = -5
    val peer            = canvas.canvasComponent.peer
    val w               = peer.getWidth
    // val h            = peer.getHeight
    var x2              = w + 5

    var move            = 0L
    import canvas.{frameToScreen, trackToScreen, framesToScreen}
    import r.{ttFadeState => fadeState, ttMoveState => moveState, ttResizeState => resizeState}
    import r.clipRect

    def adjustStart(start: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStart
        if (dt0 >= 0) dt0 else {
          val total = tlv.timelineModel.bounds
          math.max(-(start - total.start), dt0)
        }
      } else 0L

    def adjustStop(stop: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStop
        if (dt0 >= 0) dt0 else {
          val total = tlv.timelineModel.bounds
          math.max(-(stop - total.start + TimelineView.MinDur), dt0)
        }
      } else 0L

    def adjustMove(start: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime
        if (dt0 >= 0) dt0 else {
          val total = tlv.timelineModel.bounds
          math.max(-(start - total.start), dt0)
        }
      } else 0L

    spanValue match {
      case Span(_start, stop) =>
        start         = _start
        val dStart    = adjustStart(start)
        val dStop     = adjustStop (stop )
        val newStart  = start + dStart
        val newStop   = math.max(newStart + TimelineView.MinDur, stop + dStop)
        x1            = frameToScreen(newStart).toInt
        x2            = frameToScreen(newStop ).toInt
        move          = adjustMove(start)

      case Span.From(_start) =>
        start         = _start
        val dStart    = adjustStart(start)
        val newStart  = start + dStart
        x1            = frameToScreen(newStart).toInt
        // x2         = w + 5
        move          = adjustMove(start)

      case Span.Until(stop) =>
        val dStop     = adjustStop(stop)
        val newStop   = stop + dStop
        x2            = frameToScreen(newStop).toInt
      // start         = Long.MinValue
      // x1         = -5
      // move       = 0L

      case Span.All =>
      // start         = Long.MinValue
      // x1            = -5
      // x2            = w + 5
      // move            = 0L

      case _ => // don't draw Span.Void
        return
    }

    val pTrk  = if (selected) math.max(0, trackIndex + moveState.deltaTrack) else trackIndex
    val py    = trackToScreen(pTrk)
    val px    = x1
    val pw    = x2 - x1
    val ph    = trackToScreen(pTrk + trackHeight) - py

    // clipped coordinates
    val px1C    = math.max(px +  1, clipRect.x - 2)
    val px2C    = math.min(px + pw, clipRect.x + clipRect.width + 3)

    if (px1C < px2C) {  // skip this if we are not overlapping with clip
      import r.{pntBackground, pntFadeFill, pntFadeOutline, pntNameDark, pntNameLight, pntNameShadowDark, pntNameShadowLight, pntRegionBackground, pntRegionBackgroundMuted, pntRegionBackgroundSelected, pntRegionOutline, pntRegionOutlineSelected, regionTitleBaseline, regionTitleHeight, shape1, shape2}

      if (regionViewMode != RegionViewMode.None) {
        g.translate(px, py)
        g.setPaint(if (selected) pntRegionOutlineSelected    else pntRegionOutline   )
        g.fillRoundRect(0, 0, pw, ph, 5, 5)
        g.setPaint(if (selected) pntRegionBackgroundSelected else pntRegionBackground)
        g.fillRoundRect(1, 1, pw - 2, ph - 2, 4, 4)
        g.translate(-px, -py)
      }
      g.setPaint(pntBackground)
      g.drawLine(px - 1, py, px - 1, py + ph - 1) // better distinguish directly neighbouring regions

      val hndl = regionViewMode match {
        case RegionViewMode.None       => 0
        case RegionViewMode.Box        => 1
        case RegionViewMode.TitledBox  => regionTitleHeight
      }

      val innerH    = ph - (hndl + 1)
      val innerY    = py + hndl
      val clipOrig  = g.getClip
      g.clipRect(px + 1, innerY, pw - 2, innerH)

      paintInner(g, tlv = tlv, r = r, x = px, y = innerY, w = pw, h = innerH, px1C = px1C, px2C = px2C,
        selected = selected)

      // --- fades ---
      def paintFade(curve: Curve, fw: Float, y1: Float, y2: Float, x: Float, x0: Float): Unit = {
        import math.{log10, max}
        shape1.reset()
        shape2.reset()
        val vScale  = innerH / -3f
        val y1s     = max(-3, log10(y1)) * vScale + innerY
        shape1.moveTo(x, y1s)
        shape2.moveTo(x, y1s)
        var xs = 4
        while (xs < fw) {
          val ys = max(-3, log10(curve.levelAt(xs / fw, y1, y2))) * vScale + innerY
          shape1.lineTo(x + xs, ys)
          shape2.lineTo(x + xs, ys)
          xs += 3
        }
        val y2s     = max(-3, log10(y2)) * vScale + innerY
        shape1.lineTo(x + fw, y2s)
        shape2.lineTo(x + fw, y2s)
        shape1.lineTo(x0, innerY)
        g.setPaint(pntFadeFill)
        g.fill    (shape1)
        g.setPaint(pntFadeOutline)
        g.draw    (shape2)
      }

      this match {
        case fv: TimelineObjView.HasFade if trackTools.fadeViewMode == FadeViewMode.Curve =>
          def adjustFade(in: Curve, deltaCurve: Float): Curve = in match {
            case Curve.linear                 => Curve.parametric(math.max(-20, math.min(20, deltaCurve)))
            case Curve.parametric(curvature)  => Curve.parametric(math.max(-20, math.min(20, curvature + deltaCurve)))
            case other                        => other
          }

          val st      = if (selected) fadeState else TrackTool.NoFade
          val fdIn    = fv.fadeIn  // XXX TODO: continue here. add delta
          val fdInFr  = fdIn.numFrames + st.deltaFadeIn
          if (fdInFr > 0) {
            val fw    = framesToScreen(fdInFr).toFloat
            val fdC   = st.deltaFadeInCurve
            val shape = if (fdC != 0f) adjustFade(fdIn.curve, fdC) else fdIn.curve
            paintFade(shape, fw = fw, y1 = fdIn.floor, y2 = 1f, x = px, x0 = px)
          }
          val fdOut   = fv.fadeOut
          val fdOutFr = fdOut.numFrames + st.deltaFadeOut
          if (fdOutFr > 0) {
            val fw    = framesToScreen(fdOutFr).toFloat
            val fdC   = st.deltaFadeOutCurve
            val shape = if (fdC != 0f) adjustFade(fdOut.curve, fdC) else fdOut.curve
            val x0    = px + pw - 1
            paintFade(shape, fw = fw, y1 = 1f, y2 = fdOut.floor, x = x0 - fw, x0 = x0)
          }

        case _ =>
      }

      g.setClip(clipOrig)

      // --- label ---
      if (regionViewMode == RegionViewMode.TitledBox) {
        val isDark = if (colorOption.isEmpty) true else {
          g.translate(px, py)
          val colr0 = colorOption.get.rgba
          // XXX TODO -- a quick hack to mix with blue
          val colr  = if (selected) ((((colr0 & 0xFFFFFF) >> 1) & 0x7F7F7F) + 0x80) | (colr0 & 0xFF000000) else colr0
          val colr2 = new java.awt.Color(colr, true)
          g.setColor(colr2)
          g.fillRoundRect(1, 1, pw - 2, regionTitleHeight - 1, 4, 4)
          g.translate(-px, -py)
          // cf. https://stackoverflow.com/questions/596216/
          val b = ((colr & 0xFF0000) >> 16) * 30 + ((colr & 0xFF00) >> 8) * 59 + (colr & 0xFF) * 11
          b < 15000
        }

        g.clipRect(px + 2, py + 2, pw - 4, ph - 4)
        // possible unicodes: 2327 23DB 24DC 25C7 2715 29BB
        // val text  = if (view.muted) "\u23DB " + name else name
        val text: String = this match {
          case mv: TimelineObjView.HasMute if mv.muted => s"\u25C7 $name"
          case _ => name
        }
        val tx    = px + 4
        val ty    = py + regionTitleBaseline
        g.setPaint(if (isDark) pntNameShadowDark else pntNameShadowLight)
        g.drawString(text, tx, ty + 1)
        g.setPaint(if (isDark) pntNameDark else pntNameLight)
        g.drawString(text, tx, ty)
        //              stakeInfo(ar).foreach { info =>
        //                g2.setColor(awt.Color.yellow)
        //                g2.drawString(info, x + 4, y + hndlBaseline + hndlExtent)
        //              }
        g.setClip(clipOrig)
      }

      this match {
        case mv: TimelineObjView.HasMute if mv.muted =>
          g.setPaint(pntRegionBackgroundMuted)
          g.fillRoundRect(px, py, pw, ph, 5, 5)
        case _ =>
      }
    }
  }

  def paintFront(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit = ()
}