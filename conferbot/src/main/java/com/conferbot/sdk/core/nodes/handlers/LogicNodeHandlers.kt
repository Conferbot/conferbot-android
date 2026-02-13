package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*
import java.util.*

/**
 * Handler for condition-node
 * Evaluates conditions and routes to different branches
 */
class ConditionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.CONDITION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val isNumber = getBoolean(nodeData, "isNumber", false)
        // Support both field naming conventions: leftValue/rightValue (Android) and variable/value (web widget)
        val leftValue = getString(nodeData, "leftValue", "")
            .ifEmpty { getString(nodeData, "variable", "") }
        val rightValue = getString(nodeData, "rightValue", "")
            .ifEmpty { getString(nodeData, "value", getString(nodeData, "compareValue", "")) }
        val operator = getString(nodeData, "operator", "=")

        // Resolve values (they might be variable references)
        val resolvedLeft = state.resolveValue(leftValue)
        val resolvedRight = state.resolveValue(rightValue)

        // Handle isEmpty/isNotEmpty first (they don't need a right value)
        val operatorLower = operator.lowercase()
        if (operatorLower == "isempty" || operatorLower == "isnotempty") {
            val result = evaluateStringCondition(resolvedLeft.toString(), "", operator)
            return if (result) {
                NodeResult.Proceed(targetPort = "source-0")  // True branch
            } else {
                NodeResult.Proceed(targetPort = "source-1")  // False branch
            }
        }

        val result = if (isNumber) {
            evaluateNumericCondition(resolvedLeft, resolvedRight, operator)
        } else {
            evaluateStringCondition(resolvedLeft.toString(), resolvedRight.toString(), operator)
        }

        // Route based on result
        return if (result) {
            NodeResult.Proceed(targetPort = "source-0")  // True branch
        } else {
            NodeResult.Proceed(targetPort = "source-1")  // False branch
        }
    }

    private fun evaluateNumericCondition(left: Any, right: Any, operator: String): Boolean {
        val leftNum = when (left) {
            is Number -> left.toDouble()
            is String -> left.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val rightNum = when (right) {
            is Number -> right.toDouble()
            is String -> right.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        return when (operator) {
            "<", "lessThan" -> leftNum < rightNum
            ">", "greaterThan" -> leftNum > rightNum
            "=", "==", "equals" -> leftNum == rightNum
            "!=", "notEquals" -> leftNum != rightNum
            "<=", "lessThanOrEquals" -> leftNum <= rightNum
            ">=", "greaterThanOrEquals" -> leftNum >= rightNum
            else -> false
        }
    }

    private fun evaluateStringCondition(left: String, right: String, operator: String): Boolean {
        return when (operator.lowercase()) {
            "=", "==", "equals" -> left.equals(right, ignoreCase = true)
            "!=", "notequals" -> !left.equals(right, ignoreCase = true)
            "contains" -> left.contains(right, ignoreCase = true)
            "does not contain" -> !left.contains(right, ignoreCase = true)
            "starts with", "startswith" -> left.startsWith(right, ignoreCase = true)
            "ends with", "endswith" -> left.endsWith(right, ignoreCase = true)
            "matches" -> {
                try {
                    Regex(right, RegexOption.IGNORE_CASE).containsMatchIn(left)
                } catch (e: Exception) {
                    false
                }
            }
            "does not match" -> {
                try {
                    !Regex(right, RegexOption.IGNORE_CASE).containsMatchIn(left)
                } catch (e: Exception) {
                    true
                }
            }
            "isempty" -> {
                left.trim().isEmpty()
            }
            "isnotempty" -> {
                left.trim().isNotEmpty()
            }
            "in" -> {
                // Check if left is in right (comma-separated string or array-like)
                val collection = right.split(",").map { it.trim().lowercase() }
                collection.contains(left.lowercase().trim())
            }
            "notin" -> {
                val collection = right.split(",").map { it.trim().lowercase() }
                !collection.contains(left.lowercase().trim())
            }
            else -> left == right
        }
    }
}

/**
 * Handler for boolean-logic-node
 * Performs boolean operations on two values
 */
class BooleanLogicNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.BOOLEAN_LOGIC

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val leftValue = getString(nodeData, "leftValue", "false")
        val rightValue = getString(nodeData, "rightValue", "false")
        val operator = getString(nodeData, "operator", "AND")

        // Resolve and convert to boolean
        val left = toBoolean(state.resolveValue(leftValue))
        val right = toBoolean(state.resolveValue(rightValue))

        val result = when (operator.uppercase()) {
            "AND" -> left && right
            "OR" -> left || right
            "NOT" -> left && !right  // NOT as "left AND NOT right"
            "XOR" -> left xor right
            "NAND" -> !(left && right)
            "NOR" -> !(left || right)
            "XNOR" -> !(left xor right)
            else -> left && right
        }

        return if (result) {
            NodeResult.Proceed(targetPort = "source-0")  // True
        } else {
            NodeResult.Proceed(targetPort = "source-1")  // False
        }
    }

    private fun toBoolean(value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.lowercase() in listOf("true", "yes", "1")
            else -> false
        }
    }
}

