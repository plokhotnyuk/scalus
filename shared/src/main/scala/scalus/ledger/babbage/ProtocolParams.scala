package scalus.ledger.babbage
import upickle.default.*
import scala.quoted.Quotes

import upickle.default.ReadWriter

case class ProtocolVersion(major: Int, minor: Int) derives ReadWriter

case class MaxTxExecutionUnits(memory: Long, steps: Long) derives ReadWriter

case class MaxBlockExecutionUnits(memory: Long, steps: Long) derives ReadWriter

case class ExecutionUnitPrices(priceMemory: Double, priceSteps: Double) derives ReadWriter

/** Protocol parameters for the Cardano blockchain of Babbage era Field names are taken from the
  * `cardano-cli query protocol-parameters` output
  * @note
  *   These names are different from CIP-55, don't ask me why.
  */
case class ProtocolParams(
    collateralPercentage: Long,
    costModels: Map[String, Seq[Int]],
    decentralization: Option[Double],
    executionUnitPrices: ExecutionUnitPrices,
    extraPraosEntropy: Option[String],
    maxBlockBodySize: Long,
    maxBlockExecutionUnits: MaxBlockExecutionUnits,
    maxBlockHeaderSize: Long,
    maxCollateralInputs: Long,
    maxTxExecutionUnits: MaxTxExecutionUnits,
    maxTxSize: Long,
    maxValueSize: Long,
    minPoolCost: Long,
    minUTxOValue: Option[Long],
    monetaryExpansion: Double,
    poolPledgeInfluence: Double,
    poolRetireMaxEpoch: Long,
    protocolVersion: ProtocolVersion,
    stakeAddressDeposit: Long,
    stakePoolDeposit: Long,
    stakePoolTargetNum: Long,
    treasuryCut: Double,
    txFeeFixed: Long,
    txFeePerByte: Long,
    utxoCostPerByte: Long,
    utxoCostPerWord: Option[Long]
) derives ReadWriter

