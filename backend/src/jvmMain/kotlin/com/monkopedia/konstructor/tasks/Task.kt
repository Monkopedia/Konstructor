package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.common.TaskResult

interface Task {
    suspend fun execute(): TaskResult
}