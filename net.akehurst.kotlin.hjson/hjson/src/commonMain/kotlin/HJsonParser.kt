/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.hjson

import net.akehurst.kotlinx.collections.Stack

class HJsonParserException : RuntimeException {
    constructor(message: String) : super(message)
}

class SimpleScanner(private val input: CharSequence) {
    var position: Int = 0

    fun hasMore(): Boolean {
        return this.position < this.input.length
    }

    fun hasNext(literal: String): Boolean {
        return this.input.startsWith(literal, this.position, false)
    }

    fun hasNext(pattern: Regex): Boolean {
        val lookingAt = pattern.find(this.input, this.position)?.range?.start == this.position
        return lookingAt
    }

    fun next(literal: String): String {
        //assumes hasNext already called
        this.position += literal.length
        return literal
    }

    fun next(pattern: Regex): String {
        val m = pattern.find(this.input, this.position)
        val lookingAt = (m?.range?.start == this.position)
        if (lookingAt) {
            val match = m?.value ?: throw HJsonParserException("Should never happen")
            this.position += m.value.length
            return match
        } else {
            throw HJsonParserException("Error scanning for pattern ${pattern} at Position ${this.position}")
        }
    }
}

object HJsonParser {

    val TOKEN_WHITESPACE_OR_COMMENT = Regex("([ \\t\\f\\x0B]+)|(#(.)*\n)|(//(.)*\n)|(/[*](.|\n)*[*]/)", RegexOption.MULTILINE)
    val TOKEN_EOL_OR_WHITESPACE_OR_COMMENT = Regex("([ \\t\\f\\x0B\\r\\n]+)|(#(.)*\n)|(//(.)*\n)|(/[*](.|\n)*[*]/)", RegexOption.MULTILINE)
    val TOKEN_TO_EOL_STRING = Regex("(?:\\\\?(.))+?\n", RegexOption.MULTILINE)
    val TOKEN_EOL = Regex("\n", RegexOption.MULTILINE)
    val TOKEN_QUOTED_STRING = Regex("\"(?:\\\\?(.))*?\"", RegexOption.MULTILINE)
    val TOKEN_MULTILINE_STRING = Regex("'''(?:\\\\?(.|\n))*?'''", RegexOption.MULTILINE)
    val TOKEN_NULL = "null"
    val TOKEN_NUMBER = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
    val TOKEN_BOOLEAN = Regex("true|false", RegexOption.IGNORE_CASE)
    val TOKEN_ARRAY_START = "["
    val TOKEN_ARRAY_END = "]"
    val TOKEN_OBJECT_START = "{"
    val TOKEN_OBJECT_END = "}"
    val TOKEN_UNQUOTED_PROPERTY_NAME = Regex("[^,:\\[\\]{} \\t\\n\\x0B\\f\\r]+")
    val TOKEN_PROPERTY_SEP = ":"
    val TOKEN_SEP = ","

    fun consumeWhitespaceOrComment(scanner: SimpleScanner) {
        while (scanner.hasMore() && scanner.hasNext(TOKEN_WHITESPACE_OR_COMMENT)) {
            scanner.next(TOKEN_WHITESPACE_OR_COMMENT)
        }
    }

    fun consumeEolOrWhitespaceOrComment(scanner: SimpleScanner) {
        while (scanner.hasMore() && scanner.hasNext(TOKEN_EOL_OR_WHITESPACE_OR_COMMENT)) {
            scanner.next(TOKEN_EOL_OR_WHITESPACE_OR_COMMENT)
        }
    }

