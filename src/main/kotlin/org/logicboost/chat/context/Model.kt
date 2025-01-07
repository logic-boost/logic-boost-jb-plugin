import com.intellij.openapi.vfs.VirtualFile

/**
 * Data class representing a context file with its content.
 *
 * @property virtualFile The virtual file reference
 * @property content The content of the file as string
 */
data class ContextFile(
    val virtualFile: VirtualFile,
    val content: String
)