object ProtocolParams {
    import upickle.default.{readwriter, ReadWriter}
    val blockfrostParamsRW: Reader[ProtocolParams] =
        readwriter[ujson.Value].bimap[ProtocolParams](
          params =>
              ujson.Obj(
                "min_fee_a" -> params.txFeePerByte,
                "min_fee_b" -> params.txFeeFixed,
                "max_block_size" -> params.maxBlockBodySize,
                "max_tx_size" -> params.maxTxSize,
                "max_block_header_size" -> params.maxBlockHeaderSize,
                "key_deposit" -> params.stakeAddressDeposit.toString,
                "pool_deposit" -> params.stakePoolDeposit.toString,
                "e_max" -> params.poolRetireMaxEpoch,
                "n_opt" -> params.stakePoolTargetNum,
                "a0" -> params.poolPledgeInfluence,
                "rho" -> params.monetaryExpansion,
                "tau" -> params.treasuryCut,
                "decentralisation_param" -> 0,
                "extra_entropy" -> params.extraPraosEntropy,
                "protocol_major_ver" -> params.protocolVersion.major,
                "protocol_minor_ver" -> params.protocolVersion.minor,
                "min_utxo" -> params.utxoCostPerByte.toString,
                "min_pool_cost" -> params.minPoolCost.toString,
                "nonce" -> null,
                "price_mem" -> params.executionUnitPrices.priceMemory,
                "price_step" -> params.executionUnitPrices.priceSteps,
                "max_tx_ex_mem" -> params.maxTxExecutionUnits.memory.toString,
                "max_tx_ex_steps" -> params.maxTxExecutionUnits.steps.toString,
                "max_block_ex_mem" -> params.maxBlockExecutionUnits.memory.toString,
                "max_block_ex_steps" -> params.maxBlockExecutionUnits.steps.toString,
                "max_val_size" -> params.maxValueSize,
                "collateral_percent" -> params.collateralPercentage,
                "max_collateral_inputs" -> params.maxCollateralInputs,
                "coins_per_utxo_size" -> params.utxoCostPerByte.toString,
                "coins_per_utxo_word" -> params.utxoCostPerWord.map(_.toString).getOrElse(null)
              ),
          json =>
              ProtocolParams(
                collateralPercentage = json("collateral_percent").num.toLong,
                costModels = json("cost_models").obj.map { case (k, v) =>
                    k -> v.obj.values.map(_.num.toInt).toSeq
                }.toMap,
                decentralization = None,
                executionUnitPrices = ExecutionUnitPrices(
                  priceMemory = json("price_mem").num,
                  priceSteps = json("price_step").num
                ),
                extraPraosEntropy = None,
                maxBlockBodySize = json("max_block_size").num.toLong,
                maxBlockExecutionUnits = MaxBlockExecutionUnits(
                  memory = json("max_block_ex_mem").str.toLong,
                  steps = json("max_block_ex_steps").str.toLong
                ),
                maxBlockHeaderSize = json("max_block_header_size").num.toLong,
                maxCollateralInputs = json("max_collateral_inputs").num.toLong,
                maxTxExecutionUnits = MaxTxExecutionUnits(
                  memory = json("max_tx_ex_mem").str.toLong,
                  steps = json("max_tx_ex_steps").str.toLong
                ),
                maxTxSize = json("max_tx_size").num.toLong,
                maxValueSize = json("max_val_size").str.toLong,
                minPoolCost = json("min_pool_cost").str.toLong,
                minUTxOValue = None,
                monetaryExpansion = json("rho").num,
                poolPledgeInfluence = json("a0").num,
                poolRetireMaxEpoch = json("e_max").num.toLong,
                protocolVersion = ProtocolVersion(
                  major = json("protocol_major_ver").num.toInt,
                  minor = json("protocol_minor_ver").num.toInt
                ),
                stakeAddressDeposit = json("key_deposit").str.toLong,
                stakePoolDeposit = json("pool_deposit").str.toLong,
                stakePoolTargetNum = json("n_opt").num.toLong,
                treasuryCut = json("tau").num,
                txFeeFixed = json("min_fee_b").num.toLong,
                txFeePerByte = json("min_fee_a").num.toLong,
                utxoCostPerByte = json("min_utxo").str.toLong,
                utxoCostPerWord = None
              )
        )
}

/*
  Funny thing is that JVM has a limit of 255 parameters in a method if the args are Ints.
  If it's Long, than the limit is 127.
  And we can't generate a constructor call for `PlutusV1Params` or `PlutusV2Params`
  which has more than 127 parameters.
  So I'm using Ints here, and that should be enough for the protocol parameters.
 */