    fun process(input: String): HJsonDocument {
        val doc = HJsonDocument("json")
        if (input.isEmpty()) {
            throw HJsonParserException("Expected Json content but input was empty")
        }
        val scanner = SimpleScanner(input)
        val path = Stack<String>()
        val nameStack = Stack<String>()
        val valueStack = Stack<HJsonValue>()
        while (scanner.hasMore()) {
            when {
                scanner.hasNext(TOKEN_WHITESPACE_OR_COMMENT) -> scanner.next(TOKEN_WHITESPACE_OR_COMMENT)
                scanner.hasNext(TOKEN_NULL) -> {
                    scanner.next(TOKEN_NULL)
                    valueStack.push(HJsonNull)
                }
                scanner.hasNext(TOKEN_BOOLEAN) -> {
                    val value = scanner.next(TOKEN_BOOLEAN)
                    valueStack.push(HJsonBoolean(value.toBoolean()))
                }
                scanner.hasNext(TOKEN_NUMBER) -> {
                    val value = scanner.next(TOKEN_NUMBER)
                    valueStack.push(HJsonNumber(value))
                }
                scanner.hasNext(TOKEN_QUOTED_STRING) -> {
                    val value = scanner.next(TOKEN_QUOTED_STRING)
                    valueStack.push(HJsonString.decode(value.drop(1).dropLast(1)))
                }
                scanner.hasNext(TOKEN_MULTILINE_STRING) -> {
                    val value = scanner.next(TOKEN_MULTILINE_STRING)
                    valueStack.push(HJsonString.decode(value.drop(3).dropLast(3)))
                }
                scanner.hasNext(TOKEN_ARRAY_START) -> {
                    scanner.next(TOKEN_ARRAY_START)
                    path.push("0") // path segment for first element
                    valueStack.push(HJsonArray())
                    consumeEolOrWhitespaceOrComment(scanner)
                    //check for empty array
                    if (scanner.hasNext(TOKEN_ARRAY_END)) {
                        scanner.next(TOKEN_ARRAY_END)
                        path.pop()
                    }
                }
                scanner.hasNext(TOKEN_ARRAY_END) -> {
                    scanner.next(TOKEN_ARRAY_END)
                    path.pop()
                    val value = valueStack.pop()
                    val peek = valueStack.peek()
                    if (peek is HJsonArray) {
                        peek.addElement(value)
                    } else {
                        throw HJsonParserException("Expected an Array but was a ${peek}")
                    }
                }
                scanner.hasNext(TOKEN_SEP) -> {
                    scanner.next(TOKEN_SEP)
                    consumeEolOrWhitespaceOrComment(scanner)
                    path.pop()
                    val value = valueStack.pop()
                    val peek = valueStack.peek()
                    when (peek) {
                        is HJsonArray -> {
                            peek.addElement(value)
                            path.push(peek.elements.size.toString())
                        }
                        is HJsonObject -> {
                            val name = nameStack.pop()
                            peek.setProperty(name, value)
                        }
                        else -> throw HJsonParserException("Expected an Array or an Object but was a ${peek::class.simpleName}")
                    }
                }
                scanner.hasNext(TOKEN_OBJECT_START) -> {
                    scanner.next(TOKEN_OBJECT_START)
                    valueStack.push(HJsonUnreferencableObject())
                    consumeEolOrWhitespaceOrComment(scanner)
                    //check for empty object
                    if (scanner.hasNext(TOKEN_OBJECT_END)) {
                        scanner.next(TOKEN_OBJECT_END)
                    }
                }
                scanner.hasNext(TOKEN_OBJECT_END) -> {
                    scanner.next(TOKEN_OBJECT_END)
                    path.pop()
                    val value = valueStack.pop()
                    val peek = valueStack.peek()
                    if (peek is HJsonObject) {
                        val name = nameStack.pop()
                        peek.setProperty(name, value)
                        // handle different kinds of object!
                        when {
                            // JsonReference
                            (1 == peek.property.size && peek.property.containsKey(HJson.REF)) -> {
                                val refStr = peek.property[HJson.REF]!!.asString().value
                                // remove leading '/' then split
                                val refStr1 = refStr.substring(1)
                                val refPath = if (refStr1.isEmpty()) emptyList<String>() else refStr1.split("/")
                                val ref = HJsonReference(doc, refPath)
                                valueStack.pop()
                                valueStack.push(ref)
                            }
                            // JsonReferenceableObject
                            (peek.property.containsKey(HJsonDocument.TYPE) && peek.property[HJsonDocument.TYPE] == HJsonDocument.OBJECT) -> {
                                val jPath = path.elements.filter {
                                    it != HJsonDocument.ELEMENTS && it != HJsonDocument.VALUE
                                }
                                val obj = HJsonReferencableObject(doc, jPath)
                                valueStack.pop()
                                obj.property = peek.property
                                valueStack.push(obj)
                            }
                        }

                    } else {
                        throw HJsonParserException("Expected an Object but was a ${peek::class.simpleName}")
                    }
                }
                scanner.hasNext(TOKEN_PROPERTY_SEP) -> {
                    scanner.next(TOKEN_PROPERTY_SEP)
                    val name = valueStack.pop().asString().value
                    nameStack.push(name)
                    path.push(name)
                }
                scanner.hasNext(TOKEN_UNQUOTED_PROPERTY_NAME) && valueStack.elements.isNotEmpty() && valueStack.peek() is HJsonObject -> {
                    val value = scanner.next(TOKEN_UNQUOTED_PROPERTY_NAME)
                    valueStack.push(HJsonString.decode(value))
                }
                scanner.hasNext(TOKEN_TO_EOL_STRING) -> {
                    val value1 = scanner.next(TOKEN_TO_EOL_STRING)
                    consumeEolOrWhitespaceOrComment(scanner)
                    valueStack.push(HJsonString.decode(value1.dropLast(1)))
                    if (scanner.hasNext(TOKEN_ARRAY_END) || scanner.hasNext(TOKEN_OBJECT_END)) {
                        //delay
                    } else {
                        path.pop()
                        val value = valueStack.pop()
                        val peek = valueStack.peek()
                        when (peek) {
                            is HJsonArray -> {
                                peek.addElement(value)
                                path.push(peek.elements.size.toString())
                            }
                            is HJsonObject -> {
                                val name = nameStack.pop()
                                peek.setProperty(name, value)
                            }
                            else -> throw HJsonParserException("Expected an Array or an Object but was a ${peek::class}")
                        }
                    }
                }
                scanner.hasNext(TOKEN_EOL) -> {
                    scanner.next(TOKEN_EOL)
                    consumeEolOrWhitespaceOrComment(scanner)
                    if (scanner.hasNext(TOKEN_ARRAY_END) || scanner.hasNext(TOKEN_OBJECT_END)) {
                        //delay
                    } else {
                        if (1 == valueStack.elements.size) {
                            //must be end of input!
                            //TODO: check end of input
                        } else {
                            path.pop()
                            val value = valueStack.pop()
                            val peek = valueStack.peek()
                            when (peek) {
                                is HJsonArray -> {
                                    peek.addElement(value)
                                    path.push(peek.elements.size.toString())
                                }
                                is HJsonObject -> {
                                    val name = nameStack.pop()
                                    peek.setProperty(name, value)
                                }
                                else -> throw HJsonParserException("Expected an Array or an Object but was a ${peek::class}")
                            }
                        }
                    }
                }
                else -> throw HJsonParserException("Unexpected character at position ${scanner.position} - '${input.substring(scanner.position)}'")
            }
        }
        if (1==valueStack.elements.size) {
            doc.root = valueStack.pop()
        } else {
            throw HJsonParserException("invalid input,  probably an object or array is not closed")
        }
        return doc
    }
}