/**
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import net.akehurst.language.agl.collections.toSeparatedList
import net.akehurst.language.agl.syntaxAnalyser.SyntaxAnalyserByMethodRegistrationAbstract
import net.akehurst.language.agl.syntaxAnalyser.matchedTextNoSkip
import net.akehurst.language.api.analyser.SyntaxAnalyser
import net.akehurst.language.api.grammar.GrammarItem
import net.akehurst.language.api.processor.LanguageIssue
import net.akehurst.language.api.processor.SentenceContext
import net.akehurst.language.api.sppt.SpptDataNodeInfo

class SyntaxAnalyserHJson(

) : SyntaxAnalyserByMethodRegistrationAbstract<HJsonDocument>() {

    override val embeddedSyntaxAnalyser: Map<String, SyntaxAnalyser<HJsonDocument>> = emptyMap()

    override fun configure(
        configurationContext: SentenceContext<GrammarItem>,
        configuration: Map<String, Any>
    ): List<LanguageIssue> {
        TODO("not implemented")
    }

    override fun registerHandlers() {
        super.register(this::hjson)
        super.registerFor("value", this::value_)
        super.registerFor("object", this::object_)
        super.register(this::property)
        super.register(this::array)
        super.register(this::arrayElements)
        super.register(this::arrayElementsSeparated)
        super.register(this::arrayElementsSimple)
        super.register(this::name)
        super.register(this::literal)
        super.register(this::string)
        super.register(this::ID)
        super.register(this::NULL)
        super.register(this::BOOLEAN)
        super.register(this::NUMBER)
        super.register(this::QUOTELESS_STRING)
        super.register(this::DOUBLE_QUOTE_STRING)
        super.register(this::MULTI_LINE_STRING)
    }

    // hjson = value ;
    fun hjson(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonDocument {
        val doc = HJsonDocument("hjson")
        doc.root = children[0] as HJsonValue
        return doc
    }

    // value = literal | object | array ;
    fun value_(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonValue =
        children[0] as HJsonValue

    // object = '{' property* '}' ;
    fun object_(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonObject {
        val l = children[1] as List<Pair<String, HJsonValue>>? ?: emptyList()
        val props = when {
            l.isNotEmpty() && null==l[0] -> emptyList()
            else -> l
        }
        val obj = HJsonUnreferencableObject()
        props.forEach { (n,v) -> obj.setProperty(n,v) }
        return obj
    }

    // property = name ':' value ;
    fun property(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): Pair<String, HJsonValue> {
        val name = children[0] as String
        val value = children[2] as HJsonValue
        return Pair(name, value)
    }

    // array = '[' arrayElements ']' ;
    fun array(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonArray {
        val l = children[1] as List<HJsonValue>
        return HJsonArray(l)
    }

    // arrayElements = arrayElementsSeparated | arrayElementsSimple ;
    fun arrayElements(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): List<HJsonValue> =
        children[0] as List<HJsonValue>

    // arrayElementsSeparated = [ value / ',' ]* ;
    fun arrayElementsSeparated(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): List<HJsonValue> =
        (children as List<*>).toSeparatedList<HJsonValue,String>().items

    // arrayElementsSimple =  value* ;
    fun arrayElementsSimple(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): List<HJsonValue> =
        children as List<HJsonValue>

    // name = DOUBLE_QUOTE_STRING | ID ;
    fun name(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): String {
        val v = children[0]
        return when(v) {
            is HJsonString -> v.value
            is String -> v
            else -> error("should never happen")
        }
    }

    // literal = string | NUMBER | BOOLEAN | NULL ;
    fun literal(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonValue =
        children[0] as HJsonValue

    // string = DOUBLE_QUOTE_STRING | QUOTELESS_STRING | MULTI_LINE_STRING ;
    fun string(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonString =
        children[0] as HJsonString

    // leaf ID = "[^\{\}\[\],:\n\r\t ]+" ;
    fun ID(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): String =
        nodeInfo.node.matchedTextNoSkip(sentence)

    // leaf NULL = 'null' ;
    fun NULL(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonNull =
        HJsonNull

    // leaf BOOLEAN = "true|false" ;
    fun BOOLEAN(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonBoolean {
        val str = nodeInfo.node.matchedTextNoSkip(sentence)
        return when (str) {
            "true" -> HJsonBoolean(true)
            "false" -> HJsonBoolean(false)
            else -> error("Invalid value for BOOLEAN '$str'")
        }
    }

    // leaf NUMBER = "-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?" ;
    fun NUMBER(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonNumber {
        val str = nodeInfo.node.matchedTextNoSkip(sentence)
        return HJsonNumber(str)
    }

    // leaf QUOTELESS_STRING = "[^\{\}\[\],:\n][^\n]+" ;
    fun QUOTELESS_STRING(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonString {
        val str = nodeInfo.node.matchedTextNoSkip(sentence)
        return HJsonString(str)
    }

    // leaf DOUBLE_QUOTE_STRING = "\"([^"\\]|\\.)*\"" ;
    fun DOUBLE_QUOTE_STRING(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonString {
        val str = nodeInfo.node.matchedTextNoSkip(sentence)
        val s = str.substring(1, str.length - 1)
        return HJsonString(s)
    }

    // leaf MULTI_LINE_STRING = "'''([^"\\]|\\.)*'''" ;
    fun MULTI_LINE_STRING(nodeInfo: SpptDataNodeInfo, children: List<Any?>, sentence: String): HJsonString {
        val str = nodeInfo.node.matchedTextNoSkip(sentence)
        val s = str.substring(3, str.length - 3)
        return HJsonString(s)
    }
}