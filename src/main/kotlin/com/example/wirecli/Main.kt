package com.example.wirecli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option

class HelloCommand : CliktCommand(name = "helloworld", help = "Simple Hello World CLI") {
    private val name by option("--name", "-n", help = "Name to greet").default("world")

    override fun run() {
        echo("Hello, $name!")
    }
}

fun main(args: Array<String>) = HelloCommand().main(args)