case class PlutusV1Params(
    `addInteger-cpu-arguments-intercept`: Int,
    `addInteger-cpu-arguments-slope`: Int,
    `addInteger-memory-arguments-intercept`: Int,
    `addInteger-memory-arguments-slope`: Int,
    `appendByteString-cpu-arguments-intercept`: Int,
    `appendByteString-cpu-arguments-slope`: Int,
    `appendByteString-memory-arguments-intercept`: Int,
    `appendByteString-memory-arguments-slope`: Int,
    `appendString-cpu-arguments-intercept`: Int,
    `appendString-cpu-arguments-slope`: Int,
    `appendString-memory-arguments-intercept`: Int,
    `appendString-memory-arguments-slope`: Int,
    `bData-cpu-arguments`: Int,
    `bData-memory-arguments`: Int,
    `blake2b_256-cpu-arguments-intercept`: Int,
    `blake2b_256-cpu-arguments-slope`: Int,
    `blake2b_256-memory-arguments`: Int,
    `cekApplyCost-exBudgetCPU`: Int,
    `cekApplyCost-exBudgetMemory`: Int,
    `cekBuiltinCost-exBudgetCPU`: Int,
    `cekBuiltinCost-exBudgetMemory`: Int,
    `cekConstCost-exBudgetCPU`: Int,
    `cekConstCost-exBudgetMemory`: Int,
    `cekDelayCost-exBudgetCPU`: Int,
    `cekDelayCost-exBudgetMemory`: Int,
    `cekForceCost-exBudgetCPU`: Int,
    `cekForceCost-exBudgetMemory`: Int,
    `cekLamCost-exBudgetCPU`: Int,
    `cekLamCost-exBudgetMemory`: Int,
    `cekStartupCost-exBudgetCPU`: Int,
    `cekStartupCost-exBudgetMemory`: Int,
    `cekVarCost-exBudgetCPU`: Int,
    `cekVarCost-exBudgetMemory`: Int,
    `chooseData-cpu-arguments`: Int,
    `chooseData-memory-arguments`: Int,
    `chooseList-cpu-arguments`: Int,
    `chooseList-memory-arguments`: Int,
    `chooseUnit-cpu-arguments`: Int,
    `chooseUnit-memory-arguments`: Int,
    `consByteString-cpu-arguments-intercept`: Int,
    `consByteString-cpu-arguments-slope`: Int,
    `consByteString-memory-arguments-intercept`: Int,
    `consByteString-memory-arguments-slope`: Int,
    `constrData-cpu-arguments`: Int,
    `constrData-memory-arguments`: Int,
    `decodeUtf8-cpu-arguments-intercept`: Int,
    `decodeUtf8-cpu-arguments-slope`: Int,
    `decodeUtf8-memory-arguments-intercept`: Int,
    `decodeUtf8-memory-arguments-slope`: Int,
    `divideInteger-cpu-arguments-constant`: Int,
    `divideInteger-cpu-arguments-model-arguments-intercept`: Int,
    `divideInteger-cpu-arguments-model-arguments-slope`: Int,
    `divideInteger-memory-arguments-intercept`: Int,
    `divideInteger-memory-arguments-minimum`: Int,
    `divideInteger-memory-arguments-slope`: Int,
    `encodeUtf8-cpu-arguments-intercept`: Int,
    `encodeUtf8-cpu-arguments-slope`: Int,
    `encodeUtf8-memory-arguments-intercept`: Int,
    `encodeUtf8-memory-arguments-slope`: Int,
    `equalsByteString-cpu-arguments-constant`: Int,
    `equalsByteString-cpu-arguments-intercept`: Int,
    `equalsByteString-cpu-arguments-slope`: Int,
    `equalsByteString-memory-arguments`: Int,
    `equalsData-cpu-arguments-intercept`: Int,
    `equalsData-cpu-arguments-slope`: Int,
    `equalsData-memory-arguments`: Int,
    `equalsInteger-cpu-arguments-intercept`: Int,
    `equalsInteger-cpu-arguments-slope`: Int,
    `equalsInteger-memory-arguments`: Int,
    `equalsString-cpu-arguments-constant`: Int,
    `equalsString-cpu-arguments-intercept`: Int,
    `equalsString-cpu-arguments-slope`: Int,
    `equalsString-memory-arguments`: Int,
    `fstPair-cpu-arguments`: Int,
    `fstPair-memory-arguments`: Int,
    `headList-cpu-arguments`: Int,
    `headList-memory-arguments`: Int,
    `iData-cpu-arguments`: Int,
    `iData-memory-arguments`: Int,
    `ifThenElse-cpu-arguments`: Int,
    `ifThenElse-memory-arguments`: Int,
    `indexByteString-cpu-arguments`: Int,
    `indexByteString-memory-arguments`: Int,
    `lengthOfByteString-cpu-arguments`: Int,
    `lengthOfByteString-memory-arguments`: Int,
    `lessThanByteString-cpu-arguments-intercept`: Int,
    `lessThanByteString-cpu-arguments-slope`: Int,
    `lessThanByteString-memory-arguments`: Int,
    `lessThanEqualsByteString-cpu-arguments-intercept`: Int,
    `lessThanEqualsByteString-cpu-arguments-slope`: Int,
    `lessThanEqualsByteString-memory-arguments`: Int,
    `lessThanEqualsInteger-cpu-arguments-intercept`: Int,
    `lessThanEqualsInteger-cpu-arguments-slope`: Int,
    `lessThanEqualsInteger-memory-arguments`: Int,
    `lessThanInteger-cpu-arguments-intercept`: Int,
    `lessThanInteger-cpu-arguments-slope`: Int,
    `lessThanInteger-memory-arguments`: Int,
    `listData-cpu-arguments`: Int,
    `listData-memory-arguments`: Int,
    `mapData-cpu-arguments`: Int,
    `mapData-memory-arguments`: Int,
    `mkCons-cpu-arguments`: Int,
    `mkCons-memory-arguments`: Int,
    `mkNilData-cpu-arguments`: Int,
    `mkNilData-memory-arguments`: Int,
    `mkNilPairData-cpu-arguments`: Int,
    `mkNilPairData-memory-arguments`: Int,
    `mkPairData-cpu-arguments`: Int,
    `mkPairData-memory-arguments`: Int,
    `modInteger-cpu-arguments-constant`: Int,
    `modInteger-cpu-arguments-model-arguments-intercept`: Int,
    `modInteger-cpu-arguments-model-arguments-slope`: Int,
    `modInteger-memory-arguments-intercept`: Int,
    `modInteger-memory-arguments-minimum`: Int,
    `modInteger-memory-arguments-slope`: Int,
    `multiplyInteger-cpu-arguments-intercept`: Int,
    `multiplyInteger-cpu-arguments-slope`: Int,
    `multiplyInteger-memory-arguments-intercept`: Int,
    `multiplyInteger-memory-arguments-slope`: Int,
    `nullList-cpu-arguments`: Int,
    `nullList-memory-arguments`: Int,
    `quotientInteger-cpu-arguments-constant`: Int,
    `quotientInteger-cpu-arguments-model-arguments-intercept`: Int,
    `quotientInteger-cpu-arguments-model-arguments-slope`: Int,
    `quotientInteger-memory-arguments-intercept`: Int,
    `quotientInteger-memory-arguments-minimum`: Int,
    `quotientInteger-memory-arguments-slope`: Int,
    `remainderInteger-cpu-arguments-constant`: Int,
    `remainderInteger-cpu-arguments-model-arguments-intercept`: Int,
    `remainderInteger-cpu-arguments-model-arguments-slope`: Int,
    `remainderInteger-memory-arguments-intercept`: Int,
    `remainderInteger-memory-arguments-minimum`: Int,
    `remainderInteger-memory-arguments-slope`: Int,
    `sha2_256-cpu-arguments-intercept`: Int,
    `sha2_256-cpu-arguments-slope`: Int,
    `sha2_256-memory-arguments`: Int,
    `sha3_256-cpu-arguments-intercept`: Int,
    `sha3_256-cpu-arguments-slope`: Int,
    `sha3_256-memory-arguments`: Int,
    `sliceByteString-cpu-arguments-intercept`: Int,
    `sliceByteString-cpu-arguments-slope`: Int,
    `sliceByteString-memory-arguments-intercept`: Int,
    `sliceByteString-memory-arguments-slope`: Int,
    `sndPair-cpu-arguments`: Int,
    `sndPair-memory-arguments`: Int,
    `subtractInteger-cpu-arguments-intercept`: Int,
    `subtractInteger-cpu-arguments-slope`: Int,
    `subtractInteger-memory-arguments-intercept`: Int,
    `subtractInteger-memory-arguments-slope`: Int,
    `tailList-cpu-arguments`: Int,
    `tailList-memory-arguments`: Int,
    `trace-cpu-arguments`: Int,
    `trace-memory-arguments`: Int,
    `unBData-cpu-arguments`: Int,
    `unBData-memory-arguments`: Int,
    `unConstrData-cpu-arguments`: Int,
    `unConstrData-memory-arguments`: Int,
    `unIData-cpu-arguments`: Int,
    `unIData-memory-arguments`: Int,
    `unListData-cpu-arguments`: Int,
    `unListData-memory-arguments`: Int,
    `unMapData-cpu-arguments`: Int,
    `unMapData-memory-arguments`: Int,
    `verifyEd25519Signature-cpu-arguments-intercept`: Int,
    `verifyEd25519Signature-cpu-arguments-slope`: Int,
    `verifyEd25519Signature-memory-arguments`: Int
)

