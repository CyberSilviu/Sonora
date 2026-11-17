package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint?,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
        val commandExecutorCommand: CommandExecutorCommand? = null,
    ) {
        @Serializable
        data class ContinuationCommand(
            val token: String?,
        )

        @Serializable
        data class CommandExecutorCommand(
            val commands: List<Command>? = null,
        ) {
            @Serializable
            data class Command(
                val continuationCommand: ContinuationCommand? = null,
            )
        }
    }

    fun getContinuation(): String? =
        continuationEndpoint?.continuationCommand?.token
            ?: continuationEndpoint
                ?.commandExecutorCommand
                ?.commands
                ?.firstNotNullOfOrNull { it.continuationCommand?.token }
}