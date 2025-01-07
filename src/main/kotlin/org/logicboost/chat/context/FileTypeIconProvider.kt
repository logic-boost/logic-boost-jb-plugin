package org.logicboost.chat.context

import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Object responsible for providing appropriate icons for different file types.
 */
object FileTypeIconProvider {
    private val EXTENSION_TO_ICON = mapOf(
        "kt" to loadIcon("/icons/kotlin.png"),
        "kts" to loadIcon("/icons/kotlin.png"),
        "java" to loadIcon("/icons/java.png"),
        "jar" to loadIcon("/icons/java.png"),
        "py" to loadIcon("/icons/python.png"),
        "pyi" to loadIcon("/icons/python.png"),
        "pyc" to loadIcon("/icons/python.png"),
        "pyd" to loadIcon("/icons/python.png"),
        "pyw" to loadIcon("/icons/python.png"),
        "js" to loadIcon("/icons/javascript.png"),
        "jsx" to loadIcon("/icons/javascript.png"),
        "mjs" to loadIcon("/icons/javascript.png"),
        "ts" to loadIcon("/icons/typescript.png"),
        "tsx" to loadIcon("/icons/typescript.png"),
        "html" to loadIcon("/icons/html.png"),
        "htm" to loadIcon("/icons/html.png"),
        "xhtml" to loadIcon("/icons/html.png"),
        "css" to loadIcon("/icons/css.png"),
        "scss" to loadIcon("/icons/css.png"),
        "sass" to loadIcon("/icons/css.png"),
        "less" to loadIcon("/icons/css.png"),
        "styl" to loadIcon("/icons/css.png"),
        "xml" to loadIcon("/icons/xml.png"),
        "xsd" to loadIcon("/icons/xml.png"),
        "xsl" to loadIcon("/icons/xml.png"),
        "xslt" to loadIcon("/icons/xml.png"),
        "wsdl" to loadIcon("/icons/xml.png"),
        "tld" to loadIcon("/icons/xml.png"),
        "json" to loadIcon("/icons/json.png"),
        "jsonc" to loadIcon("/icons/json.png"),
        "yaml" to loadIcon("/icons/yaml.png"),
        "yml" to loadIcon("/icons/yaml.png"),
        "md" to loadIcon("/icons/markdown.png"),
        "markdown" to loadIcon("/icons/markdown.png"),
        "go" to loadIcon("/icons/go.png"),
        "mod" to loadIcon("/icons/go.png"),
        "rs" to loadIcon("/icons/rust.png"),
        "rlib" to loadIcon("/icons/rust.png"),
        "cpp" to loadIcon("/icons/cpp.png"),
        "cxx" to loadIcon("/icons/cpp.png"),
        "cc" to loadIcon("/icons/cpp.png"),
        "c" to loadIcon("/icons/cpp.png"),
        "h" to loadIcon("/icons/cpp.png"),
        "hpp" to loadIcon("/icons/cpp.png"),
        "hxx" to loadIcon("/icons/cpp.png"),
        "cs" to loadIcon("/icons/csharp.png"),
        "csx" to loadIcon("/icons/csharp.png"),
        "rb" to loadIcon("/icons/ruby.png"),
        "rbw" to loadIcon("/icons/ruby.png"),
        "php" to loadIcon("/icons/php.png"),
        "phtml" to loadIcon("/icons/php.png"),
        "php4" to loadIcon("/icons/php.png"),
        "php5" to loadIcon("/icons/php.png"),
        "php7" to loadIcon("/icons/php.png"),
        "phps" to loadIcon("/icons/php.png"),
        "swift" to loadIcon("/icons/swift.png"),
        "swiftmodule" to loadIcon("/icons/swift.png"),
        "scala" to loadIcon("/icons/scala.png"),
        "sc" to loadIcon("/icons/scala.png"),
        "groovy" to loadIcon("/icons/groovy.png"),
        "gvy" to loadIcon("/icons/groovy.png"),
        "gy" to loadIcon("/icons/groovy.png"),
        "gsh" to loadIcon("/icons/groovy.png"),
        "dart" to loadIcon("/icons/dart.png"),
        "elm" to loadIcon("/icons/elm.png"),
        "erl" to loadIcon("/icons/erlang.png"),
        "hrl" to loadIcon("/icons/erlang.png"),
        "ex" to loadIcon("/icons/elixir.png"),
        "exs" to loadIcon("/icons/elixir.png"),
        "fs" to loadIcon("/icons/fsharp.png"),
        "fsi" to loadIcon("/icons/fsharp.png"),
        "fsx" to loadIcon("/icons/fsharp.png"),
        "fsscript" to loadIcon("/icons/fsharp.png"),
        "hs" to loadIcon("/icons/haskell.png"),
        "lhs" to loadIcon("/icons/haskell.png"),
        "jl" to loadIcon("/icons/julia.png"),
        "lua" to loadIcon("/icons/lua.png"),
        "m" to loadIcon("/icons/objectivec.png"),
        "mm" to loadIcon("/icons/objectivec.png"),
        "pas" to loadIcon("/icons/pascal.png"),
        "pp" to loadIcon("/icons/pascal.png"),
        "pl" to loadIcon("/icons/perl.png"),
        "pm" to loadIcon("/icons/perl.png"),
        "t" to loadIcon("/icons/perl.png"),
        "r" to loadIcon("/icons/r.png"),
        "rdata" to loadIcon("/icons/r.png"),
        "rds" to loadIcon("/icons/r.png"),
        "rda" to loadIcon("/icons/r.png"),
        "clj" to loadIcon("/icons/clojure.png"),
        "cljs" to loadIcon("/icons/clojure.png"),
        "cljc" to loadIcon("/icons/clojure.png"),
        "edn" to loadIcon("/icons/clojure.png"),
        "coffee" to loadIcon("/icons/coffeescript.png"),
        "litcoffee" to loadIcon("/icons/coffeescript.png"),
        "vue" to loadIcon("/icons/vue.png"),
        "sql" to loadIcon("/icons/sql.png"),
        "sh" to loadIcon("/icons/shell.png"),
        "bash" to loadIcon("/icons/shell.png"),
        "zsh" to loadIcon("/icons/shell.png"),
        "toml" to loadIcon("/icons/toml.png"),
        "gradle" to loadIcon("/icons/gradle.png"),
        "cmake" to loadIcon("/icons/cmake.png")
    )

    /**
     * Gets the appropriate icon for a virtual file based on its type.
     */
    fun getIcon(file: VirtualFile): Icon {
        val extension = file.extension?.lowercase(Locale.getDefault()) ?: return AllIcons.FileTypes.Any_type
        return EXTENSION_TO_ICON[extension] ?: file.fileType.icon ?: AllIcons.FileTypes.Any_type
    }

    /**
     * Loads an icon from the resource path.
     */
    private fun loadIcon(path: String): Icon? {
        return try {
            FileTypeIconProvider::class.java.getResource(path)?.let { ImageIcon(it) }
        } catch (e: Exception) {
            null
        }
    }
}
