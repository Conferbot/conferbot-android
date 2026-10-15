package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowLogic
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState

/**
 * The logic steps: condition, boolean-logic, math-operation, variable,
 * random-flow, jump-to and business-hours. They show nothing and route at
 * once. The rules are the web widget's, ported unchanged in core/flow/FlowLogic
 * (handlers/conditionEval.ts, handlers/logicValues.ts); the routing is
 * handlers/logical.ts (RN LogicNodeHandlers.ts):
 *
 * - condition / boolean: true leaves by source-0, false by source-1;
 * - business hours: open source-0, closed source-1;
 * - random split: "source-" + the picked branch id (legacy: its position);
 *   no wired branch ends the chat;
 * - math and variable save by name and take the first edge;
 * - jump-to: data.nodeId, else targetNodeId, else jumpTo; a missing target ends the chat.
 */

/** A variable read the way the web logic steps read it: the answers, first entry by name. */
internal fun answerLookup(key: String): Any? = if (ChatState.hasAnswerVariable(key)) ChatState.getAnswer(key) else null

/** condition-node: true -> source-0, false -> source-1. */
class ConditionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.CONDITION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult =
        NodeResult.Proceed(if (FlowLogic.evaluateCondition(nodeData, ::answerLookup)) "source-0" else "source-1")
}

/** boolean-logic-node: "No", "off", "0" and empty are false; true -> source-0. */
class BooleanLogicNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.BOOLEAN_LOGIC

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult =
        NodeResult.Proceed(if (FlowLogic.booleanResult(nodeData, ::answerLookup)) "source-0" else "source-1")
}

/** math-operation-node: leftValue op rightValue, saved by name (answerVariable, default "math_operations"). */
class MathOperationNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.MATH_OPERATION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        ChatState.saveVariableByName(FlowLogic.mathVariableName(nodeData), FlowLogic.mathResult(nodeData, ::answerLookup), nodeId)
        return NodeResult.Proceed()
    }
}

/** random-flow-node: a wired branch by weight, leaving by "source-" + its id. */
class RandomFlowNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.RANDOM_FLOW

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val handle = FlowLogic.pickRandomHandle(nodeData["branches"], ctx.edges().map { it.sourceHandle }, ctx.random())
        return NodeResult.Proceed(handle ?: FlowLogic.NO_PATH)
    }
}

/** variable-node: data.value ({x} filled; a number with isNumber), saved by data.name. */
class VariableNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.VARIABLE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val name = Js.str(nodeData["name"]).trim()
        val value = FlowLogic.variableValue(nodeData, ::answerLookup)
        if (name.isNotEmpty()) {
            ChatState.saveVariableByName(name, value, nodeId)
            ChatState.setVariable(name, value)
        }
        return NodeResult.Proceed()
    }
}

/** jump-to-node: to data.nodeId / targetNodeId / jumpTo; a missing target ends the chat. */
class JumpToNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.JUMP_TO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult = NodeResult.JumpTo(FlowLogic.jumpTargetOf(nodeData))
}

/** business-hours-node: open now on the owner's clock -> source-0, closed -> source-1. */
class BusinessHoursNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.BUSINESS_HOURS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        return NodeResult.Proceed(if (FlowLogic.isWithinBusinessHours(nodeData, ctx.now(), ctx.visitorTimeZone)) "source-0" else "source-1")
    }
}
