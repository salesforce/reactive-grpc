/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs

import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import jline.console.ConsoleReader
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Demonstrates building a gRPC streaming client using Reactor and Reactive-Grpc.
 */
class ReactorChatClient

fun main(args: Array<String>) {
    // Connect to the sever
    val channel = ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext(true).build()
    val stub = ReactorChatGrpc.newReactorStub(channel)

    val done = CountDownLatch(1)
    val console = ConsoleReader()

    // Prompt the user for their name
    console.println("Press ctrl+D to quit")
    val author = console.readLine("Who are you? > ")
    stub.postMessage("$author joined.".toMessage(author)).subscribe()

    // Subscribe to incoming messages
    val chatSubscription = stub.getMessages(Mono.just(Empty.getDefaultInstance())).subscribe(
            {
                // Don't re-print our own messages
                if (it.author != author) {
                    ConsoleUtil.printLine(console, it.author, it.message)
                }
            },
            {
                ConsoleUtil.printLine(console, "ERROR", it.message)
                done.countDown()
            },
            { done.countDown() }
    )

    // Publish outgoing messages
    Flux.fromIterable(ConsoleIterator(console, "$author > "))
            .map({ it.toMessage(author) })
            .flatMap({ stub.postMessage(it) })
            .subscribe(
                    {},
                    {
                        ConsoleUtil.printLine(console, "ERROR", it.message)
                        done.countDown()
                    },
                    { done.countDown() }
            )

    // Wait for a signal to exit, then clean up
    done.await()
    stub.postMessage("$author left.".toMessage(author)).subscribe()
    chatSubscription.dispose()
    channel.shutdown()
    channel.awaitTermination(1, TimeUnit.SECONDS)
    console.terminal.restore()
}

private fun String.toMessage(author: String): Mono<ChatProto.ChatMessage> {
    return Mono.just(
            ChatProto.ChatMessage.newBuilder()
                    .setAuthor(author)
                    .setMessage(this)
                    .build()
    )
}

