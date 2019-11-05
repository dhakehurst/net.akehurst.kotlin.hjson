val version_kotlinx:String by project
val version_korio:String  = "1.8.4"

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-collections:$version_kotlinx")

    commonTestImplementation("com.soywiz.korlibs.korio:korio:$version_korio")
}
