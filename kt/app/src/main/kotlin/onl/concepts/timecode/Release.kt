package onl.concepts.timecode

import com.jcabi.manifests.Manifests
import picocli.CommandLine.IVersionProvider

object Release {
    val application: String by lazy {
        Manifests.read("Application")
    }

    val name: String by lazy {
        application.split('.').last()
    }

    val release by lazy {
        Manifests.read("Release")
    }

    class VersionProvider : IVersionProvider {
        override fun getVersion(): Array<String> = arrayOf("$name@$release")
    }
}
