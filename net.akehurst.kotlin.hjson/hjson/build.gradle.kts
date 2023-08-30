
val version_kotlinx:String by project
val version_agl:String by project

dependencies {
    //for class Stack used in HJsonParser
    "commonMainImplementation"("net.akehurst.kotlinx:kotlinx-collections:$version_kotlinx")
    "commonMainImplementation"("net.akehurst.language:agl-processor:$version_agl")
    //commonMainImplementation("net.akehurst.kotlin.json:json:1.2.1")
}
