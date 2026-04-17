package io.gitlab.arturbosch.detekt.idea

import dev.detekt.api.Issue

val Issue.id: String
    get() = ruleInstance.id

val Issue.description: String
    get() = ruleInstance.description

val Issue.baselineId: String
    get() = "${ruleInstance.id}:${location.path.fileName}:${entity.signature}"

val Issue.isAutoCorrectable: Boolean
    get() = ruleInstance.ruleSetId.value == FORMATTING_RULE_SET_ID

fun Issue.messageOrDescription(): String = message.ifBlank { description }
