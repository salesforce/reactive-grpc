/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs

import jline.console.ConsoleReader
import java.io.IOException
import javax.annotation.Nonnull

/**
 * Adapts jLine to Iterator.
 */
class ConsoleIterator internal constructor(private val console: ConsoleReader, private val prompt: String) : Iterable<String>, Iterator<String> {
    private var lastLine: String? = null

    @Nonnull
    override fun iterator(): Iterator<String> {
        return this
    }

    override fun hasNext(): Boolean {
        return try {
            lastLine = console.readLine(prompt)
            lastLine != null
        } catch (e: IOException) {
            false
        }

    }

    override fun next(): String {
        return this.lastLine!!
    }
}
