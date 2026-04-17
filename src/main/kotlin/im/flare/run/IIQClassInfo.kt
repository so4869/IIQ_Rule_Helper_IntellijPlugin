package im.flare.run

data class IIQClassInfo(
    val qualifiedName: String,
    val imports: String,
    val helperMethods: String,
    val executeBody: String,
    val name: String?,
    val type: String?
)
