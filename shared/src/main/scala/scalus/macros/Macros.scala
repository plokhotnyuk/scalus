package scalus.macros

import scalus.builtins
import scalus.sir.{Binding, SIR}
import scalus.uplc.ExprBuilder.*
import scalus.uplc.{
  Constant,
  Data,
  DefaultUni,
  ExprBuilder,
  NamedDeBruijn,
  Expr as Exp,
  Term as Trm
}
import scalus.utils.Utils

import scala.collection.{IterableFactory, SeqFactory, immutable}
import scala.quoted.*
object Macros {
  def lamMacro[A: Type, B: Type](f: Expr[Exp[A] => Exp[B]])(using Quotes): Expr[Exp[A => B]] =
    import quotes.reflect.*
    val name = f.asTerm match
      // lam(x => body)
      case Inlined(_, _, Block(List(DefDef(_, List(List(ValDef(name, _, _))), _, body)), _)) =>
        Expr(name)
      // lam { x => body }
      case Inlined(
            _,
            _,
            Block(List(), Block(List(DefDef(_, List(List(ValDef(name, _, _))), _, body)), _))
          ) =>
        Expr(name)
      case x => report.errorAndAbort(x.toString)
    '{
      Exp(Trm.LamAbs($name, $f(vr($name)).term))
    }

  def fieldAsDataMacro[A: Type](e: Expr[A => Any])(using Quotes): Expr[Exp[Data] => Exp[Data]] =
    import quotes.reflect.*
    e.asTerm match
      case Inlined(
            _,
            _,
            Block(List(DefDef(_, _, _, Some(select @ Select(_, fieldName)))), _)
          ) =>
        def genGetter(typeSymbolOfA: Symbol, fieldName: String): Expr[Exp[Data] => Exp[Data]] =
          val fieldOpt: Option[(Symbol, Int)] =
            if typeSymbolOfA == TypeRepr.of[Tuple2].typeSymbol then
              fieldName match
                case "_1" => typeSymbolOfA.caseFields.find(_.name == fieldName).map(s => (s, 0))
                case "_2" => typeSymbolOfA.caseFields.find(_.name == fieldName).map(s => (s, 1))
                case _ =>
                  report.errorAndAbort("Unexpected field name for Tuple2 type: " + fieldName)
            else typeSymbolOfA.caseFields.zipWithIndex.find(_._1.name == fieldName)
