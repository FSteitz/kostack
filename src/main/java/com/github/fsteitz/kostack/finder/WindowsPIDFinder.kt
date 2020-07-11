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
package com.github.fsteitz.kostack.finder

import com.github.fsteitz.kostack.command.CommandExecutor
import java.io.IOException

/**
 * @author Florian Steitz (florian@fsteitz.com)
 */
class WindowsPIDFinder : PIDFinder {

  private val taskListCommand = "tasklist /v /fo csv"

  @Throws(IOException::class)
  override fun find(processSearchText: String): List<String> {
    val pids = mutableListOf<String>()

    CommandExecutor.execute(taskListCommand) { stdin ->
      var line: String?

      try {
        while (stdin.readLine().also { line = it } != null) {
          val columns = line?.split(",") ?: emptyList()

          if (columns.size < 9) {
            System.err.println("FEHLER: Format von 'tasklist' ist ungueltig")
          } else if (processSearchTextMatches(processSearchText, columns)) {
            val pid = columns[1].replace("\"".toRegex(), "")

            println("PID fuer '$processSearchText' ermittelt: $pid")
            pids.add(pid)
          }
        }
      } catch (e: IOException) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden")
        e.printStackTrace()
      }
    }

    return pids
  }

  private fun processSearchTextMatches(searchText: String, columns: List<String>): Boolean {
    val windowTitle = columns[columns.size - 1] // Is always in the last column
    val imageName = columns[0]                  // Is always in the first column

    return windowTitle.contains(searchText) || imageName.contains(searchText)
  }

}