
package scalus.utils

import scalus.flat.{*, given}


class HashConsedEncoderState(val encode: EncoderState,
                             val hashConsed: HashConsed.State)  {

    inline def lookupValue(ihc: Int, tag: HashConsed.Tag): Option[HashConsedRef[?]] = {
        hashConsed.lookupValue(ihc, tag)
    }

    inline def setRef[A<:AnyRef](ihc: Int, tag: HashConsed.Tag, a: HashConsedRef[A]): Boolean = {
        hashConsed.setRef(ihc, tag, a)
    }

    inline def putForwardRef(fw: HashConsed.ForwardRefAcceptor): Boolean = {
        hashConsed.putForwardRef(fw)
    }


}

class HashConsedDecoderState(val decode: DecoderState,
                             val hashConsed: HashConsed.State,
                             var finCallbacks: List[(HashConsed.State) => Unit] = Nil) {

    def addFinCallback(callback: (HashConsed.State) => Unit): Unit =
        this.finCallbacks = callback :: finCallbacks

    def runFinCallbacks(): Unit =
        finCallbacks.foreach(_(hashConsed))

}




trait HashConsedFlat[A] extends Flat[A] {

    final override def bitSize(a: A): Int =
        bitSizeHC(a, HashConsed.State.empty)

    final override def encode(a: A, encoderState: EncoderState): Unit =
        encodeHC(a, HashConsedEncoderState(encoderState, HashConsed.State.empty))

    def decode(decode: DecoderState): A =
        decodeHC(HashConsedDecoderState(decode, HashConsed.State.empty))

    def bitSizeHC(a: A, encoderState: HashConsed.State): Int

    def encodeHC(a: A, encoderState: HashConsedEncoderState): Unit

    def decodeHC(decoderState: HashConsedDecoderState): A

}


trait HashConsedReprFlat[A<:AnyRef, SA <: HashConsedRef[A]]  {

    type Repr = SA

    def toRepr(a: A): SA

    def bitSizeHC(a: A, hashConsed: HashConsed.State): Int

    def encodeHC(a: A, encode: HashConsedEncoderState): Unit

    def decodeHC(decode: HashConsedDecoderState): SA
    
    def finDecodeHC(hashConsed: HashConsed.State, sa: SA): A =
        sa.finValue(hashConsed)

}

object HashConsedReprFlat {

    case class ListRepl[A <: AnyRef, SA <: HashConsedRef[A]](elems: List[SA]) extends HashConsedRef[List[A]] {

        def isComplete(hashConsed: HashConsed.State): Boolean = elems.forall(_.isComplete(hashConsed))

        def finValue(hashConsed: HashConsed.State): List[A] = elems.map(_.finValue(hashConsed))

    }


    implicit def listHashConsedRepr[A<:AnyRef,SA<:HashConsedRef[A]](using flatA: HashConsedReprFlat[A, SA]): HashConsedReprFlat[List[A], ListRepl[A, SA]] =
        new HashConsedReprFlat[List[A], ListRepl[A, SA]] {

            def toRepr(a: List[A]): ListRepl[A,SA] = ListRepl[A,SA](a.map(flatA.toRepr))

            def bitSizeHC(a: List[A], hashConsed: HashConsed.State): Int = {
                a.foldLeft(1)((acc, elem) => acc + flatA.bitSizeHC(elem, hashConsed)+1)
            }

            def encodeHC(a: List[A], encode: HashConsedEncoderState): Unit = {
                a.foreach( elem =>
                    encode.encode.bits(1, 1)
                    flatA.encodeHC(elem, encode)
                )
                encode.encode.bits(1, 0)
            }

            def decodeHC(decode: HashConsedDecoderState): ListRepl[A,SA] = {
                val builder = List.newBuilder[SA]
                while
                    val tag = decode.decode.bits8(1)
                    tag == 1
                  do
                    val elem = flatA.decodeHC(decode)
                    builder += elem
                ListRepl(builder.result())
            }

        }

    def listRepr[A<:AnyRef,SA<:HashConsedRef[A]](flatRepr: HashConsedReprFlat[A,SA]): HashConsedReprFlat[List[A], ListRepl[A, SA]] =
        listHashConsedRepr[A,SA](using flatRepr)


}



trait HashConsedTagged[A] {

    def tag: HashConsed.Tag

}


object HashConsedFlat {

    given listHashConsedFlat[A](using flatA: HashConsedFlat[A]): HashConsedFlat[List[A]] with

        def bitSizeHC(a: List[A], hashConsed: HashConsed.State): Int = {
            val size = a.foldLeft(0)((acc, elem) => acc + flatA.bitSizeHC(elem, hashConsed))
            size
        }
        def encodeHC(a: List[A], encode: HashConsedEncoderState): Unit = {
            val nElements = a.size
            summon[Flat[Int]].encode(nElements, encode.encode)
            a.foreach(elem => flatA.encodeHC(elem,encode))
        }
        def decodeHC(decode: HashConsedDecoderState): List[A] = {
            val size = summon[Flat[Int]].decode(decode.decode)
            (0 until size).map(_ => flatA.decodeHC(decode)).toList
        }

}

object PlainIntFlat extends HashConsedFlat[Int] {

    def bitSizeHC(a: Int, hashConsed: HashConsed.State): Int = 4*8

    def encodeHC(a: Int, encoderState: HashConsedEncoderState): Unit =
        encoderState.encode.bits(8, (a >>> 24).toByte)
        encoderState.encode.bits(8, (a >>> 16).toByte)
        encoderState.encode.bits(8, (a >>> 8).toByte)
        encoderState.encode.bits(8, a.toByte)

