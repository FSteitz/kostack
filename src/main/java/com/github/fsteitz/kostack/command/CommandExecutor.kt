/*
 * Copyright 2020 Florian Steitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.fsteitz.kostack.command

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * @author Florian Steitz (florian@fsteitz.com)
 */
object CommandExecutor {

  @Throws(IOException::class)
  fun execute(command: String, stdinConsumer: (BufferedReader) -> Unit) {
    val process = Runtime.getRuntime().exec(command)
    val stdin = BufferedReader(InputStreamReader(process.inputStream))
    val stderr = BufferedReader(InputStreamReader(process.errorStream))
    var line: String?

    // Read the output from the command
    stdinConsumer(stdin)
    stdin.close()

    // Read any errors from the attempted command
    while (stderr.readLine().also { line = it } != null) {
      println(line)
    }

    stderr.close()
  }

}