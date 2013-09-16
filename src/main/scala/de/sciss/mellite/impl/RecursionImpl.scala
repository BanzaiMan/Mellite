/*
 *  RecursionImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package mellite
package impl

import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.{ExprImplicits, ProcGroup, Artifact}
import de.sciss.lucre.{expr, event => evt}
import expr.Expr
import de.sciss.span.SpanLike
import de.sciss.serial.{ImmutableSerializer, DataInput, DataOutput}
import scala.annotation.switch
import de.sciss.lucre.synth.{InMemory, Sys}
import de.sciss.lucre.synth.expr.SpanLikes

object RecursionImpl {
  import Recursion.Channels

  private final val COOKIE  = 0x5265    // "Re"

  implicit def serializer[S <: Sys[S]]: serial.Serializer[S#Tx, S#Acc, Recursion[S]] with evt.Reader[S, Recursion[S]] =
    anySer.asInstanceOf[Ser[S]]

  private val anySer = new Ser[InMemory]

  implicit object RangeSer extends ImmutableSerializer[Range.Inclusive] {
    def write(v: Range.Inclusive, out: DataOutput): Unit =
      if (v.start == v.end) {
        out.writeByte(0)
        out.writeInt(v.start)
      } else {
        out.writeByte(1)
        out.writeInt(v.start)
        out.writeInt(v.end  )
        out.writeInt(v.step )
      }

    def read(in: DataInput): Range.Inclusive = {
      (in.readByte(): @switch) match {
        case 0  =>
          val start = in.readInt()
          new Range.Inclusive(start, start, 1)
        case 1  =>
          val start = in.readInt()
          val end   = in.readInt()
          val step  = in.readInt()
          new Range.Inclusive(start, end, step)
      }
    }
  }

  private final class Ser[S <: Sys[S]] extends evt.NodeSerializer[S, Recursion[S]] {
    def read(in: DataInput, access: S#Acc, targets: evt.Targets[S])(implicit tx: S#Tx): Recursion[S] with evt.Node[S] = {
      val cookie  = in.readShort()
      require(cookie == COOKIE, s"Unexpected cookie $cookie (requires $COOKIE)")
      val group       = ProcGroup.read(in, access)
      val span        = SpanLikes.readVar(in, access)
      val id          = targets.id
      val gain        = tx.readVar[Gain    ](id, in)
      val channels    = tx.readVar[Channels](id, in)(ImmutableSerializer.indexedSeq[Range.Inclusive])
      val transform   = serial.Serializer.option[S#Tx, S#Acc, Element.Code[S]].read(in, access)
      //      val deployed    = Grapheme.Elem.Audio.readExpr(in, access) match {
      //        case ja: Grapheme.Elem.Audio[S] => ja // XXX TODO sucky shit
      //      }
      val deployed    = Element.AudioGrapheme.serializer[S].read(in, access)
      val product     = Artifact.Modifiable.read(in, access)
      val productSpec = AudioFileSpec.Serializer.read(in)
      new Impl(targets, group, span, gain, channels, transform, deployed, product, productSpec)
    }
  }

  def apply[S <: Sys[S]](group: ProcGroup[S], span: SpanLike, deployed: Element.AudioGrapheme[S],
                         gain: Gain, channels: Channels, transform: Option[Element.Code[S]])
                        (implicit tx: S#Tx): Recursion[S] = {
    val imp = ExprImplicits[S]
    import imp._

    val targets   = evt.Targets[S]
    val id        = targets.id
    val _span     = SpanLikes.newVar(span)
    val _gain     = tx.newVar(id, gain)
    val _channels = tx.newVar(id, channels)(ImmutableSerializer.indexedSeq[Range.Inclusive])

    //    val depArtif  = Artifact.Modifiable(artifact.location, artifact.value)
    //    val depOffset = Longs  .newVar(0L)
    //    val depGain   = Doubles.newVar(1.0)
    //    val deployed  = Grapheme.Elem.Audio.apply(depArtif, spec, depOffset, depGain)

    val depGraph  = deployed.entity
    val product   = Artifact.Modifiable.copy(depGraph.artifact)
    val spec      = depGraph.value.spec  // XXX TODO: should that be a method on entity?

    new Impl(targets, group, _span, _gain, _channels, transform, deployed, product = product, productSpec = spec)
  }

  private final class Impl[S <: Sys[S]](protected val targets: evt.Targets[S], val group: ProcGroup[S],
      _span          : Expr.Var[S, SpanLike],
      _gain          : S#Var[Gain],
      _channels      : S#Var[Channels],
      val transform  : /* S#Var[ */ Option[Element.Code[S]] /* ] */,
      val deployed   : Element.AudioGrapheme[S] /* Grapheme.Elem.Audio[S] */, val product: Artifact[S],
      val productSpec: AudioFileSpec)
    extends Recursion[S]
    with evt.impl.Generator     [S, Recursion.Update[S], Recursion[S]]
    with evt.impl.StandaloneLike[S, Recursion.Update[S], Recursion[S]] {

    def span(implicit tx: S#Tx): SpanLike = _span.value
    def span_=(value: SpanLike)(implicit tx: S#Tx): Unit = {
      val imp = ExprImplicits[S]
      import imp._
      _span() = value
    }

    def gain(implicit tx: S#Tx): Gain = _gain()
    def gain_=(value: Gain)(implicit tx: S#Tx): Unit = {
      _gain() = value
      fire()
    }

    def channels(implicit tx: S#Tx): Channels = _channels()
    def channels_=(value: Channels)(implicit tx: S#Tx): Unit = {
      _channels() = value
      fire()
    }

    //    def transform(implicit tx: S#Tx): Option[Element.Code[S]] = _transform()
    //    def transform_=(value: Option[Element.Code[S]])(implicit tx: S#Tx): Unit = {
    //      _transform() = value
    //      fire()
    //    }

    /** Moves the product to deployed position. */
    def iterate()(implicit tx: S#Tx): Unit = {
      val mod = deployed.entity.artifact.modifiableOption.getOrElse(
        sys.error("Can't iterate - deployed artifact not modifiable")
      )
      val prodF = product.value
      val prodC = Artifact.relativize(mod.location.directory, prodF)
      mod.child_=(prodC)
    }

    // ---- event dorfer ----

    def changed: evt.EventLike[S, Recursion.Update[S]] = this

    protected def reader: evt.Reader[S, Recursion[S]] = serializer

    def pullUpdate(pull: evt.Pull[S])(implicit tx: S#Tx): Option[Recursion.Update[S]] = {
      if (pull.isOrigin(this)) return Some()

      val spanEvt = _span.changed
      val spanUpd = if (pull.contains(spanEvt)) pull(spanEvt) else None
      if (spanUpd.isDefined) return Some()

      val depEvt = deployed.changed
      val depUpd = if (pull.contains(depEvt)) pull(depEvt) else None
      if (depUpd.isDefined) return Some()

      val prodEvt = product.changed
      val prodUpd = if (pull.contains(prodEvt)) pull(prodEvt) else None
      if (prodUpd.isDefined) return Some()

      transform.foreach { t =>
        val tEvt = t.changed
        val tUpd = if (pull.contains(tEvt)) pull(tEvt) else None
        if (tUpd.isDefined) return Some()
      }

      None
    }

    protected def writeData(out: DataOutput): Unit = {
      out.writeShort(COOKIE)
      group    .write(out)
      _span    .write(out)
      _gain    .write(out)
      _channels.write(out)
      serial.Serializer.option[S#Tx, S#Acc, Element.Code[S]].write(transform, out)
      deployed .write(out)
      product  .write(out)
      AudioFileSpec.Serializer.write(productSpec, out)
    }

    protected def disposeData()(implicit tx: S#Tx): Unit = {
      // group: NO
      _span    .dispose()
      _gain    .dispose()
      _channels.dispose()
    }

    def connect()(implicit tx: S#Tx): Unit = {
      // ignore group
      _span   .changed ---> this
      deployed.changed ---> this
      product .changed ---> this
      transform.foreach(_.changed ---> this)
    }

    def disconnect()(implicit tx: S#Tx): Unit = {
      _span   .changed -/-> this
      deployed.changed -/-> this
      product .changed -/-> this
      transform.foreach(_.changed -/-> this)
    }
  }
}