/**
 * Handler for math-operation-node
 * Performs mathematical operations
 */
class MathOperationNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.MATH_OPERATION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Support both field naming conventions
        val leftValue = getString(nodeData, "leftValue", "")
            .ifEmpty { getString(nodeData, "operand1", getString(nodeData, "value1", getString(nodeData, "left", "0"))) }
        val rightValue = getString(nodeData, "rightValue", "")
            .ifEmpty { getString(nodeData, "operand2", getString(nodeData, "value2", getString(nodeData, "right", "0"))) }
        val operator = getString(nodeData, "operator", "+")
            .ifEmpty { getString(nodeData, "operation", "+") }

        // Resolve values
        val left = toNumber(state.resolveValue(leftValue))
        val right = toNumber(state.resolveValue(rightValue))

        val result = when (operator.lowercase()) {
            "+", "add" -> left + right
            "-", "subtract" -> left - right
            "*", "multiply" -> left * right
            "/", "divide" -> if (right != 0.0) left / right else 0.0
            "%", "modulo" -> if (right != 0.0) left % right else 0.0
            "power", "pow" -> Math.pow(left, right)
            "round" -> {
                // right = number of decimal places (default 0)
                val places = right.toInt().coerceAtLeast(0)
                val multiplier = Math.pow(10.0, places.toDouble())
                Math.round(left * multiplier).toDouble() / multiplier
            }
            "floor" -> Math.floor(left)
            "ceil" -> Math.ceil(left)
            "abs" -> Math.abs(left)
            "sqrt" -> Math.sqrt(left)
            "min" -> minOf(left, right)
            "max" -> maxOf(left, right)
            "random" -> {
                // Generate random number between left (min) and right (max)
                val min = minOf(left, right)
                val max = maxOf(left, right)
                min + kotlin.random.Random.nextDouble() * (max - min)
            }
            else -> left + right
        }

        // Store result in answer variable
        state.setAnswerVariable(nodeId, result)

        // Also store in named variable if specified (matching web widget behavior)
        val resultVariable = nodeData["resultVariable"]?.toString()
            ?: nodeData["variable"]?.toString()
        if (!resultVariable.isNullOrEmpty()) {
            state.setVariable(resultVariable, result)
            state.setAnswerVariableByKey(resultVariable, result)
        }

        return NodeResult.Proceed()
    }

    private fun toNumber(value: Any): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}

/**
 * Handler for random-flow-node
 * Routes randomly to different branches based on weights
 */
class RandomFlowNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.RANDOM_FLOW

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Support multiple field names matching both Android and web widget conventions
        var branches = getList<Map<String, Any?>>(nodeData, "branches")
        if (branches.isEmpty()) {
            branches = getList(nodeData, "paths")
        }
        if (branches.isEmpty()) {
            branches = getList(nodeData, "options")
        }

        if (branches.isEmpty()) {
            // No paths defined, try to use default port count
            val portCount = getInt(nodeData, "portCount", 2)
            val selectedIndex = kotlin.random.Random.nextInt(0, portCount)
            return NodeResult.Proceed(targetPort = "source-$selectedIndex")
        }

        // Calculate cumulative weights (support both "weight" and "percentage" fields)
        val weights = branches.map { branch ->
            val w = branch["weight"] ?: branch["percentage"]
            when (w) {
                is Number -> w.toDouble()
                is String -> w.toDoubleOrNull() ?: 1.0
                else -> 1.0
            }
        }

        val totalWeight = weights.sum()
        val random = kotlin.random.Random.nextDouble(0.0, totalWeight)

        // Find selected branch
        var cumulative = 0.0
        var selectedIndex = 0
        for (i in weights.indices) {
            cumulative += weights[i]
            if (random < cumulative) {
                selectedIndex = i
                break
            }
        }

        return NodeResult.Proceed(targetPort = "source-$selectedIndex")
    }
}

