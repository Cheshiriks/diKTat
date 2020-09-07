package org.cqfn.diktat.ruleset.rules

import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.FLOAT_LITERAL
import com.pinterest.ktlint.core.ast.ElementType.INTEGER_LITERAL
import java.lang.StringBuilder
import org.cqfn.diktat.common.config.rules.RuleConfiguration
import org.cqfn.diktat.common.config.rules.RulesConfig
import org.cqfn.diktat.common.config.rules.getRuleConfig
import org.cqfn.diktat.ruleset.constants.Warnings
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement

class LongNumericalValuesSeparatedRule : Rule("long-numerical-values") {
    private lateinit var configRules: List<RulesConfig>
    private lateinit var emitWarn: ((offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit)
    private var isFixMode: Boolean = false

    companion object {
        private const val DELIMITER_LENGTH: Int = 3
        private const val MAX_NUMBER_LENGTH: Int = 3
    }

    override fun visit(node: ASTNode,
                       autoCorrect: Boolean,
                       params: KtLint.Params,
                       emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit) {
        configRules = params.getDiktatConfigRules()
        emitWarn = emit
        isFixMode = autoCorrect

        val configuration = LongNumericalValuesConfiguration(
                configRules.getRuleConfig(Warnings.LONG_NUMERICAL_VALUES_SEPARATED)?.configuration ?: mapOf())

        if (node.elementType == INTEGER_LITERAL) {
            if (!isValidConstant(node.text, configuration, node)) {
                Warnings.LONG_NUMERICAL_VALUES_SEPARATED.warnAndFix(configRules, emitWarn, isFixMode, node.text, node.startOffset) {
                    fixIntegerConstant(node, configuration.maxBlockLength)
                }
            }
        }

        if (node.elementType == FLOAT_LITERAL) {
            if (!isValidConstant(node.text, configuration, node)) {
                val parts = node.text.split(".")

                Warnings.LONG_NUMERICAL_VALUES_SEPARATED.warnAndFix(configRules, emitWarn, isFixMode, node.text, node.startOffset) {
                    fixFloatConstantPart(parts[0], parts[1], configuration, node)
                }
            }
        }
    }

    private fun fixIntegerConstant(node: ASTNode, maxBlockLength: Int) {
        val resultRealPart = StringBuilder(nodePrefix(node.text))

        val chunks = removePrefixSuffix(node.text).reversed().chunked(maxBlockLength).reversed()
        resultRealPart.append(chunks.joinToString(separator = "_") { it.reversed() })

        resultRealPart.append(nodeSuffix(node.text))
        (node as LeafPsiElement).replaceWithText(resultRealPart.toString())
    }

    private fun fixFloatConstantPart(realPart: String, fractionalPart: String, configuration: LongNumericalValuesConfiguration, node: ASTNode) {
        val resultRealPart = StringBuilder(nodePrefix(realPart))
        val resultFractionalPart = StringBuilder()

        val realNumber = removePrefixSuffix(realPart)
        if (realNumber.length > configuration.maxLength) {
            val chunks = realNumber.reversed().chunked(configuration.maxBlockLength).reversed()
            resultRealPart.append(chunks.joinToString(separator = "_") { it.reversed() })

            resultRealPart.append(nodeSuffix(realPart)).append(".")
        } else {
            resultRealPart.append(realNumber).append(".")
        }

        val fractionalNumber = removePrefixSuffix(fractionalPart)
        if (fractionalNumber.length > configuration.maxLength) {
            val chunks = fractionalNumber.chunked(configuration.maxBlockLength)
            resultFractionalPart.append(chunks.joinToString(separator = "_", postfix = nodeSuffix(fractionalPart)) { it })

            resultFractionalPart.append(nodeSuffix(fractionalPart))
        } else {
            resultFractionalPart.append(fractionalNumber).append(nodeSuffix(fractionalPart))
        }

        (node as LeafPsiElement).replaceWithText(resultRealPart.append(resultFractionalPart).toString())
    }

    private fun nodePrefix(nodeText: String) = when {
        nodeText.startsWith("0b") -> "0b"
        nodeText.startsWith("0x") -> "0x"
        else -> ""
    }

    private fun nodeSuffix(nodeText: String) = when {
        nodeText.endsWith("L") -> "L"
        nodeText.endsWith("f", true) -> "f"
        else -> ""
    }


    private fun isValidConstant (text: String, configuration: LongNumericalValuesConfiguration, node: ASTNode) : Boolean {
        if (text.contains("_")) {
            checkBlocks(removePrefixSuffix(text), configuration, node)
            return true
        }

        return text.split(".").map { removePrefixSuffix(it) }.all { it.length < configuration.maxLength }
    }

    private fun checkBlocks(text: String, configuration: LongNumericalValuesConfiguration, node: ASTNode) {
        val blocks = text.split("_", ".")

        blocks.forEach {
            if (it.length > configuration.maxBlockLength) {
                Warnings.LONG_NUMERICAL_VALUES_SEPARATED.warn(configRules, emitWarn, false, "this block is too long $it", node.startOffset)
            }
        }
    }

    private fun removePrefixSuffix (text : String) : String {
        if (text.startsWith("0x")) {
            return text.removePrefix("0x")
        }

        return text.removePrefix("0b")
                .removeSuffix("L")
                .removeSuffix("f")
                .removeSuffix("F")
    }

    class LongNumericalValuesConfiguration(config: Map<String, String>) : RuleConfiguration(config) {
        val maxLength = config["maxNumberLength"]?.toIntOrNull() ?: MAX_NUMBER_LENGTH
        val maxBlockLength = config["maxBlockLength"]?.toIntOrNull() ?: DELIMITER_LENGTH
    }
}