case class PlutusV2Params(
    `addInteger-cpu-arguments-intercept`: Int,
    `addInteger-cpu-arguments-slope`: Int,
    `addInteger-memory-arguments-intercept`: Int,
    `addInteger-memory-arguments-slope`: Int,
    `appendByteString-cpu-arguments-intercept`: Int,
    `appendByteString-cpu-arguments-slope`: Int,
    `appendByteString-memory-arguments-intercept`: Int,
    `appendByteString-memory-arguments-slope`: Int,
    `appendString-cpu-arguments-intercept`: Int,
    `appendString-cpu-arguments-slope`: Int,
    `appendString-memory-arguments-intercept`: Int,
    `appendString-memory-arguments-slope`: Int,
    `bData-cpu-arguments`: Int,
    `bData-memory-arguments`: Int,
    `blake2b_256-cpu-arguments-intercept`: Int,
    `blake2b_256-cpu-arguments-slope`: Int,
    `blake2b_256-memory-arguments`: Int,
    `cekApplyCost-exBudgetCPU`: Int,
    `cekApplyCost-exBudgetMemory`: Int,
    `cekBuiltinCost-exBudgetCPU`: Int,
    `cekBuiltinCost-exBudgetMemory`: Int,
    `cekConstCost-exBudgetCPU`: Int,
    `cekConstCost-exBudgetMemory`: Int,
    `cekDelayCost-exBudgetCPU`: Int,
    `cekDelayCost-exBudgetMemory`: Int,
    `cekForceCost-exBudgetCPU`: Int,
    `cekForceCost-exBudgetMemory`: Int,
    `cekLamCost-exBudgetCPU`: Int,
    `cekLamCost-exBudgetMemory`: Int,
    `cekStartupCost-exBudgetCPU`: Int,
    `cekStartupCost-exBudgetMemory`: Int,
    `cekVarCost-exBudgetCPU`: Int,
    `cekVarCost-exBudgetMemory`: Int,
    `chooseData-cpu-arguments`: Int,
    `chooseData-memory-arguments`: Int,
    `chooseList-cpu-arguments`: Int,
    `chooseList-memory-arguments`: Int,
    `chooseUnit-cpu-arguments`: Int,
    `chooseUnit-memory-arguments`: Int,
    `consByteString-cpu-arguments-intercept`: Int,
    `consByteString-cpu-arguments-slope`: Int,
    `consByteString-memory-arguments-intercept`: Int,
    `consByteString-memory-arguments-slope`: Int,
    `constrData-cpu-arguments`: Int,
    `constrData-memory-arguments`: Int,
    `decodeUtf8-cpu-arguments-intercept`: Int,
    `decodeUtf8-cpu-arguments-slope`: Int,
    `decodeUtf8-memory-arguments-intercept`: Int,
    `decodeUtf8-memory-arguments-slope`: Int,
    `divideInteger-cpu-arguments-constant`: Int,
    `divideInteger-cpu-arguments-model-arguments-intercept`: Int,
    `divideInteger-cpu-arguments-model-arguments-slope`: Int,
    `divideInteger-memory-arguments-intercept`: Int,
    `divideInteger-memory-arguments-minimum`: Int,
    `divideInteger-memory-arguments-slope`: Int,
    `encodeUtf8-cpu-arguments-intercept`: Int,
    `encodeUtf8-cpu-arguments-slope`: Int,
    `encodeUtf8-memory-arguments-intercept`: Int,
    `encodeUtf8-memory-arguments-slope`: Int,
    `equalsByteString-cpu-arguments-constant`: Int,
    `equalsByteString-cpu-arguments-intercept`: Int,
    `equalsByteString-cpu-arguments-slope`: Int,
    `equalsByteString-memory-arguments`: Int,
    `equalsData-cpu-arguments-intercept`: Int,
    `equalsData-cpu-arguments-slope`: Int,
    `equalsData-memory-arguments`: Int,
    `equalsInteger-cpu-arguments-intercept`: Int,
    `equalsInteger-cpu-arguments-slope`: Int,
    `equalsInteger-memory-arguments`: Int,
    `equalsString-cpu-arguments-constant`: Int,
    `equalsString-cpu-arguments-intercept`: Int,
    `equalsString-cpu-arguments-slope`: Int,
    `equalsString-memory-arguments`: Int,
    `fstPair-cpu-arguments`: Int,
    `fstPair-memory-arguments`: Int,
    `headList-cpu-arguments`: Int,
    `headList-memory-arguments`: Int,
    `iData-cpu-arguments`: Int,
    `iData-memory-arguments`: Int,
    `ifThenElse-cpu-arguments`: Int,
    `ifThenElse-memory-arguments`: Int,
    `indexByteString-cpu-arguments`: Int,
    `indexByteString-memory-arguments`: Int,
    `lengthOfByteString-cpu-arguments`: Int,
    `lengthOfByteString-memory-arguments`: Int,
    `lessThanByteString-cpu-arguments-intercept`: Int,
    `lessThanByteString-cpu-arguments-slope`: Int,
    `lessThanByteString-memory-arguments`: Int,
    `lessThanEqualsByteString-cpu-arguments-intercept`: Int,
    `lessThanEqualsByteString-cpu-arguments-slope`: Int,
    `lessThanEqualsByteString-memory-arguments`: Int,
    `lessThanEqualsInteger-cpu-arguments-intercept`: Int,
    `lessThanEqualsInteger-cpu-arguments-slope`: Int,
    `lessThanEqualsInteger-memory-arguments`: Int,
    `lessThanInteger-cpu-arguments-intercept`: Int,
    `lessThanInteger-cpu-arguments-slope`: Int,
    `lessThanInteger-memory-arguments`: Int,
    `listData-cpu-arguments`: Int,
    `listData-memory-arguments`: Int,
    `mapData-cpu-arguments`: Int,
    `mapData-memory-arguments`: Int,
    `mkCons-cpu-arguments`: Int,
    `mkCons-memory-arguments`: Int,
    `mkNilData-cpu-arguments`: Int,
    `mkNilData-memory-arguments`: Int,
    `mkNilPairData-cpu-arguments`: Int,
    `mkNilPairData-memory-arguments`: Int,
    `mkPairData-cpu-arguments`: Int,
    `mkPairData-memory-arguments`: Int,
    `modInteger-cpu-arguments-constant`: Int,
    `modInteger-cpu-arguments-model-arguments-intercept`: Int,
    `modInteger-cpu-arguments-model-arguments-slope`: Int,
    `modInteger-memory-arguments-intercept`: Int,
    `modInteger-memory-arguments-minimum`: Int,
    `modInteger-memory-arguments-slope`: Int,
    `multiplyInteger-cpu-arguments-intercept`: Int,
    `multiplyInteger-cpu-arguments-slope`: Int,
    `multiplyInteger-memory-arguments-intercept`: Int,
    `multiplyInteger-memory-arguments-slope`: Int,
    `nullList-cpu-arguments`: Int,
    `nullList-memory-arguments`: Int,
    `quotientInteger-cpu-arguments-constant`: Int,
    `quotientInteger-cpu-arguments-model-arguments-intercept`: Int,
    `quotientInteger-cpu-arguments-model-arguments-slope`: Int,
    `quotientInteger-memory-arguments-intercept`: Int,
    `quotientInteger-memory-arguments-minimum`: Int,
    `quotientInteger-memory-arguments-slope`: Int,
    `remainderInteger-cpu-arguments-constant`: Int,
    `remainderInteger-cpu-arguments-model-arguments-intercept`: Int,
    `remainderInteger-cpu-arguments-model-arguments-slope`: Int,
    `remainderInteger-memory-arguments-intercept`: Int,
    `remainderInteger-memory-arguments-minimum`: Int,
    `remainderInteger-memory-arguments-slope`: Int,
    `serialiseData-cpu-arguments-intercept`: Int,
    `serialiseData-cpu-arguments-slope`: Int,
    `serialiseData-memory-arguments-intercept`: Int,
    `serialiseData-memory-arguments-slope`: Int,
    `sha2_256-cpu-arguments-intercept`: Int,
    `sha2_256-cpu-arguments-slope`: Int,
    `sha2_256-memory-arguments`: Int,
    `sha3_256-cpu-arguments-intercept`: Int,
    `sha3_256-cpu-arguments-slope`: Int,
    `sha3_256-memory-arguments`: Int,
    `sliceByteString-cpu-arguments-intercept`: Int,
    `sliceByteString-cpu-arguments-slope`: Int,
    `sliceByteString-memory-arguments-intercept`: Int,
    `sliceByteString-memory-arguments-slope`: Int,
    `sndPair-cpu-arguments`: Int,
    `sndPair-memory-arguments`: Int,
    `subtractInteger-cpu-arguments-intercept`: Int,
    `subtractInteger-cpu-arguments-slope`: Int,
    `subtractInteger-memory-arguments-intercept`: Int,
    `subtractInteger-memory-arguments-slope`: Int,
    `tailList-cpu-arguments`: Int,
    `tailList-memory-arguments`: Int,
    `trace-cpu-arguments`: Int,
    `trace-memory-arguments`: Int,
    `unBData-cpu-arguments`: Int,
    `unBData-memory-arguments`: Int,
    `unConstrData-cpu-arguments`: Int,
    `unConstrData-memory-arguments`: Int,
    `unIData-cpu-arguments`: Int,
    `unIData-memory-arguments`: Int,
    `unListData-cpu-arguments`: Int,
    `unListData-memory-arguments`: Int,
    `unMapData-cpu-arguments`: Int,
    `unMapData-memory-arguments`: Int,
    `verifyEcdsaSecp256k1Signature-cpu-arguments`: Int,
    `verifyEcdsaSecp256k1Signature-memory-arguments`: Int,
    `verifyEd25519Signature-cpu-arguments-intercept`: Int,
    `verifyEd25519Signature-cpu-arguments-slope`: Int,
    `verifyEd25519Signature-memory-arguments`: Int,
    `verifySchnorrSecp256k1Signature-cpu-arguments-intercept`: Int,
    `verifySchnorrSecp256k1Signature-cpu-arguments-slope`: Int,
    `verifySchnorrSecp256k1Signature-memory-arguments`: Int
)

object PlutusV1Params:
    inline def mkReadWriter[A]: ReadWriter[A] = ${ scalus.macros.Macros.mkReadWriterImpl[A] }
    inline def mkSeqRW[A]: (A => Seq[Int], Seq[Int] => A) = ${
        scalus.macros.Macros.mkSeqRWImpl[A]
    }

    given ReadWriter[PlutusV1Params] = mkReadWriter[PlutusV1Params]
    val (toSeq, fromSeq) = mkSeqRW[PlutusV1Params]

object PlutusV2Params:

    given ReadWriter[PlutusV2Params] = PlutusV1Params.mkReadWriter[PlutusV2Params]
    val (toSeq, fromSeq) = PlutusV1Params.mkSeqRW[PlutusV2Params]
