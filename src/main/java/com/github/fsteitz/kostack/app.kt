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
package com.github.fsteitz.kostack

import kotlin.system.exitProcess

/**
 * @author Florian Steitz (florian@fsteitz.com)
 */
fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("FEHLER: Es wurden keine Programmparameter uebergeben")
    exitProcess(-1)
  } else if (args.size != 3) {
    System.err.println("FEHLER: Programmparameter sind ungueltig")
    exitProcess(-1)
  }

  Kostack.createThreadDumps(AppParams(args[0], args[1], args[2].split(",")))
}