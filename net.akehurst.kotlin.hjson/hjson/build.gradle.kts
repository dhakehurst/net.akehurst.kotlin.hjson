
val version_kotlinx:String by project
val version_korio:String by project

dependencies {
    //for class Stack used in HJsonParser
    "commonMainImplementation"("net.akehurst.kotlinx:kotlinx-collections:$version_kotlinx")
    //commonMainImplementation("net.akehurst.kotlin.json:json:1.2.1")
}
