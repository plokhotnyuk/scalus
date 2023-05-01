package scalus.uplc

import scalus.builtins

import java.util
import scala.collection.immutable

sealed trait Constant:
  def tpe: DefaultUni

object Constant:

  case class Integer(value: BigInt) extends Constant:
    def tpe = DefaultUni.Integer

  case class ByteString(value: builtins.ByteString) extends Constant:
    def tpe = DefaultUni.ByteString

  case class String(value: java.lang.String) extends Constant:
    def tpe = DefaultUni.String

  case object Unit extends Constant:
    def tpe = DefaultUni.Unit

  case class Bool(value: Boolean) extends Constant:
    def tpe = DefaultUni.Bool

  case class Data(value: scalus.uplc.Data) extends Constant:
    def tpe = DefaultUni.Data

  case class List(elemType: DefaultUni, value: immutable.List[Constant]) extends Constant:
    def tpe = DefaultUni.Apply(DefaultUni.ProtoList, elemType)

  case class Pair(a: Constant, b: Constant) extends Constant:
    def tpe = DefaultUni.Apply(DefaultUni.Apply(DefaultUni.ProtoPair, a.tpe), b.tpe)

  def fromValue(tpe: DefaultUni, a: Any): Constant = tpe match {
    case DefaultUni.Integer    => Integer(a.asInstanceOf[BigInt])
    case DefaultUni.ByteString => ByteString(a.asInstanceOf[builtins.ByteString])
    case DefaultUni.String     => String(a.asInstanceOf[java.lang.String])
    case DefaultUni.Unit       => Unit
    case DefaultUni.Bool       => Bool(a.asInstanceOf[Boolean])
    case DefaultUni.Data =>
      Data(a.asInstanceOf[scalus.uplc.Data])
    case DefaultUni.Apply(DefaultUni.ProtoList, elemType) =>
      List(elemType, a.asInstanceOf[Seq[Any]].toList.map(fromValue(elemType, _)))
    case DefaultUni.Apply(DefaultUni.Apply(DefaultUni.ProtoPair, aType), bType) =>
      Pair(
        fromValue(aType, a.asInstanceOf[(Any, Any)]._1),
        fromValue(bType, a.asInstanceOf[(Any, Any)]._2)
      )
    case _ => throw new IllegalArgumentException(s"Cannot convert $a to $tpe")
  }

  def toValue(c: Constant): Any = c match
    case Integer(value)    => value
    case ByteString(value) => value
    case String(value)     => value
    case Unit              => ()
    case Bool(value)       => value
    case Data(value)       => value
    case List(_, value)    => value.map(toValue)
    case Pair(a, b)        => (toValue(a), toValue(b))
