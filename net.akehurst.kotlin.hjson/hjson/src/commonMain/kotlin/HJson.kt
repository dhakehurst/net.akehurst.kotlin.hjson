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

import net.akehurst.language.agl.processor.Agl
import net.akehurst.language.agl.processor.IssueHolder
import net.akehurst.language.agl.processor.ProcessResultDefault
import net.akehurst.language.agl.syntaxAnalyser.ContextSimple
import net.akehurst.language.agl.syntaxAnalyser.TypeModelFromGrammar
import net.akehurst.language.api.processor.LanguageProcessor
import net.akehurst.language.api.processor.LanguageProcessorPhase

object HJson {

    val REF = "\$ref"
    val KEY_WORDS = arrayOf("true", "false", "null")

    internal val processor: LanguageProcessor<HJsonDocument, ContextSimple> by lazy {
        val grammarStr = fetchGrammarStr()
        val res = Agl.processorFromString(
            grammarDefinitionStr = grammarStr,
            Agl.configuration<HJsonDocument, ContextSimple> {
                typeModelResolver { p ->
                    ProcessResultDefault(
                        TypeModelFromGrammar.createFrom(p.grammar!!),
                        IssueHolder(LanguageProcessorPhase.ALL)
                    )
                }
                syntaxAnalyserResolver { p ->
                    ProcessResultDefault(
                        SyntaxAnalyserHJson(),
                        IssueHolder(LanguageProcessorPhase.ALL)
                    )
                }
                semanticAnalyserResolver { p ->
                    ProcessResultDefault(
                        SemanticAnalyserHJson(),
                        IssueHolder(LanguageProcessorPhase.ALL)
                    )
                }
            }
        )
        val proc = when {
            res.issues.errors.isEmpty() -> res.processor!!
            else -> error(res.issues.toString())
        }
        proc.buildFor()
    }

    internal fun fetchGrammarStr(): String {
        return """
            namespace net.akehurst.hjson
            
            grammar HJson {
                skip leaf WHITE_SPACE = "\s+" ;
                skip leaf COMMENT
                 = "/\*[^*]*\*+([^*/][^*]*\*+)*/"
                 | "(//|#)[^\n\r]*"
                 ;
    
                hjson = value ;
                value = literal | object | array ;
                object = '{' property* '}' ;
                property = name ':' value ','?;
                array = '[' arrayElements ']' ;
                arrayElements = arrayElementsSeparated | arrayElementsSimple ;
                arrayElementsSeparated = [ value / ',' ]* ;
                arrayElementsSimple =  value* ;
                name = DOUBLE_QUOTE_STRING | ID ;
                
                literal = string | NUMBER | BOOLEAN | NULL ;
                string = QUOTELESS_STRING | DOUBLE_QUOTE_STRING | MULTI_LINE_STRING ;
                
                leaf ID = "[^\{\}\[\],:\n\r\t ]+" ;
                leaf NULL = 'null' ;
                leaf BOOLEAN = "true|false" ;
                leaf NUMBER = "-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?" ;
                leaf QUOTELESS_STRING = "[^\{\}\[\],:\n][^\n]+" ;
                leaf DOUBLE_QUOTE_STRING = "\"([^\n\"\\]|\\.)*\"" ;
                leaf MULTI_LINE_STRING = "'''([^\\]|\\.)*'''" ;
            }
            
            """.trimIndent()
    }

    fun process(jsonString: String): HJsonDocument {
        val res = this.processor.process(jsonString)
        return when {
            res.issues.errors.isEmpty() -> res.asm!!
            else -> throw HJsonParserException(res.issues.toString(), res.issues.errors.first().location!!.line, res.issues.errors.first().location!!.column, res.issues.errors.first().message)
        }
        //return HJsonParser.process(jsonString)
    }
}