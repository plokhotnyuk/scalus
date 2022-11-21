package scalus

import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scalus.builtins.ByteString.given
import scalus.builtins.{Builtins, ByteString}
import scalus.ledger.api.v1.*
import scalus.ledger.api.v1.Instances.given
import scalus.sir.Recursivity.*
import scalus.sir.SIR.*
import scalus.sir.{Binding, Recursivity, SIR, SimpleSirToUplcLowering}
import scalus.uplc.*
import scalus.uplc.DefaultFun.*
import scalus.uplc.ExprBuilder.{compile, fieldAsData1}
import scalus.uplc.TermDSL.{lam, λ}
import scalus.utils.Utils

import scala.collection.immutable
import scalus.uplc.Data.FromData

class CompileFromDataToSIRSpec extends AnyFunSuite with ScalaCheckPropertyChecks:
  val deadbeef = Constant.ByteString(hex"deadbeef")

  inline def compilesTo(expected: SIR)(inline e: Any) = assert(compile(e) == expected)

  def testFromData[A: Data.ToData](compiled: SIR, arg: A, expectedSize: Int, expectedResult: Term) = {
    // println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    // println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    // println(flatBytes.length)
    import TermDSL.*
    import scalus.uplc.Data.*
    val result = Cek.evalUPLC(term $ Term.Const(Constant.Data(arg.toData)))
    // println(result)
    assert(flatBytes.length == expectedSize)
    assert(result == expectedResult)
  }

  test("compile FromData[Boolean]") {
    val compiled = compile {
      summon[Data.FromData[Boolean]](Builtins.mkConstr(0, scalus.builtins.List.empty[Data]))
    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
  }

  test("compile FromData[(A, B)]") {
    val compiled = compile {
      val t = Builtins.mkConstr(1, scalus.builtins.List.empty[Data])
      val f = Builtins.mkConstr(0, scalus.builtins.List.empty[Data])
      summon[Data.FromData[(Boolean, Boolean)]](
        Builtins.mkConstr(
          0,
          scalus.builtins.List(f, t)
        )
      )
    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
  }

  test("compile FromData[PubKeyHash]") {
    val compiled = compile {
      (d: Data) => summon[Data.FromData[PubKeyHash]](d).hash
    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
    import TermDSL.*
    import scalus.uplc.Data.*
    println(Cek.evalUPLC(term $ Term.Const(Constant.Data(TxId(hex"deadbeef").toData))).pretty.render(80))
  }

  test("compile FromData[List[A]]") {
    val compiled = compile {
      val ls = Builtins.mkList(builtins.List.empty[Data])
      val txids = summon[Data.FromData[scalus.Predef.List[TxId]]](ls)
      txids
    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
  }

  test("compile FromData[Value]") {
    import scalus.Predef.List.{Nil, Cons}
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[Value]](v)
        value match
          case Nil => BigInt(0)
          case Cons(head, tail) =>  head match
            case (cs, vals) => vals match
              case Nil => BigInt(1)
              case Cons(tn, vl) => tn match
                case (tn, vl) => vl

    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
    assert(flatBytes.length == 179)
    import TermDSL.*
    import scalus.uplc.Data.*
    val result = Cek.evalUPLC(term $ Term.Const(Constant.Data(Value.lovelace(42).toData)))
    println(result)
    assert(result == Term.Const(Constant.Integer(42)))
  }

  test("compile FromData[TxOutRef]") {
    import scalus.Predef.List.{Nil, Cons}
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[TxOutRef]](v)
        value match
          case TxOutRef(id, idx) => idx
    }
    println(compiled.pretty.render(80))
    val term = new SimpleSirToUplcLowering().lower(compiled)
    println(term.pretty.render(80))
    val flatBytes = ProgramFlatCodec.encodeFlat(Program(version = (1, 0, 0), term = term))
    println(flatBytes.length)
    import TermDSL.*
    import scalus.uplc.Data.*
    val result = Cek.evalUPLC(term $ Term.Const(Constant.Data(TxOutRef(TxId(hex"12"), 2).toData)))
    println(result)
    assert(flatBytes.length == 66)
    assert(result == Term.Const(Constant.Integer(2)))
  }

  test("compile FromData[Credential]") {
    import scalus.Predef.List.{Nil, Cons}
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[Credential]](v)
        value match
          case Credential.PubKeyCredential(pubKeyHash) => pubKeyHash.hash
          case Credential.ScriptCredential(hash) => hash
    }
    testFromData(compiled, Credential.ScriptCredential(hex"12"), 97, Term.Const(Constant.ByteString(hex"12")))
  }

  test("compile FromData[StakingCredential]") {
    import StakingCredential.*
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[StakingCredential]](v)
        value match
          case StakingHash(cred) => BigInt(1)
          case StakingPtr(a, b, c) => c
    }
    testFromData(compiled, StakingPtr(1, 2, 3), 202, Term.Const(Constant.Integer(3)))
  }

  test("compile FromData[DCert]") {
    import scalus.Predef.List.{Nil, Cons}
    import DCert.*
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[DCert]](v)
        value match
          case DelegRegKey(cred) => BigInt(1)
          case DelegDeRegKey(cred) => BigInt(2)
          case DelegDelegate(cred, delegatee) => BigInt(3)
          case PoolRegister(poolId, vrf) => BigInt(4)
          case PoolRetire(poolId, epoch) => BigInt(5)
          case Genesis => BigInt(6)
          case Mir => BigInt(7)

    }
    testFromData(compiled, DCert.Genesis, 492, Term.Const(Constant.Integer(6)))
  }

  test("compile FromData[Extended]") {
    import Extended.*
    val compiled = compile {
      (v: Data) =>
        val value = summon[Data.FromData[Extended[BigInt]]](v)
        value match
          case NegInf => BigInt(1)
          case Finite(a) => a
          case PosInf => BigInt(2)

    }
    testFromData(compiled, Finite(123), 124, Term.Const(Constant.Integer(123)))
  }