    def decodeHC(decoderState: HashConsedDecoderState): Int =
        var retval = 0
        retval |= ((decoderState.decode.bits8(8) << 24) & 0xFF000000)
        retval |= ((decoderState.decode.bits8(8) << 16) & 0x00FF0000)
        retval |= ((decoderState.decode.bits8(8) << 8)  & 0x0000FF00)
        retval |= (decoderState.decode.bits8(8) & 0x000000FF)
        retval

}


/**
 * Here we assume, that A can participate in the recursive structures, but
 *  assumes, that A does not contaoins forward references to A.
 *  (note, that subpart of A, which readed separately, can contain forward references to A)
 *
 * TODO: check, are we used one.
 * @tparam A
 */
trait HashConsedRefFlat[A <: AnyRef]  extends HashConsedFlat[HashConsedRef[A]] with HashConsedTagged[A] {

    def tag: HashConsed.Tag

    def bitSizeHCNew(a: A, encode: HashConsed.State): Int

    def encodeHCNew(a: A, encode: HashConsedEncoderState): Unit

    def decodeHCNew(decoderState: HashConsedDecoderState): A

    override def bitSizeHC(refA: HashConsedRef[A], hashConsed: HashConsed.State): Int = {
        if (!refA.isComplete(hashConsed)) then
            throw IllegalStateException("Incomplete reference during writing")
        val a = refA.finValue(hashConsed)
        if (a == null) {
            throw IllegalStateException("Null reference during writing")
        }
        val ihc = a.hashCode
        var retval = PlainIntFlat.bitSize(ihc)
        hashConsed.lookup(ihc, tag) match
            case None =>
                hashConsed.putForwardRef(HashConsed.ForwardRefAcceptor(ihc, tag, Nil))
                retval += bitSizeHCNew(a, hashConsed)
                hashConsed.setRef(ihc, tag, HashConsedRef.fromData(a))
            case Some(_) =>
        retval
    }

    override def encodeHC(refA: HashConsedRef[A], encoderState: HashConsedEncoderState): Unit =
        if (!refA.isComplete(encoderState.hashConsed)) then
            throw IllegalStateException("Incomplete reference during writing")
        val a = refA.finValue(encoderState.hashConsed)
        val ihc = a.hashCode
        PlainIntFlat.encode(ihc, encoderState.encode)
        encoderState.hashConsed.lookup(ihc, tag) match
            case None =>
                encoderState.hashConsed.putForwardRef(HashConsed.ForwardRefAcceptor(ihc, tag, Nil))
                encodeHCNew(a, encoderState)
                encoderState.hashConsed.setRef(ihc, tag, HashConsedRef.fromData(a))
            case Some(_) =>

    override def decodeHC(decoderState: HashConsedDecoderState): HashConsedRef[A] = {
        val ihc = PlainIntFlat.decode(decoderState.decode)
        decoderState.hashConsed.lookup(ihc, tag) match
            case None =>
                val refAcceptor = HashConsed.ForwardRefAcceptor(ihc, tag, Nil)
                decoderState.hashConsed.putForwardRef(refAcceptor)
                val a = decodeHCNew(decoderState)
                val retval = HashConsedRef.fromData(a)
                decoderState.hashConsed.setRef(ihc, tag, retval)
                retval
            case Some(Left(fw)) =>
                val retval = HashConsed.MutRef[A](null)
                fw.addAction(a => retval.value = a.asInstanceOf[A])
                retval
            case Some(Right(ra)) => ra.asInstanceOf[HashConsedRef[A]]
    }

}

trait HashConsedMutRefReprFlat[A <: AnyRef]  extends HashConsedReprFlat[A, HashConsedRef[A]] {

    def tag: HashConsed.Tag

    def bitSizeHCNew(a: A, encode: HashConsed.State): Int

    def encodeHCNew(a: A, encode: HashConsedEncoderState): Unit

    def decodeHCNew(decoderState: HashConsedDecoderState): HashConsedRef[A]

    override def bitSizeHC(a: A, encoderState: HashConsed.State): Int = {
        val ihc = a.hashCode
        var retval = PlainIntFlat.bitSize(ihc)
        encoderState.lookup(ihc, tag) match
            case None =>
                encoderState.putForwardRef(HashConsed.ForwardRefAcceptor(ihc, tag, Nil))
                retval += bitSizeHCNew(a, encoderState)
                encoderState.setRef(ihc, tag, HashConsedRef.fromData(a))
            case Some(_) =>
        retval
    }

    override def encodeHC(a: A, encoderState: HashConsedEncoderState): Unit = {
        val ihc = a.hashCode
        PlainIntFlat.encode(ihc, encoderState.encode)
        encoderState.hashConsed.lookup(ihc, tag) match
            case None =>
                encoderState.putForwardRef(HashConsed.ForwardRefAcceptor(ihc, tag, Nil))
                encodeHCNew(a, encoderState)
                encoderState.setRef(ihc, tag, HashConsedRef.fromData(a))
            case Some(_) =>
    }

    override def decodeHC(decoderState: HashConsedDecoderState): HashConsedRef[A] = {
        val ihc = PlainIntFlat.decode(decoderState.decode)
        decoderState.hashConsed.lookup(ihc, tag) match
            case None =>
                decoderState.hashConsed.putForwardRef(HashConsed.ForwardRefAcceptor(ihc, tag, Nil))
                val sa = decodeHCNew(decoderState)
                decoderState.hashConsed.setRef(ihc, tag, sa)
                sa
            case Some(Left(fw)) =>
                HashConsedRef.fromForward[A](decoderState.hashConsed, ihc, tag)
            case Some(Right(sa)) => sa.asInstanceOf[HashConsedRef[A]]
    }
    
}


