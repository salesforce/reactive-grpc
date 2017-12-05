/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs

import jline.console.ConsoleReader
import jline.console.CursorBuffer

/**
 * Utility methods for working with jLine.
 */
object ConsoleUtil {

    fun printLine(console: ConsoleReader, author: String, message: String?) {
        val stashed = stashLine(console)
        console.println("$author > $message")
        unstashLine(console, stashed)
        console.flush()
    }

    private fun stashLine(console: ConsoleReader): CursorBuffer {
        val stashed = console.cursorBuffer.copy()
        console.output.write("\u001b[1G\u001b[K")
        console.flush()
        return stashed
    }


    private fun unstashLine(console: ConsoleReader, stashed: CursorBuffer) {
        console.resetPromptLine(console.prompt, stashed.toString(), stashed.cursor)
    }
}