//          report.info(s"$typeSymbolOfA => fieldOpt: $fieldOpt")
          fieldOpt match
            case Some((fieldSym: Symbol, idx)) =>
              val idxExpr = Expr(idx)
              '{
                var expr: Exp[Data] => Exp[List[Data]] = d => sndPair(unConstrData(d))
                var i = 0
                while i < $idxExpr do
                  val exp = expr // save the current expr, otherwise it will loop forever
                  expr = d => tailList(exp(d))
                  i += 1
                d => headList(expr(d))
              }
            case None =>
              report.errorAndAbort("fieldMacro: " + fieldName)

        def composeGetters(tree: Tree): Expr[Exp[Data] => Exp[Data]] = tree match
          case Select(select @ Select(_, _), fieldName) =>
            val a = genGetter(select.tpe.typeSymbol, fieldName)
            val b = composeGetters(select)
            '{ $a compose $b }
          case Select(ident @ Ident(_), fieldName) =>
            genGetter(ident.tpe.typeSymbol, fieldName)
          case _ =>
            report.errorAndAbort(
              s"field macro supports only this form: _.caseClassField1.field2, but got " + tree.show
            )
        composeGetters(select)
      case x => report.errorAndAbort(x.toString)

  def fieldMacro[A: Type](e: Expr[A => Any])(using Quotes): Expr[Exp[Data] => Exp[Any]] =
    import quotes.reflect.*
    e.asTerm match
      case Inlined(
            _,
            _,
            Block(List(DefDef(_, _, _, Some(select @ Select(_, fieldName)))), _)
          ) =>
        def genGetter(
            typeSymbolOfA: Symbol,
            fieldName: String
        ): (Symbol, Expr[Exp[Data] => Exp[Data]]) =
          val fieldOpt: Option[(Symbol, Int)] =
            if typeSymbolOfA == TypeRepr.of[Tuple2].typeSymbol then
              fieldName match
                case "_1" => typeSymbolOfA.caseFields.find(_.name == fieldName).map(s => (s, 0))
                case "_2" => typeSymbolOfA.caseFields.find(_.name == fieldName).map(s => (s, 1))
                case _ =>
                  report.errorAndAbort("Unexpected field name for Tuple2 type: " + fieldName)
            else typeSymbolOfA.caseFields.zipWithIndex.find(_._1.name == fieldName)
          fieldOpt match
            case Some((fieldSym: Symbol, idx)) =>
              val idxExpr = Expr(idx)
              (
                fieldSym,
                '{
                  var expr: Exp[Data] => Exp[List[Data]] = d => sndPair(unConstrData(d))
                  var i = 0
                  while i < $idxExpr do
                    val exp = expr // save the current expr, otherwise it will loop forever
                    expr = d => tailList(exp(d))
                    i += 1
                  (d: Exp[Data]) => headList(expr(d))
                }
              )
            case None =>
              report.errorAndAbort("fieldMacro: " + fieldName)

        def composeGetters(tree: Tree): (TypeRepr, Expr[Exp[Data] => Exp[Data]]) = tree match
          case Select(select @ Select(_, _), fieldName) =>
            val (_, a) = genGetter(select.tpe.typeSymbol, fieldName)
            val (s, b) = composeGetters(select)
            (s, '{ $a compose $b })
          case Select(ident @ Ident(_), fieldName) =>
            val (fieldSym, f) = genGetter(ident.tpe.typeSymbol, fieldName)
            val fieldType = ident.tpe.memberType(fieldSym).dealias
            (fieldType, f)
          case _ =>
            report.errorAndAbort(
              s"field macro supports only this form: _.caseClassField1.field2, but got " + tree.show
            )

        val (fieldType, getter) = composeGetters(select)
        val unliftTypeRepr = TypeRepr.of[Unlift].appliedTo(fieldType)
        /*report.info(
          s"composeGetters: fieldType = ${fieldType.show} unliftTypeRepr = ${unliftTypeRepr.show}, detailed fieldType: $fieldType"
        )*/
        Implicits.search(unliftTypeRepr) match
          case success: ImplicitSearchSuccess =>
            unliftTypeRepr.asType match
              case '[Unlift[t]] =>
                val expr = success.tree
                val impl = success.tree.asExpr
                /*report
                  .info(
                    s"found implicit ${unliftTypeRepr.show} => ${expr.show}: ${expr.tpe.show}"
                  )*/
                '{ (d: Exp[Data]) =>
                  ExprBuilder
                    .app($impl.asInstanceOf[Unlift[t]].unlift, $getter(d))
                }
          case failure: ImplicitSearchFailure =>
            report.info(s"not found implicit of type ${unliftTypeRepr.show}")
            getter
      case x => report.errorAndAbort(x.toString)

  def compileImpl(e: Expr[Any])(using Quotes): Expr[SIR] =
    import quotes.reflect.*
    import scalus.uplc.Constant.*
    import scalus.uplc.DefaultFun
    import scalus.sir.Recursivity

    extension (t: Term) def isList = t.tpe <:< TypeRepr.of[builtins.List[_]]
    extension (t: Term) def isPair = t.tpe <:< TypeRepr.of[builtins.Pair[_, _]]
    extension (t: Term) def isLiteral = compileConstant.isDefinedAt(t)
    extension (t: Term) def isData = t.tpe <:< TypeRepr.of[scalus.uplc.Data]

    given ToExpr[DefaultUni] with {
      def apply(x: DefaultUni)(using Quotes) =
        import quotes.reflect._
        x match
          case DefaultUni.Unit       => '{ DefaultUni.Unit }
          case DefaultUni.Bool       => '{ DefaultUni.Bool }
          case DefaultUni.Integer    => '{ DefaultUni.Integer }
          case DefaultUni.String     => '{ DefaultUni.String }
          case DefaultUni.ByteString => '{ DefaultUni.ByteString }
          case DefaultUni.Data       => '{ DefaultUni.Data }
          case DefaultUni.Apply(DefaultUni.ProtoList, a) =>
            '{ DefaultUni.List(${ Expr(a) }) }
          case DefaultUni.Apply(DefaultUni.Apply(DefaultUni.ProtoPair, a), b) =>
            '{ DefaultUni.Pair(${ Expr(a) }, ${ Expr(b) }) }
          case DefaultUni.Apply(f, a) =>
            '{ DefaultUni.Apply(${ Expr(f) }, ${ Expr(a) }) }
    }

    def typeReprToDefaultUni(t: TypeRepr): DefaultUni =
      t.asType match
        case '[BigInt]              => DefaultUni.Integer
        case '[java.lang.String]    => DefaultUni.String
        case '[Boolean]             => DefaultUni.Bool
        case '[Unit]                => DefaultUni.Unit
        case '[builtins.ByteString] => DefaultUni.ByteString
        case '[builtins.List[a]] =>
          val immutable.List(a) = t.typeArgs
          val aType = typeReprToDefaultUni(a)
          DefaultUni.List(aType)
        case '[builtins.Pair[a, b]] =>
          t.typeArgs match
            case immutable.List(a, b) =>
              DefaultUni.Pair(typeReprToDefaultUni(a), typeReprToDefaultUni(b))
            case _ => report.errorAndAbort("Unexpected type arguments for Pair: " + t.show)
        case _ if t <:< TypeRepr.of[scalus.uplc.Data] => DefaultUni.Data
        case _ => report.errorAndAbort(s"Unsupported type: ${t.show}")

    def compileStmt(stmt: Statement, expr: Expr[SIR]): Expr[SIR] = {
      stmt match
        case ValDef(a, tpe, Some(body)) =>
          val bodyExpr = compileExpr(body)
          val aExpr = Expr(a)
          '{ SIR.Let(Recursivity.NonRec, immutable.List(Binding($aExpr, $bodyExpr)), $expr) }
        case DefDef(name, immutable.List(TermParamClause(args)), tpe, Some(body)) =>
          val bodyExpr: Expr[scalus.sir.SIR] = {
            val bE = compileExpr(body)
            if args.isEmpty then '{ SIR.LamAbs("_", $bE) }
            else
              val names = args.map { case ValDef(name, tpe, rhs) => Expr(name) }
              names.foldRight(bE) { (name, acc) =>
                '{ SIR.LamAbs($name, $acc) }
              }
          }
          val nameExpr = Expr(name)
          '{ SIR.Let(Recursivity.Rec, immutable.List(Binding($nameExpr, $bodyExpr)), $expr) }
        case DefDef(name, args, tpe, _) =>
          report.errorAndAbort(
            "compileStmt: Only single argument list defs are supported, but given: " + stmt.show
          )
        case x: Term =>
          '{ SIR.Let(Recursivity.NonRec, immutable.List(Binding("_", ${ compileExpr(x) })), $expr) }
        case x => report.errorAndAbort(s"compileStmt: $x")
    }
    def compileBlock(stmts: immutable.List[Statement], expr: Term): Expr[SIR] = {
      import quotes.reflect.*
      val e = compileExpr(expr)
      stmts.foldRight(e)(compileStmt)
    }

    def compileConstant: PartialFunction[Term, Expr[scalus.uplc.Constant]] = {
      case Literal(UnitConstant()) => '{ Unit }
      case Literal(StringConstant(lit)) =>
        val litE = Expr(lit)
        '{ String($litE) }
      case Literal(BooleanConstant(lit)) =>
        val litE = Expr(lit)
        '{ Bool($litE) }
      case Literal(_) => report.errorAndAbort("compileExpr: Unsupported literal " + e.show)
      case lit @ Apply(Select(Ident("BigInt"), "apply"), _) =>
        val litE = lit.asExprOf[BigInt]
        '{ Integer($litE) }
      case lit @ Apply(Ident("int2bigInt"), _) =>
        val litE = lit.asExprOf[BigInt]
        '{ Integer($litE) }
      case lit @ Ident("empty") if lit.tpe.show == "scalus.builtins.ByteString.empty" =>
        val litE = lit.asExprOf[builtins.ByteString]
        '{ ByteString($litE) }
      case lit @ Apply(Select(byteString, "fromHex" | "unsafeFromArray" | "apply"), args)
          if byteString.tpe =:= TypeRepr.of[builtins.ByteString.type] =>
        val litE = lit.asExprOf[builtins.ByteString]
        '{ ByteString($litE) }
    }

    def compileExpr(e: Term): Expr[SIR] = {
      import quotes.reflect.*
      if compileConstant.isDefinedAt(e) then
        val const = compileConstant(e)
        '{ SIR.Const($const) }
      else
        e match
          case Ident("Nil") if e.isList =>
            report.errorAndAbort(s"compileExpr: Nil is not supported. Use List.empty instead")
          case Ident(a) =>
            val aE = Expr(a)
            '{ SIR.Var(NamedDeBruijn($aE)) }
          case If(cond, t, f) =>
            '{ SIR.IfThenElse(${ compileExpr(cond) }, ${ compileExpr(t) }, ${ compileExpr(f) }) }
          // PAIR
          case Select(pair, fun) if pair.isPair =>
            fun match
              case "fst" =>
                '{ SIR.Apply(SIR.Builtin(DefaultFun.FstPair), ${ compileExpr(pair) }) }
              case "snd" =>
                '{ SIR.Apply(SIR.Builtin(DefaultFun.SndPair), ${ compileExpr(pair) }) }
              case _ => report.errorAndAbort(s"compileExpr: Unsupported pair function: $fun")
          case Apply(TypeApply(pair, immutable.List(tpe1, tpe2)), immutable.List(a, b))
              if pair.tpe.show == "scalus.builtins.Pair.apply" =>
            // We can create a Pair by either 2 literals as (con pair...)
            // or 2 Data variables using MkPairData builtin
            if a.isLiteral && b.isLiteral then
              '{ SIR.Const(Pair(${ compileConstant(a) }, ${ compileConstant(b) })) }
            else if a.isData && b.isData then
              '{
                SIR.Apply(
                  SIR.Apply(SIR.Builtin(DefaultFun.MkPairData), ${ compileExpr(a) }),
                  ${ compileExpr(b) }
                )
              }
            else
              report.errorAndAbort(
                s"""Builtin Pair can only be created either by 2 literals or 2 Data variables:
              |Pair[${tpe1.tpe.show},${tpe2.tpe.show}](${a.show}, ${b.show})
              |- ${a.show} literal: ${a.isLiteral}, data: ${a.isData}
              |- ${b.show} literal: ${b.isLiteral}, data: ${b.isData}
              |""".stripMargin
              )

          case Select(lst, fun) if lst.isList =>
            fun match
              case "head" =>
                '{ SIR.Apply(SIR.Builtin(DefaultFun.HeadList), ${ compileExpr(lst) }) }
              case "tail" =>
                '{ SIR.Apply(SIR.Builtin(DefaultFun.TailList), ${ compileExpr(lst) }) }
              case "isEmpty" =>
                '{ SIR.Apply(SIR.Builtin(DefaultFun.NullList), ${ compileExpr(lst) }) }
              case _ =>
                report.errorAndAbort(
                  s"compileExpr: Unsupported list method $fun. Only head, tail and isEmpty are supported"
                )

          case TypeApply(Select(list, "empty"), immutable.List(tpe))
              if list.tpe =:= TypeRepr.of[builtins.List.type] =>
            val tpeE = Expr(typeReprToDefaultUni(tpe.tpe))
            '{ SIR.Const(List($tpeE, Nil)) }
          case Apply(
                TypeApply(Select(list, "::"), immutable.List(tpe)),
                immutable.List(arg)
              ) if list.isList =>
            val argE = compileExpr(arg)
            '{ SIR.Apply(SIR.Apply(SIR.Builtin(DefaultFun.MkCons), $argE), ${ compileExpr(list) }) }
          case Apply(
                TypeApply(Select(list, "apply"), immutable.List(tpe)),
                immutable.List(ex)
              ) if list.tpe =:= TypeRepr.of[builtins.List.type] =>
            val tpeE = Expr(typeReprToDefaultUni(tpe.tpe))
            ex match
              case Typed(Repeated(args, _), _) =>
                val allLiterals = args.forall(arg => compileConstant.isDefinedAt(arg))
                if allLiterals then
                  val lits = Expr.ofList(args.map(compileConstant))
                  '{ SIR.Const(List($tpeE, $lits)) }
                else
                  val nil = '{ SIR.Const(List($tpeE, Nil)) }
                  args.foldRight(nil) { (arg, acc) =>
                    '{
                      SIR.Apply(
                        SIR.Apply(SIR.Builtin(DefaultFun.MkCons), ${ compileExpr(arg) }),
                        $acc
                      )
                    }
                  }
              case _ =>
                report.errorAndAbort(
                  s"compileExpr: List is not supported yet ${ex}"
                )
          // throw new Exception("error msg")
          // Supports any exception type that uses first argument as message
          case Apply(Ident("throw"), immutable.List(ex)) =>
            val msg = ex match
              case Apply(
                    Select(New(tpt), "<init>"),
                    immutable.List(Literal(StringConstant(msg)), _*)
                  ) if tpt.tpe <:< TypeRepr.of[Exception] =>
                Expr(msg)
              case term =>
                Expr("error")
            '{ SIR.Error($msg) }
          // f.apply(arg) => Apply(f, arg)
          case Apply(Select(Ident(a), "apply"), args) =>
            val argsE = args.map(compileExpr)
            argsE.foldLeft('{ SIR.Var(NamedDeBruijn(${ Expr(a) })) })((acc, arg) =>
              '{ SIR.Apply($acc, $arg) }
            )
          // ByteString equality
          case Select(lhs, "==") if lhs.tpe.widen =:= TypeRepr.of[builtins.ByteString] =>
            '{ SIR.Apply(SIR.Builtin(DefaultFun.EqualsByteString), ${ compileExpr(lhs) }) }
          // Data BUILTINS
          case bi if bi.tpe.show == "scalus.builtins.Builtins.mkConstr" =>
            '{ SIR.Builtin(DefaultFun.ConstrData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.mkList" =>
            '{ SIR.Builtin(DefaultFun.ListData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.mkMap" =>
            '{ SIR.Builtin(DefaultFun.MapData) }
          case Select(dataB, "apply") if dataB.tpe =:= TypeRepr.of[scalus.uplc.Data.B.type] =>
            '{ SIR.Builtin(DefaultFun.BData) }
          case Select(dataI, "apply") if dataI.tpe =:= TypeRepr.of[scalus.uplc.Data.I.type] =>
            '{ SIR.Builtin(DefaultFun.IData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.unsafeDataAsConstr" =>
            '{ SIR.Builtin(DefaultFun.UnConstrData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.unsafeDataAsList" =>
            '{ SIR.Builtin(DefaultFun.UnListData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.unsafeDataAsMap" =>
            '{ SIR.Builtin(DefaultFun.UnMapData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.unsafeDataAsB" =>
            '{ SIR.Builtin(DefaultFun.UnBData) }
          case bi if bi.tpe.show == "scalus.builtins.Builtins.unsafeDataAsI" =>
            '{ SIR.Builtin(DefaultFun.UnIData) }
          // Generic Apply
          case Apply(f, args) =>
            val fE = compileExpr(f)
            val argsE = args.map(compileExpr)
            if argsE.isEmpty then '{ SIR.Apply($fE, SIR.Const(Unit)) }
            else argsE.foldLeft(fE)((acc, arg) => '{ SIR.Apply($acc, $arg) })
          // (x: T) => body
          case Block(
                immutable.List(
                  DefDef("$anonfun", immutable.List(TermParamClause(args)), tpe, Some(body))
                ),
                Closure(Ident("$anonfun"), _)
              ) =>
            val bodyExpr: Expr[scalus.sir.SIR] = {
              val bE = compileExpr(body)
              if args.isEmpty then '{ SIR.LamAbs("_", $bE) }
              else
                val names = args.map { case ValDef(name, tpe, rhs) => Expr(name) }
                names.foldRight(bE) { (name, acc) =>
                  '{ SIR.LamAbs($name, $acc) }
                }
            }
            '{ $bodyExpr }
          case Block(stmt, expr)       => compileBlock(stmt, expr)
          case Typed(expr, _)          => compileExpr(expr)
          case Closure(Ident(name), _) => '{ SIR.Var(NamedDeBruijn(${ Expr(name) })) }
          case Inlined(_, _, expr)     => compileExpr(expr)
          case x => report.errorAndAbort(s"Unsupported expression: ${x.show}\n$x")
    }

//    report.info(s"Compiling ${e.asTerm}")
    compileExpr(e.asTerm)
}
