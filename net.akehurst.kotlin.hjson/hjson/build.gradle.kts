plugins {
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin")
}

kotlinxReflect {

}

val version_kotlinx:String by project
val version_korio:String by project

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-collections:$version_kotlinx")

    //commonTestImplementation("com.soywiz.korlibs.korio:korio:$version_korio")
}