/**
 * Handler for variable-node
 * Creates or updates variables
 */
class VariableNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.VARIABLE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val customNameValue = getBoolean(nodeData, "customNameValue", false)
        val name = getString(nodeData, "name", "")
        val isNumber = getBoolean(nodeData, "isNumber", false)
        val value = nodeData["value"]

        // Resolve the value (might reference another variable)
        val resolvedValue = when (value) {
            is String -> state.resolveValue(value)
            else -> value ?: ""
        }

        // Convert to number if needed
        val finalValue = if (isNumber) {
            when (resolvedValue) {
                is Number -> resolvedValue.toDouble()
                is String -> resolvedValue.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } else {
            resolvedValue
        }

        // Store the variable
        val varName = if (customNameValue) nodeId else name

        if (varName.isNotEmpty()) {
            state.setVariable(varName, finalValue)
            state.setAnswerVariable(nodeId, finalValue)
            state.setAnswerVariableByKey(varName, finalValue)
        }

        return NodeResult.Proceed()
    }
}

/**
 * Handler for jump-to-node
 * Jumps to a specific node in the flow
 */
class JumpToNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.JUMP_TO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val targetNodeId = getString(nodeData, "targetNodeId", "")
            .ifEmpty { getString(nodeData, "targetId", "") }
            .ifEmpty { getString(nodeData, "jumpTo", "") }
            .ifEmpty { getString(nodeData, "goto", "") }
            .ifEmpty { getString(nodeData, "nodeId", "") }

        if (targetNodeId.isEmpty()) {
            return NodeResult.Error("No target node specified", shouldProceed = true)
        }

        return NodeResult.JumpTo(targetNodeId)
    }
}

/**
 * Handler for business-hours-node
 * Routes based on current time vs business hours
 */
class BusinessHoursNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.BUSINESS_HOURS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val timezone = getString(nodeData, "timezone", TimeZone.getDefault().id)
        val excludeDays = getList<String>(nodeData, "excludeDays")
        val excludeDates = getList<String>(nodeData, "excludeDates")
        val weeklyHours = getList<Map<String, Any?>>(nodeData, "weeklyHours")

        // Get current time in specified timezone
        val tz = TimeZone.getTimeZone(timezone)
        val calendar = Calendar.getInstance(tz)

        // Check excluded dates (format: yyyy-MM-dd)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.timeZone = tz
        val currentDate = dateFormat.format(calendar.time)
        if (currentDate in excludeDates) {
            return NodeResult.Proceed(targetPort = "source-1")  // Outside business hours
        }

        // Check excluded days
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        if (currentDayName in excludeDays) {
            return NodeResult.Proceed(targetPort = "source-1")  // Outside business hours
        }

        // Check weekly hours
        val dayHours = weeklyHours.find {
            it["dayName"]?.toString()?.equals(currentDayName, ignoreCase = true) == true
        }

        if (dayHours == null || getBoolean(dayHours, "available", false) == false) {
            return NodeResult.Proceed(targetPort = "source-1")  // Not available this day
        }

        // Check time slots
        val slots = getList<Map<String, Any?>>(dayHours, "slots")
        val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.US)
        timeFormat.timeZone = tz
        val currentTime = timeFormat.format(calendar.time)

        val withinBusinessHours = slots.any { slot ->
            val start = slot["start"]?.toString() ?: "00:00"
            val end = slot["end"]?.toString() ?: "23:59"
            currentTime >= start && currentTime <= end
        }

        return if (withinBusinessHours) {
            NodeResult.Proceed(targetPort = "source-0")  // Within business hours
        } else {
            NodeResult.Proceed(targetPort = "source-1")  // Outside business hours
        }
    }
}
